package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A person ({@code INDI} record), a view on its GEDCOM structure. Names, facts, sources, photos and notes are
 * read from and written to the structure; parents, partners and children come from the {@link FamilyTree}.
 */
public final class Person {
    /** Facts that mean a person has died. */
    public static final Set<String> DEATH_TAGS = Set.of("DEAT", "BURI", "CREM", "PROB", "WILL");

    // --8<-- [start:view]
    private final FamilyTree tree;
    private final GedcomNode node;

    Person(FamilyTree tree, GedcomNode node) {
        this.tree = Objects.requireNonNull(tree);
        this.node = Objects.requireNonNull(node);
    }

    public String id() {
        return node.xref();
    }

    public GedcomNode node() {
        return node;
    }

    public FamilyTree tree() {
        return tree;
    }

    // ---- names -----------------------------------------------------------------------------------------------

    public List<PersonName> names() {
        List<PersonName> list = new ArrayList<>();
        for (GedcomNode n : node.children("NAME")) list.add(new PersonName(n));
        return list;
    }
    // --8<-- [end:view]

    /** The first name that is not a birth name of its own, else the first name; empty without names. */
    public Optional<PersonName> primaryName() {
        List<PersonName> all = names();
        for (PersonName n : all) if (!n.type().equals("BIRTH") && !n.type().equals("MAIDEN")) return Optional.of(n);
        return all.stream().findFirst();
    }

    public String given() {
        return primaryName().map(PersonName::given).orElse("");
    }

    /** All given names; see {@link #calledName()} for the one used every day. */
    public String firstGiven() {
        String g = given();
        int sp = g.indexOf(' ');
        return sp < 0 ? g : g.substring(0, sp);
    }

    /** The call name ({@code NICK}) when there is one, else the first given name. */
    public String calledName() {
        String called = primaryName().map(PersonName::called).orElse("");
        return called.isEmpty() ? firstGiven() : called;
    }

    /**
     * The surname a person is known by: a married name (a {@code NAME} of type MARRIED, or {@code _MARNM}),
     * else the surname of the primary name.
     */
    public String surname() {
        String married = marriedSurname();
        return married.isEmpty() ? primaryName().map(PersonName::surname).orElse("") : married;
    }

    public String marriedSurname() {
        for (PersonName n : names()) if (n.type().equals("MARRIED") && !n.surname().isEmpty()) return n.surname();
        return primaryName().map(PersonName::marriedSurname).orElse("");
    }

    /** The surname at birth when it differs from {@link #surname()} ("née Keller"), else "". */
    public String birthSurname() {
        String shown = surname();
        String birth = "";
        for (PersonName n : names()) {
            if (n.type().equals("BIRTH") || n.type().equals("MAIDEN")) {
                birth = n.surname();
                break;
            }
        }
        if (birth.isEmpty() && !marriedSurname().isEmpty()) birth = primaryName().map(PersonName::surname).orElse("");
        return birth.equalsIgnoreCase(shown) ? "" : birth;
    }

    /** "Ernst Wilhelm Brenner"; "?" when the person has no name at all. */
    public String fullName() {
        String n = (given() + " " + surname()).strip();
        String suffix = primaryName().map(PersonName::suffix).orElse("");
        if (!suffix.isEmpty()) n += " " + suffix;
        return n.isEmpty() ? "?" : n;
    }

    /** "Ernst Brenner": call name and surname, for the boxes of the charts. */
    public String shortName() {
        String n = (calledName() + " " + surname()).strip();
        return n.isEmpty() ? "?" : n;
    }

    /** "Brenner, Ernst Wilhelm", for lists sorted by surname. */
    public String sortName() {
        String s = surname();
        String g = given();
        if (s.isEmpty()) return g.isEmpty() ? "?" : g;
        return g.isEmpty() ? s : s + ", " + g;
    }

    public Sex sex() {
        return Sex.of(node.valueOf("SEX"));
    }

    public void setSex(Sex sex) {
        node.set("SEX", sex == null || sex == Sex.UNKNOWN ? "U" : sex.code());
    }

