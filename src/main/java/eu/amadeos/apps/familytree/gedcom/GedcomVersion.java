package eu.amadeos.apps.familytree.gedcom;

/** The GEDCOM versions the program reads and writes. 5.5 files are read as 5.5.1. */
public enum GedcomVersion {
    V551("5.5.1"),
    V70("7.0");

    private final String number;

    GedcomVersion(String number) {
        this.number = number;
    }

    /** "5.5.1" or "7.0", as written in {@code HEAD.GEDC.VERS}. */
    public String number() {
        return number;
    }

    /** The version a {@code VERS} value names; 7.x is 7.0, everything else (5.5, 5.5.1, 5.5.5) 5.5.1. */
    public static GedcomVersion of(String vers) {
        return vers != null && vers.strip().startsWith("7") ? V70 : V551;
    }
}
