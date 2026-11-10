package eu.amadeos.apps.familytree.service;

import eu.amadeos.commons.desktop.DesktopLauncher;

import java.io.IOException;
import java.nio.file.Path;

/** Opens files, folders and the handbook through the desktop. */
public class DesktopOpener {
    /** The handbook section about the Family Tree program. */
    public static final String HANDBOOK = "https://docs.amadeos.eu/amadeos/45-calendar/#family-tree";

    private final DesktopLauncher launcher;

    public DesktopOpener(DesktopLauncher launcher) {
        this.launcher = launcher;
    }

    public void open(Path file) throws IOException {
        launcher.open(file);
    }

    public void browse(String uri) throws IOException {
        launcher.browse(uri);
    }

    public void handbook() throws IOException {
        launcher.browse(HANDBOOK);
    }
}
