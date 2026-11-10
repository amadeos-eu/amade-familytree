package eu.amadeos.apps.familytree.gedcom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One GEDCOM structure: a tag, an optional cross-reference id ({@code @I1@}, stored without the at signs), an
 * optional value and the structures below it. The whole file is kept as a tree of these, so that entries the
 * program does not understand (for example {@code _UID} or {@code _UPD} from other programs) survive unchanged
 * and are written back on export.
 *
 * <p>Values hold line breaks as {@code \n}; {@code CONT} and {@code CONC} exist only in the file, the reader
 * joins them and the writer splits again. A value that points to a record ({@code @I1@}) is kept as written.
 */
public final class GedcomNode {
    private String xref;
    private String tag;
    private String value;
    private final List<GedcomNode> children = new ArrayList<>();

    public GedcomNode(String tag) {
        this(null, tag, null);
    }

    public GedcomNode(String tag, String value) {
        this(null, tag, value);
    }

    public GedcomNode(String xref, String tag, String value) {
        this.xref = xref == null || xref.isEmpty() ? null : xref;
        this.tag = Objects.requireNonNull(tag, "tag");
        this.value = value == null || value.isEmpty() ? null : value;
    }

    /** The id without at signs, or null. */
    public String xref() {
        return xref;
    }

    public void setXref(String newXref) {
        xref = newXref == null || newXref.isEmpty() ? null : newXref;
    }

    public String tag() {
        return tag;
    }

    public void setTag(String newTag) {
        tag = Objects.requireNonNull(newTag, "tag");
    }

    /** The value, or null when there is none. */
    public String value() {
        return value;
    }

    /** The value, or "" when there is none. */
    public String text() {
        return value == null ? "" : value;
    }

    public void setValue(String newValue) {
        value = newValue == null || newValue.isEmpty() ? null : newValue;
    }

    /** A live view of the structures below this one. */
    public List<GedcomNode> children() {
        return Collections.unmodifiableList(children);
    }

    /** True for a value like {@code @I12@}, which points to a record. */
    public boolean isPointer() {
        return isPointer(value);
    }

    public static boolean isPointer(String v) {
        return v != null && v.length() > 2 && v.charAt(0) == '@' && v.charAt(v.length() - 1) == '@'
                && v.charAt(1) != '#' && v.charAt(1) != '@' && v.indexOf('@', 1) == v.length() - 1;
    }

    /** The id this value points to (without at signs), or null. */
    public String pointer() {
        return isPointer() ? value.substring(1, value.length() - 1) : null;
    }

    public static String pointerTo(String id) {
        return "@" + id + "@";
    }

    // ---- reading -----------------------------------------------------------------------------------------------

    public Optional<GedcomNode> child(String childTag) {
        for (GedcomNode c : children) if (c.tag.equals(childTag)) return Optional.of(c);
        return Optional.empty();
    }

    public List<GedcomNode> children(String childTag) {
        List<GedcomNode> list = new ArrayList<>();
        for (GedcomNode c : children) if (c.tag.equals(childTag)) list.add(c);
        return list;
    }

    public boolean has(String childTag) {
        return child(childTag).isPresent();
    }

    /** The node at a dotted path below this one, such as {@code BIRT.DATE}; the first match at each step. */
    public Optional<GedcomNode> find(String path) {
        GedcomNode n = this;
        for (String part : path.split("\\.")) {
            Optional<GedcomNode> c = n.child(part);
            if (c.isEmpty()) return Optional.empty();
            n = c.get();
        }
        return Optional.of(n);
    }

    /** The value at a dotted path, or "" when there is none. */
    public String valueOf(String path) {
        return find(path).map(GedcomNode::text).orElse("");
    }

    // ---- changing ----------------------------------------------------------------------------------------------

    public GedcomNode add(GedcomNode child) {
        children.add(Objects.requireNonNull(child));
        return child;
    }

    public GedcomNode add(int index, GedcomNode child) {
        children.add(Math.max(0, Math.min(index, children.size())), Objects.requireNonNull(child));
        return child;
    }

    public GedcomNode add(String childTag, String childValue) {
        return add(new GedcomNode(childTag, childValue));
    }

    public boolean remove(GedcomNode child) {
        return children.remove(child);
    }

    public void removeAll(String childTag) {
        children.removeIf(c -> c.tag.equals(childTag));
    }

    public void clearChildren() {
        children.clear();
    }

    public int indexOf(GedcomNode child) {
        return children.indexOf(child);
    }

    public void move(GedcomNode child, int newIndex) {
        if (!children.remove(child)) return;
        children.add(Math.max(0, Math.min(newIndex, children.size())), child);
    }

    /** The first child with this tag, made when missing. */
    public GedcomNode getOrAdd(String childTag) {
        return child(childTag).orElseGet(() -> add(new GedcomNode(childTag)));
    }

    /**
     * Sets the value at a dotted path, making missing nodes; an empty value removes the last node of the path
     * when it has nothing below it.
     */
    public void set(String path, String newValue) {
        String[] parts = path.split("\\.");
        GedcomNode n = this;
        for (int i = 0; i < parts.length - 1; i++) {
            if (newValue == null || newValue.isEmpty()) {
                Optional<GedcomNode> c = n.child(parts[i]);
                if (c.isEmpty()) return;
                n = c.get();
            } else {
                n = n.getOrAdd(parts[i]);
            }
        }
        String last = parts[parts.length - 1];
        if (newValue == null || newValue.isEmpty()) {
            n.child(last).filter(c -> c.children.isEmpty()).ifPresent(n::remove);
            n.child(last).ifPresent(c -> c.setValue(null));
        } else {
            n.getOrAdd(last).setValue(newValue);
        }
    }

    /** A deep copy. */
    public GedcomNode copy() {
        GedcomNode c = new GedcomNode(xref, tag, value);
        for (GedcomNode child : children) c.children.add(child.copy());
        return c;
    }

    /** Calls the visitor for this node and every node below it, parents first. */
    public void walk(java.util.function.Consumer<GedcomNode> visitor) {
        visitor.accept(this);
        for (GedcomNode c : List.copyOf(children)) c.walk(visitor);
    }

    /** Same tag, id, value and children, in the same order. */
    public boolean sameAs(GedcomNode other) {
        if (other == null || !tag.equals(other.tag) || !Objects.equals(xref, other.xref)
                || !Objects.equals(value, other.value) || children.size() != other.children.size()) return false;
        for (int i = 0; i < children.size(); i++) if (!children.get(i).sameAs(other.children.get(i))) return false;
        return true;
    }

    @Override
    public String toString() {
        return (xref != null ? "@" + xref + "@ " : "") + tag + (value != null ? " " + value : "");
    }
}
