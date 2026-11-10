package eu.amadeos.apps.familytree.service;

import eu.amadeos.apps.familytree.Samples;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.apps.familytree.gedcom.GedcomReader;
import eu.amadeos.apps.familytree.repository.FamilyTreePreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipInputStream;

import static eu.amadeos.apps.familytree.Samples.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTest {
    @TempDir
    Path dir;

    private Path sample() throws IOException {
        Path file = dir.resolve("brenner.ged");
        Files.write(file, Samples.bytes(Samples.BRENNER));
        return file;
    }

    // ---- the store -------------------------------------------------------------------------------------------

    @Test
    void treesAreSavedLoadedRenamedAndDeleted() throws IOException {
        TreeStore store = new TreeStore(dir.resolve("trees"), "1.0");
        StoredTree t = store.create("Brenner", Samples.brenner());
        assertEquals(List.of(t), store.list());
        assertTrue(Files.readString(t.file(), StandardCharsets.UTF_8).contains("2 VERS 7.0"));
        FamilyTree loaded = store.load(t);
        assertEquals(21, loaded.count("INDI"));
        assertEquals("Brenner 2", store.freeName("Brenner"));

        store.save(t, loaded);
        assertEquals(0, store.backups(t).size(), "a tree made in this session needs no backup");
        TreeStore nextSession = new TreeStore(dir.resolve("trees"), "1.0");
        person(loaded, "I1").setNotes("Toolmaker in Esslingen");
        nextSession.save(t, loaded);
        nextSession.save(t, loaded);
        assertEquals(1, nextSession.backups(t).size(), "one backup per session");
        assertEquals("Toolmaker in Esslingen", person(store.load(t), "I1").ownNotes());

        StoredTree renamed = store.rename(t, "Familie Brenner");
        assertTrue(Files.isRegularFile(renamed.file()));
        assertFalse(Files.exists(t.folder()));
        store.delete(renamed, dir.resolve("Trash"));
        assertTrue(store.list().isEmpty());
        assertTrue(Files.exists(dir.resolve("Trash/files/Familie Brenner")));
    }

    @Test
    void namesAreMadeSafeForFolders() {
        assertEquals("A B", TreeStore.safeName("A/B"));
        assertFalse(TreeStore.safeName("..").startsWith("."));
    }

    // ---- import ----------------------------------------------------------------------------------------------

    @Test
    void importReportTellsWhatIsInTheFile() throws IOException {
        try (GedcomImport imp = GedcomImport.read(sample())) {
            ImportReport r = imp.report();
            assertEquals(21, r.people());
            assertEquals(8, r.families());
            assertEquals("5.5.1", r.version());
            assertEquals(1, r.webPhotos());
            assertEquals(1, r.unreadableDateCount());
            assertTrue(r.unreadableDates().getFirst().contains("Frühjahr"));
            assertTrue(r.extensionTags() > 0);
            assertEquals("brenner", imp.suggestedName());
        }
    }

    @Test
    void importIntoAStoreAndMergeIntoATree() throws IOException {
        TreeStore store = new TreeStore(dir.resolve("trees"), "1.0");
        StoredTree t;
        try (GedcomImport imp = GedcomImport.read(sample())) {
            t = imp.into(store, "Brenner");
        }
        FamilyTree tree = store.load(t);
        assertEquals(21, tree.count("INDI"));
        try (GedcomImport imp = GedcomImport.read(sample())) {
            assertEquals(21, imp.mergeInto(t, tree));
        }
        assertEquals(42, tree.count("INDI"));
    }

    // ---- export ----------------------------------------------------------------------------------------------

    @Test
    void exportLeavesOutPrivateAndShortensLivingPeople() throws IOException {
        FamilyTree tree = Samples.brenner();
        person(tree, "I16").setPrivate(true);
        StoredTree t = new StoredTree("Brenner", dir);
        Path out = dir.resolve("out.ged");
        GedcomExport.Result r = GedcomExport.export(tree, t, new GedcomExport.Options(GedcomExport.Format.GEDCOM_551,
                GedcomExport.LivingPeople.NAME_ONLY, "Living"), out, "1.0");
        assertEquals(20, r.written());
        assertTrue(r.shortened() > 0);
        String text = Files.readString(out, StandardCharsets.UTF_8);
        assertTrue(text.contains("2 VERS 5.5.1"));
        assertFalse(text.contains("Lotte"));
        assertFalse(text.contains("5 JUL 1954"), "Peter's birth is not written");
        FamilyTree back = FamilyTree.of(GedcomReader.read(Files.readAllBytes(out))).tree();
        assertEquals(20, back.count("INDI"));
    }

    @Test
    void exportCanLeaveLivingPeopleOut() throws IOException {
        FamilyTree tree = Samples.brenner();
        Path out = dir.resolve("out.ged");
        GedcomExport.Result r = GedcomExport.export(tree, new StoredTree("Brenner", dir),
                new GedcomExport.Options(GedcomExport.Format.GEDCOM_7, GedcomExport.LivingPeople.LEAVE_OUT, ""), out,
                "1.0");
        assertTrue(r.leftOut() > 0);
        assertFalse(Files.readString(out, StandardCharsets.UTF_8).contains("Markus"));
    }

    @Test
    void gedzipHoldsTheTreeAndItsPhotos() throws IOException {
        FamilyTree tree = Samples.brenner();
        StoredTree t = new StoredTree("Brenner", dir.resolve("tree"));
        Files.createDirectories(t.media());
        Files.write(t.media().resolve("ernst.jpg"), new byte[]{1, 2, 3});
        var m = tree.newMedia("media/ernst.jpg", "image/jpeg", "Ernst");
        person(tree, "I1").setPortrait(m.id());
        Path out = dir.resolve("out.gdz");
        GedcomExport.Result r = GedcomExport.export(tree, t, new GedcomExport.Options(GedcomExport.Format.GEDZIP,
                GedcomExport.LivingPeople.INCLUDE, ""), out, "1.0");
        assertEquals(1, r.mediaFiles());
        List<String> names = new java.util.ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(out))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) names.add(e.getName());
        }
        assertTrue(names.contains("gedcom.ged"));
        assertTrue(names.contains("media/ernst.jpg"));
    }

    // ---- undo ------------------------------------------------------------------------------------------------

    @Test
    void undoAndRedoPutRecordsBack() {
        FamilyTree tree = Samples.brenner();
        Undo undo = new Undo();
        Person lena = person(tree, "I15");
        Undo.Step step = undo.begin(tree, "Add partner");
        step.touchAround(lena);
        Person partner = tree.addPartner(lena, Sex.MALE, "Jonas", "Roth");
        undo.commit(step);
        assertEquals(22, tree.count("INDI"));
        assertEquals("Add partner", undo.undoLabel());

        assertTrue(undo.undo());
        assertEquals(21, tree.count("INDI"));
        assertTrue(person(tree, "I15").partners().isEmpty());
        assertTrue(undo.redo());
        assertEquals(22, tree.count("INDI"));
        assertEquals("Jonas Roth", person(tree, "I15").partners().getFirst().fullName());
        assertTrue(tree.person(partner.id()).isPresent());
    }

    @Test
    void cancelTakesAnUnfinishedChangeBack() {
        FamilyTree tree = Samples.brenner();
        Undo undo = new Undo();
        Person ernst = person(tree, "I1");
        Undo.Step step = undo.begin(tree, "Edit");
        step.touchAround(ernst);
        ernst.setNames("Hans", "Brenner", "", "");
        tree.newSource("A register");
        undo.cancel(step);
        assertEquals("Ernst Wilhelm Brenner", person(tree, "I1").fullName());
        assertTrue(tree.sources().stream().noneMatch(s -> s.title().equals("A register")));
        assertFalse(undo.canUndo());
    }

    // ---- preferences -----------------------------------------------------------------------------------------

    @Test
    void preferencesRememberTreeHomeAndView() {
        Path file = dir.resolve("familytree.properties");
        FamilyTreePreferences prefs = new FamilyTreePreferences(file);
        prefs.setLastTree("Brenner");
        prefs.setHomePerson("Brenner", "I12");
        prefs.setView(3);
        prefs.setGenerations(3, 6);
        FamilyTreePreferences again = new FamilyTreePreferences(file);
        assertEquals("Brenner", again.lastTree().orElseThrow());
        assertEquals("I12", again.homePerson("Brenner").orElseThrow());
        assertEquals(3, again.view());
        assertEquals(6, again.generations(3, 5));
        assertEquals(4, again.generations(1, 4));
    }
}
