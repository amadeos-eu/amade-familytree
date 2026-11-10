package eu.amadeos.apps.familytree.gedcom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads GEDCOM 5.5, 5.5.1 and 7.0. Lenient on purpose, because files from real programs bend the rules:
 * <ul>
 * <li>Character sets: a byte order mark wins; otherwise {@code HEAD.CHAR} (UTF-8, UNICODE, ANSEL, ANSI,
 * ASCII, ISO-8859-1, IBMPC). A file that claims ANSI or ASCII but is valid UTF-8 is read as UTF-8, and a file
 * that claims UTF-8 but is not is read as Windows-1252, with a warning.</li>
 * <li>Leading spaces and blank lines are ignored, a line without level counts as the continuation of the value
 * above it, and a level that jumps by more than one is attached to the structure above.</li>
 * <li>{@code CONT} and {@code CONC} are joined into the value; a leading {@code @@} becomes {@code @}.</li>
 * </ul>
 */
public final class GedcomReader {
    private static final Pattern LINE = Pattern.compile("^\\s*(\\d{1,2})\\s+(?:@([^@\\s][^@]*)@\\s+)?(\\S+)(?: (.*))?$");
    private static final Pattern CHAR = Pattern.compile("(?m)^\\s*1\\s+CHAR\\s+(\\S+)");

    private final List<String> warnings = new ArrayList<>();

