package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.OnEdt;
import eu.amadeos.apps.familytree.Samples;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.repository.FamilyTreePreferences;
import eu.amadeos.apps.familytree.service.DesktopOpener;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.TreeStore;
import eu.amadeos.apps.familytree.ui.edit.PersonEditor;
import eu.amadeos.apps.familytree.ui.text.Texts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The real window under a display (Xvfb in CI); skipped without one. */
class MainWindowTest {
    @TempDir
    Path dir;
    private MainWindow window;
    private MainPresenter presenter;
    private StoredTree stored;

    @BeforeEach
    void setUp() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display");
        TreeStore store = new TreeStore(dir.resolve("trees"), "test");
        stored = store.create("Brenner", Samples.brenner());
        FamilyTreePreferences prefs = new FamilyTreePreferences(dir.resolve("prefs.properties"));
        prefs.setHomePerson("Brenner", "I1");
        OnEdt.run(() -> {
            window = new MainWindow(Samples.english(), null, Executors.newSingleThreadExecutor(), "test");
            presenter = new MainPresenter(window, Samples.english(), new MainPresenter.Services(store, prefs,
                    () -> null, new DesktopOpener(null), null, "test", () -> {
            }));
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(Optional.empty());
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (window != null) OnEdt.run(window::dispose);
    }

    @Test
    void everyViewShowsTheFamily() throws Exception {
        OnEdt.run(() -> {
            assertEquals("Brenner – Family Tree", window.getTitle());
            assertEquals(21, window.personList().getModel().getSize());
            assertTrue(window.chartPanel().chart().boxes().size() > 10, "hourglass with parents and children");
            presenter.showView(ViewKind.ANCESTORS);
            assertEquals(1 + 2 + 4 + 2, window.chartPanel().chart().boxes().size());
            presenter.showView(ViewKind.DESCENDANTS);
            assertTrue(window.chartPanel().chart().boxes().size() >= 8);
            presenter.showView(ViewKind.FAN);
            assertEquals(4, window.fanChart().generations(), "one empty ring beyond Georg and Christina");
            presenter.showView(ViewKind.FAMILY);
            assertTrue(window.pagePane().getText().contains("Hildegard"));
            presenter.showView(ViewKind.TIMELINE);
            assertTrue(window.pagePane().getText().contains("1928"));
            presenter.showView(ViewKind.HOURGLASS);
        });
    }

    @Test
    void chartsCanBeSavedAsPictures() throws Exception {
        OnEdt.run(() -> {
            for (ViewKind v : ViewKind.values()) {
                presenter.showView(v);
                Path png = dir.resolve(v + ".png");
                window.saveChartPicture(png);
                assertTrue(Files.size(png) > 1000, v.name());
            }
            BufferedImage img = window.fanChart().image(1);
            assertTrue(img.getWidth() > 400);
        });
    }

    @Test
    void selectingAndCentring() throws Exception {
        OnEdt.run(() -> {
            Person peter = presenter.tree().orElseThrow().person("I9").orElseThrow();
            presenter.select(peter);
            assertEquals(peter, window.personList().getSelectedValue());
            presenter.centre(peter);
            assertTrue(window.chartPanel().chart().boxOf(peter).orElseThrow().root());
            assertTrue(window.menus().item("menu.view.back").isEnabled());
        });
    }

    @Test
    void theEditorChangesThePerson() throws Exception {
        Person ernst = presenter.tree().orElseThrow().person("I1").orElseThrow();
        OnEdt.run(() -> presenter.select(ernst));
        whenDialogOpens(d -> {
            JTextField given = find(d, JTextField.class, "given");
            assertEquals("Ernst Wilhelm", given.getText());
            JTable facts = find(d, JTable.class, "facts");
            assertTrue(facts.getRowCount() >= 3);
            find(d, JTextField.class, "factPlace").setText("Stuttgart");
            given.setText("Ernst");
            find(d, AbstractButton.class, "ok").doClick();
        });
        OnEdt.run(presenter::editSelected);
        OnEdt.run(() -> {
            Person again = presenter.tree().orElseThrow().person("I1").orElseThrow();
            assertEquals("Ernst Brenner", again.fullName());
            assertTrue(window.menus().item("menu.edit.undo").isEnabled());
        });
    }

    @Test
    void cancellingTheEditorKeepsEverything() throws Exception {
        Person ernst = presenter.tree().orElseThrow().person("I1").orElseThrow();
        int facts = ernst.facts().size();
        OnEdt.run(() -> presenter.select(ernst));
        whenDialogOpens(d -> {
            find(d, JTable.class, "facts").clearSelection();
            find(d, AbstractButton.class, "addFact").doClick();
            find(d, JTextField.class, "factDate").setText("1950");
            find(d, JTextField.class, "given").setText("Hans");
            find(d, AbstractButton.class, "cancel").doClick();
        });
        OnEdt.run(presenter::editSelected);
        Person again = presenter.tree().orElseThrow().person("I1").orElseThrow();
        assertEquals("Ernst Wilhelm Brenner", again.fullName());
        assertEquals(facts, again.facts().size());
        assertFalse(presenter.undoHistory().canUndo());
    }

    @Test
    void theEditorWorksOnItsOwn() throws Exception {
        Person ernst = presenter.tree().orElseThrow().person("I1").orElseThrow();
        whenDialogOpens(d -> {
            find(d, JTextField.class, "called").setText("Willi");
            find(d, AbstractButton.class, "ok").doClick();
        });
        OnEdt.run(() -> assertTrue(PersonEditor.edit(window, new Texts(Samples.english()), ernst, stored, false)));
        assertEquals("Willi Brenner", ernst.shortName());
    }

    /** Runs {@code action} on the next modal dialog, from the event thread, once it shows. */
    private static void whenDialogOpens(Consumer<JDialog> action) {
        Timer t = new Timer(150, null);
        t.addActionListener(e -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    t.stop();
                    action.accept(d);
                    return;
                }
            }
        });
        SwingUtilities.invokeLater(t::start);
    }

    static <T extends Component> T find(Container root, Class<T> type, String name) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
            if (c instanceof Container inner) {
                T found = findOrNull(inner, type, name);
                if (found != null) return found;
            }
        }
        throw new AssertionError("no " + name);
    }

    private static <T extends Component> T findOrNull(Container root, Class<T> type, String name) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c) && name.equals(c.getName())) return type.cast(c);
            if (c instanceof Container inner) {
                T found = findOrNull(inner, type, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
