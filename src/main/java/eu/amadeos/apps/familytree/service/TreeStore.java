package eu.amadeos.apps.familytree.service;

import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.MediaRecord;
import eu.amadeos.apps.familytree.gedcom.GedcomConverter;
import eu.amadeos.apps.familytree.gedcom.GedcomNode;
import eu.amadeos.apps.familytree.gedcom.GedcomReader;
import eu.amadeos.apps.familytree.gedcom.GedcomVersion;
import eu.amadeos.apps.familytree.gedcom.GedcomWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Keeps family trees in a folder, by default "Family Trees" in the documents folder. Each tree is a GEDCOM 7.0
 * file that any genealogy program can read, written safely (to a {@code .part} file that then replaces the old
 * one). Before the first save after opening, the old file is copied to {@code backups}; the newest
 * {@value #BACKUPS_KEPT} backups are kept.
 */
public final class TreeStore {
    public static final int BACKUPS_KEPT = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private final Path root;
    private final String programVersion;
    private final Set<Path> backedUp = new HashSet<>();

    public TreeStore(Path root, String programVersion) {
        this.root = root;
        this.programVersion = programVersion;
    }

    public Path root() {
        return root;
    }

    /** The trees in the folder, by name. */
    public List<StoredTree> list() {
        List<StoredTree> trees = new ArrayList<>();
        if (!Files.isDirectory(root)) return trees;
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : dirs) {
                StoredTree t = new StoredTree(dir.getFileName().toString(), dir);
                if (Files.isRegularFile(t.file())) trees.add(t);
            }
        } catch (IOException e) {
            return trees;
        }
        trees.sort(Comparator.comparing(t -> t.name().toLowerCase(Locale.ROOT)));
        return trees;
    }

    public Optional<StoredTree> find(String name) {
        return list().stream().filter(t -> t.name().equals(name)).findFirst();
    }

    /** A name that is not in use yet: "Brenner", "Brenner 2", ... */
    public String freeName(String wanted) {
        String base = safeName(wanted);
        String name = base;
        for (int i = 2; Files.exists(root.resolve(name)); i++) name = base + " " + i;
        return name;
    }

    /** A folder name without characters that file systems or other programs dislike. */
    public static String safeName(String wanted) {
        String s = wanted == null ? "" : wanted.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ")
                .strip();
        while (s.startsWith(".")) s = s.substring(1);
        if (s.length() > 80) s = s.substring(0, 80).strip();
        return s.isEmpty() ? "Family Tree" : s;
    }

    /** Makes the folders of a new tree and writes the tree. */
    public StoredTree create(String name, FamilyTree tree) throws IOException {
        String free = freeName(name);
        StoredTree t = new StoredTree(free, root.resolve(free));
        Files.createDirectories(t.media());
        backedUp.add(t.file());
        save(t, tree);
        return t;
    }

    public FamilyTree load(StoredTree t) throws IOException {
        return FamilyTree.of(GedcomReader.read(t.file())).tree();
    }

    /** Writes the tree as GEDCOM 7.0, safely; the first save in a session keeps a backup of the old file. */
    public void save(StoredTree t, FamilyTree tree) throws IOException {
        Files.createDirectories(t.folder());
        Path file = t.file();
        if (Files.exists(file) && backedUp.add(file)) backup(t);
        GedcomNode header = GedcomConverter.header(tree.header(), GedcomVersion.V70, programVersion, null, null);
        Path part = file.resolveSibling(file.getFileName() + ".part");
        try (OutputStream os = Files.newOutputStream(part)) {
            GedcomWriter.write(header, tree.records(), GedcomVersion.V70, os);
        }
        try {
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void backup(StoredTree t) throws IOException {
        Files.createDirectories(t.backups());
        Path copy = t.backups().resolve(t.name() + " " + LocalDateTime.now().format(STAMP) + ".ged");
        Files.copy(t.file(), copy, StandardCopyOption.REPLACE_EXISTING);
        List<Path> all;
        try (Stream<Path> files = Files.list(t.backups())) {
            all = files.filter(p -> p.getFileName().toString().endsWith(".ged"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
        }
        for (int i = BACKUPS_KEPT; i < all.size(); i++) Files.deleteIfExists(all.get(i));
    }

    public List<Path> backups(StoredTree t) {
        if (!Files.isDirectory(t.backups())) return List.of();
        try (Stream<Path> files = Files.list(t.backups())) {
            return files.filter(p -> p.getFileName().toString().endsWith(".ged"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Renames a tree: folder and file. */
    public StoredTree rename(StoredTree t, String newName) throws IOException {
        String name = freeName(newName);
        Path folder = root.resolve(name);
        Files.move(t.folder(), folder);
        StoredTree renamed = new StoredTree(name, folder);
        Files.move(folder.resolve(t.name() + ".ged"), renamed.file());
        return renamed;
    }

    /** Moves the tree's folder to the trash ({@code ~/.local/share/Trash}) or, without one, deletes it. */
    public void delete(StoredTree t, Path trash) throws IOException {
        if (trash != null) {
            Path files = trash.resolve("files");
            Path info = trash.resolve("info");
            Files.createDirectories(files);
            Files.createDirectories(info);
            String name = t.folder().getFileName().toString();
            Path target = files.resolve(name);
            for (int i = 2; Files.exists(target); i++) target = files.resolve(name + "." + i);
            Files.writeString(info.resolve(target.getFileName() + ".trashinfo"), "[Trash Info]\nPath="
                    + t.folder().toAbsolutePath().toUri().getRawPath() + "\nDeletionDate="
                    + LocalDateTime.now().withNano(0) + "\n");
            Files.move(t.folder(), target);
            return;
        }
        try (Stream<Path> walk = Files.walk(t.folder())) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    // ---- media -----------------------------------------------------------------------------------------------

    /** The file of a media record on this computer: relative to the tree's folder, absolute or a file: URI. */
    public static Optional<Path> localFile(StoredTree t, MediaRecord m) {
        return localFile(t == null ? null : t.folder(), m.file());
    }

    public static Optional<Path> localFile(Path base, String file) {
        if (file == null || file.isBlank()) return Optional.empty();
        String f = file.strip();
        String lower = f.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) return Optional.empty();
        try {
            Path p;
            if (lower.startsWith("file:")) p = Path.of(URI.create(f));
            else if (f.matches("^[A-Za-z]:[\\\\/].*")) return Optional.empty();
            else {
                Path given = Path.of(f.replace('\\', '/'));
                p = given.isAbsolute() || base == null ? given : base.resolve(given).normalize();
            }
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Copies a file into the tree's media folder (under a free name) and returns the {@code FILE} value for it,
     * relative to the tree's folder ("media/photo.jpg").
     */
    public static String copyIntoMedia(StoredTree t, Path source) throws IOException {
        Files.createDirectories(t.media());
        Path target = freeFile(t.media(), source.getFileName().toString());
        Files.copy(source, target);
        return StoredTree.MEDIA + "/" + target.getFileName();
    }

    /** A file name in {@code folder} that is not taken yet: "photo.jpg", "photo-2.jpg", ... */
    public static Path freeFile(Path folder, String wanted) {
        String name = wanted.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (name.isBlank() || name.startsWith(".")) name = "file" + name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        if (stem.length() > 100) stem = stem.substring(0, 100);
        Path p = folder.resolve(stem + ext);
        for (int i = 2; Files.exists(p); i++) p = folder.resolve(stem + "-" + i + ext);
        return p;
    }
}