    public static GedcomFile read(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    public static GedcomFile read(InputStream in) throws IOException {
        return read(in.readAllBytes());
    }

    public static GedcomFile read(byte[] bytes) throws IOException {
        return new GedcomReader().parse(bytes);
    }

    private GedcomFile parse(byte[] bytes) throws IOException {
        Decoded decoded = decode(bytes, this);
        String text = decoded.text();
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        List<GedcomNode> top = parseLines(text);
        GedcomNode header = null;
        List<GedcomNode> records = new ArrayList<>();
        for (GedcomNode n : top) {
            if (n.tag().equals("HEAD") && header == null) header = n;
            else if (n.tag().equals("TRLR")) continue;
            else records.add(n);
        }
        if (header == null) {
            if (records.isEmpty()) throw new IOException("This is not a GEDCOM file.");
            warn("The file has no header (HEAD); a new one is used.");
            header = new GedcomNode("HEAD");
        }
        boolean v7 = GedcomVersion.of(header.valueOf("GEDC.VERS")) == GedcomVersion.V70;
        for (GedcomNode r : records) join(r, v7);
        join(header, v7);
        return new GedcomFile(header, records, GedcomVersion.of(header.valueOf("GEDC.VERS")), decoded.charset(),
                warnings);
    }

    private void warn(String text) {
        if (warnings.size() < GedcomFile.MAX_WARNINGS) warnings.add(text);
    }

    // ---- characters ------------------------------------------------------------------------------------------

    record Decoded(String text, String charset) {
    }

    /** Works out the character set and decodes. */
    static Decoded decode(byte[] bytes) {
        return decode(bytes, null);
    }

    // --8<-- [start:decode]
    private static Decoded decode(byte[] bytes, GedcomReader reader) {
        int n = bytes.length;
        if (n >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new Decoded(new String(bytes, 3, n - 3, StandardCharsets.UTF_8), "UTF-8");
        }
        if (n >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new Decoded(new String(bytes, 2, n - 2, StandardCharsets.UTF_16BE), "UTF-16");
        }
        if (n >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new Decoded(new String(bytes, 2, n - 2, StandardCharsets.UTF_16LE), "UTF-16");
        }
        if (n >= 2 && bytes[0] == 0 && bytes[1] != 0) return new Decoded(new String(bytes, StandardCharsets.UTF_16BE), "UTF-16");
        if (n >= 2 && bytes[0] != 0 && bytes[1] == 0) return new Decoded(new String(bytes, StandardCharsets.UTF_16LE), "UTF-16");

        // The header is plain ASCII in every character set the program reads.
        String head = new String(bytes, 0, Math.min(n, 4096), StandardCharsets.ISO_8859_1);
        Matcher cm = CHAR.matcher(head);
        String declared = cm.find() ? cm.group(1).toUpperCase(Locale.ROOT) : "";
        String utf8 = strictUtf8(bytes);
        switch (declared) {
            case "ANSEL" -> {
                if (utf8 != null && !isAscii(bytes)) return new Decoded(utf8, "UTF-8");
                return new Decoded(Ansel.decode(bytes), "ANSEL");
            }
            case "ANSI", "WINDOWS-1252", "CP1252", "ASCII", "ANSII" -> {
                if (utf8 != null) return new Decoded(utf8, isAscii(bytes) ? "ASCII" : "UTF-8");
                return new Decoded(new String(bytes, Charset.forName("windows-1252")), "Windows-1252");
            }
            case "ISO-8859-1", "ISO8859-1", "LATIN1", "ISO-LATIN-1" -> {
                if (utf8 != null) return new Decoded(utf8, isAscii(bytes) ? "ASCII" : "UTF-8");
                return new Decoded(new String(bytes, StandardCharsets.ISO_8859_1), "ISO-8859-1");
            }
            case "IBMPC", "IBM-PC", "CP437", "CP850" -> {
                if (utf8 != null) return new Decoded(utf8, isAscii(bytes) ? "ASCII" : "UTF-8");
                return new Decoded(new String(bytes, Charset.forName(declared.equals("CP850") ? "IBM850" : "IBM437")),
                        declared);
            }
            default -> {
                if (utf8 != null) return new Decoded(utf8, "UTF-8");
                if (reader != null) reader.warn("The file is not valid UTF-8; it was read as Windows-1252.");
                return new Decoded(new String(bytes, Charset.forName("windows-1252")), "Windows-1252");
            }
        }
    }
    // --8<-- [end:decode]

    private static boolean isAscii(byte[] bytes) {
        for (byte b : bytes) if (b < 0) return false;
        return true;
    }

    private static String strictUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    // ---- lines -----------------------------------------------------------------------------------------------

    private List<GedcomNode> parseLines(String text) {
        List<GedcomNode> top = new ArrayList<>();
        Deque<GedcomNode> stack = new ArrayDeque<>();
        Deque<Integer> levels = new ArrayDeque<>();
        GedcomNode last = null;
        int lineNo = 0;
        for (String raw : text.split("\r\n|\r|\n", -1)) {
            lineNo++;
            if (raw.isBlank()) continue;
            Matcher m = LINE.matcher(raw);
            if (!m.matches()) {
                if (last != null) {
                    // A line break inside a value, written without CONT.
                    last.setValue(last.text() + "\n" + raw.strip());
                    warn("Line " + lineNo + " has no level; it was added to the line above.");
                } else {
                    warn("Line " + lineNo + " could not be read and was skipped.");
                }
                continue;
            }
            int level = Integer.parseInt(m.group(1));
            String value = m.group(4);
            GedcomNode node = new GedcomNode(m.group(2), m.group(3).toUpperCase(Locale.ROOT).equals(m.group(3))
                    ? m.group(3) : fixTag(m.group(3)), value);
            if (level == 0) {
                top.add(node);
                stack.clear();
                levels.clear();
            } else {
                while (!levels.isEmpty() && levels.peek() >= level) {
                    levels.pop();
                    stack.pop();
                }
                if (stack.isEmpty()) {
                    warn("Line " + lineNo + " has no record above it and was skipped.");
                    continue;
                }
                if (level > levels.peek() + 1) {
                    warn("Line " + lineNo + " skips a level; it was attached to the line above.");
                }
                stack.peek().add(node);
            }
            stack.push(node);
            levels.push(level);
            last = node;
        }
        return top;
    }

    /** Tags are upper case; extension tags keep their spelling ({@code _Marnm} stays as written). */
    private static String fixTag(String tag) {
        return tag.startsWith("_") ? tag : tag.toUpperCase(Locale.ROOT);
    }

    /** Joins CONT and CONC into the values, recursively, and undoes the @@ escape. */
    // --8<-- [start:join]
    private static void join(GedcomNode node, boolean v7) {
        if (node.has("CONT") || node.has("CONC")) {
            StringBuilder value = new StringBuilder(node.text());
            for (GedcomNode c : List.copyOf(node.children())) {
                if (c.tag().equals("CONT")) {
                    value.append('\n').append(c.text());
                    node.remove(c);
                } else if (c.tag().equals("CONC")) {
                    value.append(c.text());
                    node.remove(c);
                }
            }
            node.setValue(value.toString());
        }
        String v = node.value();
        if (v != null && v.contains("@@")) {
            if (v.startsWith("@@")) v = v.substring(1);
            if (!v7) v = v.replace("@@", "@");
            node.setValue(v);
        }
        for (GedcomNode c : node.children()) join(c, v7);
    }
    // --8<-- [end:join]
}
