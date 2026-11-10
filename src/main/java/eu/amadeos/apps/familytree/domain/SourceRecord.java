package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

/**
 * A source ({@code SOUR} record): a church book, a register office, a census, a letter. Citations point to it
 * and say where in it ({@link Citation#page()}).
 */
public record SourceRecord(GedcomNode node) {
    public String id() {
        return node.xref();
    }

    public String title() {
        String t = node.valueOf("TITL");
        if (t.isEmpty()) t = node.valueOf("ABBR");
        return t.isEmpty() ? node.text() : t;
    }

    public void setTitle(String title) {
        node.set("TITL", title == null ? "" : title.strip());
    }

    public String author() {
        return node.valueOf("AUTH");
    }

    public void setAuthor(String author) {
        node.set("AUTH", author == null ? "" : author.strip());
    }

    public String publication() {
        return node.valueOf("PUBL");
    }

    public void setPublication(String publication) {
        node.set("PUBL", publication == null ? "" : publication.strip());
    }

    public String text() {
        return node.valueOf("TEXT");
    }

    @Override
    public String toString() {
        String t = title();
        return t.isEmpty() ? id() : t;
    }
}
