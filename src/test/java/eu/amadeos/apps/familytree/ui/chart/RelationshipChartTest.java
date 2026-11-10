package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.Samples;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.ui.text.RelationshipText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static eu.amadeos.apps.familytree.Samples.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The way from one person to another as a chart (the Relationship view). */
class RelationshipChartTest {
    private final FamilyTree tree = Samples.brenner();
    private final RelationshipText words = new RelationshipText(Samples.english());

    private Chart chart(String from, String to) {
        Person a = person(tree, from);
        return TreeLayout.relationship(a, person(tree, to), p -> words.of(Relationship.between(a, p)), "title");
    }

    private static Chart.Box box(Chart c, Person p) {
        return c.boxOf(p).orElseThrow(() -> new AssertionError("no box for " + p.fullName()));
    }

    private List<String> captions(Chart c) {
        return c.labels().stream().map(Chart.Label::text).toList();
    }

    @Test
    void aGrandsonIsStraightBelow() {
        Chart c = chart("I1", "I12");
        assertEquals(3, c.boxes().size());
        Chart.Box ernst = box(c, person(tree, "I1"));
        Chart.Box peter = box(c, person(tree, "I9"));
        Chart.Box markus = box(c, person(tree, "I12"));
        assertTrue(ernst.root());
        assertEquals(ernst.x(), markus.x(), 0.1);
        assertTrue(ernst.y() < peter.y() && peter.y() < markus.y());
        assertEquals(List.of("son", "grandson"), captions(c));
    }

    @Test
    void anAncestorIsAtTheTop() {
        Chart c = chart("I1", "I17");
        Chart.Box georg = box(c, person(tree, "I17"));
        Chart.Box ernst = box(c, person(tree, "I1"));
        assertTrue(georg.y() < ernst.y());
        assertTrue(captions(c).contains("great-grandfather"));
    }

    @Test
    void cousinsMeetAtTheCommonCouple() {
        Chart c = chart("I12", "I14");
        // Ernst and Hildegard at the top, Peter and Markus on the left, Gisela and Julia on the right.
        assertEquals(6, c.boxes().size());
        Chart.Box ernst = box(c, person(tree, "I1"));
        Chart.Box hildegard = box(c, person(tree, "I2"));
        Chart.Box markus = box(c, person(tree, "I12"));
        Chart.Box julia = box(c, person(tree, "I14"));
        assertEquals(ernst.y(), hildegard.y(), 0.1);
        assertEquals(markus.y(), julia.y(), 0.1);
        assertTrue(markus.x() < julia.x());
        assertTrue(ernst.y() < markus.y());
        assertTrue(captions(c).contains("cousin"));
        for (Chart.Box a : c.boxes()) {
            for (Chart.Box b : c.boxes()) {
                if (a != b) assertFalse(a.bounds().intersects(b.bounds()), a.person() + " / " + b.person());
            }
        }
    }

    @Test
    void inLawsStandNextToTheirPartner() {
        // Ingrid is Peter's wife: Ernst, Peter, and Ingrid next to Peter.
        Chart c = chart("I1", "I19");
        Chart.Box peter = box(c, person(tree, "I9"));
        Chart.Box ingrid = box(c, person(tree, "I19"));
        assertEquals(3, c.boxes().size());
        assertEquals(peter.y(), ingrid.y(), 0.1);
        assertTrue(ingrid.x() > peter.x());
        assertTrue(captions(c).contains("daughter-in-law"));

        // From Petra (Thomas's wife) to Ernst: Petra next to Thomas, Ernst above.
        Chart d = chart("I21", "I1");
        Chart.Box petra = box(d, person(tree, "I21"));
        Chart.Box thomas = box(d, person(tree, "I11"));
        assertEquals(petra.y(), thomas.y(), 0.1);
        assertTrue(petra.x() < thomas.x());
        assertTrue(box(d, person(tree, "I1")).y() < thomas.y());
    }

    @Test
    void partnersAndTheSamePerson() {
        assertEquals(2, chart("I1", "I2").boxes().size());
        Chart same = chart("I1", "I1");
        assertEquals(1, same.boxes().size());
        assertTrue(same.labels().isEmpty());
    }
}