    // ---- facts -----------------------------------------------------------------------------------------------

    /** All events and attributes, in file order. */
    public List<Fact> facts() {
        List<Fact> list = new ArrayList<>();
        for (GedcomNode c : node.children()) if (FactType.of(c.tag()).isPresent()) list.add(new Fact(c));
        return list;
    }

    public Optional<Fact> fact(String tag) {
        return node.child(tag).map(Fact::new);
    }

    /** Birth, else christening or baptism. */
    public Optional<Fact> birth() {
        return fact("BIRT").filter(f -> f.date().isValid() || !f.place().isEmpty())
                .or(() -> fact("CHR")).or(() -> fact("BAPM")).or(() -> fact("BIRT"));
    }

    /** Death, else burial or cremation. */
    public Optional<Fact> death() {
        return fact("DEAT").filter(f -> f.date().isValid() || !f.place().isEmpty())
                .or(() -> fact("BURI")).or(() -> fact("CREM")).or(() -> fact("DEAT"));
    }

    public GenDate birthDate() {
        return birth().map(Fact::date).orElse(GenDate.NONE);
    }

    public GenDate deathDate() {
        return death().map(Fact::date).orElse(GenDate.NONE);
    }

    /** True when the tree says so: a death, burial, cremation, will or probate fact (or {@code DEAT Y}). */
    public boolean isDeceased() {
        for (GedcomNode c : node.children()) if (DEATH_TAGS.contains(c.tag())) return true;
        return false;
    }

    /** Marks a person as deceased ({@code 1 DEAT Y}) or, when there is no dated death fact, as living. */
    public void setDeceased(boolean deceased) {
        if (deceased == isDeceased()) return;
        if (deceased) {
            node.add(new GedcomNode("DEAT", "Y"));
        } else {
            node.children("DEAT").stream().filter(d -> d.children().isEmpty()).forEach(node::remove);
        }
    }

    /** "1928–2009", "* 1954", "† 2009", "" when no year is known. */
    public String lifespan() {
        String b = DateText.year(birthDate());
        String d = DateText.year(deathDate());
        if (!b.isEmpty() && !d.isEmpty()) return b + "–" + d;
        if (!b.isEmpty()) return isDeceased() ? b + "–?" : "* " + b;
        if (!d.isEmpty()) return "† " + d;
        return isDeceased() ? "†" : "";
    }

    /** The occupation, if any (the first {@code OCCU}). */
    public String occupation() {
        return node.valueOf("OCCU");
    }

    /** {@code RESN} with PRIVACY or CONFIDENTIAL: not given to others on export. */
    public boolean isPrivate() {
        String r = node.valueOf("RESN").toUpperCase(Locale.ROOT);
        return r.contains("PRIVACY") || r.contains("CONFIDENTIAL");
    }

    public void setPrivate(boolean value) {
        if (value == isPrivate()) return;
        if (value) {
            String r = node.valueOf("RESN");
            node.set("RESN", r.isEmpty() ? "PRIVACY" : r + ", PRIVACY");
        } else {
            String r = node.valueOf("RESN").replaceAll("(?i),?\\s*(PRIVACY|CONFIDENTIAL)", "").replaceAll("^,\\s*", "");
            node.set("RESN", r.strip());
        }
    }

    // ---- sources, photos, notes ------------------------------------------------------------------------------

    /** Sources given for the person as a whole. */
    public List<Citation> citations() {
        List<Citation> list = new ArrayList<>();
        for (GedcomNode s : node.children("SOUR")) list.add(new Citation(s));
        return list;
    }

    /** All citations of the person, including those of the facts. */
    public int citationCount() {
        int count = node.children("SOUR").size();
        for (Fact f : facts()) count += f.citations().size();
        return count;
    }

    /** The ids of the media records the person links to, in order; the portrait first if one is marked. */
    public List<String> mediaIds() {
        List<String> ids = new ArrayList<>();
        for (GedcomNode o : node.children("OBJE")) if (o.isPointer()) ids.add(o.pointer());
        return ids;
    }

