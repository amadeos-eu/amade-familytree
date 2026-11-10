package eu.amadeos.apps.familytree;

import eu.amadeos.apps.familytree.bootstrap.FamilyTreeModule;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.gedcom.GedcomReader;
import eu.amadeos.commons.i18n.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** The invented Brenner family of the test resources. */
public final class Samples {
    public static final String BRENNER = "/gedcom/brenner-5.5.1.ged";

    private Samples() {
    }

    public static byte[] bytes(String resource) {
        try (InputStream in = Samples.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing " + resource);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String text(String resource) {
        return new String(bytes(resource), StandardCharsets.UTF_8);
    }

    public static FamilyTree brenner() {
        try {
            return FamilyTree.of(GedcomReader.read(bytes(BRENNER))).tree();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static FamilyTree gedcom(String text) {
        try {
            return FamilyTree.of(GedcomReader.read(text.getBytes(StandardCharsets.UTF_8))).tree();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Messages messages(Locale locale) {
        return Messages.load(FamilyTreeModule.class, FamilyTreeModule.MESSAGES, locale);
    }

    public static Messages english() {
        return messages(Locale.ENGLISH);
    }

    public static Person person(FamilyTree tree, String id) {
        return tree.person(id).orElseThrow();
    }
}
