package eu.amadeos.apps.familytree.domain;

import java.util.OptionalInt;

/**
 * Works out whether a person may still be alive, for privacy on export and print: not when the tree records a
 * death, burial or cremation; not when the person was born (or, without a birth date, is estimated to have been
 * born) more than {@value #MAX_AGE} years ago. A person without any date is counted as living, to be safe.
 */
public final class Living {
    public static final int MAX_AGE = 110;
    /** Years between the births of a parent and a child, for estimates. */
    static final int GENERATION = 25;

    private final int thisYear;

    public Living(int thisYear) {
        this.thisYear = thisYear;
    }

    public static Living now() {
        return new Living(java.time.Year.now().getValue());
    }

    public boolean isLiving(Person p) {
        if (p.isDeceased()) return false;
        OptionalInt born = estimatedBirthYear(p);
        return born.isEmpty() || born.getAsInt() > thisYear - MAX_AGE;
    }

    /** The birth year, else one worked out from the person's own facts, partners, children or parents. */
    public static OptionalInt estimatedBirthYear(Person p) {
        OptionalInt own = p.birthDate().year();
        if (own.isPresent()) return own;
        int best = Integer.MAX_VALUE;
        for (Fact f : p.facts()) {
            OptionalInt y = f.date().year();
            if (y.isPresent() && !f.tag().equals("BIRT")) best = Math.min(best, y.getAsInt() - 20);
        }
        for (Family fam : p.families()) {
            for (Fact f : fam.facts()) {
                OptionalInt y = f.date().year();
                if (y.isPresent()) best = Math.min(best, y.getAsInt() - GENERATION);
            }
            for (Person partner : fam.partners()) {
                if (partner.equals(p)) continue;
                OptionalInt y = partner.birthDate().year();
                if (y.isPresent()) best = Math.min(best, y.getAsInt());
            }
        }
        for (Person c : p.children()) {
            OptionalInt y = c.birthDate().year();
            if (y.isPresent()) best = Math.min(best, y.getAsInt() - GENERATION);
        }
        if (best != Integer.MAX_VALUE) return OptionalInt.of(best);
        for (Person parent : p.parents()) {
            OptionalInt y = parent.birthDate().year();
            if (y.isPresent()) best = Math.min(best, y.getAsInt() + GENERATION);
        }
        return best == Integer.MAX_VALUE ? OptionalInt.empty() : OptionalInt.of(best);
    }

    /** The age in whole years between two dates, if both are precise enough (at least the year). */
    public static OptionalInt age(GenDate born, GenDate at) {
        if (born == null || at == null || !born.isValid() || !at.isValid()) return OptionalInt.empty();
        GenDate.Part b = born.first() != null ? born.first() : born.second();
        GenDate.Part a = at.first() != null ? at.first() : at.second();
        int years = a.astronomicalYear() - b.astronomicalYear();
        if (a.month() > 0 && b.month() > 0) {
            if (a.month() < b.month() || a.month() == b.month() && a.day() > 0 && b.day() > 0 && a.day() < b.day()) {
                years--;
            }
        }
        return years < 0 || years > 130 ? OptionalInt.empty() : OptionalInt.of(years);
    }
}
