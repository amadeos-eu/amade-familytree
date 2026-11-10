package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.apps.familytree.service.ImportReport;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.Undo;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** What the presenter needs from the main window (and what the tests fake). */
public interface MainView {
    /** The page for a first start: import a file or start a new tree. */
    void showWelcome();

    void showTree(StoredTree stored, FamilyTree tree);

    /** The tree changed: list, chart, summary and menus show it again. */
    void refresh();

    /** {@code root} is the person the chart is about, {@code selected} the one the summary shows. */
    void showPerson(Person root, Person selected);

    void showView(ViewKind view, int generations);

    void updateActions(boolean hasTree, boolean hasSelection, String undoLabel, String redoLabel, boolean back,
                       boolean forward);

    void showStatus(String text);

    void showError(String text);

    boolean confirm(String text);

    Optional<Path> chooseImportFile();

    Optional<Path> chooseExportFile(String suggestedName, GedcomExport.Format format);

    Optional<Path> chooseFolder(String title);

    Optional<Path> choosePictureFile(String suggestedName);

    Optional<String> askName(String title, String label, String initial);

    /** The choice in the import dialog: a new tree with this name, or merge into the open tree. */
    record ImportChoice(boolean merge, String name, boolean downloadPhotos) {
    }

    Optional<ImportChoice> askImport(ImportReport report, String suggestedName, boolean treeOpen);

    Optional<GedcomExport.Options> askExport(int privatePeople, int livingPeople);

    /** The tree list: open, rename, delete; returns the tree to open. */
    Optional<StoredTree> chooseTree(List<StoredTree> trees, TreeActions actions);

    interface TreeActions {
        Optional<StoredTree> rename(StoredTree t);

        boolean delete(StoredTree t);

        void newTree();

        void importFile();
    }

    /** Opens the person editor; true when the user saved. */
    boolean editPerson(Person p, Undo.Step step, boolean isNew);

    Optional<Person> choosePerson(String title, List<Person> people);

    Optional<Connection> chooseConnection(Person p);

    void showRelationship(Person to, Person other, Relationship r);

    /** Runs work in the background with a progress dialog that can be cancelled. */
    interface Task<T> {
        T run(Progress progress) throws Exception;
    }

    interface Progress {
        void update(int done, int total, String text);

        boolean cancelled();
    }

    <T> void runWithProgress(String title, Task<T> task, Consumer<T> done, Consumer<Exception> failed);

    /** Prints the current chart or page. */
    void print();

    void saveChartPicture(Path file) throws java.io.IOException;

    void showAbout();

    void close();
}
