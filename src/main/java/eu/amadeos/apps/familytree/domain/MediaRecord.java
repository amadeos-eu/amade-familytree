package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

import java.util.Locale;
import java.util.Optional;

/**
 * A media record ({@code OBJE}): a photo or document file, given as a path relative to the tree's folder, as an
 * absolute path or as a web address ({@code FILE}), with its type ({@code FORM}) and title ({@code TITL}).
 */
public record MediaRecord(GedcomNode node) {
    public String id() {
        return node.xref();
    }

    public Optional<GedcomNode> fileNode() {
        return node.child("FILE");
    }

    /** The {@code FILE} value: "media/ernst.jpg", "/home/…/x.jpg", "https://…" or "". */
    public String file() {
        return node.valueOf("FILE");
    }

    public void setFile(String file) {
        node.getOrAdd("FILE").setValue(file);
    }

    public boolean isWeb() {
        String f = file().toLowerCase(Locale.ROOT);
        return f.startsWith("http://") || f.startsWith("https://");
    }

    public String form() {
        return node.valueOf("FILE.FORM");
    }

    public void setForm(String mime) {
        fileNode().ifPresent(f -> f.set("FORM", mime));
    }

    public String title() {
        String t = node.valueOf("FILE.TITL");
        return t.isEmpty() ? node.valueOf("TITL") : t;
    }

    public void setTitle(String title) {
        GedcomNode file = node.getOrAdd("FILE");
        file.set("TITL", title == null ? "" : title.strip());
    }

    /** True for pictures the program can show (by type or by the file's extension). */
    public boolean isImage() {
        String form = form().toLowerCase(Locale.ROOT);
        if (form.startsWith("image/")) return true;
        String f = file().toLowerCase(Locale.ROOT);
        int q = f.indexOf('?');
        if (q >= 0) f = f.substring(0, q);
        return f.endsWith(".jpg") || f.endsWith(".jpeg") || f.endsWith(".png") || f.endsWith(".gif")
                || f.endsWith(".bmp") || f.endsWith(".webp") || f.endsWith(".tif") || f.endsWith(".tiff");
    }

    /** The name to show: the title, else the file name. */
    public String label() {
        String t = title();
        if (!t.isEmpty()) return t;
        String f = file();
        int q = f.indexOf('?');
        if (q >= 0) f = f.substring(0, q);
        int slash = Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\'));
        return slash >= 0 ? f.substring(slash + 1) : f;
    }
}
