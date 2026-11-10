package eu.amadeos.apps.familytree.service;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A family tree on disk: a folder named after the tree with {@code <name>.ged} (GEDCOM 7.0), a {@code media}
 * folder for photos and documents and a {@code backups} folder.
 */
public record StoredTree(String name, Path folder) {
    public static final String MEDIA = "media";
    public static final String BACKUPS = "backups";

    public StoredTree {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(folder, "folder");
    }

    public Path file() {
        return folder.resolve(name + ".ged");
    }

    public Path media() {
        return folder.resolve(MEDIA);
    }

    public Path backups() {
        return folder.resolve(BACKUPS);
    }

    @Override
    public String toString() {
        return name;
    }
}
