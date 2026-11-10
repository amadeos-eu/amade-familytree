package eu.amadeos.apps.familytree.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The events and attributes GEDCOM knows for people and families. Events happen at a time and place
 * (birth, marriage); attributes describe a person and carry their text as value (occupation, religion).
 */
public enum FactType {
    // People: events
    BIRT(Scope.PERSON, false), CHR(Scope.PERSON, false), BAPM(Scope.PERSON, false), CONF(Scope.PERSON, false),
    FCOM(Scope.PERSON, false), BARM(Scope.PERSON, false), BASM(Scope.PERSON, false), ADOP(Scope.PERSON, false),
    GRAD(Scope.PERSON, false), RETI(Scope.PERSON, false), EMIG(Scope.PERSON, false), IMMI(Scope.PERSON, false),
    NATU(Scope.PERSON, false), CENS(Scope.BOTH, false), PROB(Scope.PERSON, false), WILL(Scope.PERSON, false),
    DEAT(Scope.PERSON, false), BURI(Scope.PERSON, false), CREM(Scope.PERSON, false),
    // People: attributes
    OCCU(Scope.PERSON, true), RESI(Scope.BOTH, true), EDUC(Scope.PERSON, true), RELI(Scope.PERSON, true),
    NATI(Scope.PERSON, true), TITL(Scope.PERSON, true), PROP(Scope.PERSON, true), DSCR(Scope.PERSON, true),
    CAST(Scope.PERSON, true), NCHI(Scope.BOTH, true), NMR(Scope.PERSON, true), IDNO(Scope.PERSON, true),
    // Families
    ENGA(Scope.FAMILY, false), MARB(Scope.FAMILY, false), MARC(Scope.FAMILY, false), MARL(Scope.FAMILY, false),
    MARS(Scope.FAMILY, false), MARR(Scope.FAMILY, false), DIVF(Scope.FAMILY, false), DIV(Scope.FAMILY, false),
    ANUL(Scope.FAMILY, false),
    // Both: anything else, with its own TYPE
    EVEN(Scope.BOTH, false), FACT(Scope.BOTH, true);

    public enum Scope { PERSON, FAMILY, BOTH }

    private final Scope scope;
    private final boolean attribute;

    FactType(Scope scope, boolean attribute) {
        this.scope = scope;
        this.attribute = attribute;
    }

    public String tag() {
        return name();
    }

    /** Attributes keep their text as the value of the line ({@code 1 OCCU Toolmaker}). */
    public boolean isAttribute() {
        return attribute;
    }

    public boolean forPeople() {
        return scope != Scope.FAMILY;
    }

    public boolean forFamilies() {
        return scope != Scope.PERSON;
    }

    public String textKey() {
        return "fact." + name().toLowerCase(Locale.ROOT);
    }

    public static Optional<FactType> of(String tag) {
        if (tag == null) return Optional.empty();
        String t = tag.toUpperCase(Locale.ROOT);
        if (t.equals("BAPT")) t = "BAPM";
        String u = t;
        return Arrays.stream(values()).filter(f -> f.name().equals(u)).findFirst();
    }

    /** The types the editor offers for people, most used first. */
    public static List<FactType> forPeopleInOrder() {
        return Arrays.stream(values()).filter(FactType::forPeople).toList();
    }

    public static List<FactType> forFamiliesInOrder() {
        return Arrays.stream(values()).filter(FactType::forFamilies).toList();
    }
}
