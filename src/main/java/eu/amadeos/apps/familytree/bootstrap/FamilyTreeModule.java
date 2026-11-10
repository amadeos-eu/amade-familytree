package eu.amadeos.apps.familytree.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import eu.amadeos.apps.familytree.repository.FamilyTreePreferences;
import eu.amadeos.apps.familytree.service.DesktopOpener;
import eu.amadeos.apps.familytree.service.PhotoDownloader;
import eu.amadeos.apps.familytree.service.TreeStore;
import eu.amadeos.apps.familytree.ui.main.MainPresenter;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * amade-familytree bindings. Theme, language, directories and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class FamilyTreeModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.familytree.messages";

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(FamilyTreeModule.class, MESSAGES, locale);
    }

    @Provides
    @Singleton
    FamilyTreePreferences preferences(AppDirectories directories) {
        return new FamilyTreePreferences(directories);
    }

    /** The trees live in Documents, in a folder named in the user's language ("Stammbäume"). */
    static Path treeFolder(FamilyTreePreferences prefs, XdgDirectories xdg, Messages msg) {
        return prefs.folder().orElseGet(() -> xdg.documents().resolve(msg.get("folder.trees")));
    }

    // --8<-- [start:windows]
    @Provides
    @Singleton
    Windows windows(Messages msg, LocaleSettings locales, FamilyTreePreferences prefs, XdgDirectories xdg,
                    DesktopLauncher launcher, ExecutorService background) {
        String version = AmadeFamilyTreeApplication.version();
        TreeStore store = new TreeStore(treeFolder(prefs, xdg, msg), version);
        MainPresenter.Services services = new MainPresenter.Services(store, prefs, PhotoDownloader::system,
                new DesktopOpener(launcher), locales, version, () -> System.exit(0));
        return new Windows(msg, locales, background, services);
    }
    // --8<-- [end:windows]
}
