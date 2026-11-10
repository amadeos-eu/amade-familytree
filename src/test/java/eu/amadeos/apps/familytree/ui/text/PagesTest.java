package eu.amadeos.apps.familytree.ui.text;

import eu.amadeos.apps.familytree.Samples;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.ui.chart.ChartColors;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static eu.amadeos.apps.familytree.Samples.person;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagesTest {
    private final FamilyTree tree = Samples.brenner();
    private final ChartColors colors = ChartColors.current();

    @Test
    void familySheetShowsCoupleMarriageAndChildren() {
        String html = new Pages(new Texts(Samples.english())).familySheet(person(tree, "I1"), 0, colors);
        assertTrue(html.contains("Ernst Wilhelm Brenner"));
        assertTrue(html.contains("Hildegard"));
        assertTrue(html.contains("3 children"));
        assertTrue(html.contains("person:I9"));
    }

    @Test
    void timelineIsInOrderAndInGerman() {
        String html = new Pages(new Texts(Samples.messages(Locale.GERMAN))).timeline(person(tree, "I1"), colors);
        int birth = html.indexOf("14. März 1928");
        int death = html.indexOf("3. Februar 2009");
        assertTrue(birth > 0 && death > birth, html);
        assertTrue(html.contains("Geburt"));
    }

    @Test
    void summaryNamesTheFamily() {
        Person anna = person(tree, "I4");
        String html = new Pages(new Texts(Samples.english())).summary(anna, colors);
        assertTrue(html.contains("Parents"));
        assertTrue(html.contains("Karl"));
        assertTrue(html.contains("Ernst"));
    }

    @Test
    void agesAndBirthNames() {
        Texts t = new Texts(Samples.english());
        assertTrue(t.age(person(tree, "I1")).equals("aged 80"));
        assertTrue(t.nee(person(tree, "I4").birthSurname()).equals("née Keller"));
    }
}
