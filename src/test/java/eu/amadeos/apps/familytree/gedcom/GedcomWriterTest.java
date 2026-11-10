package eu.amadeos.apps.familytree.gedcom;

import eu.amadeos.apps.familytree.Samples;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomWriterTest {
    @Test
    void contAndEscapes() {
        GedcomNode n = new GedcomNode("N1", "NOTE", "line one\nline two\n\nfour");
        GedcomNode i = new GedcomNode("I1", "INDI", null);
        i.add("NOTE", "@home");
        i.add("FAMS", "@F1@");
        String out = GedcomWriter.write(new GedcomNode("HEAD"), List.of(n, i), GedcomVersion.V70);
        assertEquals("0 HEAD\n0 @N1@ NOTE line one\n1 CONT line two\n1 CONT\n1 CONT four\n0 @I1@ INDI\n"
                + "1 NOTE @@home\n1 FAMS @F1@\n0 TRLR\n", out);
    }

    @Test
    void longLinesIn551() throws IOException {
        String words = "Wort ".repeat(120).strip();
        GedcomNode n = new GedcomNode("N1", "NOTE", words);
        String out = GedcomWriter.write(new GedcomNode("HEAD"), List.of(n), GedcomVersion.V551);
        for (String line : out.split("\n")) {
            assertTrue(line.length() <= 255, line);
            assertFalse(line.endsWith(" "), "split next to a space: " + line);
            if (line.startsWith("1 CONC ")) assertFalse(line.startsWith("1 CONC  "), line);
        }
        assertTrue(out.contains("1 CONC "));
        GedcomFile back = GedcomReader.read(out.getBytes(StandardCharsets.UTF_8));
        assertEquals(words, back.records().getFirst().value());
        // 7.0 has no line limit.
        assertFalse(GedcomWriter.write(new GedcomNode("HEAD"), List.of(n), GedcomVersion.V70).contains("CONC"));
    }

    @Test
    void roundTripKeepsEverything() throws IOException {
        GedcomFile f = GedcomReader.read(Samples.bytes(Samples.BRENNER));
        String out = GedcomWriter.write(f.header(), f.records(), GedcomVersion.V551);
        GedcomFile back = GedcomReader.read(out.getBytes(StandardCharsets.UTF_8));
        assertEquals(f.records().size(), back.records().size());
        for (int k = 0; k < f.records().size(); k++) {
            assertTrue(f.records().get(k).sameAs(back.records().get(k)), f.records().get(k).toString());
        }
        assertTrue(out.contains("1 _UID 7A1B2C3D4E5F60718293A4B5C6D7E8F9"));
    }

    @Test
    void version7StartsWithAByteOrderMark() throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        GedcomWriter.write(new GedcomNode("HEAD"), List.of(), GedcomVersion.V70, os);
        byte[] b = os.toByteArray();
        assertEquals((byte) 0xEF, b[0]);
        assertEquals("0 HEAD\n0 TRLR\n", new String(b, 3, b.length - 3, StandardCharsets.UTF_8));
    }
}
