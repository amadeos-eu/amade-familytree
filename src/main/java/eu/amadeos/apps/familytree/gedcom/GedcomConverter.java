package eu.amadeos.apps.familytree.gedcom;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns structures read from one GEDCOM version into the other, on copies, and makes the header for writing.
 * Only what differs between 5.5.1 and 7.0 and matters for family trees is changed:
 *
 * <table>
 * <tr><th>5.5.1</th><th>7.0</th></tr>
 * <tr><td>{@code 0 @N1@ NOTE text}, {@code 1 NOTE @N1@}</td><td>{@code 0 @N1@ SNOTE text}, {@code 1 SNOTE @N1@}</td></tr>
 * <tr><td>{@code FILE} with {@code FORM jpg}</td><td>{@code FILE} with {@code FORM image/jpeg}</td></tr>
 * <tr><td>{@code NAME.TYPE birth}, {@code married}, {@code aka}</td><td>{@code BIRTH}, {@code MARRIED}, {@code AKA}</td></tr>
 * <tr><td>{@code RESN privacy}</td><td>{@code RESN PRIVACY}</td></tr>
 * <tr><td>{@code DATE INT 1850 (text)}, {@code DATE (text)}</td><td>{@code DATE 1850} with {@code PHRASE text}</td></tr>
 * <tr><td>{@code CHAR}, {@code SUBM}, {@code GEDC.FORM} in the header</td><td>not used</td></tr>
 * </table>
 *
 * Everything else, including all extension tags ({@code _UID}, {@code _MARNM}, ...), is copied unchanged.
 */
