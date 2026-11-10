package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.swing.EdtExecutor;

import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** File, Edit, View, Person, Options and Help. */
public final class MainMenuBar extends JMenuBar {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;

    private final Messages msg;
    private final Map<String, JMenuItem> items = new HashMap<>();
    private final Map<ViewKind, JRadioButtonMenuItem> views = new EnumMap<>(ViewKind.class);

    /** What the menus do that is not the presenter's business: the list's search field and the zoom. */
    public interface WindowActions {
        void find();

        void zoom(int direction);
    }

    public MainMenuBar(MainPresenter presenter, Messages msg, LocaleSettings locales, WindowActions window) {
        EdtExecutor.requireEdt();
        this.msg = msg;

        JMenu file = menu("menu.file");
        file.add(item("menu.file.new", key(KeyEvent.VK_N, CTRL), presenter::newTree));
        file.add(item("menu.file.open", key(KeyEvent.VK_O, CTRL), presenter::openTreeDialog));
        file.addSeparator();
        file.add(item("menu.file.import", key(KeyEvent.VK_I, CTRL), presenter::importGedcom));
        file.add(item("menu.file.export", key(KeyEvent.VK_E, CTRL), presenter::export));
        file.addSeparator();
        file.add(item("menu.file.photos", null, presenter::downloadPhotos));
        file.add(item("menu.file.find-photos", null, presenter::findPhotos));
        file.addSeparator();
        file.add(item("menu.file.print", key(KeyEvent.VK_P, CTRL), presenter::print));
        file.add(item("menu.file.picture", null, presenter::saveChartPicture));
        file.add(item("menu.file.folder", null, presenter::showFolder));
        file.addSeparator();
        file.add(item("menu.file.exit", key(KeyEvent.VK_Q, CTRL), presenter::exit));

        JMenu edit = menu("menu.edit");
        edit.add(item("menu.edit.undo", key(KeyEvent.VK_Z, CTRL), presenter::undo));
        edit.add(item("menu.edit.redo", key(KeyEvent.VK_Y, CTRL), presenter::redo));
        edit.addSeparator();
        edit.add(item("menu.edit.person", key(KeyEvent.VK_ENTER, CTRL), presenter::editSelected));
        edit.add(item("menu.edit.delete", key(KeyEvent.VK_DELETE, CTRL), presenter::deleteSelected));
        edit.addSeparator();
        edit.add(item("menu.edit.find", key(KeyEvent.VK_F, CTRL), window::find));

        JMenu view = menu("menu.view");
        ButtonGroup group = new ButtonGroup();
        int n = 0;
        for (ViewKind kind : ViewKind.values()) {
            JRadioButtonMenuItem item = Mnemonics.apply(new JRadioButtonMenuItem(), msg.get(kind.textKey()));
            item.setName(kind.textKey());
            item.setAccelerator(key(KeyEvent.VK_1 + n++, CTRL));
            item.addActionListener(e -> presenter.showView(kind));
            group.add(item);
            views.put(kind, item);
            view.add(item);
        }
        view.addSeparator();
        view.add(item("menu.view.more", key(KeyEvent.VK_PAGE_UP, CTRL), presenter::moreGenerations));
        view.add(item("menu.view.fewer", key(KeyEvent.VK_PAGE_DOWN, CTRL), presenter::fewerGenerations));
        view.addSeparator();
        view.add(item("menu.view.zoom-in", key(KeyEvent.VK_PLUS, CTRL), () -> window.zoom(1)));
        view.add(item("menu.view.zoom-out", key(KeyEvent.VK_MINUS, CTRL), () -> window.zoom(-1)));
        view.add(item("menu.view.zoom-reset", key(KeyEvent.VK_0, CTRL), () -> window.zoom(0)));
        view.addSeparator();
        view.add(item("menu.view.back", key(KeyEvent.VK_LEFT, ALT), presenter::back));
        view.add(item("menu.view.forward", key(KeyEvent.VK_RIGHT, ALT), presenter::forward));
        view.add(item("menu.view.home", key(KeyEvent.VK_HOME, ALT), presenter::goHome));
        view.add(item("menu.view.centre", key(KeyEvent.VK_M, CTRL), presenter::centreSelected));

        JMenu person = menu("menu.person");
        for (Relative r : Relative.values()) {
            person.add(item(r.textKey(), r == Relative.PARTNER ? key(KeyEvent.VK_P, CTRL | SHIFT) : null,
                    () -> presenter.addRelative(r)));
            if (r == Relative.MOTHER || r == Relative.PARTNER || r == Relative.DAUGHTER) person.addSeparator();
        }
        person.add(item("menu.person.connect", key(KeyEvent.VK_L, CTRL), presenter::connectExisting));
        person.addSeparator();
        person.add(item("menu.person.home", null, presenter::setHome));
        person.add(item("menu.person.relationship", key(KeyEvent.VK_R, CTRL), presenter::relationship));

        if (locales != null) {
            JMenu options = menu("menu.options");
            options.add(languageMenu(presenter, locales));
        }

        JMenu help = menu("menu.help");
        help.add(item("menu.help.handbook", key(KeyEvent.VK_F1, 0), presenter::handbook));
        help.addSeparator();
        help.add(item("menu.help.about", null, presenter::about));
    }

