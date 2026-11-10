package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.apps.familytree.service.ImportReport;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.Undo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A main window that answers like a user would, as the test says, and remembers what it was shown. */
public final class FakeView implements MainView {
    final List<String> errors = new ArrayList<>();
    final List<String> statuses = new ArrayList<>();
    boolean welcome;
    StoredTree tree;
    Person root;
    Person selected;
    String home;
    ViewKind view;
    int generations;
    int refreshes;
    boolean canUndo;
    String undoLabel = "";
    boolean back;
    boolean forward;
    Relationship relationship;

    Optional<String> name = Optional.empty();
    Predicate<Person> editor = p -> true;
    boolean confirmAnswer = true;
    Optional<Connection> connection = Optional.empty();
    Optional<Person> chosenPerson = Optional.empty();
    Optional<Path> importFile = Optional.empty();
    Optional<ImportChoice> importChoice = Optional.empty();
    Optional<GedcomExport.Options> exportOptions = Optional.empty();
    Optional<Path> exportFile = Optional.empty();
    ImportReport lastReport;

    @Override
    public void showWelcome() {
        welcome = true;
    }

    @Override
    public void showTree(StoredTree stored, FamilyTree t) {
        welcome = false;
        tree = stored;
    }

    @Override
    public void refresh() {
        refreshes++;
    }

    @Override
    public void showPerson(Person r, Person s) {
        root = r;
        selected = s;
    }

    @Override
    public void showHome(String personId) {
        home = personId;
    }

    @Override
    public void showView(ViewKind v, int g) {
        view = v;
        generations = g;
    }

    @Override
    public void updateActions(boolean hasTree, boolean hasSelection, String undo, String redo, boolean b, boolean f) {
        canUndo = !undo.isEmpty();
        undoLabel = undo;
        back = b;
        forward = f;
    }

    @Override
    public void showStatus(String text) {
        statuses.add(text);
    }

    @Override
    public void showError(String text) {
        errors.add(text);
    }

    @Override
    public boolean confirm(String text) {
        return confirmAnswer;
    }

    @Override
    public Optional<Path> chooseImportFile() {
        return importFile;
    }

    @Override
    public Optional<Path> chooseExportFile(String suggestedName, GedcomExport.Format format) {
        return exportFile;
    }

    @Override
    public Optional<Path> chooseFolder(String title) {
        return Optional.empty();
    }

    @Override
    public Optional<Path> choosePictureFile(String suggestedName) {
        return Optional.empty();
    }

    @Override
    public Optional<String> askName(String title, String label, String initial) {
        return name;
    }

    @Override
    public Optional<ImportChoice> askImport(ImportReport report, String suggestedName, boolean treeOpen) {
        lastReport = report;
        return importChoice;
    }

    @Override
    public Optional<GedcomExport.Options> askExport(int privatePeople, int livingPeople) {
        return exportOptions;
    }

    @Override
    public Optional<StoredTree> chooseTree(List<StoredTree> trees, TreeActions actions) {
        return trees.stream().findFirst();
    }

    @Override
    public boolean editPerson(Person p, Undo.Step step, boolean isNew) {
        return editor.test(p);
    }

    @Override
    public Optional<Person> choosePerson(String title, List<Person> people) {
        return chosenPerson;
    }

    @Override
    public Optional<Connection> chooseConnection(Person p) {
        return connection;
    }

    @Override
    public void showRelationship(Person to, Person other, Relationship r) {
        relationship = r;
    }

    @Override
    public <T> void runWithProgress(String title, Task<T> task, Consumer<T> done, Consumer<Exception> failed) {
        T result;
        try {
            result = task.run(new Progress() {
                @Override
                public void update(int d, int total, String text) {
                }

                @Override
                public boolean cancelled() {
                    return false;
                }
            });
        } catch (Exception e) {
            failed.accept(e);
            return;
        }
        done.accept(result);
    }

    @Override
    public void print() {
    }

    @Override
    public void saveChartPicture(Path file) {
    }

    @Override
    public void showAbout() {
    }

    @Override
    public void close() {
    }
}
