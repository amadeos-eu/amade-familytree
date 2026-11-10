package eu.amadeos.apps.familytree.gedcom;

import eu.amadeos.apps.familytree.Samples;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomReaderTest {
    private static GedcomFile read(String text) throws IOException {
        return GedcomReader.read(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsTheSample() throws IOException {
        GedcomFile f = GedcomReader.read(Samples.bytes(Samples.BRENNER));
        assertEquals(GedcomVersion.V551, f.version());
        assertEquals("UTF-8", f.charset());
        assertEquals("Some Web Genealogy Service", f.sourceProgram());
        assertEquals(21, f.records().stream().filter(r -> r.tag().equals("INDI")).count());
        assertEquals(8, f.records().stream().filter(r -> r.tag().equals("FAM")).count());
        assertTrue(f.warnings().isEmpty(), f.warnings().toString());
        GedcomNode ernst = f.records().stream().filter(r -> "I1".equals(r.xref())).findFirst().orElseThrow();
        assertEquals("Ernst Wilhelm /Brenner/", ernst.valueOf("NAME"));
        assertEquals("14 MAR 1928", ernst.valueOf("BIRT.DATE"));
        assertEquals("Ernst erzählte gern von der Lehre bei Bosch.\nEr war 40 Jahre im selben Betrieb.",
                ernst.valueOf("NOTE"));
    }

    @Test
    void joinsContAndConc() throws IOException {
        GedcomFile f = read("0 HEAD\n0 @N1@ NOTE First li\n1 CONC ne\n1 CONT second line\n1 CONT\n1 CONT fourth\n0 TRLR\n");
        assertEquals("First line\nsecond line\n\nfourth", f.records().getFirst().value());
        assertTrue(f.records().getFirst().children().isEmpty());
    }

    @Test
    void undoesTheAtEscape() throws IOException {
        GedcomFile f = read("0 HEAD\n1 GEDC\n2 VERS 5.5.1\n0 @I1@ INDI\n1 EMAIL a@@example.org\n1 NOTE @@home\n"
                + "1 BIRT\n2 DATE @#DJULIAN@ 1700\n0 TRLR\n");
        GedcomNode i = f.records().getFirst();
        assertEquals("a@example.org", i.valueOf("EMAIL"));
        assertEquals("@home", i.valueOf("NOTE"));
        assertEquals("@#DJULIAN@ 1700", i.valueOf("BIRT.DATE"));
    }

    @Test
    void ansel() throws IOException {
        // "Müller" and "Gräfin" with the accent byte before the letter, "Łódź" with spacing and combining bytes.
        byte[] head = "0 HEAD\n1 CHAR ANSEL\n0 @I1@ INDI\n1 NAME ".getBytes(StandardCharsets.US_ASCII);
        byte[] name = {'M', (byte) 0xE8, 'u', 'l', 'l', 'e', 'r', ' ', 'G', 'r', (byte) 0xE8, 'a', 'f', 'i', 'n', ' ',
                (byte) 0xA1, (byte) 0xE2, 'o', 'd', (byte) 0xE2, 'z', '\n'};
        byte[] all = new byte[head.length + name.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(name, 0, all, head.length, name.length);
        GedcomFile f = GedcomReader.read(all);
        assertEquals("ANSEL", f.charset());
        assertEquals("Müller Gräfin Łódź", f.records().getFirst().valueOf("NAME"));
        assertEquals("ß", Ansel.decode(new byte[]{(byte) 0xCF}));
    }

    @Test
    void byteOrderMarksAndUtf16() throws IOException {
        String text = "0 HEAD\n0 @I1@ INDI\n1 NAME Jürgen /Weiß/\n0 TRLR\n";
        byte[] utf16 = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[utf16.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, withBom, 2, utf16.length);
        assertEquals("Jürgen /Weiß/", GedcomReader.read(withBom).records().getFirst().valueOf("NAME"));
        assertEquals("Jürgen /Weiß/", GedcomReader.read(text.getBytes(StandardCharsets.UTF_16BE)).records()
                .getFirst().valueOf("NAME"));
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        byte[] bom8 = new byte[utf8.length + 3];
        bom8[0] = (byte) 0xEF;
        bom8[1] = (byte) 0xBB;
        bom8[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, bom8, 3, utf8.length);
        assertEquals("Jürgen /Weiß/", GedcomReader.read(bom8).records().getFirst().valueOf("NAME"));
    }

    @Test
    void ansiAndMislabelledFiles() throws IOException {
        String text = "0 HEAD\n1 CHAR ANSI\n0 @I1@ INDI\n1 NAME Jürgen /Weiß/\n0 TRLR\n";
        GedcomFile cp1252 = GedcomReader.read(text.getBytes(Charset.forName("windows-1252")));
        assertEquals("Windows-1252", cp1252.charset());
        assertEquals("Jürgen /Weiß/", cp1252.records().getFirst().valueOf("NAME"));
        // Claims ANSI but is UTF-8: read as UTF-8.
        GedcomFile utf8 = GedcomReader.read(text.getBytes(StandardCharsets.UTF_8));
        assertEquals("UTF-8", utf8.charset());
        assertEquals("Jürgen /Weiß/", utf8.records().getFirst().valueOf("NAME"));
        // Claims UTF-8 but is not: repaired with a warning.
        GedcomFile bad = GedcomReader.read(text.replace("ANSI", "UTF-8").getBytes(Charset.forName("windows-1252")));
        assertEquals("Jürgen /Weiß/", bad.records().getFirst().valueOf("NAME"));
        assertFalse(bad.warnings().isEmpty());
    }

    @Test
    void lenientWithBrokenLines() throws IOException {
        GedcomFile f = read("0 HEAD\n\n  0 @I1@ INDI\n1 NOTE first\nwithout level\n1 BIRT\n3 DATE 1900\n1 name lower\n0 TRLR\n");
        GedcomNode i = f.records().getFirst();
        assertEquals("first\nwithout level", i.valueOf("NOTE"));
        assertEquals("1900", i.valueOf("BIRT.DATE"));
        assertEquals("lower", i.valueOf("NAME"));
        assertEquals(2, f.warnings().size(), f.warnings().toString());
    }

    @Test
    void version7() throws IOException {
        GedcomFile f = read("0 HEAD\n1 GEDC\n2 VERS 7.0\n0 @I1@ INDI\n1 NOTE @@x and a@@b\n0 TRLR\n");
        assertEquals(GedcomVersion.V70, f.version());
        // 7.0 only escapes a leading at sign.
        assertEquals("@x and a@@b", f.records().getFirst().valueOf("NOTE"));
    }

    @Test
    void notGedcom() {
        assertThrows(IOException.class, () -> read("hello world\n"));
        assertThrows(IOException.class, () -> read(""));
    }
}
