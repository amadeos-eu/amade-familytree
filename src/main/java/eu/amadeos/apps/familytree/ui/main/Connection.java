package eu.amadeos.apps.familytree.ui.main;

import java.util.Locale;

/** How an existing person can be connected to the selected one. */
public enum Connection {
    PARENT, PARTNER, CHILD;

    public String textKey() {
        return "connect." + name().toLowerCase(Locale.ROOT);
    }
}
