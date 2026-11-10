package eu.amadeos.apps.familytree.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Finds people by what one types: every word must match. A word matches a name when a name starts with it
 * (without accents: "muller" finds "Müller") or sounds like it (Kölner Phonetik: "Meyer" finds "Maier" and
 * "Mayr"), a place when the place contains it, and a year when a fact of the person happened in it.
 */
public final class Search {
    private Search() {
    }

    /** Lower case without accents; ß becomes ss. */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT).replace("ß", "ss"), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    public static List<String> words(String text) {
        List<String> list = new ArrayList<>();
        for (String w : fold(text).split("[^\\p{L}\\p{N}]+")) if (!w.isEmpty()) list.add(w);
        return list;
    }

    public static boolean matches(Person p, String query) {
        List<String> q = words(query);
        if (q.isEmpty()) return true;
        List<String> names = new ArrayList<>();
        for (PersonName n : p.names()) {
            names.addAll(words(n.given()));
            names.addAll(words(n.surname()));
            names.addAll(words(n.called()));
            names.addAll(words(n.marriedSurname()));
        }
        List<String> codes = new ArrayList<>();
        for (String n : names) codes.add(cologne(n));
        List<Fact> facts = p.facts();
        for (String w : q) {
            if (w.matches("\\d{3,4}")) {
                int year = Integer.parseInt(w);
                boolean found = false;
                for (Fact f : facts) {
                    OptionalInt a = f.date().year();
                    OptionalInt b = f.date().lastYear();
                    if (a.isPresent() && a.getAsInt() <= year && year <= b.orElse(a.getAsInt())) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
                continue;
            }
            boolean found = names.stream().anyMatch(n -> n.startsWith(w));
            if (!found && w.length() >= 3) {
                String code = cologne(w);
                found = !code.isEmpty() && codes.contains(code);
            }
            if (!found) {
                for (Fact f : facts) {
                    if (fold(f.place()).contains(w)) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found && p.id() != null && p.id().equalsIgnoreCase(w)) found = true;
            if (!found) return false;
        }
        return true;
    }

    /**
     * The Kölner Phonetik code of a word: names that sound alike in German get the same digits
     * ("Meyer", "Maier", "Mayr" → 67; "Schmidt", "Schmitt" → 862).
     */
    // --8<-- [start:cologne]
    public static String cologne(String word) {
        String s = fold(word).toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        if (s.isEmpty()) return "";
        StringBuilder raw = new StringBuilder();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            char prev = i > 0 ? s.charAt(i - 1) : 0;
            char next = i + 1 < n ? s.charAt(i + 1) : 0;
            switch (c) {
                case 'A', 'E', 'I', 'J', 'O', 'U', 'Y' -> raw.append('0');
                case 'H' -> {
                }
                case 'B' -> raw.append('1');
                case 'P' -> raw.append(next == 'H' ? '3' : '1');
                case 'D', 'T' -> raw.append(next == 'C' || next == 'S' || next == 'Z' ? '8' : '2');
                case 'F', 'V', 'W' -> raw.append('3');
                case 'G', 'K', 'Q' -> raw.append('4');
                case 'C' -> {
                    boolean four;
                    if (i == 0) four = "AHKLOQRUX".indexOf(next) >= 0 && next != 0;
                    else four = "AHKOQUX".indexOf(next) >= 0 && next != 0 && prev != 'S' && prev != 'Z';
                    raw.append(four ? '4' : '8');
                }
                case 'X' -> raw.append(prev == 'C' || prev == 'K' || prev == 'Q' ? "8" : "48");
                case 'L' -> raw.append('5');
                case 'M', 'N' -> raw.append('6');
                case 'R' -> raw.append('7');
                case 'S', 'Z' -> raw.append('8');
                default -> {
                }
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (out.length() > 0 && out.charAt(out.length() - 1) == c) continue;
            if (i > 0 && raw.charAt(i - 1) == c) continue;
            out.append(c);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < out.length(); i++) if (out.charAt(i) != '0' || i == 0) result.append(out.charAt(i));
        return result.toString();
    }
    // --8<-- [end:cologne]
}
