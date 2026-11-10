package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A family ({@code FAM} record): up to two partners ({@code HUSB}, {@code WIFE}, whatever their sex) and their
 * children ({@code CHIL}), with marriage and divorce as facts.
 */
public final class Family {
    private final FamilyTree tree;
    private final GedcomNode node;

    Family(FamilyTree tree, GedcomNode node) {
        this.tree = Objects.requireNonNull(tree);
        this.node = Objects.requireNonNull(node);
    }

    public String id() {
        return node.xref();
    }

    public GedcomNode node() {
        return node;
    }

    public Optional<Person> husband() {
        return node.child("HUSB").map(GedcomNode::pointer).flatMap(tree::person);
    }

    public Optional<Person> wife() {
        return node.child("WIFE").map(GedcomNode::pointer).flatMap(tree::person);
    }

    public List<Person> partners() {
        List<Person> list = new ArrayList<>();
        husband().ifPresent(list::add);
        wife().ifPresent(list::add);
        return list;
    }

    public Optional<Person> partnerOf(Person p) {
        Optional<Person> h = husband();
        Optional<Person> w = wife();
        if (h.isPresent() && h.get().equals(p)) return w;
        if (w.isPresent() && w.get().equals(p)) return h;
        return Optional.empty();
    }

    /** The children in the order of the record (which is usually birth order). */
    public List<Person> children() {
        List<Person> list = new ArrayList<>();
        for (GedcomNode c : node.children("CHIL")) tree.person(c.pointer()).ifPresent(list::add);
        return list;
    }

    public List<Fact> facts() {
        List<Fact> list = new ArrayList<>();
        for (GedcomNode c : node.children()) if (FactType.of(c.tag()).isPresent()) list.add(new Fact(c));
        return list;
    }

    public Optional<Fact> marriage() {
        return node.child("MARR").map(Fact::new);
    }

    public Optional<Fact> divorce() {
        return node.child("DIV").map(Fact::new);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Family f && f.node == node;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(node);
    }

    @Override
    public String toString() {
        return "Family " + id();
    }
}