    /** The media record shown as the person's picture: one marked as primary, else the first picture. */
    public Optional<MediaRecord> portrait() {
        MediaRecord first = null;
        for (GedcomNode o : node.children("OBJE")) {
            if (!o.isPointer()) continue;
            Optional<MediaRecord> m = tree.media(o.pointer());
            if (m.isEmpty() || !m.get().isImage()) continue;
            if (isMarkedPrimary(o) || isMarkedPrimary(m.get().node())) return m;
            if (first == null) first = m.get();
        }
        return Optional.ofNullable(first);
    }

    private static boolean isMarkedPrimary(GedcomNode n) {
        for (String tag : List.of("_PRIM", "_PRIMARY", "_PERSONALPHOTO", "_PHOTO")) {
            if (n.valueOf(tag).equalsIgnoreCase("Y")) return true;
        }
        return false;
    }

    /** Makes this media record the portrait: its link goes first and is marked {@code _PRIM Y}. */
    public void setPortrait(String mediaId) {
        GedcomNode link = null;
        for (GedcomNode o : node.children("OBJE")) {
            for (String tag : List.of("_PRIM", "_PRIMARY", "_PERSONALPHOTO")) o.removeAll(tag);
            if (mediaId.equals(o.pointer())) link = o;
        }
        if (link == null) link = node.add(new GedcomNode("OBJE", GedcomNode.pointerTo(mediaId)));
        link.add("_PRIM", "Y");
        int firstObje = node.children().indexOf(node.children("OBJE").getFirst());
        node.move(link, firstObje);
    }

    public List<GedcomNode> noteNodes() {
        List<GedcomNode> list = new ArrayList<>(node.children("NOTE"));
        list.addAll(node.children("SNOTE"));
        return list;
    }

    /** The text of the person's notes; shared notes ({@code SNOTE}) are looked up. */
    public String notes() {
        List<String> parts = new ArrayList<>();
        for (GedcomNode n : node.children()) {
            if (n.tag().equals("NOTE") && !n.isPointer()) parts.add(n.text());
            else if ((n.tag().equals("SNOTE") || n.tag().equals("NOTE")) && n.isPointer()) {
                tree.record(n.pointer()).ifPresent(r -> parts.add(r.text()));
            }
        }
        return String.join("\n\n", parts);
    }

    /** Replaces the person's own notes ({@code NOTE} text); shared notes stay. */
    public void setNotes(String text) {
        node.children("NOTE").stream().filter(n -> !n.isPointer()).forEach(node::remove);
        if (text != null && !text.isBlank()) node.add(new GedcomNode("NOTE", text.strip()));
    }

    public String ownNotes() {
        List<String> parts = new ArrayList<>();
        for (GedcomNode n : node.children("NOTE")) if (!n.isPointer()) parts.add(n.text());
        return String.join("\n\n", parts);
    }

    // ---- changing names --------------------------------------------------------------------------------------

