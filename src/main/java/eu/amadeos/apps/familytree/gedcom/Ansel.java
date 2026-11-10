package eu.amadeos.apps.familytree.gedcom;

import java.text.Normalizer;

/**
 * Decodes ANSEL (ANSI Z39.47 with the GEDCOM additions), the character set of many older GEDCOM files. ANSEL
 * writes accents as separate bytes <em>before</em> the letter; Unicode writes combining marks after it, so the
 * marks are moved behind their letter and the result is composed (NFC): {@code E8 61} becomes "ä".
 * The program never writes ANSEL; it always writes UTF-8.
 */
public final class Ansel {
    private Ansel() {
    }

    /** Spacing characters 0xA1 to 0xCF; 0 where ANSEL has nothing. */
    private static final char[] SPACING = new char[0x30];
    /** Combining marks 0xE0 to 0xFE; 0 where ANSEL has nothing. */
    private static final char[] COMBINING = new char[0x1F];

    static {
        String spacing = "ŁØĐÞÆŒʹ·♭®±ƠƯʼ\0"
                + "ʻłøđþæœʺı£ð\0ơư□■"
                + "°ℓ℗©♯¿¡ß€\0\0\0\0eoß";
        for (int i = 0; i < spacing.length() && i < SPACING.length; i++) SPACING[i] = spacing.charAt(i);
        String combining = "̉̀́̂̃̄̆̇̈̌̊︠︡̕"
                + "̧̨̣̤̥̳̲̦̜̮̋̐︢︣\0\0̓";
        for (int i = 0; i < combining.length() && i < COMBINING.length; i++) COMBINING[i] = combining.charAt(i);
    }

    /** Decodes the bytes; bytes ANSEL does not define become U+FFFD. */
    public static String decode(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length);
        StringBuilder marks = new StringBuilder();
        for (byte value : bytes) {
            int b = value & 0xFF;
            if (b >= 0xE0 && b <= 0xFE) {
                char mark = COMBINING[b - 0xE0];
                marks.append(mark == 0 ? '�' : mark);
                continue;
            }
            char c;
            if (b < 0x80) c = (char) b;
            else if (b >= 0xA1 && b <= 0xD0) c = SPACING[b - 0xA1] == 0 ? '�' : SPACING[b - 0xA1];
            else c = '�';
            if (c == '\n' || c == '\r') {
                // A mark before a line end has no letter; keep it on a space so it is not lost.
                if (!marks.isEmpty()) out.append(' ').append(marks);
                marks.setLength(0);
                out.append(c);
                continue;
            }
            out.append(c);
            if (!marks.isEmpty()) {
                out.append(marks);
                marks.setLength(0);
            }
        }
        if (!marks.isEmpty()) out.append(' ').append(marks);
        return Normalizer.normalize(out, Normalizer.Form.NFC);
    }
}
