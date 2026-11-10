package eu.amadeos.apps.familytree;

import eu.amadeos.apps.familytree.domain.FactType;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.apps.familytree.repository.FamilyTreePreferences;
import eu.amadeos.apps.familytree.service.DesktopOpener;
import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.apps.familytree.service.TreeStore;
import eu.amadeos.apps.familytree.ui.main.Connection;
import eu.amadeos.apps.familytree.ui.main.ListFilter;
import eu.amadeos.apps.familytree.ui.main.MainMenuBar;
import eu.amadeos.apps.familytree.ui.main.MainPresenter;
import eu.amadeos.apps.familytree.ui.main.Relative;
import eu.amadeos.apps.familytree.ui.main.ViewKind;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** All five languages have every text, the patterns are valid, and the menus have unique mnemonics. */
class TranslationTest {
    static final List<String> LANGUAGES = List.of("", "_de", "_fr", "_es", "_it");

    @TempDir
    Path dir;

    static Properties load(String suffix) throws IOException {
        Properties p = new Properties();
        try (InputStream in = TranslationTest.class.getResourceAsStream(
                "/eu/amadeos/apps/familytree/messages" + suffix + ".properties")) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void everyLanguageHasTheSameKeys() throws IOException {
        Set<String> english = load("").stringPropertyNames();
        for (String suffix : LANGUAGES) {
            Set<String> keys = load(suffix).stringPropertyNames();
            Set<String> missing = new TreeSet<>(english);
            missing.removeAll(keys);
            Set<String> extra = new TreeSet<>(keys);
            extra.removeAll(english);
            assertEquals(Set.of(), missing, "missing in messages" + suffix);
            assertEquals(Set.of(), extra, "unknown in messages" + suffix);
        }
    }

    @Test
    void everyPatternIsValid() throws IOException {
        for (String suffix : LANGUAGES) {
            Properties p = load(suffix);
            for (String key : p.stringPropertyNames()) {
                String v = p.getProperty(key);
                if (v.contains("{")) new MessageFormat(v.replace("'", "''"), Locale.ROOT).format(new Object[]{2, 3, 4, 5});
            }
        }
    }

    /** Keys written in the code as a whole ("editor.given"); keys put together are checked below. */
    @Test
    void everyKeyTheCodeUsesExists() throws IOException {
        Set<String> english = load("").stringPropertyNames();
        Pattern literal = Pattern.compile("\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"");
        Set<String> prefixes = new HashSet<>();
        for (String k : english) prefixes.add(k.substring(0, k.indexOf('.')));
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = literal.matcher(Files.readString(f));
                while (m.find()) {
                    String key = m.group(1);
                    if (!prefixes.contains(key.substring(0, key.indexOf('.')))) continue;
                    if (key.startsWith("eu.") || key.endsWith(".properties")) continue;
                    // Relationship words are bases with ".m", ".f" and ".u" added for the sex.
                    if (english.contains(key + ".m")) continue;
                    if (!english.contains(key)) missing.add(key + " (" + f.getFileName() + ")");
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void keysPutTogetherExist() throws IOException {
        Set<String> en = load("").stringPropertyNames();
        List<String> keys = new ArrayList<>();
        for (FactType t : FactType.values()) keys.add(t.textKey());
        for (Sex s : Sex.values()) keys.add(s.textKey());
        for (ViewKind v : ViewKind.values()) keys.add(v.textKey());
        for (Relative r : Relative.values()) {
            keys.add(r.textKey());
            keys.add(r.textKey() + ".undo");
        }
        for (Connection c : Connection.values()) {
            keys.add(c.textKey());
            keys.add("connect.button." + c.name().toLowerCase(Locale.ROOT));
        }
        for (ListFilter f : ListFilter.values()) keys.add(f.textKey());
        for (GedcomExport.Format f : GedcomExport.Format.values()) keys.add(f.textKey());
        for (GedcomExport.LivingPeople l : GedcomExport.LivingPeople.values()) keys.add(l.textKey());
        for (String base : List.of("partner", "parent", "grandparent", "great-grandparent", "ancestor.many", "child",
                "grandchild", "great-grandchild", "descendant.many", "sibling", "half-sibling", "aunt", "great-aunt",
                "aunt.many", "niece", "grand-niece", "niece.many", "cousin", "parent-in-law", "sibling-in-law",
                "stepchild", "child-in-law", "step-parent")) {
            for (String s : List.of(".m", ".f", ".u")) keys.add("rel." + base + s);
        }
        for (int i = 1; i <= 3; i++) {
            keys.add("row.up." + i);
            keys.add("row.down." + i);
        }
        keys.add("menu.edit.undo.what");
        keys.add("menu.edit.redo.what");
        Set<String> missing = new TreeSet<>(keys);
        missing.removeAll(en);
        assertEquals(Set.of(), missing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "fr", "es", "it"})
    void menusHaveUniqueMnemonics(String language) throws Exception {
        Messages msg = Samples.messages(Locale.forLanguageTag(language));
        MainPresenter presenter = new MainPresenter(new eu.amadeos.apps.familytree.ui.main.FakeView(), msg,
                new MainPresenter.Services(new TreeStore(dir, "test"), new FamilyTreePreferences(dir.resolve("p")),
                        () -> null, new DesktopOpener(null), null, "test", () -> {
                }));
        List<String> problems = new ArrayList<>();
        OnEdt.run(() -> {
            MainMenuBar bar = new MainMenuBar(presenter, msg, null, new MainMenuBar.WindowActions() {
                @Override
                public void find() {
                }

                @Override
                public void zoom(int direction) {
                }
            });
            Set<Integer> top = new HashSet<>();
            for (int i = 0; i < bar.getMenuCount(); i++) {
                JMenu menu = bar.getMenu(i);
                if (!top.add(menu.getMnemonic())) problems.add("menu bar: " + menu.getText());
                Set<Integer> seen = new HashSet<>();
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item == null) continue;
                    if (item.getMnemonic() == 0) problems.add(menu.getText() + ": no mnemonic in " + item.getText());
                    else if (!seen.add(item.getMnemonic())) problems.add(menu.getText() + ": " + item.getText());
                }
            }
        });
        assertTrue(problems.isEmpty(), language + " " + problems);
    }
}
