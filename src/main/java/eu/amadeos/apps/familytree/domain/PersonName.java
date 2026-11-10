package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.gedcom.GedcomNode;

/**
 * A {@code NAME}: "Ernst Wilhelm /Brenner/" with given names, surname between slashes and a suffix after it.
 * The parts written below the name ({@code GIVN}, {@code SURN}, {@code NICK}, ...) fill in what the value
 * leaves out.
 */
public record PersonName(GedcomNode node) {
    public String given() {
        String v = node.text();
        int slash = v.indexOf('/');
        String g = slash < 0 ? v : v.substring(0, slash);
        g = g.strip();
        return g.isEmpty() ? node.valueOf("GIVN").strip() : g;
    }

    public String surname() {
        String v = node.text();
        int a = v.indexOf('/');
        int b = a < 0 ? -1 : v.indexOf('/', a + 1);
        String s = a >= 0 && b > a ? v.substring(a + 1, b).strip() : "";
        if (s.isEmpty()) {
            s = node.valueOf("SURN").strip();
            String prefix = node.valueOf("SPFX").strip();
            if (!prefix.isEmpty() && !s.isEmpty()) s = prefix + " " + s;
        }
        return s;
    }

    public String suffix() {
        String v = node.text();
        int a = v.indexOf('/');
        int b = a < 0 ? -1 : v.indexOf('/', a + 1);
        String s = b > a && a >= 0 ? v.substring(b + 1).strip() : "";
        return s.isEmpty() ? node.valueOf("NSFX").strip() : s;
    }

    /** {@code NICK}: the name a person was called by. */
    public String called() {
        return node.valueOf("NICK").strip();
    }

    /** {@code TYPE} in upper case: BIRTH, MARRIED, AKA, ... or "". */
    public String type() {
        return node.valueOf("TYPE").strip().toUpperCase(java.util.Locale.ROOT);
    }

    /** A married name written as {@code _MARNM} below the name (several programs do this), or "". */
    public String marriedSurname() {
        return node.valueOf("_MARNM").strip();
    }

    /** "Given /Surname/ Suffix", the GEDCOM value. */
    public static String value(String given, String surname, String suffix) {
        StringBuilder b = new StringBuilder();
        if (given != null && !given.isBlank()) b.append(given.strip()).append(' ');
        b.append('/').append(surname == null ? "" : surname.strip()).append('/');
        if (suffix != null && !suffix.isBlank()) b.append(' ').append(suffix.strip());
        return b.toString();
    }

    /** Writes given name and surname into the value and into {@code GIVN}/{@code SURN} when those exist. */
    public void set(String given, String surname) {
        node.setValue(value(given, surname, suffix()));
        node.child("GIVN").ifPresent(n -> n.setValue(given == null ? null : given.strip()));
        node.child("SURN").ifPresent(n -> {
            n.setValue(surname == null ? null : surname.strip());
            node.removeAll("SPFX");
        });
    }

    public void setCalled(String called) {
        node.set("NICK", called == null ? "" : called.strip());
    }
}
