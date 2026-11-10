package eu.amadeos.apps.familytree.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How {@code other} is related to {@code to}: through the nearest common ancestor ({@code up} generations
 * above {@code to}, {@code down} generations above {@code other}), or, without one, through one marriage.
 *
 * <p>{@code up} and {@code down} decide the word: 0/1 is a parent, 1/0 a child, 1/1 a sibling, 2/1 an aunt or
 * uncle, 1/2 a niece or nephew, 2/2 a first cousin, 3/2 a first cousin once removed, and so on.
 *
 * @param path  the people from {@code to} to {@code other}, both included
 * @param via   for in-laws: the partner through whom ({@link Kind#PARTNERS_RELATIVE}) or the blood relative
 *              whose partner {@code other} is ({@link Kind#RELATIVES_PARTNER})
 * @param inner for in-laws: the blood relationship between the relative and {@code to} or {@code other}
 */
public record Relationship(Kind kind, int up, int down, boolean half, Sex sex, List<Person> path, Person via,
                           Relationship inner) {
    public enum Kind {
        SAME, BLOOD, PARTNER, PARTNERS_RELATIVE, RELATIVES_PARTNER, NONE
    }

    /** At most this many generations are searched upwards. */
    public static final int MAX_GENERATIONS = 40;

    public static Relationship between(Person to, Person other) {
        if (to.equals(other)) return new Relationship(Kind.SAME, 0, 0, false, other.sex(), List.of(to), null, null);
        Optional<Relationship> blood = blood(to, other);
        if (blood.isPresent()) return blood.get();
        if (to.partners().contains(other)) {
            return new Relationship(Kind.PARTNER, 0, 0, false, other.sex(), List.of(to, other), null, null);
        }
        // A blood relative of one of my partners ("my wife's sister").
        Relationship best = null;
        for (Person partner : to.partners()) {
            Optional<Relationship> r = blood(partner, other);
            if (r.isPresent() && (best == null || distance(r.get()) < distance(best.inner()))) {
                List<Person> path = new ArrayList<>();
                path.add(to);
                path.addAll(r.get().path());
                best = new Relationship(Kind.PARTNERS_RELATIVE, r.get().up(), r.get().down(), r.get().half(),
                        other.sex(), path, partner, r.get());
            }
        }
        // The partner of one of my blood relatives ("my sister's husband").
        for (Person partner : other.partners()) {
            Optional<Relationship> r = blood(to, partner);
            if (r.isPresent() && (best == null || distance(r.get()) < distance(best.inner()))) {
                List<Person> path = new ArrayList<>(r.get().path());
                path.add(other);
                best = new Relationship(Kind.RELATIVES_PARTNER, r.get().up(), r.get().down(), r.get().half(),
                        other.sex(), path, partner, r.get());
            }
        }
        if (best != null) return best;
        return new Relationship(Kind.NONE, 0, 0, false, other.sex(), List.of(), null, null);
    }

    private static int distance(Relationship r) {
        return r == null ? Integer.MAX_VALUE : r.up() + r.down();
    }

    /** Ancestors with their generation (0 is the person) and the child through whom each was reached. */
    private static Map<Person, Integer> ancestors(Person p, Map<Person, Person> childOf) {
        Map<Person, Integer> gen = new LinkedHashMap<>();
        Deque<Person> queue = new ArrayDeque<>();
        gen.put(p, 0);
        queue.add(p);
        while (!queue.isEmpty()) {
            Person c = queue.poll();
            int g = gen.get(c);
            if (g >= MAX_GENERATIONS) continue;
            for (Person parent : c.parents()) {
                if (!gen.containsKey(parent)) {
                    gen.put(parent, g + 1);
                    childOf.put(parent, c);
                    queue.add(parent);
                }
            }
        }
        return gen;
    }

    static Optional<Relationship> blood(Person to, Person other) {
        Map<Person, Person> viaTo = new LinkedHashMap<>();
        Map<Person, Person> viaOther = new LinkedHashMap<>();
        Map<Person, Integer> a = ancestors(to, viaTo);
        Map<Person, Integer> b = ancestors(other, viaOther);
        Person common = null;
        int best = Integer.MAX_VALUE;
        for (Map.Entry<Person, Integer> e : a.entrySet()) {
            Integer d = b.get(e.getKey());
            if (d == null) continue;
            int sum = e.getValue() + d;
            if (sum < best) {
                best = sum;
                common = e.getKey();
            }
        }
        if (common == null) return Optional.empty();
        int up = a.get(common);
        int down = b.get(common);
        List<Person> path = new ArrayList<>();
        for (Person p = to; p != null && !p.equals(common); p = parentOnPath(viaTo, p, common)) path.add(p);
        path.add(common);
        List<Person> back = new ArrayList<>();
        for (Person p = other; p != null && !p.equals(common); p = parentOnPath(viaOther, p, common)) back.add(p);
        Collections.reverse(back);
        path.addAll(back);
        boolean half = false;
        if (up >= 1 && down >= 1) {
            // Half relations share only one of the two ancestors of the top couple.
            Person childA = up == 1 ? to : path.get(up - 1);
            Person childB = down == 1 ? other : path.get(up + 1);
            List<Person> pa = childA.parents();
            List<Person> pb = childB.parents();
            half = pa.size() == 2 && pb.size() == 2 && !(pa.containsAll(pb) && pb.containsAll(pa));
        }
        return Optional.of(new Relationship(Kind.BLOOD, up, down, half, other.sex(), path, null, null));
    }

    /** The next person up from {@code p} on the way to {@code common}. */
    private static Person parentOnPath(Map<Person, Person> childOf, Person p, Person common) {
        // childOf maps parent -> child; walk from common down to find p's parent on the path.
        for (Person q = common; q != null; q = childOf.get(q)) {
            Person child = childOf.get(q);
            if (p.equals(child)) return q;
        }
        return null;
    }
}
