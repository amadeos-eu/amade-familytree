package eu.amadeos.apps.familytree.gedcom;

import eu.amadeos.apps.familytree.Samples;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GedcomConverterTest {
    private static List<GedcomNode> records(String text) throws IOException {
        return GedcomReader.read(("0 HEAD\n" + text + "0 TRLR\n").getBytes(StandardCharsets.UTF_8)).records();
    }

    @Test
    void to70() throws IOException {
        List<GedcomNode> in = records("0 @N1@ NOTE shared\n0 @I1@ INDI\n1 NAME Anna /Keller/\n2 TYPE birth\n"
                + "1 NAME Anna /Brenner/\n2 TYPE Klostername\n1 NOTE @N1@\n1 RESN privacy\n1 BIRT\n"
                + "2 DATE INT 1850 (around Easter)\n1 DEAT\n2 DATE (in the war)\n0 @M1@ OBJE\n1 FILE a.JPG\n2 FORM JPG\n");
        List<GedcomNode> out = GedcomConverter.records(in, GedcomVersion.V70);
        assertEquals("SNOTE", out.get(0).tag());
        GedcomNode i = out.get(1);
        assertEquals("BIRTH", i.children("NAME").get(0).valueOf("TYPE"));
        assertEquals("OTHER", i.children("NAME").get(1).valueOf("TYPE"));
        assertEquals("Klostername", i.children("NAME").get(1).valueOf("TYPE.PHRASE"));
        assertEquals("@N1@", i.valueOf("SNOTE"));
        assertEquals("PRIVACY", i.valueOf("RESN"));
        assertEquals("1850", i.valueOf("BIRT.DATE"));
        assertEquals("around Easter", i.valueOf("BIRT.DATE.PHRASE"));
        assertEquals("", i.valueOf("DEAT.DATE"));
        assertEquals("in the war", i.valueOf("DEAT.DATE.PHRASE"));
        assertEquals("image/jpeg", out.get(2).valueOf("FILE.FORM"));
        // The input is unchanged.
        assertEquals("NOTE", in.get(0).tag());
    }

    @Test
    void backTo551() throws IOException {
        List<GedcomNode> in = records("0 @N1@ SNOTE shared\n0 @I1@ INDI\n1 NAME Anna /Keller/\n2 TYPE OTHER\n"
                + "3 PHRASE Klostername\n1 SNOTE @N1@\n1 RESN PRIVACY\n1 BIRT\n2 DATE 1850\n3 PHRASE around Easter\n"
                + "0 @M1@ OBJE\n1 FILE a.jpg\n2 FORM image/jpeg\n");
        List<GedcomNode> out = GedcomConverter.records(in, GedcomVersion.V551);
        assertEquals("NOTE", out.get(0).tag());
        GedcomNode i = out.get(1);
        assertEquals("Klostername", i.valueOf("NAME.TYPE"));
        assertEquals("@N1@", i.valueOf("NOTE"));
        assertEquals("privacy", i.valueOf("RESN"));
        assertEquals("INT 1850 (around Easter)", i.valueOf("BIRT.DATE"));
        assertEquals("jpg", out.get(2).valueOf("FILE.FORM"));
    }

    @Test
    void header() throws IOException {
        GedcomFile f = GedcomReader.read(Samples.bytes(Samples.BRENNER));
        GedcomNode h7 = GedcomConverter.header(f.header(), GedcomVersion.V70, "1.0.0", null, null);
        assertEquals("7.0", h7.valueOf("GEDC.VERS"));
        assertFalse(h7.has("CHAR"));
        assertEquals(GedcomConverter.PRODUCT, h7.valueOf("SOUR"));
        assertEquals("1.0.0", h7.valueOf("SOUR.VERS"));
        assertEquals("German", h7.valueOf("LANG"));
        assertTrue(h7.has("_PROJECT_GUID"));
        GedcomNode h5 = GedcomConverter.header(f.header(), GedcomVersion.V551, "1.0.0", "U1", "x.ged");
        assertEquals("UTF-8", h5.valueOf("CHAR"));
        assertEquals("LINEAGE-LINKED", h5.valueOf("GEDC.FORM"));
        assertEquals("@U1@", h5.valueOf("SUBM"));
        assertEquals("x.ged", h5.valueOf("FILE"));
    }

    @Test
    void mediaTypes() {
        assertEquals("image/jpeg", GedcomConverter.mime("JPEG"));
        assertEquals("image/png", GedcomConverter.mime(".png"));
        assertEquals("image/x-foo", GedcomConverter.mime("image/x-foo"));
        assertEquals("jpg", GedcomConverter.form551("image/jpeg"));
        assertEquals("pdf", GedcomConverter.form551("application/pdf"));
        assertEquals("x-foo", GedcomConverter.form551("image/x-foo"));
    }
}
