package eu.amadeos.apps.familytree.gedcom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A read GEDCOM file: the header, the records in file order (without {@code HEAD} and {@code TRLR}), the version
 * and character set the file declared, and what the reader had to repair.
 *
 * @param warnings readable notes about lines that were repaired or skipped, at most {@link #MAX_WARNINGS}
 */
public record GedcomFile(GedcomNode header, List<GedcomNode> records, GedcomVersion version, String charset,
                         List<String> warnings) {
    public static final int MAX_WARNINGS = 200;

    public GedcomFile {
        Objects.requireNonNull(header, "header");
        records = new ArrayList<>(records);
        Objects.requireNonNull(version, "version");
        warnings = List.copyOf(warnings);
    }

    /** The program that wrote the file ({@code HEAD.SOUR.NAME}, else {@code HEAD.SOUR}), or "". */
    public String sourceProgram() {
        String name = header.valueOf("SOUR.NAME");
        return name.isEmpty() ? header.valueOf("SOUR") : name;
    }
}
