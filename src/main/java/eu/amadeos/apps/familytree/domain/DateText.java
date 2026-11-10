package eu.amadeos.apps.familytree.domain;

import eu.amadeos.commons.i18n.Messages;

import java.text.DateFormatSymbols;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows {@link GenDate}s in the language of the program ("um 1850", "14. März 1928") and reads what people type
 * into a date field. Typing accepts the GEDCOM form ({@code ABT 1850}) and the words of all five languages of
 * amadeOS, whatever the language of the program: "um 1850", "about 1850", "vers 1850", "vor 1900",
 * "zwischen 1850 und 1855", "14.3.1928", "1928-03-14", "14. März 1928", "March 1928", "1750/51",
 * "300 v. Chr.", "1700 julianisch".
 */
public final class DateText {
    private final Messages msg;
    private final Locale locale;
    private final String[] monthNames;

    public DateText(Messages msg) {
        this.msg = msg;
        this.locale = msg.locale();
        DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        this.monthNames = msg.get("date.month-style").equals("long") ? symbols.getMonths() : symbols.getShortMonths();
    }

    // ---- showing ---------------------------------------------------------------------------------------------

    public String format(GenDate d) {
        if (d == null || d.isEmpty()) return "";
        String text = switch (d.kind()) {
            case NONE -> "";
            case TEXT -> d.phrase() != null ? d.phrase() : d.original();
            case EXACT -> part(d.first());
            case ABOUT -> msg.format("date.about", part(d.first()));
            case CALCULATED -> msg.format("date.calculated", part(d.first()));
            case ESTIMATED -> msg.format("date.estimated", part(d.first()));
            case BEFORE -> msg.format("date.before", part(d.first()));
            case AFTER -> msg.format("date.after", part(d.first()));
            case BETWEEN -> msg.format("date.between", part(d.first()), part(d.second()));
            case FROM -> msg.format("date.from", part(d.first()));
            case TO -> msg.format("date.to", part(d.second()));
            case FROM_TO -> msg.format("date.from-to", part(d.first()), part(d.second()));
        };
        if (d.kind() != GenDate.Kind.TEXT && d.phrase() != null) text += " (" + d.phrase() + ")";
        return text;
    }

    /** Only the year, for boxes in the charts: "1928", "~1850", "<1900", "1850–55"; "" when unknown. */
    public static String year(GenDate d) {
        if (d == null || !d.isValid()) return "";
        GenDate.Part p = d.first() != null ? d.first() : d.second();
        String y = p.year() + (p.bce() ? " BC" : "");
        return switch (d.kind()) {
            case ABOUT, CALCULATED, ESTIMATED -> "~" + y;
            case BEFORE, TO -> "<" + y;
            case AFTER, FROM -> ">" + y;
            // "1910/12": between two years, short enough for a lifespan ("1840–1910/12").
            case BETWEEN, FROM_TO -> {
                int second = d.second().year();
                if (second == p.year()) yield y;
                yield second / 100 == p.year() / 100 && second > p.year()
                        ? y + "/" + String.format("%02d", second % 100) : y + "/" + second;
            }
            default -> y;
        };
    }

    String part(GenDate.Part p) {
        String month = "";
        if (p.month() > 0) {
            month = switch (p.calendar()) {
                case GREGORIAN, JULIAN -> monthNames[p.month() - 1];
                default -> GenDate.monthCode(p.calendar(), p.month());
            };
        }
        String year = Integer.toString(p.year());
        if (p.dualYear() > 0) year += "/" + String.format(Locale.ROOT, "%02d", p.dualYear() % 100);
        String text;
        if (p.day() > 0 && p.month() > 0) text = msg.format("date.full", Integer.toString(p.day()), month, year);
        else if (p.month() > 0) text = msg.format("date.month-year", month, year);
        else text = year;
        if (p.bce()) text = msg.format("date.bce", text);
        return switch (p.calendar()) {
            case JULIAN -> msg.format("date.julian", text);
            case HEBREW -> msg.format("date.hebrew", text);
            case FRENCH_R -> msg.format("date.french", text);
            default -> text;
        };
    }

    // ---- reading what people type ----------------------------------------------------------------------------

    private static final Map<String, GenDate.Kind> PREFIXES = new HashMap<>();
    private static final List<String> BETWEEN = List.of("between", "bet", "zwischen", "entre", "tra", "fra");
    private static final List<String> AND = List.of("and", "und", "et", "y", "e", "-", "–");
    private static final List<String> FROM = List.of("from", "von", "de", "du", "desde", "dal", "da", "dall'");
    private static final List<String> TO = List.of("to", "bis", "à", "a", "au", "hasta", "al", "fino al");
    private static final List<String> BCE = List.of("bce", "bc", "b.c.", "v. chr.", "v.chr.", "vor christus",
            "av. j.-c.", "av. j-c", "a.c.", "a. c.", "a.c", "ac");
    private static final List<String> JULIAN = List.of("julian", "julianisch", "julien", "juliano", "giuliano",
            "(jul)", "jul.", "(julian)", "(julianisch)");
    private static final List<String> FILLERS = List.of("de", "del", "of", "the", "le", "il");
    private static final Map<String, Integer> MONTH_WORDS = new HashMap<>();

