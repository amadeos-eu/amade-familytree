package eu.amadeos.apps.familytree.repository;

import com.google.inject.Inject;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * What the program remembers, in {@code ~/.config/amadeos/familytree/familytree.properties}: the folder of the
 * trees, the tree open last, the home person of each tree, the view and the number of generations.
 */
public class FamilyTreePreferences {
    public static final String FILE = "familytree.properties";

    private final Path file;
    private final Properties properties;

    @Inject
    public FamilyTreePreferences(AppDirectories directories) {
        this(directories.configFile(FILE));
    }

    public FamilyTreePreferences(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.properties = PropertiesFile.load(file);
    }

    public Optional<Path> folder() {
        return Optional.ofNullable(properties.getProperty("folder")).filter(s -> !s.isBlank()).map(Path::of);
    }

    public void setFolder(Path folder) {
        set("folder", folder.toString());
    }

    public Optional<String> lastTree() {
        return Optional.ofNullable(properties.getProperty("tree.last")).filter(s -> !s.isBlank());
    }

    public void setLastTree(String name) {
        set("tree.last", name == null ? "" : name);
    }

    public Optional<String> homePerson(String tree) {
        return Optional.ofNullable(properties.getProperty("home." + key(tree))).filter(s -> !s.isBlank());
    }

    public void setHomePerson(String tree, String id) {
        set("home." + key(tree), id == null ? "" : id);
    }

    /** The view the window shows: 0 hourglass ... 5 timeline. */
    public int view() {
        return number("view", 0, 0, 5);
    }

    public void setView(int view) {
        set("view", Integer.toString(view));
    }

    public int generations(int view, int fallback) {
        return number("generations." + view, fallback, 1, 8);
    }

    public void setGenerations(int view, int generations) {
        set("generations." + view, Integer.toString(generations));
    }

    private int number(String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(properties.getProperty(key, "" + fallback).strip())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String key(String tree) {
        return tree.replaceAll("[^\\p{L}\\p{N}_-]", "_");
    }

    private void set(String key, String value) {
        properties.setProperty(key, value);
        PropertiesFile.store(file, properties, "amade-familytree");
    }
}
