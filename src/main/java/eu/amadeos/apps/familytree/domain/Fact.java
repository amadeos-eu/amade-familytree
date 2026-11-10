package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One event or attribute of a person or family, a view on its GEDCOM structure ({@code BIRT}, {@code OCCU},
 * ...). Changes go straight into the structure.
 */
public record Fact(GedcomNode node) {
    public Optional<FactType> type() {
        return FactType.of(node.tag());
    }

    public String tag() {
        return node.tag();
    }

    public GenDate date() {
        Optional<GedcomNode> d = node.child("DATE");
        return d.map(n -> GenDate.parse(n.value(), n.valueOf("PHRASE"))).orElse(GenDate.NONE);
    }

    public void setDate(GenDate date) {
        if (date == null || date.isEmpty()) {
            node.removeAll("DATE");
            return;
        }
        GedcomNode d = node.child("DATE").orElseGet(() -> node.add(indexAfterType(), new GedcomNode("DATE")));
        d.setValue(date.gedcom());
        d.removeAll("PHRASE");
        if (date.phrase() != null) d.add(0, new GedcomNode("PHRASE", date.phrase()));
    }

    public String place() {
        return node.valueOf("PLAC");
    }

    public void setPlace(String place) {
        if (place == null || place.isBlank()) {
            node.child("PLAC").ifPresent(p -> {
                if (p.children().isEmpty()) node.remove(p);
                else p.setValue(null);
            });
            return;
        }
        GedcomNode p = node.child("PLAC").orElseGet(() -> {
            int i = node.child("DATE").map(d -> node.indexOf(d) + 1).orElse(indexAfterType());
            return node.add(i, new GedcomNode("PLAC"));
        });
        p.setValue(place.strip());
    }

    private int indexAfterType() {
        return node.child("TYPE").map(t -> node.indexOf(t) + 1).orElse(0);
    }

    /**
     * The text of the fact: the value of an attribute (the occupation), the kind of an {@code EVEN} or
     * {@code FACT} ({@code TYPE}), or for other events their {@code TYPE} or value. "Y" (event happened, date
     * unknown) is not text.
     */
    public String details() {
        String v = node.text();
        if (v.equals("Y")) v = "";
        boolean typed = node.tag().equals("EVEN") || node.tag().equals("FACT");
        if (typed) {
            String type = node.valueOf("TYPE");
            if (!v.isEmpty() && !type.isEmpty()) return type + ": " + v;
            return type.isEmpty() ? v : type;
        }
        if (!v.isEmpty()) return v;
        return node.valueOf("TYPE");
    }

    /** The kind of an {@code EVEN} or {@code FACT} ("Emigration ship"), or "". */
    public String customType() {
        return node.valueOf("TYPE");
    }

    public void setDetails(String text) {
        String t = text == null ? "" : text.strip();
        boolean attribute = type().map(FactType::isAttribute).orElse(false);
        if (node.tag().equals("EVEN") || node.tag().equals("FACT")) {
            node.set("TYPE", t);
            if (node.text().equals("Y")) node.setValue(null);
        } else if (attribute) {
            node.setValue(t);
        } else if (!node.text().isEmpty() && !node.text().equals("Y")) {
            node.setValue(t.isEmpty() ? null : t);
        } else {
            node.set("TYPE", t);
        }
    }

    public List<Citation> citations() {
        List<Citation> list = new ArrayList<>();
        for (GedcomNode s : node.children("SOUR")) list.add(new Citation(s));
        return list;
    }

    public List<String> notes() {
        List<String> list = new ArrayList<>();
        for (GedcomNode n : node.children("NOTE")) list.add(n.text());
        return list;
    }

    /** The other partner of a family fact, or the age at the time ({@code AGE}), or the cause ({@code CAUS}). */
    public String cause() {
        return node.valueOf("CAUS");
    }

    public String age() {
        return node.valueOf("AGE");
    }
}