    static {
        add(GenDate.Kind.ABOUT, "about", "abt", "abt.", "around", "approx.", "approx", "circa", "ca.", "ca", "c.",
                "um", "etwa", "ungefähr", "vers", "environ", "env.", "hacia", "aprox.", "aproximadamente",
                "alrededor de", "verso il", "verso", "intorno al", "~");
        add(GenDate.Kind.ESTIMATED, "est", "est.", "estimated", "geschätzt", "estimé", "estimée", "estimado",
                "estimada", "stimato", "stimata");
        add(GenDate.Kind.CALCULATED, "cal", "cal.", "calculated", "errechnet", "berechnet", "calculé", "calculée",
                "calculado", "calculada", "calcolato", "calcolata");
        add(GenDate.Kind.BEFORE, "before", "bef", "bef.", "vor", "avant", "av.", "antes de", "antes del", "antes",
                "prima del", "prima di", "prima", "<");
        add(GenDate.Kind.AFTER, "after", "aft", "aft.", "nach", "après", "ap.", "después de", "después del",
                "despues de", "dopo il", "dopo", ">");
        add(GenDate.Kind.FROM, "since", "seit", "ab", "depuis", "dès", "dal");
        add(GenDate.Kind.TO, "until", "till", "bis", "jusqu'en", "jusqu'à", "hasta", "fino al");
        String[] gedcom = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};
        for (int i = 0; i < 12; i++) MONTH_WORDS.put(gedcom[i], i + 1);
        for (String tag : List.of("en", "de", "fr", "es", "it")) {
            DateFormatSymbols s = DateFormatSymbols.getInstance(Locale.forLanguageTag(tag));
            for (int i = 0; i < 12; i++) {
                for (String name : new String[]{s.getMonths()[i], s.getShortMonths()[i]}) {
                    if (name == null || name.isBlank()) continue;
                    String n = name.toLowerCase(Locale.ROOT).replace(".", "").strip();
                    MONTH_WORDS.putIfAbsent(n, i + 1);
                    MONTH_WORDS.putIfAbsent(plain(n), i + 1);
                }
            }
        }
        MONTH_WORDS.put("mrz", 3);
        MONTH_WORDS.put("maerz", 3);
        MONTH_WORDS.put("sept", 9);
    }

    private static void add(GenDate.Kind kind, String... words) {
        for (String w : words) PREFIXES.put(w, kind);
    }

    private static String plain(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Reads a typed date. A text that is not a date is kept as {@link GenDate.Kind#TEXT}, so nothing is lost;
     * {@link GenDate#isValid()} tells the editor whether to warn.
     */
    // --8<-- [start:parse]
    public static GenDate parse(String input) {
        if (input == null || input.isBlank()) return GenDate.NONE;
        String in = input.strip();
        GenDate g = GenDate.parse(in);
        if (g.isValid()) return GenDate.parse(g.gedcom(), g.phrase());
        String phrase = null;
        Matcher pm = Pattern.compile("^(.*?)\\s*\\((.*)\\)\\s*$").matcher(in);
        if (pm.matches() && !JULIAN.contains("(" + pm.group(2).toLowerCase(Locale.ROOT) + ")")) {
            phrase = pm.group(2);
            in = pm.group(1);
        }
        String s = " " + in.toLowerCase(Locale.ROOT).replace(',', ' ').replaceAll("\\s+", " ").strip() + " ";
        GenDate result = human(s.strip(), phrase, input.strip());
        return result != null ? result : GenDate.text(input.strip());
    }
    // --8<-- [end:parse]

    private static GenDate human(String s, String phrase, String original) {
        for (String b : BETWEEN) {
            if (s.startsWith(b + " ")) {
                String rest = s.substring(b.length() + 1);
                for (String a : AND) {
                    int i = rest.indexOf(" " + a + " ");
                    if (i > 0) {
                        GenDate.Part p1 = humanPart(rest.substring(0, i));
                        GenDate.Part p2 = humanPart(rest.substring(i + a.length() + 2));
                        if (p1 != null && p2 != null) return make(GenDate.Kind.BETWEEN, p1, p2, phrase);
                    }
                }
            }
        }
        for (String f : FROM) {
            if (s.startsWith(f + " ")) {
                String rest = s.substring(f.length() + 1);
                for (String t : TO) {
                    int i = rest.indexOf(" " + t + " ");
                    if (i > 0) {
                        GenDate.Part p1 = humanPart(rest.substring(0, i));
                        GenDate.Part p2 = humanPart(rest.substring(i + t.length() + 2));
                        if (p1 != null && p2 != null) return make(GenDate.Kind.FROM_TO, p1, p2, phrase);
                    }
                }
                if (!f.equals("de") && !f.equals("da")) {
                    GenDate.Part p = humanPart(rest);
                    if (p != null) return make(GenDate.Kind.FROM, p, null, phrase);
                }
            }
        }
        // A range written with a dash: 1850-1855 (but not 1928-03-14).
        Matcher range = Pattern.compile("^(\\d{3,4})\\s*[-–]\\s*(\\d{2,4})$").matcher(s);
        if (range.matches()) {
            int a = Integer.parseInt(range.group(1));
            int b = Integer.parseInt(range.group(2));
            if (range.group(2).length() == 2) b = a / 100 * 100 + b;
            if (b > a) return make(GenDate.Kind.FROM_TO, GenDate.Part.of(0, 0, a), GenDate.Part.of(0, 0, b), phrase);
        }
        List<String> keys = new ArrayList<>(PREFIXES.keySet());
        keys.sort((a, b) -> b.length() - a.length());
        for (String k : keys) {
            if (s.startsWith(k + " ") || (k.length() == 1 && s.startsWith(k) && s.length() > 1)) {
                String rest = s.substring(k.length()).strip();
                GenDate.Part p = humanPart(rest);
                if (p == null) continue;
                GenDate.Kind kind = PREFIXES.get(k);
                return kind == GenDate.Kind.TO ? make(kind, null, p, phrase) : make(kind, p, null, phrase);
            }
        }
        GenDate.Part p = humanPart(s);
        return p == null ? null : make(GenDate.Kind.EXACT, p, null, phrase);
    }

    private static GenDate make(GenDate.Kind kind, GenDate.Part a, GenDate.Part b, String phrase) {
        GenDate d = new GenDate(kind, a, b, phrase, "");
        return new GenDate(kind, a, b, phrase, d.gedcom());
    }

    /** One date: numbers with dots, slashes or dashes, or day, month name and year in any of the languages. */
    static GenDate.Part humanPart(String text) {
        String s = text.strip();
        GenDate.Calendar cal = GenDate.Calendar.GREGORIAN;
        for (String j : JULIAN) {
            if (s.endsWith(" " + j) || s.endsWith(j) && j.startsWith("(")) {
                cal = GenDate.Calendar.JULIAN;
                s = s.substring(0, s.length() - j.length()).strip();
                break;
            }
        }
        boolean bce = false;
        for (String b : BCE) {
            if (s.endsWith(" " + b)) {
                bce = true;
                s = s.substring(0, s.length() - b.length()).strip();
                break;
            }
        }
        Matcher m = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})[./](\\d{3,4})$").matcher(s);
        if (m.matches()) return checked(cal, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), 0, bce);
        m = Pattern.compile("^(\\d{3,4})-(\\d{1,2})(?:-(\\d{1,2}))?$").matcher(s);
        if (m.matches()) return checked(cal, m.group(3) == null ? 0 : Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)), 0, bce);
        m = Pattern.compile("^(\\d{1,2})[./](\\d{3,4})$").matcher(s);
        if (m.matches()) return checked(cal, 0, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), 0, bce);

        List<String> words = new ArrayList<>();
        for (String w : s.split("[\\s]+")) {
            String t = w.endsWith(".") && !w.matches("\\d+\\.?") ? w.substring(0, w.length() - 1) : w;
            if (t.isEmpty() || FILLERS.contains(t)) continue;
            words.add(t);
        }
        int day = 0, month = 0, year = -1, dual = 0;
        for (String w : words) {
            Matcher y = Pattern.compile("^(\\d{1,4})(?:/(\\d{1,2}))?$").matcher(w.replaceAll("\\.$", ""));
            Integer mo = MONTH_WORDS.get(w);
            if (mo == null) mo = MONTH_WORDS.get(plain(w));
            if (mo != null && month == 0) {
                month = mo;
            } else if (y.matches()) {
                int v = Integer.parseInt(y.group(1));
                boolean dotted = w.endsWith(".");
                if (year < 0 && month > 0 && !dotted) {
                    year = v;
                    if (y.group(2) != null) dual = Integer.parseInt(y.group(2));
                } else if (day == 0 && month == 0 && (dotted || v <= 31) && year < 0 && words.size() > 1) {
                    day = v;
                } else if (year < 0) {
                    year = v;
                    if (y.group(2) != null) dual = Integer.parseInt(y.group(2));
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        if (year < 0) return null;
        if (day > 0 && month == 0) return null;
        return checked(cal, day, month, year, dual, bce);
    }

    private static GenDate.Part checked(GenDate.Calendar cal, int day, int month, int year, int dual, boolean bce) {
        if (month < 0 || month > 12 || day < 0 || day > 31 || year <= 0 || year > 9999) return null;
        if (day > 0 && month == 0) return null;
        if (day > 0) {
            int max = switch (month) {
                case 2 -> 29;
                case 4, 6, 9, 11 -> 30;
                default -> 31;
            };
            if (day > max) return null;
        }
        return new GenDate.Part(cal, day, month, year, dual, bce);
    }
}
