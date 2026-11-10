package eu.amadeos.apps.familytree.ui.main;

import java.util.Locale;

/** The views of the main window and how many generations each shows at first. */
public enum ViewKind {
    HOURGLASS(2), ANCESTORS(4), DESCENDANTS(3), FAN(5), FAMILY(0), TIMELINE(0),
    /** The way from the person in the middle (at first the home person) to the selected one. */
    RELATIONSHIP(0);

    private final int generations;

    ViewKind(int generations) {
        this.generations = generations;
    }

    public int defaultGenerations() {
        return generations;
    }

    public boolean hasGenerations() {
        return generations > 0;
    }

    /** Drawn as boxes and lines in the chart panel (zoom, scroll, print as a chart). */
    public boolean isBoxChart() {
        return this == HOURGLASS || this == ANCESTORS || this == DESCENDANTS || this == RELATIONSHIP;
    }

    public int maxGenerations() {
        return this == FAN ? 8 : this == HOURGLASS ? 5 : 8;
    }

    public String textKey() {
        return "view." + name().toLowerCase(Locale.ROOT);
    }
}
