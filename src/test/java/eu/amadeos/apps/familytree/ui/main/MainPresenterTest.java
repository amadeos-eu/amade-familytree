package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.Samples;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.apps.familytree.repository.FamilyTreePreferences;
import eu.amadeos.apps.familytree.service.DesktopOpener;
import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.TreeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainPresenterTest {
    @TempDir
    Path dir;
    private FakeView view;
    private TreeStore store;
    private FamilyTreePreferences prefs;
    private MainPresenter presenter;

    @BeforeEach
    void setUp() {
        view = new FakeView();
        store = new TreeStore(dir.resolve("trees"), "test");
        prefs = new FamilyTreePreferences(dir.resolve("prefs.properties"));
        presenter = new MainPresenter(view, Samples.english(), new MainPresenter.Services(store, prefs,
                () -> null, new DesktopOpener(null), null, "test", () -> {
        }));
    }

    private StoredTree brenner() throws IOException {
        return store.create("Brenner", Samples.brenner());
    }

    private Person person(String id) {
        return presenter.tree().orElseThrow().person(id).orElseThrow();
    }

    @Test
    void withoutTreesTheWelcomePageShows() {
        presenter.start(Optional.empty());
        assertTrue(view.welcome);
        assertTrue(presenter.tree().isEmpty());
    }

    @Test
    void theOnlyTreeOpensAtStart() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        assertFalse(view.welcome);
        assertEquals("Brenner", view.tree.name());
        assertNotNull(view.root);
        assertEquals(ViewKind.HOURGLASS, view.view);
    }

    @Test
    void theHomePersonIsInTheMiddle() throws IOException {
        brenner();
        prefs.setHomePerson("Brenner", "I12");
        presenter.start(Optional.empty());
        assertEquals("I12", view.root.id());
    }

    @Test
    void aNewTreeStartsWithItsFirstPerson() {
        view.name = Optional.of("Meine Familie");
        view.editor = p -> {
            p.setNames("Ralf", "Muster", "", "");
            return true;
        };
        presenter.newTree();
        assertEquals("Meine Familie", presenter.stored().orElseThrow().name());
        assertEquals("Ralf Muster", view.root.fullName());
        assertEquals(view.root.id(), prefs.homePerson("Meine Familie").orElseThrow());
        assertTrue(view.canUndo);
    }

    @Test
    void addingAFatherAndCancelling() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        presenter.centre(person("I15"));
        int before = presenter.tree().orElseThrow().count("INDI");

        view.editor = p -> false;
        presenter.addRelative(Relative.FATHER);
        assertEquals(before, presenter.tree().orElseThrow().count("INDI"), "cancel takes the new person back");

        view.editor = p -> {
            assertEquals("Brenner", p.surname(), "the surname is filled in");
            p.setNames("Thomas", p.surname(), "", "");
            return true;
        };
        presenter.addRelative(Relative.BROTHER);
        assertEquals(before + 1, presenter.tree().orElseThrow().count("INDI"));
        assertEquals("Add brother", view.undoLabel);
        FamilyTree saved = store.load(presenter.stored().orElseThrow());
        assertEquals(before + 1, saved.count("INDI"), "changes are saved at once");
    }

    @Test
    void undoAndRedoAChange() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        presenter.select(person("I1"));
        view.editor = p -> {
            p.setNames("Ernst", "Brenner", "", "");
            return true;
        };
        presenter.editSelected();
        assertEquals("Ernst Brenner", person("I1").fullName());
        presenter.undo();
        assertEquals("Ernst Wilhelm Brenner", person("I1").fullName());
        assertEquals("Ernst Wilhelm Brenner", view.selected.fullName(), "the view shows the person as before");
        presenter.redo();
        assertEquals("Ernst Brenner", person("I1").fullName());
    }

    @Test
    void connectingAnExistingPersonAsChild() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        Person stranger = presenter.tree().orElseThrow().newPerson("Otto", "Brenner", Sex.MALE);
        presenter.select(person("I15"));
        view.connection = Optional.of(Connection.CHILD);
        view.chosenPerson = Optional.of(stranger);
        presenter.connectExisting();
        assertTrue(person("I15").children().contains(stranger));
        presenter.connectExisting();
        assertEquals(1, view.errors.size(), "connecting twice is refused");
    }

    @Test
    void deletingMovesTheSelectionToARelative() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        Person lena = person("I15");
        presenter.select(lena);
        presenter.deleteSelected();
        assertTrue(presenter.tree().orElseThrow().person("I15").isEmpty());
        assertEquals("I11", view.selected.id());
    }

    @Test
    void backAndForward() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        String first = view.root.id();
        presenter.centre(person("I12"));
        presenter.centre(person("I14"));
        assertTrue(view.back);
        presenter.back();
        assertEquals("I12", view.root.id());
        assertTrue(view.forward);
        presenter.back();
        assertEquals(first, view.root.id());
        presenter.forward();
        assertEquals("I12", view.root.id());
    }

    @Test
    void generationsStayWithinTheView() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        presenter.showView(ViewKind.FAN);
        assertEquals(5, view.generations);
        for (int i = 0; i < 10; i++) presenter.moreGenerations();
        assertEquals(8, view.generations);
        presenter.showView(ViewKind.ANCESTORS);
        assertEquals(4, view.generations);
        presenter.showView(ViewKind.FAN);
        assertEquals(8, view.generations, "each view remembers its own number");
    }

    @Test
    void relationshipToTheHomePerson() throws IOException {
        brenner();
        prefs.setHomePerson("Brenner", "I12");
        presenter.start(Optional.empty());
        presenter.select(person("I17"));
        presenter.relationship();
        assertEquals(Relationship.Kind.BLOOD, view.relationship.kind());
        assertEquals(5, view.relationship.up());
        assertEquals(ViewKind.RELATIONSHIP, view.view, "the chart switches to the relationship view");
        assertEquals("I12", view.root.id(), "the way starts at the home person");
        assertEquals("I17", view.selected.id());

        presenter.centre(person("I1"));
        presenter.select(person("I14"));
        presenter.relationship();
        assertEquals("I12", view.root.id(), "already in the view: the way starts at home again");
        assertEquals("I14", view.selected.id());
    }

    @Test
    void importAsNewTreeAndExport() throws IOException {
        Path file = dir.resolve("brenner.ged");
        Files.write(file, Samples.bytes(Samples.BRENNER));
        view.importChoice = Optional.of(new MainView.ImportChoice(false, "Brenner", false));
        presenter.start(Optional.of(file));
        assertEquals(21, view.lastReport.people());
        assertEquals("Brenner", presenter.stored().orElseThrow().name());
        assertEquals(21, presenter.tree().orElseThrow().count("INDI"));

        Path out = dir.resolve("export.ged");
        view.exportOptions = Optional.of(new GedcomExport.Options(GedcomExport.Format.GEDCOM_551,
                GedcomExport.LivingPeople.INCLUDE, ""));
        view.exportFile = Optional.of(out);
        presenter.export();
        assertTrue(Files.readString(out).contains("Ernst Wilhelm /Brenner/"));
        assertTrue(view.errors.isEmpty(), view.errors.toString());
    }

    @Test
    void importMergesIntoTheOpenTree() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        Path file = dir.resolve("brenner.ged");
        Files.write(file, Samples.bytes(Samples.BRENNER));
        view.importFile = Optional.of(file);
        view.importChoice = Optional.of(new MainView.ImportChoice(true, "", false));
        presenter.importGedcom();
        assertEquals(42, presenter.tree().orElseThrow().count("INDI"));
        presenter.undo();
        assertEquals(21, presenter.tree().orElseThrow().count("INDI"), "the import can be undone");
    }

    @Test
    void aBrokenFileGivesAnError() throws IOException {
        Path file = dir.resolve("broken.ged");
        Files.writeString(file, "not a gedcom file at all");
        view.importChoice = Optional.of(new MainView.ImportChoice(false, "Broken", false));
        presenter.importFile(file);
        assertEquals(1, view.errors.size());
    }

    @Test
    void openTreeDialogOpensTheChosenTree() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        store.create("Zweiter", FamilyTree.empty());
        presenter.openTreeDialog();
        assertEquals("Brenner", presenter.stored().orElseThrow().name());
    }

    @Test
    void theRelationshipViewStartsAtHomeAndFollowsTheSelection() throws IOException {
        brenner();
        prefs.setHomePerson("Brenner", "I1");
        presenter.start(Optional.empty());
        presenter.centre(person("I14"));
        presenter.showView(ViewKind.RELATIONSHIP);
        assertEquals("I1", view.root.id(), "the way starts at the home person");
        assertEquals("I14", view.selected.id(), "and leads to the selected person");
        presenter.select(person("I12"));
        assertEquals("I1", view.root.id());
        assertEquals("I12", view.selected.id());
        presenter.centre(person("I14"));
        assertEquals("I14", view.root.id(), "a double-click chooses another start");
        assertEquals("I12", view.selected.id(), "the destination stays");
        presenter.back();
        assertEquals("I1", view.root.id());
        assertEquals("I12", view.selected.id());
    }

    @Test
    void theListMarksTheHomePerson() throws IOException {
        brenner();
        presenter.start(Optional.empty());
        assertEquals(null, view.home, "no home person chosen yet");
        presenter.select(person("I12"));
        presenter.setHome();
        assertEquals("I12", view.home);
        prefs.setHomePerson("Brenner", "I1");
        presenter.start(Optional.empty());
        assertEquals("I1", view.home, "opening the tree marks its home person");
    }
}