public final class GedcomConverter {
    /** The program name written to {@code HEAD.SOUR}. */
    public static final String PRODUCT = "AMADE_FAMILYTREE";
    public static final String PRODUCT_NAME = "amadeOS Family Tree";

    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("bmp", "image/bmp"), Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"), Map.entry("htm", "text/html"), Map.entry("html", "text/html"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("ogg", "audio/ogg"),
            Map.entry("mp4", "video/mp4"), Map.entry("avi", "video/x-msvideo"), Map.entry("svg", "image/svg+xml"),
            Map.entry("heic", "image/heic"), Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("odt", "application/vnd.oasis.opendocument.text"));
    private static final Set<String> NAME_TYPES = Set.of("AKA", "BIRTH", "IMMIGRANT", "MAIDEN", "MARRIED",
            "PROFESSIONAL");
    private static final Set<String> RESN = Set.of("CONFIDENTIAL", "LOCKED", "PRIVACY");

    private GedcomConverter() {
    }

    /** Copies of the records in the target version; the input stays as it is. */
    public static List<GedcomNode> records(List<GedcomNode> records, GedcomVersion target) {
        List<GedcomNode> out = new ArrayList<>(records.size());
        for (GedcomNode r : records) {
            GedcomNode c = r.copy();
            if (target == GedcomVersion.V70) to70(c, true);
            else to551(c, true);
            out.add(c);
        }
        return out;
    }

    /**
     * The header to write: the parts of the old header that describe the data (language, place format, notes,
     * copyright, extension tags) are kept; version, character set, program and date are written new.
     *
     * @param submitter for 5.5.1, which requires a submitter: the id of a {@code SUBM} record, or null
     */
    public static GedcomNode header(GedcomNode old, GedcomVersion target, String programVersion, String submitter,
                                    String fileName) {
        GedcomNode h = new GedcomNode("HEAD");
        GedcomNode gedc = h.add(new GedcomNode("GEDC"));
        gedc.add("VERS", target.number());
        if (target == GedcomVersion.V551) gedc.add("FORM", "LINEAGE-LINKED");
        if (target == GedcomVersion.V551) h.add("CHAR", "UTF-8");
        GedcomNode sour = h.add(new GedcomNode("SOUR", PRODUCT));
        sour.add("NAME", PRODUCT_NAME);
        if (programVersion != null && !programVersion.isBlank()) sour.add("VERS", programVersion);
        h.add("DATE", LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ROOT))
                .toUpperCase(Locale.ROOT));
        if (target == GedcomVersion.V551 && submitter != null) h.add("SUBM", GedcomNode.pointerTo(submitter));
        if (target == GedcomVersion.V551 && fileName != null && !fileName.isBlank()) h.add("FILE", fileName);
        if (old != null) {
            for (GedcomNode c : old.children()) {
                switch (c.tag()) {
                    case "GEDC", "CHAR", "SOUR", "DATE", "SUBM", "SUBN", "FILE", "DEST", "SCHMA" -> {
                    }
                    default -> {
                        GedcomNode copy = c.copy();
                        if (target == GedcomVersion.V70) to70(copy, false);
                        else to551(copy, false);
                        h.add(copy);
                    }
                }
            }
        }
        return h;
    }

    // ---- to 7.0 ----------------------------------------------------------------------------------------------

    private static void to70(GedcomNode n, boolean record) {
        if (record && n.tag().equals("NOTE") && n.xref() != null) n.setTag("SNOTE");
        if (!record && n.tag().equals("NOTE") && n.isPointer()) n.setTag("SNOTE");
        switch (n.tag()) {
            case "FILE" -> n.child("FORM").ifPresent(f -> {
                String mime = mime(f.text());
                if (mime != null) f.setValue(mime);
                f.child("MEDI").ifPresent(m -> m.setValue(m.text().toUpperCase(Locale.ROOT)));
            });
            case "OBJE" -> {
                // 5.5: FORM next to FILE instead of below it.
                n.child("FORM").ifPresent(form -> n.child("FILE").ifPresent(file -> {
                    n.remove(form);
                    if (!file.has("FORM")) {
                        String mime = mime(form.text());
                        file.add("FORM", mime != null ? mime : form.text());
                    }
                }));
            }
            case "TYPE" -> {
            }
            case "RESN" -> n.setValue(resn70(n.text()));
            case "DATE" -> {
                String v = n.text().strip();
                String phrase = null;
                int open = v.indexOf('(');
                if (open >= 0 && v.endsWith(")")) {
                    phrase = v.substring(open + 1, v.length() - 1).strip();
                    v = v.substring(0, open).strip();
                }
                if (v.toUpperCase(Locale.ROOT).startsWith("INT ")) v = v.substring(4).strip();
                n.setValue(v);
                if (phrase != null && !phrase.isEmpty() && !n.has("PHRASE")) n.add(0, new GedcomNode("PHRASE", phrase));
            }
            default -> {
            }
        }
        if (n.tag().equals("NAME")) {
            n.child("TYPE").ifPresent(t -> {
                String up = t.text().strip().toUpperCase(Locale.ROOT);
                if (NAME_TYPES.contains(up)) t.setValue(up);
                else if (!up.isEmpty() && !up.equals("OTHER")) {
                    String phrase = t.text().strip();
                    t.setValue("OTHER");
                    if (!t.has("PHRASE")) t.add("PHRASE", phrase);
                }
            });
        }
        for (GedcomNode c : n.children()) to70(c, false);
    }

    private static String resn70(String v) {
        List<String> out = new ArrayList<>();
        for (String part : v.split("[,\\s]+")) {
            String up = part.toUpperCase(Locale.ROOT);
            if (RESN.contains(up)) out.add(up);
        }
        return out.isEmpty() ? v : String.join(", ", out);
    }

    /** The media type for a 5.5.1 format such as "jpg", or the value itself when it already is one. */
    public static String mime(String form) {
        if (form == null) return null;
        String f = form.strip().toLowerCase(Locale.ROOT);
        if (f.contains("/")) return f;
        return MIME.get(f.startsWith(".") ? f.substring(1) : f);
    }

    /** The 5.5.1 format for a media type such as "image/jpeg", or the value itself. */
    public static String form551(String mime) {
        if (mime == null) return null;
        String m = mime.strip().toLowerCase(Locale.ROOT);
        if (!m.contains("/")) return m;
        if (m.equals("image/jpeg")) return "jpg";
        if (m.equals("image/tiff")) return "tif";
        for (Map.Entry<String, String> e : MIME.entrySet()) if (e.getValue().equals(m)) return e.getKey();
        return m.substring(m.indexOf('/') + 1);
    }

    // ---- to 5.5.1 --------------------------------------------------------------------------------------------

    private static void to551(GedcomNode n, boolean record) {
        if (n.tag().equals("SNOTE")) n.setTag("NOTE");
        switch (n.tag()) {
            case "FILE" -> n.child("FORM").ifPresent(f -> {
                f.setValue(form551(f.text()));
                f.child("MEDI").ifPresent(m -> m.setValue(m.text().toLowerCase(Locale.ROOT)));
            });
            case "RESN" -> n.setValue(n.text().toLowerCase(Locale.ROOT));
            case "DATE" -> n.child("PHRASE").ifPresent(p -> {
                n.remove(p);
                String v = n.text().strip();
                n.setValue(v.isEmpty() ? "(" + p.text() + ")" : "INT " + v + " (" + p.text() + ")");
            });
            case "NAME" -> n.child("TYPE").ifPresent(t -> {
                if (t.text().equals("OTHER")) {
                    t.setValue(t.child("PHRASE").map(GedcomNode::text).orElse("other"));
                    t.removeAll("PHRASE");
                } else {
                    t.setValue(t.text().toLowerCase(Locale.ROOT));
                }
            });
            default -> {
            }
        }
        for (GedcomNode c : n.children()) to551(c, false);
    }
}
