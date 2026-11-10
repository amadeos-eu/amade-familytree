package eu.amadeos.apps.familytree.ui.main;

import java.util.Locale;

/** Which people the list on the left shows. */
public enum ListFilter {
    ALL, SURNAME, ANCESTORS, DESCENDANTS, WITHOUT_PARENTS, LIVING, UNREADABLE_DATES;

    public String textKey() {
        return "filter." + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
