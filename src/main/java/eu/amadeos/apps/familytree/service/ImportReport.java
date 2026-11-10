package eu.amadeos.apps.familytree.service;

import java.util.List;

/**
 * What a GEDCOM file holds and what the import had to do with it, for the import dialog.
 *
 * @param unreadableDates dates the program could not read (kept as text), at most {@link #SAMPLES}
 * @param extensionTags   lines with program-specific tags ({@code _UID}, ...), kept unchanged
 * @param webPhotos       media that are only links to a web site
 * @param missingFiles    media files that are neither in the file (GEDZIP) nor next to it
 * @param repairs         codes from {@link eu.amadeos.apps.familytree.domain.FamilyTree#normalize()}
 * @param warnings        lines the reader repaired or skipped
 */
public record ImportReport(String fileName, String version, String charset, String sourceProgram, long bytes,
                           int people, int families, int sources, int places, int facts, int notes, int media,
                           int webPhotos, int localPhotos, int missingFiles, int living, int extensionTags,
                           List<String> unreadableDates, int unreadableDateCount, List<String> repairs,
                           List<String> warnings) {
    public static final int SAMPLES = 5;

    public ImportReport {
        unreadableDates = List.copyOf(unreadableDates);
        repairs = List.copyOf(repairs);
        warnings = List.copyOf(warnings);
    }
}
