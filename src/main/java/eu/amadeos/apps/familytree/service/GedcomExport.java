package eu.amadeos.apps.familytree.service;

import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.MediaRecord;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.PersonName;
import eu.amadeos.apps.familytree.gedcom.GedcomConverter;
import eu.amadeos.apps.familytree.gedcom.GedcomNode;
import eu.amadeos.apps.familytree.gedcom.GedcomVersion;
import eu.amadeos.apps.familytree.gedcom.GedcomWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a family tree for others: as GEDCOM 7.0, as GEDCOM 5.5.1 (for older programs) or as GEDZIP (GEDCOM 7.0
 * with the photos in one ZIP file). People who may still be alive can be left out or shown with their name only,
 * and people marked as private are always left out.
 */
public final class GedcomExport {
    public enum Format {
        GEDCOM_7("ged"), GEDCOM_551("ged"), GEDZIP("gdz");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        public String textKey() {
            return "export.format." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    public enum LivingPeople {
        /** Everything about living people is written. */
        INCLUDE,
        /** Only name, sex and family links; given names become "Living". */
        NAME_ONLY,
        /** Living people are left out entirely. */
        LEAVE_OUT;

        public String textKey() {
            return "export.living." + name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    public record Options(Format format, LivingPeople living, String livingName) {
    }

    /** How many people were written and how many were left out or shortened. */
    public record Result(int written, int leftOut, int shortened, int mediaFiles) {
    }

    private GedcomExport() {
    }

    public static Result export(FamilyTree tree, StoredTree stored, Options options, Path target, String version)
            throws IOException {
        FamilyTree copy = tree.copy();
        int[] counts = privacy(copy, options);
        GedcomVersion v = options.format() == Format.GEDCOM_551 ? GedcomVersion.V551 : GedcomVersion.V70;
        int mediaFiles = 0;
        Path part = target.resolveSibling(target.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(part)) {
            if (options.format() == Format.GEDZIP) {
                mediaFiles = gedzip(copy, stored, os, version);
            } else {
                write(copy, v, os, version, target.getFileName().toString());
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        Files.move(part, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new Result(copy.count("INDI"), counts[0], counts[1], mediaFiles);
    }

    /** Applies the privacy options to a copy; returns {left out, shortened}. */
    // --8<-- [start:privacy]
    static int[] privacy(FamilyTree copy, Options options) {
        Living living = Living.now();
        int leftOut = 0, shortened = 0;
        // Decide first, change after: removing a person changes the estimate of others.
        List<Person> remove = new ArrayList<>();
        List<Person> shorten = new ArrayList<>();
        for (Person p : copy.persons()) {
            if (p.isPrivate()) remove.add(p);
            else if (options.living() != LivingPeople.INCLUDE && living.isLiving(p)) {
                if (options.living() == LivingPeople.LEAVE_OUT) remove.add(p);
                else shorten.add(p);
            }
        }
        for (Person p : remove) {
            copy.removePerson(p);
            leftOut++;
        }
        Set<String> keep = Set.of("NAME", "SEX", "FAMC", "FAMS");
        for (Person p : shorten) {
            GedcomNode n = p.node();
            String surname = p.primaryName().map(PersonName::surname).orElse("");
            for (GedcomNode c : List.copyOf(n.children())) if (!keep.contains(c.tag())) n.remove(c);
            n.removeAll("NAME");
            n.add(0, new GedcomNode("NAME", PersonName.value(options.livingName(), surname, null)));
            shortened++;
        }
        // Families of living people lose their facts (a marriage date tells the age).
        for (var f : copy.families()) {
            boolean anyShort = f.partners().stream().anyMatch(shorten::contains);
            if (anyShort) for (var fact : f.facts()) f.node().remove(fact.node());
        }
        removeUnused(copy);
        return new int[]{leftOut, shortened};
    }
    // --8<-- [end:privacy]

    /** Media records nobody points to any more (after leaving people out) are not written. */
    private static void removeUnused(FamilyTree copy) {
        Set<String> used = new HashSet<>();
        for (GedcomNode r : copy.records()) {
            if (r.tag().equals("OBJE")) continue;
            r.walk(n -> {
                if (n.tag().equals("OBJE") && n.isPointer()) used.add(n.pointer());
            });
        }
        for (MediaRecord m : copy.mediaRecords()) if (!used.contains(m.id())) copy.removeRecord(m.id());
    }

    static void write(FamilyTree copy, GedcomVersion v, OutputStream os, String version, String fileName)
            throws IOException {
        List<GedcomNode> records = GedcomConverter.records(copy.records(), v);
        String submitter = null;
        if (v == GedcomVersion.V551) {
            Optional<GedcomNode> subm = records.stream().filter(r -> r.tag().equals("SUBM")).findFirst();
            if (subm.isPresent()) {
                submitter = subm.get().xref();
            } else {
                submitter = copy.newId("U");
                GedcomNode s = new GedcomNode(submitter, "SUBM", null);
                s.add("NAME", GedcomConverter.PRODUCT_NAME);
                records.add(s);
            }
        }
        GedcomNode header = GedcomConverter.header(copy.header(), v, version, submitter, fileName);
        GedcomWriter.write(header, records, v, os);
    }

    /**
     * GEDZIP: {@code gedcom.ged} (7.0) and the local media files under {@code media/}, the {@code FILE} values
     * pointing there. Web links stay links.
     */
    static int gedzip(FamilyTree copy, StoredTree stored, OutputStream os, String version) throws IOException {
        int files = 0;
        try (ZipOutputStream zip = new ZipOutputStream(os)) {
            Set<String> names = new HashSet<>();
            for (MediaRecord m : copy.mediaRecords()) {
                Optional<Path> local = stored == null ? Optional.empty() : TreeStore.localFile(stored, m);
                if (local.isEmpty()) continue;
                String name = local.get().getFileName().toString();
                String entry = "media/" + name;
                for (int i = 2; !names.add(entry); i++) entry = "media/" + i + "-" + name;
                zip.putNextEntry(new ZipEntry(entry));
                Files.copy(local.get(), zip);
                zip.closeEntry();
                m.setFile(entry);
                files++;
            }
            ByteArrayOutputStream ged = new ByteArrayOutputStream();
            write(copy, GedcomVersion.V70, ged, version, null);
            zip.putNextEntry(new ZipEntry("gedcom.ged"));
            zip.write(ged.toByteArray());
            zip.closeEntry();
        }
        return files;
    }
}
