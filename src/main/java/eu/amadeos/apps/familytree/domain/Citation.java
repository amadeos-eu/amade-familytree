package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

/**
 * A reference to a source ({@code SOUR @S1@}) with the place in it ({@code PAGE}: "p. 112, entry 37").
 * Old files may give the source as text instead of a pointer; {@link #sourceId()} is then null.
 */
public record Citation(GedcomNode node) {
    public String sourceId() {
        return node.pointer();
    }

    /** The text of a source given in place (no record), or "". */
    public String inlineText() {
        return node.isPointer() ? "" : node.text();
    }

    public String page() {
        return node.valueOf("PAGE");
    }

    public void setPage(String page) {
        node.set("PAGE", page == null ? "" : page.strip());
    }

    /** {@code QUAY}: 0 unreliable to 3 direct evidence, or -1 when not given. */
    public int quality() {
        try {
            return Integer.parseInt(node.valueOf("QUAY").strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static GedcomNode to(String sourceId, String page) {
        GedcomNode n = new GedcomNode("SOUR", GedcomNode.pointerTo(sourceId));
        if (page != null && !page.isBlank()) n.add("PAGE", page.strip());
        return n;
    }
}