    public JMenuItem item(String name) {
        return items.get(name);
    }

    /** Marks the view that is shown. */
    public void showView(ViewKind kind) {
        JRadioButtonMenuItem item = views.get(kind);
        if (item != null) item.setSelected(true);
    }

    /** Enables what makes sense now; Undo and Redo name the change they take back. */
    public void update(boolean hasTree, boolean hasSelection, String undoLabel, String redoLabel, boolean back,
                       boolean forward, ViewKind view) {
        for (String k : List.of("menu.file.export", "menu.file.photos", "menu.file.find-photos", "menu.file.print",
                "menu.file.picture")) enable(k, hasTree);
        for (ViewKind v : ViewKind.values()) views.get(v).setEnabled(hasTree);
        for (Relative r : Relative.values()) enable(r.textKey(), hasTree && hasSelection);
        for (String k : List.of("menu.edit.person", "menu.edit.delete", "menu.person.connect", "menu.person.home",
                "menu.person.relationship", "menu.view.centre")) enable(k, hasTree && hasSelection);
        enable("menu.edit.find", hasTree);
        enable("menu.view.home", hasTree);
        enable("menu.view.more", hasTree && view.hasGenerations());
        enable("menu.view.fewer", hasTree && view.hasGenerations());
        boolean zoom = hasTree && view.isBoxChart();
        for (String k : List.of("menu.view.zoom-in", "menu.view.zoom-out", "menu.view.zoom-reset")) enable(k, zoom);
        enable("menu.view.back", back);
        enable("menu.view.forward", forward);
        label("menu.edit.undo", undoLabel);
        label("menu.edit.redo", redoLabel);
    }

    private void label(String key, String what) {
        JMenuItem item = items.get(key);
        Mnemonics.apply(item, what.isEmpty() ? msg.get(key) : msg.format(key + ".what", what.replace("&", "&&")));
        item.setEnabled(!what.isEmpty());
    }

    private void enable(String key, boolean on) {
        JMenuItem item = items.get(key);
        if (item != null) item.setEnabled(on);
    }

    /** "Desktop setting (Deutsch)" and the five languages; the choice applies at the next start. */
    private JMenu languageMenu(MainPresenter presenter, LocaleSettings locales) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get("menu.options.language"));
        menu.setName("menu.options.language");
        Messages commons = Messages.commons(msg.locale());
        List<Optional<Language>> choices = new ArrayList<>();
        choices.add(Optional.empty());
        for (Language language : Language.values()) choices.add(Optional.of(language));
        ButtonGroup group = new ButtonGroup();
        Optional<Language> current = locales.applicationLanguage();
        for (Optional<Language> choice : choices) {
            String label = choice.map(Language::nativeName).orElseGet(() -> commons.format("language.follow-desktop",
                    locales.desktopResolution().language().nativeName()));
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(label, choice.equals(current));
            item.setName(choice.map(Language::code).orElse(LocaleSettings.FOLLOW_DESKTOP));
            item.addActionListener(e -> presenter.setLanguage(choice));
            group.add(item);
            menu.add(item);
            if (choice.isEmpty()) menu.addSeparator();
        }
        return menu;
    }

    private static KeyStroke key(int code, int modifiers) {
        return KeyStroke.getKeyStroke(code, modifiers);
    }

    private JMenu menu(String key) {
        JMenu menu = Mnemonics.apply(new JMenu(), msg.get(key));
        menu.setName(key);
        add(menu);
        return menu;
    }

    private JMenuItem item(String key, KeyStroke accelerator, Runnable action) {
        JMenuItem item = Mnemonics.apply(new JMenuItem(), msg.get(key));
        item.setName(key);
        if (accelerator != null) item.setAccelerator(accelerator);
        item.addActionListener(e -> action.run());
        items.put(key, item);
        return item;
    }
}
