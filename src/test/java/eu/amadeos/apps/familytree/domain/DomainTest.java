package eu.amadeos.apps.familytree.domain;

import eu.amadeos.apps.familytree.Samples;
import eu.amadeos.apps.familytree.ui.main.ListFilter;
import eu.amadeos.apps.familytree.ui.main.PersonList;
import eu.amadeos.apps.familytree.ui.text.RelationshipText;
import eu.amadeos.commons.i18n.Messages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Locale;

import static eu.amadeos.apps.familytree.Samples.person;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTest {
    private final FamilyTree tree = Samples.brenner();

    // ---- names -----------------------------------------------------------------------------------------------

    @Test
    void setNamesOnAPlainPerson() {
        Person p = tree.newPerson("Ernst", "Brenner", Sex.MALE);
        p.setNames("Ernst Wilhelm", "Brenner", "Willi", "");
        assertEquals("Ernst Wilhelm", p.given());
        assertEquals("Brenner", p.surname());
        assertEquals("Willi", p.calledName());
        assertEquals("", p.birthSurname());
        assertEquals(1, p.names().size());
    }

    @Test
    void aBirthSurnameBecomesABirthNameAndGoesAgain() {
        Person p = tree.newPerson("Hilde", "Brenner", Sex.FEMALE);
        p.setNames("Hilde", "Brenner", "", "Vogt");
        assertEquals("Brenner", p.surname());
        assertEquals("Vogt", p.birthSurname());
        assertEquals("BIRTH", p.names().get(1).type());
        p.setNames("Hilde", "Brenner", "", "");
        assertEquals(1, p.names().size());
        assertEquals("", p.birthSurname());
    }

    @Test
    void aMarriedNameStaysWhereTheFileKeepsIt() {
        Person anna = person(tree, "I4");
        assertEquals("Brenner", anna.surname());
        assertEquals("Keller", anna.birthSurname());
        anna.setNames("Anna Maria", "Brenner", "", "Kellner");
        assertEquals("Anna Maria Brenner", anna.fullName());
        assertEquals("Kellner", anna.birthSurname());
        assertEquals("Brenner", anna.primaryName().orElseThrow().marriedSurname());
    }

    @Test
    void aPersonWithoutNamesGetsOne() {
        Person p = tree.newPerson("", "", Sex.UNKNOWN);
        p.node().removeAll("NAME");
        p.setNames("Lotte", "Brenner", "", "");
        assertEquals("Lotte Brenner", p.fullName());
    }

    // ---- dates -----------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"en", "de", "fr", "es", "it"})
    void datesShownCanBeTypedBack(String language) {
        DateText text = new DateText(Samples.messages(Locale.forLanguageTag(language)));
        for (String gedcom : List.of("14 MAR 1928", "MAR 1928", "1928", "ABT 1890", "BEF 1900", "AFT 1900",
                "BET 1850 AND 1855", "FROM 1950 TO 1990", "FROM 1950", "TO 1990", "EST 1800", "CAL 1800",
                "1 JAN 1900", "31 DEC 1999")) {
            GenDate d = GenDate.parse(gedcom);
            String shown = text.format(d);
            assertEquals(gedcom, DateText.parse(shown).gedcom(), language + ": " + shown);
        }
    }

    @Test
    void typedDatesInAnyLanguage() {
        assertEquals("ABT 1890", DateText.parse("um 1890").gedcom());
        assertEquals("14 MAR 1928", DateText.parse("14.03.1928").gedcom());
        assertEquals("BET 1850 AND 1855", DateText.parse("zwischen 1850 und 1855").gedcom());
        assertFalse(DateText.parse("Frühjahr 1890").isValid());
    }

    // ---- relationships ---------------------------------------------------------------------------------------

    private static String word(Messages msg, FamilyTree t, String to, String other) {
        return new RelationshipText(msg).of(Relationship.between(person(t, to), person(t, other)));
    }

    @Test
    void relationshipWordsInEnglish() {
        Messages en = Samples.english();
        assertEquals("grandfather", word(en, tree, "I12", "I1"));
        assertEquals("great-great-great-grandfather", word(en, tree, "I12", "I17"));
        assertEquals("great-great-grandmother", word(en, tree, "I12", "I8"));
        assertEquals("cousin", word(en, tree, "I12", "I14"));
        assertEquals("aunt", word(en, tree, "I12", "I10"));
        assertEquals("great-aunt", word(en, tree, "I12", "I16"));
        assertEquals("grandnephew", word(en, tree, "I16", "I12"));
        assertEquals("wife", word(en, tree, "I1", "I2"));
        assertEquals("father-in-law", word(en, tree, "I2", "I3"));
        assertEquals("granddaughter", word(en, tree, "I1", "I14"));
        assertEquals("sister", word(en, tree, "I1", "I16"));
        assertEquals("the same person", word(en, tree, "I1", "I1"));
        assertEquals("cousin once removed", new RelationshipText(en).blood(3, 2, false, Sex.MALE));
        assertEquals("second cousin twice removed", new RelationshipText(en).blood(5, 3, false, Sex.FEMALE));
    }

    @Test
    void relationshipWordsInGerman() {
        Messages de = Samples.messages(Locale.GERMAN);
        assertEquals("Urururgroßvater", word(de, tree, "I12", "I17"));
        assertEquals("Ururgroßmutter", word(de, tree, "I12", "I8"));
        assertEquals("Cousine", word(de, tree, "I12", "I14"));
        assertEquals("Großtante", word(de, tree, "I12", "I16"));
        assertEquals("Schwiegervater", word(de, tree, "I2", "I3"));
        assertEquals("Urgroßtante", new RelationshipText(de).blood(4, 1, false, Sex.FEMALE));
        assertEquals("Ururenkel", new RelationshipText(de).blood(0, 4, false, Sex.MALE));
    }

    @Test
    void notRelatedWithoutAConnection() {
        Person stranger = tree.newPerson("Otto", "Fremd", Sex.MALE);
        assertEquals(Relationship.Kind.NONE, Relationship.between(person(tree, "I1"), stranger).kind());
    }

    // ---- search, living and the list -------------------------------------------------------------------------

    @Test
    void soundAlikeNamesAreFound() {
        assertEquals(Search.cologne("Meyer"), Search.cologne("Maier"));
        assertTrue(Search.matches(person(tree, "I8"), "Meyer"));
        assertTrue(Search.matches(person(tree, "I1"), "ernst 1928"));
        assertFalse(Search.matches(person(tree, "I1"), "Keller"));
    }

    @Test
    void livingIsEstimatedFromTheDates() {
        Living living = new Living(2026);
        assertTrue(living.isLiving(person(tree, "I9")));
        assertFalse(living.isLiving(person(tree, "I1")));
        assertFalse(living.isLiving(person(tree, "I17")));
    }

    @Test
    void listFilters() {
        Person ernst = person(tree, "I1");
        Living living = new Living(2026);
        assertEquals(8, PersonList.of(tree, ListFilter.ANCESTORS, "", ernst, living).size());
        assertEquals(7, PersonList.of(tree, ListFilter.DESCENDANTS, "", ernst, living).size());
        assertTrue(PersonList.of(tree, ListFilter.UNREADABLE_DATES, "", ernst, living).contains(person(tree, "I8")));
        assertTrue(PersonList.of(tree, ListFilter.SURNAME, "", ernst, living).contains(person(tree, "I4")));
        assertFalse(PersonList.of(tree, ListFilter.WITHOUT_PARENTS, "", ernst, living).contains(ernst));
        List<Person> found = PersonList.of(tree, ListFilter.ALL, "Gisela", ernst, living);
        assertEquals(List.of(person(tree, "I10")), found);
        assertEquals(tree.count("INDI"), PersonList.of(tree, ListFilter.ALL, "", null, living).size());
    }

    // ---- building the tree -----------------------------------------------------------------------------------

    @Test
    void addingRelativesLinksThem() {
        Person lena = person(tree, "I15");
        Person partner = tree.addPartner(lena, Sex.MALE, "Jonas", "Roth");
        Person child = tree.addChild(lena, lena.families().getFirst(), Sex.FEMALE, "Mia", "Roth");
        assertEquals(List.of(partner), lena.partners());
        assertTrue(child.parents().contains(lena));
        assertTrue(child.parents().contains(partner));
        Person sister = tree.addSibling(lena, Sex.FEMALE, "Emma", "Brenner");
        assertTrue(lena.siblings().contains(sister));
        tree.removePerson(partner);
        assertFalse(child.parents().contains(partner));
        assertTrue(child.parents().contains(lena));
    }

    @Test
    void mergeRenumbersEverything() {
        FamilyTree other = Samples.brenner();
        int before = tree.count("INDI");
        int added = tree.merge(other);
        assertEquals(before, added);
        assertEquals(2 * before, tree.count("INDI"));
        long distinct = tree.persons().stream().map(Person::id).distinct().count();
        assertEquals(2 * before, distinct);
    }
}
