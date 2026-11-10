package eu.amadeos.apps.familytree.service;

import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.GenDate;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.MediaRecord;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.gedcom.GedcomFile;
import eu.amadeos.apps.familytree.gedcom.GedcomNode;
import eu.amadeos.apps.familytree.gedcom.GedcomReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads a GEDCOM file ({@code .ged}) or GEDZIP ({@code .gdz}, a ZIP with {@code gedcom.ged} and the media
 * files) for import, with a report of what it holds. Nothing is written until {@link #into} or
 * {@link #mergeInto} is called.
 */
public final class GedcomImport implements AutoCloseable {
    /** GEDZIP files larger than this are refused (protection against ZIP bombs). */
    static final long MAX_UNPACKED = 4L * 1024 * 1024 * 1024;

    private final Path source;
    private final FamilyTree tree;
    private final ImportReport report;
    /** Media files from a GEDZIP, unpacked into a temporary folder, by their path in the ZIP. */
    private final Map<String, Path> zipFiles;
    private final Path unpacked;

    private GedcomImport(Path source, FamilyTree tree, ImportReport report, Map<String, Path> zipFiles,
                         Path unpacked) {
        this.source = source;
        this.tree = tree;
        this.report = report;
        this.zipFiles = zipFiles;
        this.unpacked = unpacked;
    }

    public static boolean isGedzip(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".gdz") || n.endsWith(".zip");
    }

    public static GedcomImport read(Path file) throws IOException {
        Map<String, Path> zipFiles = new TreeMap<>();
        byte[] gedcom;
        Path unpacked = null;
        if (isGedzip(file)) {
            unpacked = Files.createTempDirectory("amade-familytree-");
            try {
                gedcom = unzip(file, unpacked, zipFiles);
            } catch (IOException | RuntimeException e) {
                delete(unpacked);
                throw e;
            }
        } else {
            gedcom = Files.readAllBytes(file);
        }
        GedcomFile read = GedcomReader.read(gedcom);
        FamilyTree.Loaded loaded = FamilyTree.of(read);
        ImportReport report = report(file, gedcom.length, read, loaded, zipFiles);
        return new GedcomImport(file, loaded.tree(), report, zipFiles, unpacked);
    }

    private static byte[] unzip(Path file, Path into, Map<String, Path> files) throws IOException {
        byte[] gedcom = null;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.startsWith("__MACOSX/")) continue;
                boolean topLevelGedcom = name.toLowerCase(Locale.ROOT).endsWith(".ged") && !name.contains("/");
                if (name.equalsIgnoreCase("gedcom.ged") || topLevelGedcom && gedcom == null) {
                    byte[] bytes = readLimited(zip, MAX_UNPACKED - total);
                    total += bytes.length;
                    gedcom = bytes;
                } else {
                    Path out = into.resolve("f" + files.size());
                    try (var os = Files.newOutputStream(out)) {
                        total += copyLimited(zip, os, MAX_UNPACKED - total);
                    }
                    files.put(name, out);
                }
            }
        }
        if (gedcom == null) throw new IOException("The archive holds no GEDCOM file (gedcom.ged).");
        return gedcom;
    }

    private static long copyLimited(InputStream in, java.io.OutputStream out, long limit) throws IOException {
        byte[] buf = new byte[65536];
        long n = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            n += r;
            if (n > limit) throw new IOException("The archive is too large.");
            out.write(buf, 0, r);
        }
        return n;
    }

    private static void delete(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // A temporary folder; the system cleans it up.
        }
    }

    /** Deletes the unpacked GEDZIP files. */
    @Override
    public void close() {
        delete(unpacked);
    }

    private static byte[] readLimited(InputStream in, long limit) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        long n = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            n += r;
            if (n > limit) throw new IOException("The archive is too large.");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    private static ImportReport report(Path file, long bytes, GedcomFile read, FamilyTree.Loaded loaded,
                                       Map<String, Path> zipFiles) {
        FamilyTree t = loaded.tree();
        Set<String> places = new HashSet<>();
        int facts = 0;
        List<String> badDates = new ArrayList<>();
        int badCount = 0;
        int[] notes = {0};
        int[] ext = {0};
        for (GedcomNode r : t.records()) {
            r.walk(n -> {
                if (n.tag().startsWith("_")) ext[0]++;
                if (n.tag().equals("NOTE") || n.tag().equals("SNOTE")) notes[0]++;
                if (n.tag().equals("PLAC") && n.value() != null) places.add(n.value().strip());
            });
        }
        read.header().walk(n -> {
            if (n.tag().startsWith("_")) ext[0]++;
        });
        for (Person p : t.persons()) {
            for (Fact f : p.facts()) {
                facts++;
                GenDate d = f.date();
                if (d.kind() == GenDate.Kind.TEXT && f.node().child("DATE").isPresent()) {
                    badCount++;
                    if (badDates.size() < ImportReport.SAMPLES) badDates.add(d.original());
                }
            }
        }
        for (var fam : t.families()) {
            for (Fact f : fam.facts()) {
                facts++;
                if (f.date().kind() == GenDate.Kind.TEXT && f.node().child("DATE").isPresent()) {
                    badCount++;
                    if (badDates.size() < ImportReport.SAMPLES) badDates.add(f.date().original());
                }
            }
        }
        int web = 0, local = 0, missing = 0;
        Path base = file.getParent();
        for (MediaRecord m : t.mediaRecords()) {
            if (m.isWeb()) web++;
            else if (zipFiles.containsKey(m.file().replace('\\', '/'))
                    || TreeStore.localFile(base, m.file()).isPresent()) local++;
            else missing++;
        }
        Living living = Living.now();
        int alive = (int) t.persons().stream().filter(living::isLiving).count();
        return new ImportReport(file.getFileName().toString(), read.version().number(), read.charset(),
                read.sourceProgram(), bytes, t.count("INDI"), t.count("FAM"), t.count("SOUR"), places.size(), facts,
                notes[0], t.count("OBJE"), web, local, missing, alive, ext[0], badDates, badCount, loaded.repairs(),
                read.warnings());
    }

    public ImportReport report() {
        return report;
    }

    public FamilyTree tree() {
        return tree;
    }

    /** The name for a new tree: the file name without extension. */
    public String suggestedName() {
        String n = source.getFileName().toString();
        int dot = n.lastIndexOf('.');
        return TreeStore.safeName(dot > 0 ? n.substring(0, dot) : n);
    }

    /** Saves the imported tree as a new tree in the store, with the media files it can find copied in. */
    public StoredTree into(TreeStore store, String name) throws IOException {
        StoredTree t = store.create(name, FamilyTree.empty());
        copyMedia(t, tree);
        store.save(t, tree);
        return t;
    }

    /** Adds the imported people to an open tree; returns how many were added. */
    public int mergeInto(StoredTree target, FamilyTree into) throws IOException {
        FamilyTree copy = tree.copy();
        copyMedia(target, copy);
        return into.merge(copy);
    }

    /**
     * Copies media files that are in the GEDZIP or next to the GEDCOM file into the tree's media folder and
     * points the records there. Web links stay as they are (see {@link PhotoDownloader}).
     */
    private void copyMedia(StoredTree t, FamilyTree target) throws IOException {
        Path base = source.getParent();
        for (MediaRecord m : target.mediaRecords()) {
            if (m.isWeb() || m.file().isBlank()) continue;
            String key = m.file().replace('\\', '/');
            Path unpackedFile = zipFiles.get(key);
            if (unpackedFile != null) {
                Files.createDirectories(t.media());
                String fileName = key.substring(key.lastIndexOf('/') + 1);
                Path out = TreeStore.freeFile(t.media(), fileName);
                Files.copy(unpackedFile, out);
                m.setFile(StoredTree.MEDIA + "/" + out.getFileName());
                continue;
            }
            Optional<Path> local = TreeStore.localFile(base, m.file());
            if (local.isPresent()) m.setFile(TreeStore.copyIntoMedia(t, local.get()));
        }
    }

    /**
     * Points media records whose files are missing to files of the same name in a folder the user chose
     * (photos downloaded earlier), copying them into the tree.
     *
     * @return how many were found
     */
    public static int findInFolder(FamilyTree tree, StoredTree t, Path folder) throws IOException {
        Map<String, Path> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        try (var walk = Files.walk(folder, 4)) {
            walk.filter(Files::isRegularFile).forEach(p -> byName.putIfAbsent(p.getFileName().toString(), p));
        }
        int found = 0;
        for (MediaRecord m : tree.mediaRecords()) {
            if (TreeStore.localFile(t, m).isPresent()) continue;
            String f = m.file();
            int q = f.indexOf('?');
            if (q >= 0) f = f.substring(0, q);
            String name = f.substring(Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\')) + 1);
            Path match = byName.get(name);
            if (match == null) continue;
            String oldFile = m.file();
            m.setFile(TreeStore.copyIntoMedia(t, match));
            if (oldFile.toLowerCase(Locale.ROOT).startsWith("http")) {
                m.fileNode().ifPresent(n -> n.set("_URL", oldFile));
            }
            found++;
        }
        if (found > 0) tree.changed();
        return found;
    }
}