    /**
     * Sets given names, the surname the person is known by, the call name and the surname at birth, the way the
     * editor shows them. A married name stays a married name ({@code NAME} of type MARRIED or {@code _MARNM}); a
     * birth surname that differs from the surname becomes a {@code NAME} of type BIRTH unless the file already
     * keeps it as the primary name below a married name.
     */
    public void setNames(String given, String surname, String called, String birth) {
        String g = given == null ? "" : given.strip();
        String s = surname == null ? "" : surname.strip();
        String b = birth == null ? "" : birth.strip();
        PersonName primary = primaryName().orElseGet(() -> {
            GedcomNode n = new GedcomNode("NAME", PersonName.value("", "", null));
            int at = node.child("SEX").map(node::indexOf).orElse(node.children().size());
            node.add(Math.min(at, node.children().size()), n);
            return new PersonName(n);
        });
        Optional<PersonName> marriedName = names().stream().filter(n -> n.type().equals("MARRIED")).findFirst();
        Optional<PersonName> birthName = names().stream()
                .filter(n -> n.type().equals("BIRTH") || n.type().equals("MAIDEN")).findFirst();
        boolean hasMarnm = !primary.marriedSurname().isEmpty();
        if (marriedName.isPresent() || hasMarnm) {
            // Married: the shown surname is the married one; the primary name keeps the birth surname.
            if (marriedName.isPresent()) marriedName.get().set(g, s);
            else primary.node().set("_MARNM", s);
            boolean primaryIsMarried = marriedName.isPresent() && marriedName.get().node() == primary.node();
            String atBirth = !b.isEmpty() ? b : birthName.map(PersonName::surname)
                    .orElse(primaryIsMarried ? "" : primary.surname());
            if (birthName.isPresent()) {
                birthName.get().set(g, atBirth);
                if (!primaryIsMarried && marriedName.isPresent()) primary.set(g, primary.surname());
            } else if (primaryIsMarried) {
                if (!atBirth.isEmpty()) {
                    GedcomNode n = new GedcomNode("NAME", PersonName.value(g, atBirth, null));
                    n.add("TYPE", "BIRTH");
                    node.add(node.indexOf(primary.node()) + 1, n);
                }
            } else {
                primary.set(g, atBirth);
            }
        } else {
            primary.set(g, s);
            if (birthName.isPresent() && birthName.get().node() != primary.node()) {
                if (b.isEmpty() || b.equalsIgnoreCase(s)) node.remove(birthName.get().node());
                else birthName.get().set(g, b);
            } else if (!b.isEmpty() && !b.equalsIgnoreCase(s)) {
                GedcomNode n = new GedcomNode("NAME", PersonName.value(g, b, null));
                n.add("TYPE", "BIRTH");
                node.add(node.indexOf(primary.node()) + 1, n);
            }
        }
        primary.setCalled(called);
    }

    // ---- family ----------------------------------------------------------------------------------------------

    /** The families the person is a child of ({@code FAMC}), usually one. */
    public List<Family> parentFamilies() {
        List<Family> list = new ArrayList<>();
        for (GedcomNode f : node.children("FAMC")) tree.family(f.pointer()).ifPresent(list::add);
        return list;
    }

    /** The families the person is a partner in ({@code FAMS}), in order. */
    public List<Family> families() {
        List<Family> list = new ArrayList<>();
        for (GedcomNode f : node.children("FAMS")) tree.family(f.pointer()).ifPresent(list::add);
        return list;
    }

    public Optional<Person> father() {
        for (Family f : parentFamilies()) {
            Optional<Person> p = f.husband();
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }

    public Optional<Person> mother() {
        for (Family f : parentFamilies()) {
            Optional<Person> p = f.wife();
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }

    public List<Person> parents() {
        List<Person> list = new ArrayList<>();
        for (Family f : parentFamilies()) {
            f.husband().filter(p -> !list.contains(p)).ifPresent(list::add);
            f.wife().filter(p -> !list.contains(p)).ifPresent(list::add);
        }
        return list;
    }

    public List<Person> partners() {
        List<Person> list = new ArrayList<>();
        for (Family f : families()) f.partnerOf(this).filter(p -> !list.contains(p)).ifPresent(list::add);
        return list;
    }

    public List<Person> children() {
        List<Person> list = new ArrayList<>();
        for (Family f : families()) for (Person c : f.children()) if (!list.contains(c)) list.add(c);
        return list;
    }

    /** Brothers and sisters, also half siblings, sorted by birth. */
    public List<Person> siblings() {
        List<Person> list = new ArrayList<>();
        for (Person parent : parents()) {
            for (Person c : parent.children()) if (!c.equals(this) && !list.contains(c)) list.add(c);
        }
        for (Family f : parentFamilies()) for (Person c : f.children()) if (!c.equals(this) && !list.contains(c)) list.add(c);
        list.sort(FamilyTree.BY_BIRTH);
        return list;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Person p && p.node == node;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(node);
    }

    @Override
    public String toString() {
        return fullName() + " (" + id() + ")";
    }
}
