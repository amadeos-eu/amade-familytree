package eu.amadeos.apps.familytree.domain;

/** {@code SEX}: male, female, intersex ({@code X}, GEDCOM 7.0) or unknown. */
public enum Sex {
    MALE("M"), FEMALE("F"), INTERSEX("X"), UNKNOWN("U");

    private final String code;

    Sex(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public String textKey() {
        return "sex." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static Sex of(String value) {
        if (value == null) return UNKNOWN;
        return switch (value.strip().toUpperCase(java.util.Locale.ROOT)) {
            case "M", "MALE" -> MALE;
            case "F", "FEMALE" -> FEMALE;
            case "X" -> INTERSEX;
            default -> UNKNOWN;
        };
    }
}
