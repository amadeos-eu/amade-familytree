package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomConverter;
import eu.amadeos.apps.familytree.gedcom.GedcomFile;
import eu.amadeos.apps.familytree.gedcom.GedcomNode;
import eu.amadeos.apps.familytree.gedcom.GedcomVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A family tree: the GEDCOM header and records, kept in GEDCOM 7.0 form, with an index by id. People, families,
 * sources and media are views on the records ({@link Person}, {@link Family}, ...); everything the program does
 * not know stays in the records as it was read.
 *
 * <p>Not thread safe: the program changes a tree only on the event dispatch thread and hands copies to
 * background work ({@link #copy()}).
 */
public final class FamilyTree {
    public static final Comparator<Person> BY_BIRTH = Comparator.comparingLong(p -> p.birthDate().sortKey());
    public static final Comparator<Person> BY_NAME = Comparator
            .comparing((Person p) -> Search.fold(p.surname()))
            .thenComparing(p -> Search.fold(p.given()))
            .thenComparingLong(p -> p.birthDate().sortKey());
    private static final Pattern NUMBERED = Pattern.compile("^([A-Za-z_]*)(\\d+)$");

    private GedcomNode header;
    private final List<GedcomNode> records;
    private final Map<String, GedcomNode> byId = new HashMap<>();
    private final Map<String, Integer> lastNumber = new HashMap<>();
    private int revision;

    public FamilyTree(GedcomNode header, List<GedcomNode> records) {
        this.header = Objects.requireNonNull(header);
        this.records = new ArrayList<>(records);
        reindex();
    }

    /** An empty tree. */
    public static FamilyTree empty() {
        return new FamilyTree(new GedcomNode("HEAD"), List.of());
    }

    /**
     * A tree from a read file: the records are brought into 7.0 form and repaired where needed
     * ({@link #normalize()}).
     *
     * @return the tree and what was repaired
     */
    public static Loaded of(GedcomFile file) {
        List<GedcomNode> records = GedcomConverter.records(file.records(), GedcomVersion.V70);
        GedcomNode header = GedcomConverter.header(file.header(), GedcomVersion.V70, null, null, null);
        // The header made by the converter names this program; keep the original program for the report.
        FamilyTree tree = new FamilyTree(header, records);
        List<String> repairs = tree.normalize();
        return new Loaded(tree, repairs);
    }

    public record Loaded(FamilyTree tree, List<String> repairs) {
    }

    public GedcomNode header() {
        return header;
    }

    public void setHeader(GedcomNode newHeader) {
        header = Objects.requireNonNull(newHeader);
    }

    /** The records in file order; a live, unmodifiable view. */
    public List<GedcomNode> records() {
        return java.util.Collections.unmodifiableList(records);
    }

    /** Goes up with every change, so views know when to refresh. */
    public int revision() {
        return revision;
    }

    public void changed() {
        revision++;
    }

    public Optional<GedcomNode> record(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public Optional<Person> person(String id) {
        return record(id).filter(r -> r.tag().equals("INDI")).map(r -> new Person(this, r));
    }

    public Optional<Family> family(String id) {
        return record(id).filter(r -> r.tag().equals("FAM")).map(r -> new Family(this, r));
    }

    public Optional<SourceRecord> source(String id) {
        return record(id).filter(r -> r.tag().equals("SOUR")).map(SourceRecord::new);
    }

    public Optional<MediaRecord> media(String id) {
        return record(id).filter(r -> r.tag().equals("OBJE")).map(MediaRecord::new);
    }

    public List<Person> persons() {
        List<Person> list = new ArrayList<>();
        for (GedcomNode r : records) if (r.tag().equals("INDI") && r.xref() != null) list.add(new Person(this, r));
        return list;
    }

    public List<Family> families() {
        List<Family> list = new ArrayList<>();
        for (GedcomNode r : records) if (r.tag().equals("FAM") && r.xref() != null) list.add(new Family(this, r));
        return list;
    }

    public List<SourceRecord> sources() {
        List<SourceRecord> list = new ArrayList<>();
        for (GedcomNode r : records) if (r.tag().equals("SOUR") && r.xref() != null) list.add(new SourceRecord(r));
        return list;
    }

    public List<MediaRecord> mediaRecords() {
        List<MediaRecord> list = new ArrayList<>();
        for (GedcomNode r : records) if (r.tag().equals("OBJE") && r.xref() != null) list.add(new MediaRecord(r));
        return list;
    }

    public int count(String tag) {
        int n = 0;
        for (GedcomNode r : records) if (r.tag().equals(tag)) n++;
        return n;
    }

    // ---- ids -------------------------------------------------------------------------------------------------

    private void reindex() {
        byId.clear();
        lastNumber.clear();
        for (GedcomNode r : records) index(r);
    }

    private void index(GedcomNode r) {
        if (r.xref() == null) return;
        byId.put(r.xref(), r);
        Matcher m = NUMBERED.matcher(r.xref());
        if (m.matches()) {
            try {
                lastNumber.merge(m.group(1), Integer.parseInt(m.group(2)), Math::max);
            } catch (NumberFormatException ignored) {
                // Too long to be a counter.
            }
        }
    }

    /** A new id with this prefix ("I", "F", "S", "M"), one above the highest in use. */
    public String newId(String prefix) {
        int n = lastNumber.getOrDefault(prefix, 0) + 1;
        while (byId.containsKey(prefix + n)) n++;
        lastNumber.put(prefix, n);
        return prefix + n;
    }

    /** Adds a record at the end (before the trailer); it needs an id that is not in use. */
    public GedcomNode addRecord(GedcomNode record) {
        if (record.xref() != null && byId.containsKey(record.xref())) {
            throw new IllegalArgumentException("The id " + record.xref() + " is in use.");
        }
        records.add(record);
        index(record);
        changed();
        return record;
    }

    public void removeRecord(String id) {
        GedcomNode r = byId.remove(id);
        if (r != null) {
            records.remove(r);
            changed();
        }
    }

    /** Puts a copy of a record back (undo), or removes the record when {@code copy} is null. */
    public void restore(String id, GedcomNode copy, int index) {
        GedcomNode current = byId.get(id);
        if (copy == null) {
            if (current != null) removeRecord(id);
            return;
        }
        if (current != null) {
            int i = records.indexOf(current);
            records.set(i, copy);
        } else {
            records.add(Math.max(0, Math.min(index, records.size())), copy);
        }
        byId.put(id, copy);
        changed();
    }

    public int indexOf(String id) {
        GedcomNode r = byId.get(id);
        return r == null ? records.size() : records.indexOf(r);
    }

    // ---- building ----------------------------------------------------------------------------------------------

    public Person newPerson(String given, String surname, Sex sex) {
        GedcomNode r = new GedcomNode(newId("I"), "INDI", null);
        r.add("NAME", PersonName.value(given, surname, null));
        r.add("SEX", sex == null ? "U" : sex.code());
        addRecord(r);
        return new Person(this, r);
    }

    public Family newFamily() {
        GedcomNode r = new GedcomNode(newId("F"), "FAM", null);
        addRecord(r);
        return new Family(this, r);
    }

    public SourceRecord newSource(String title) {
        GedcomNode r = new GedcomNode(newId("S"), "SOUR", null);
        r.add("TITL", title);
        addRecord(r);
        return new SourceRecord(r);
    }

    public MediaRecord newMedia(String file, String mime, String title) {
        GedcomNode r = new GedcomNode(newId("M"), "OBJE", null);
        GedcomNode f = r.add(new GedcomNode("FILE", file));
        f.add("FORM", mime == null || mime.isBlank() ? "application/octet-stream" : mime);
        if (title != null && !title.isBlank()) f.add("TITL", title);
        addRecord(r);
        return new MediaRecord(r);
    }

    /** Makes {@code partner} a partner in {@code family}; the free place is used, HUSB for men if both are free. */
    public void linkPartner(Family family, Person partner) {
        GedcomNode f = family.node();
        boolean hasHusb = f.has("HUSB");
        boolean hasWife = f.has("WIFE");
        String role;
        if (!hasHusb && !hasWife) role = partner.sex() == Sex.FEMALE ? "WIFE" : "HUSB";
        else if (!hasHusb) role = "HUSB";
        else if (!hasWife) role = "WIFE";
        else throw new IllegalStateException("The family already has two partners.");
        GedcomNode link = new GedcomNode(role, GedcomNode.pointerTo(partner.id()));
        if (role.equals("HUSB")) f.add(0, link);
        else f.add(f.child("HUSB").map(h -> f.indexOf(h) + 1).orElse(0), link);
        partner.node().add(new GedcomNode("FAMS", GedcomNode.pointerTo(family.id())));
        changed();
    }

    /** Adds a child to a family, in birth order among the children with known dates. */
    public void linkChild(Family family, Person child) {
        GedcomNode f = family.node();
        GedcomNode link = new GedcomNode("CHIL", GedcomNode.pointerTo(child.id()));
        long key = child.birthDate().sortKey();
        int at = -1;
        for (GedcomNode c : f.children("CHIL")) {
            Optional<Person> other = person(c.pointer());
            if (other.isPresent() && key != Long.MAX_VALUE && other.get().birthDate().sortKey() > key) {
                at = f.indexOf(c);
                break;
            }
        }
        if (at < 0) {
            List<GedcomNode> kids = f.children("CHIL");
            at = kids.isEmpty() ? f.children().size() : f.indexOf(kids.getLast()) + 1;
        }
        f.add(at, link);
        child.node().add(new GedcomNode("FAMC", GedcomNode.pointerTo(family.id())));
        changed();
    }

    public void unlinkChild(Family family, Person child) {
        family.node().children("CHIL").stream().filter(c -> child.id().equals(c.pointer()))
                .forEach(family.node()::remove);
        child.node().children("FAMC").stream().filter(c -> family.id().equals(c.pointer()))
                .forEach(child.node()::remove);
        removeIfEmpty(family);
        changed();
    }

    public void unlinkPartner(Family family, Person partner) {
        for (String role : List.of("HUSB", "WIFE")) {
            family.node().children(role).stream().filter(c -> partner.id().equals(c.pointer()))
                    .forEach(family.node()::remove);
        }
        partner.node().children("FAMS").stream().filter(c -> family.id().equals(c.pointer()))
                .forEach(partner.node()::remove);
        removeIfEmpty(family);
        changed();
    }

    /** A family with at most one member left and nothing else to say is removed. */
    private void removeIfEmpty(Family family) {
        int members = family.node().children("HUSB").size() + family.node().children("WIFE").size()
                + family.node().children("CHIL").size();
        if (members > 1 || !family.facts().isEmpty()) return;
        for (GedcomNode c : List.copyOf(family.node().children())) {
            if (c.tag().equals("HUSB") || c.tag().equals("WIFE") || c.tag().equals("CHIL")) {
                person(c.pointer()).ifPresent(p -> {
                    p.node().children("FAMS").stream().filter(l -> family.id().equals(l.pointer()))
                            .forEach(p.node()::remove);
                    p.node().children("FAMC").stream().filter(l -> family.id().equals(l.pointer()))
                            .forEach(p.node()::remove);
                });
            }
        }
        removeRecord(family.id());
    }

    /**
     * The family a new father or mother of {@code child} goes into: the child's parent family with a free
     * place for that role, else a new one.
     */
    public Family parentFamilyFor(Person child, Sex parentSex) {
        String role = parentSex == Sex.FEMALE ? "WIFE" : "HUSB";
        for (Family f : child.parentFamilies()) if (!f.node().has(role)) return f;
        Family f = newFamily();
        linkChild(f, child);
        return f;
    }

    /** Adds a new parent for {@code child}. */
    public Person addParent(Person child, Sex sex, String given, String surname) {
        Family f = parentFamilyFor(child, sex);
        Person p = newPerson(given, surname, sex);
        linkPartner(f, p);
        return p;
    }

    /** Adds a new partner for {@code person}, in a new family. */
    public Person addPartner(Person person, Sex sex, String given, String surname) {
        Family f = newFamily();
        linkPartner(f, person);
        Person p = newPerson(given, surname, sex);
        linkPartner(f, p);
        return p;
    }

    /**
     * Adds a new child; with {@code family} null, to the person's only family, or to a new family of the
     * person alone.
     */
    public Person addChild(Person parent, Family family, Sex sex, String given, String surname) {
        Family f = family;
        if (f == null) {
            List<Family> fams = parent.families();
            if (fams.size() == 1) f = fams.getFirst();
            else {
                f = newFamily();
                linkPartner(f, parent);
            }
        }
        Person c = newPerson(given, surname, sex);
        linkChild(f, c);
        return c;
    }

    /** Adds a new brother or sister: a child of the person's parent family (made when missing). */
    public Person addSibling(Person person, Sex sex, String given, String surname) {
        List<Family> fams = person.parentFamilies();
        Family f;
        if (fams.isEmpty()) {
            f = newFamily();
            linkChild(f, person);
        } else {
            f = fams.getFirst();
        }
        Person s = newPerson(given, surname, sex);
        linkChild(f, s);
        return s;
    }

    /** Removes a person and every link to them; families that are left empty go too. */
    public void removePerson(Person p) {
        for (Family f : p.families()) unlinkPartner(f, p);
        for (Family f : p.parentFamilies()) unlinkChild(f, p);
        String pointer = GedcomNode.pointerTo(p.id());
        for (GedcomNode r : records) {
            r.walk(n -> {
                if ((n.tag().equals("ASSO") || n.tag().equals("ALIA")) && pointer.equals(n.value())) {
                    n.setValue(null);
                }
            });
        }
        removeRecord(p.id());
    }

    // ---- repairs ---------------------------------------------------------------------------------------------

    /**
     * Brings records into the form the program uses and repairs links:
     * <ul>
     * <li>a photo given in place ({@code 1 OBJE} with {@code FILE}, allowed in 5.5.1) becomes a media record;</li>
     * <li>a source given as text in place ({@code 1 SOUR Church book}) becomes a source record (the same text,
     * the same record);</li>
     * <li>a child or partner listed in a family but missing the link back in the person gets it, and the other
     * way round.</li>
     * </ul>
     *
     * @return a readable line for each kind of repair, with the count
     */
    public List<String> normalize() {
        int[] inlineMedia = {0};
        int[] inlineSources = {0};
        Map<String, String> sourceByText = new LinkedHashMap<>();
        for (GedcomNode r : List.copyOf(records)) {
            if (r.xref() == null) continue;
            r.walk(n -> {
                if (n == r) return;
                if (n.tag().equals("OBJE") && !n.isPointer() && n.has("FILE")) {
                    GedcomNode media = new GedcomNode(newId("M"), "OBJE", null);
                    for (GedcomNode c : n.children()) media.add(c.copy());
                    addRecord(media);
                    n.clearChildren();
                    n.setValue(GedcomNode.pointerTo(media.xref()));
                    // Marks such as _PRIM belong to the link, keep them there.
                    for (GedcomNode c : List.copyOf(media.children())) {
                        if (c.tag().startsWith("_PRIM") || c.tag().equals("_PERSONALPHOTO")) {
                            media.remove(c);
                            n.add(c);
                        }
                    }
                    inlineMedia[0]++;
                } else if (n.tag().equals("SOUR") && !n.isPointer() && n.value() != null && !r.tag().equals("HEAD")) {
                    String text = n.value().strip();
                    String id = sourceByText.computeIfAbsent(text, t -> {
                        GedcomNode s = new GedcomNode(newId("S"), "SOUR", null);
                        s.add("TITL", t);
                        for (GedcomNode c : n.children()) if (c.tag().equals("TEXT")) s.add(c.copy());
                        addRecord(s);
                        return s.xref();
                    });
                    n.setValue(GedcomNode.pointerTo(id));
                    n.removeAll("TEXT");
                    inlineSources[0]++;
                }
            });
        }
        int links = 0;
        for (Family f : families()) {
            for (String role : List.of("HUSB", "WIFE", "CHIL")) {
                String back = role.equals("CHIL") ? "FAMC" : "FAMS";
                for (GedcomNode link : f.node().children(role)) {
                    Optional<Person> p = person(link.pointer());
                    if (p.isEmpty()) continue;
                    boolean has = p.get().node().children(back).stream().anyMatch(b -> f.id().equals(b.pointer()));
                    if (!has) {
                        p.get().node().add(new GedcomNode(back, GedcomNode.pointerTo(f.id())));
                        links++;
                    }
                }
            }
        }
        for (Person p : persons()) {
            for (String back : List.of("FAMC", "FAMS")) {
                for (GedcomNode link : p.node().children(back)) {
                    Optional<Family> f = family(link.pointer());
                    if (f.isEmpty()) continue;
                    List<String> roles = back.equals("FAMC") ? List.of("CHIL") : List.of("HUSB", "WIFE");
                    boolean has = roles.stream().anyMatch(role -> f.get().node().children(role).stream()
                            .anyMatch(l -> p.id().equals(l.pointer())));
                    if (has) continue;
                    if (back.equals("FAMC")) {
                        f.get().node().add(new GedcomNode("CHIL", GedcomNode.pointerTo(p.id())));
                    } else {
                        String role = !f.get().node().has("HUSB") && p.sex() != Sex.FEMALE ? "HUSB"
                                : !f.get().node().has("WIFE") ? "WIFE" : null;
                        if (role == null) continue;
                        f.get().node().add(0, new GedcomNode(role, GedcomNode.pointerTo(p.id())));
                    }
                    links++;
                }
            }
        }
        List<String> repairs = new ArrayList<>();
        if (inlineMedia[0] > 0) repairs.add("media:" + inlineMedia[0]);
        if (inlineSources[0] > 0) repairs.add("sources:" + inlineSources[0]);
        if (links > 0) repairs.add("links:" + links);
        changed();
        return repairs;
    }

    // ---- copies and merging ------------------------------------------------------------------------------------

    /** A deep copy, for background work and export. */
    public FamilyTree copy() {
        List<GedcomNode> copies = new ArrayList<>(records.size());
        for (GedcomNode r : records) copies.add(r.copy());
        return new FamilyTree(header.copy(), copies);
    }

    /**
     * Adds all records of another tree to this one, each with a new id; pointers are changed to match. Header and submitter records of the other tree are left out.
     *
     * @return the number of people added
     */
    public int merge(FamilyTree other) {
        Map<String, String> ids = new HashMap<>();
        for (GedcomNode r : other.records) {
            if (r.xref() == null || r.tag().equals("SUBM")) continue;
            String prefix = switch (r.tag()) {
                case "INDI" -> "I";
                case "FAM" -> "F";
                case "SOUR" -> "S";
                case "OBJE" -> "M";
                case "REPO" -> "R";
                case "SNOTE", "NOTE" -> "N";
                default -> "X";
            };
            ids.put(r.xref(), newId(prefix));
            byId.put(ids.get(r.xref()), r);
        }
        // The placeholders above only reserved the ids.
        ids.values().forEach(byId::remove);
        int people = 0;
        for (GedcomNode r : other.records) {
            if (r.xref() == null || r.tag().equals("SUBM")) continue;
            GedcomNode c = r.copy();
            c.walk(n -> {
                if (n.isPointer() && ids.containsKey(n.pointer())) n.setValue(GedcomNode.pointerTo(ids.get(n.pointer())));
            });
            c.setXref(ids.get(r.xref()));
            addRecord(c);
            if (c.tag().equals("INDI")) people++;
        }
        return people;
    }
}
