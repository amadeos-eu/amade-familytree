package eu.amadeos.apps.familytree.domain;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A genealogical date as GEDCOM writes it: exact ({@code 14 MAR 1928}), partial ({@code MAR 1928},
 * {@code 1928}), approximate ({@code ABT}, {@code CAL}, {@code EST}), a range ({@code BEF}, {@code AFT},
 * {@code BET … AND …}), a period ({@code FROM … TO …}), in the Gregorian, Julian, Hebrew or French
 * republican calendar, with dual years ({@code 1750/51}) and before Christ ({@code BCE}). A value that is none
 * of these is kept as {@link Kind#TEXT}; so is a phrase in brackets.
 *
 * <p>Both spellings of the calendar are understood: {@code @#DJULIAN@ 1700} (5.5.1) and {@code JULIAN 1700}
 * (7.0). {@link #gedcom()} writes the 7.0 form.
 */
public record GenDate(Kind kind, Part first, Part second, String phrase, String original) {
    public enum Kind {
        /** No date. */
        NONE,
        EXACT,
        ABOUT,
        CALCULATED,
        ESTIMATED,
        BEFORE,
        AFTER,
        BETWEEN,
        FROM,
        TO,
        FROM_TO,
        /** Only words, no date the program could read. */
        TEXT
    }

    public enum Calendar {
        GREGORIAN, JULIAN, HEBREW, FRENCH_R;

        static Calendar of(String word) {
            String w = word.toUpperCase(Locale.ROOT).replace("@#D", "").replace("@", "").replace(' ', '_').strip();
            return switch (w) {
                case "JULIAN" -> JULIAN;
                case "HEBREW" -> HEBREW;
                case "FRENCH_R", "FRENCH" -> FRENCH_R;
                case "GREGORIAN" -> GREGORIAN;
                default -> null;
            };
        }
    }

    public static final List<String> MONTHS = List.of("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP",
            "OCT", "NOV", "DEC");
    public static final List<String> HEBREW_MONTHS = List.of("TSH", "CSH", "KSL", "TVT", "SHV", "ADR", "ADS", "NSN",
            "IYR", "SVN", "TMZ", "AAV", "ELL");
    public static final List<String> FRENCH_MONTHS = List.of("VEND", "BRUM", "FRIM", "NIVO", "PLUV", "VENT", "GERM",
            "FLOR", "PRAI", "MESS", "THER", "FRUC", "COMP");

    /**
     * One date. Day and month are 0 when unknown.
     *
     * @param dualYear the second year of a dual date such as 1750/51 (written as 51), or 0
     */
    public record Part(Calendar calendar, int day, int month, int year, int dualYear, boolean bce) {
        public Part {
            Objects.requireNonNull(calendar, "calendar");
        }

        public static Part of(int day, int month, int year) {
            return new Part(Calendar.GREGORIAN, day, month, year, 0, false);
        }

        /** The year counted as a number line (1 BCE is 0). */
        public int astronomicalYear() {
            return bce ? 1 - year : year;
        }

        /** Sortable: year, month, day. */
        int key() {
            return astronomicalYear() * 10000 + month * 100 + day;
        }

        String gedcom() {
            StringBuilder b = new StringBuilder();
            if (calendar != Calendar.GREGORIAN) b.append(calendar.name()).append(' ');
            if (day > 0) b.append(day).append(' ');
            if (month > 0) b.append(monthCode(calendar, month)).append(' ');
            b.append(year);
            if (dualYear > 0) b.append('/').append(String.format(Locale.ROOT, "%02d", dualYear % 100));
            if (bce) b.append(" BCE");
            return b.toString();
        }
    }

    public static final GenDate NONE = new GenDate(Kind.NONE, null, null, null, "");

    public GenDate {
        Objects.requireNonNull(kind, "kind");
        original = original == null ? "" : original;
        phrase = phrase == null || phrase.isBlank() ? null : phrase.strip();
    }

    public static GenDate exact(int day, int month, int year) {
        GenDate d = new GenDate(Kind.EXACT, Part.of(day, month, year), null, null, "");
        return new GenDate(Kind.EXACT, d.first, null, null, d.gedcom());
    }

    public static GenDate text(String text) {
        return new GenDate(Kind.TEXT, null, null, text, text);
    }

    public boolean isEmpty() {
        return kind == Kind.NONE;
    }

    /** True when the program understood the date (it can sort it and work out ages). */
    public boolean isValid() {
        return kind != Kind.NONE && kind != Kind.TEXT;
    }

    /** The year to show and sort by, from the first date (for "before 1900", 1900). */
    public OptionalInt year() {
        if (!isValid()) return OptionalInt.empty();
        Part p = first != null ? first : second;
        if (p.calendar() != Calendar.GREGORIAN && p.calendar() != Calendar.JULIAN) return OptionalInt.empty();
        return OptionalInt.of(p.astronomicalYear());
    }

    /** The last year the date can mean: the second part of a range, else the first. */
    public OptionalInt lastYear() {
        if (!isValid()) return OptionalInt.empty();
        Part p = second != null ? second : first;
        if (p.calendar() != Calendar.GREGORIAN && p.calendar() != Calendar.JULIAN) return year();
        return OptionalInt.of(p.astronomicalYear());
    }

    /** Sorts dates in time; dates without a readable value sort last. Before sorts a little earlier, after later. */
    public long sortKey() {
        if (!isValid()) return Long.MAX_VALUE;
        Part p = first != null ? first : second;
        if (p.calendar() == Calendar.HEBREW) return (long) (p.year() - 3760) * 10000 * 10;
        if (p.calendar() == Calendar.FRENCH_R) return (long) (p.year() + 1791) * 10000 * 10;
        long key = (long) p.key() * 10;
        if (kind == Kind.BEFORE || kind == Kind.TO) key -= 5;
        if (kind == Kind.AFTER) key += 5;
        return key;
    }

    /** The value in GEDCOM 7.0 form; a phrase belongs to {@code PHRASE}. */
    public String gedcom() {
        return switch (kind) {
            case NONE -> "";
            case TEXT -> "";
            case EXACT -> first.gedcom();
            case ABOUT -> "ABT " + first.gedcom();
            case CALCULATED -> "CAL " + first.gedcom();
            case ESTIMATED -> "EST " + first.gedcom();
            case BEFORE -> "BEF " + first.gedcom();
            case AFTER -> "AFT " + first.gedcom();
            case BETWEEN -> "BET " + first.gedcom() + " AND " + second.gedcom();
            case FROM -> "FROM " + first.gedcom();
            case TO -> "TO " + second.gedcom();
            case FROM_TO -> "FROM " + first.gedcom() + " TO " + second.gedcom();
        };
    }

    // ---- parsing ---------------------------------------------------------------------------------------------

    private static final Pattern PHRASE = Pattern.compile("^(.*?)\\s*\\((.*)\\)\\s*$");

    /** Reads a GEDCOM date value; never fails, an unreadable value becomes {@link Kind#TEXT}. */
    public static GenDate parse(String value) {
        return parse(value, null);
    }

    /** Reads a GEDCOM date value with the {@code PHRASE} below it (7.0), if any. */
    public static GenDate parse(String value, String phrase) {
        if (value == null || value.isBlank()) {
            return phrase == null || phrase.isBlank() ? NONE : text(phrase);
        }
        String v = value.strip();
        Matcher pm = PHRASE.matcher(v);
        if (pm.matches()) {
            v = pm.group(1).strip();
            phrase = pm.group(2);
            if (v.isEmpty()) return new GenDate(Kind.TEXT, null, null, phrase, value.strip());
        }
        String[] w = v.toUpperCase(Locale.ROOT).replace("@#DFRENCH R@", "@#DFRENCH_R@").split("\\s+");
        GenDate d = words(w, phrase, value.strip());
        return d != null ? d : new GenDate(Kind.TEXT, null, null, phrase != null ? phrase : value.strip(),
                value.strip());
    }

    private static GenDate words(String[] w, String phrase, String original) {
        if (w.length == 0) return null;
        String head = w[0];
        String[] rest = java.util.Arrays.copyOfRange(w, 1, w.length);
        Kind single = switch (head) {
            case "ABT" -> Kind.ABOUT;
            case "CAL" -> Kind.CALCULATED;
            case "EST" -> Kind.ESTIMATED;
            case "BEF" -> Kind.BEFORE;
            case "AFT" -> Kind.AFTER;
            case "INT" -> Kind.EXACT;
            default -> null;
        };
        if (single != null) {
            Part p = part(rest);
            return p == null ? null : new GenDate(single, p, null, phrase, original);
        }
        if (head.equals("BET")) {
            int and = indexOf(rest, "AND");
            if (and < 0) return null;
            Part a = part(java.util.Arrays.copyOfRange(rest, 0, and));
            Part b = part(java.util.Arrays.copyOfRange(rest, and + 1, rest.length));
            return a == null || b == null ? null : new GenDate(Kind.BETWEEN, a, b, phrase, original);
        }
        if (head.equals("FROM")) {
            int to = indexOf(rest, "TO");
            if (to < 0) {
                Part a = part(rest);
                return a == null ? null : new GenDate(Kind.FROM, a, null, phrase, original);
            }
            Part a = part(java.util.Arrays.copyOfRange(rest, 0, to));
            Part b = part(java.util.Arrays.copyOfRange(rest, to + 1, rest.length));
            return a == null || b == null ? null : new GenDate(Kind.FROM_TO, a, b, phrase, original);
        }
        if (head.equals("TO")) {
            Part b = part(rest);
            return b == null ? null : new GenDate(Kind.TO, null, b, phrase, original);
        }
        Part p = part(w);
        return p == null ? null : new GenDate(Kind.EXACT, p, null, phrase, original);
    }

    private static int indexOf(String[] w, String word) {
        for (int i = 0; i < w.length; i++) if (w[i].equals(word)) return i;
        return -1;
    }

    /** [calendar] [[day] month] year[/yy] [BCE|B.C.] */
    static Part part(String[] w) {
        int i = 0;
        Calendar cal = Calendar.GREGORIAN;
        if (w.length > 0) {
            Calendar c = w[0].startsWith("@#D") || w[0].matches("GREGORIAN|JULIAN|HEBREW|FRENCH_R") ? Calendar.of(w[0])
                    : null;
            if (c != null) {
                cal = c;
                i = 1;
            } else if (w[0].startsWith("@#D")) {
                return null;
            }
        }
        int n = w.length - i;
        boolean bce = false;
        if (n > 1 && (w[w.length - 1].equals("BCE") || w[w.length - 1].equals("B.C.") || w[w.length - 1].equals("BC"))) {
            bce = true;
            n--;
        }
        if (n < 1 || n > 3) return null;
        int[] yearAndDual = year(w[i + n - 1]);
        if (yearAndDual == null) return null;
        int month = 0, day = 0;
        if (n >= 2) {
            month = month(cal, w[i + n - 2]);
            if (month == 0) return null;
        }
        if (n == 3) {
            if (!w[i].matches("\\d{1,2}")) return null;
            day = Integer.parseInt(w[i]);
            if (day < 1 || day > 31) return null;
        }
        return new Part(cal, day, month, yearAndDual[0], yearAndDual[1], bce);
    }

    private static int[] year(String s) {
        Matcher m = Pattern.compile("(\\d{1,4})(?:/(\\d{1,2}))?").matcher(s);
        if (!m.matches()) return null;
        return new int[]{Integer.parseInt(m.group(1)), m.group(2) == null ? 0 : Integer.parseInt(m.group(2))};
    }

    private static int month(Calendar cal, String s) {
        List<String> names = switch (cal) {
            case HEBREW -> HEBREW_MONTHS;
            case FRENCH_R -> FRENCH_MONTHS;
            default -> MONTHS;
        };
        int i = names.indexOf(s);
        return i < 0 ? 0 : i + 1;
    }

    static String monthCode(Calendar cal, int month) {
        List<String> names = switch (cal) {
            case HEBREW -> HEBREW_MONTHS;
            case FRENCH_R -> FRENCH_MONTHS;
            default -> MONTHS;
        };
        return names.get(Math.max(0, Math.min(names.size() - 1, month - 1)));
    }
}
