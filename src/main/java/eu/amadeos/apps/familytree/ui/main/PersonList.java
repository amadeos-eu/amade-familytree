package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Search;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** The people the list on the left shows: a filter, then the search words, sorted by surname. */
public final class PersonList {
    private PersonList() {
    }

    /**
     * @param about the person the filters "same surname", "ancestors" and "descendants" are about (the person in
     *              the middle of the chart)
     */
    public static List<Person> of(FamilyTree tree, ListFilter filter, String query, Person about, Living living) {
        if (tree == null) return List.of();
        Predicate<Person> keep = switch (filter) {
            case ALL -> p -> true;
            case SURNAME -> {
                String surname = about == null ? "" : Search.fold(about.surname());
                yield p -> !surname.isEmpty() && (Search.fold(p.surname()).equals(surname)
                        || Search.fold(p.birthSurname()).equals(surname));
            }
            case ANCESTORS -> {
                Set<Person> set = about == null ? Set.of() : ancestors(about);
                yield set::contains;
            }
            case DESCENDANTS -> {
                Set<Person> set = about == null ? Set.of() : descendants(about);
                yield set::contains;
            }
            case WITHOUT_PARENTS -> p -> p.parents().isEmpty();
            case LIVING -> living::isLiving;
            case UNREADABLE_DATES -> PersonList::hasUnreadableDate;
        };
        String q = query == null ? "" : query.strip();
        return tree.persons().stream().filter(keep).filter(p -> q.isEmpty() || Search.matches(p, q))
                .sorted(FamilyTree.BY_NAME).toList();
    }

    static boolean hasUnreadableDate(Person p) {
        for (Fact f : p.facts()) if (!f.date().isEmpty() && !f.date().isValid()) return true;
        return false;
    }

    static Set<Person> ancestors(Person p) {
        Set<Person> seen = new HashSet<>();
        Deque<Person> todo = new ArrayDeque<>(p.parents());
        while (!todo.isEmpty()) {
            Person q = todo.pop();
            if (seen.add(q)) todo.addAll(q.parents());
        }
        return seen;
    }

    static Set<Person> descendants(Person p) {
        Set<Person> seen = new HashSet<>();
        Deque<Person> todo = new ArrayDeque<>(p.children());
        while (!todo.isEmpty()) {
            Person q = todo.pop();
            if (seen.add(q)) todo.addAll(q.children());
        }
        return seen;
    }
}
