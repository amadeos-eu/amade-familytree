package eu.amadeos.apps.familytree.gedcom;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes GEDCOM 7.0 or 5.5.1, always in UTF-8 with LF line ends (both versions allow them); 7.0 files start with
 * a byte order mark, as the 7.0 specification recommends. The structures must already be in the target version, see {@link GedcomConverter}.
 *
 * <ul>
 * <li>Line breaks in values become {@code CONT} lines.</li>
 * <li>5.5.1 limits a line to 255 characters; longer values are split with {@code CONC}, never next to a
 * space, because some programs drop spaces at the split.</li>
 * <li>A value that starts with {@code @} but does not point to a record is written with {@code @@}.</li>
 * </ul>
 */
public final class GedcomWriter {
    /** 5.5.1: at most 255 characters per line; this leaves room for level, tag and id. */
    static final int MAX_VALUE_551 = 200;

    private final GedcomVersion version;
    private final StringBuilder out = new StringBuilder(1 << 16);

    private GedcomWriter(GedcomVersion version) {
        this.version = version;
    }

    /** The whole file: header, records, {@code TRLR}. */
    public static String write(GedcomNode header, List<GedcomNode> records, GedcomVersion version) {
        GedcomWriter w = new GedcomWriter(version);
        w.node(header, 0);
        for (GedcomNode r : records) w.node(r, 0);
        w.out.append("0 TRLR\n");
        return w.out.toString();
    }

    public static void write(GedcomNode header, List<GedcomNode> records, GedcomVersion version, OutputStream os)
            throws IOException {
        byte[] bytes = write(header, records, version).getBytes(StandardCharsets.UTF_8);
        if (version == GedcomVersion.V70) os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        os.write(bytes);
    }

    private void node(GedcomNode n, int level) {
        String v = n.value();
        String[] lines = v == null ? new String[]{null} : v.split("\n", -1);
        line(level, n.xref(), n.tag(), lines[0], n.isPointer());
        for (int i = 1; i < lines.length; i++) line(level + 1, null, "CONT", lines[i], false);
        for (GedcomNode c : n.children()) node(c, level + 1);
    }

    private void line(int level, String xref, String tag, String value, boolean pointer) {
        if (value != null && !pointer && value.startsWith("@") && !value.startsWith("@#")) value = "@" + value;
        if (version == GedcomVersion.V551 && value != null && value.length() > MAX_VALUE_551) {
            int cut = cut(value);
            start(level, xref, tag).append(' ').append(value, 0, cut).append('\n');
            String rest = value.substring(cut);
            while (!rest.isEmpty()) {
                int c = rest.length() > MAX_VALUE_551 ? cut(rest) : rest.length();
                start(level + 1, null, "CONC").append(' ').append(rest, 0, c).append('\n');
                rest = rest.substring(c);
            }
            return;
        }
        StringBuilder b = start(level, xref, tag);
        if (value != null && !value.isEmpty()) b.append(' ').append(value);
        b.append('\n');
    }

    /** Where to split a long value: at most MAX_VALUE_551, not before or after a space. */
    private static int cut(String value) {
        for (int i = MAX_VALUE_551; i > MAX_VALUE_551 / 2; i--) {
            if (value.charAt(i - 1) != ' ' && value.charAt(i) != ' '
                    && !Character.isLowSurrogate(value.charAt(i))) return i;
        }
        int i = MAX_VALUE_551;
        if (Character.isLowSurrogate(value.charAt(i))) i--;
        return i;
    }

    private StringBuilder start(int level, String xref, String tag) {
        out.append(level).append(' ');
        if (xref != null) out.append('@').append(xref).append("@ ");
        return out.append(tag);
    }
}
