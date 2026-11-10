package eu.amadeos.apps.familytree.bootstrap;

import eu.amadeos.apps.familytree.ui.main.MainPresenter;
import eu.amadeos.apps.familytree.ui.main.MainWindow;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;

/** The one main window of the program, made when it is first needed. */
public final class Windows {
    private final Messages msg;
    private final LocaleSettings locales;
    private final Executor background;
    private final MainPresenter.Services services;
    private MainWindow main;
    private MainPresenter presenter;

    public Windows(Messages msg, LocaleSettings locales, Executor background, MainPresenter.Services services) {
        this.msg = msg;
        this.locales = locales;
        this.background = background;
        this.services = services;
    }

    /** Shows the main window; a file is imported. */
    public void showMain(Optional<Path> file) {
        EdtExecutor.requireEdt();
        if (main == null || !main.isDisplayable()) {
            main = new MainWindow(msg, locales, background, services.version());
            presenter = new MainPresenter(main, msg, services);
            main.bind(presenter);
            main.setVisible(true);
            presenter.start(file);
        } else {
            file.ifPresent(presenter::importFile);
            main.toFront();
        }
    }

    public Optional<MainWindow> mainWindow() {
        return Optional.ofNullable(main);
    }

    public Optional<MainPresenter> mainPresenter() {
        return Optional.ofNullable(presenter);
    }
}
