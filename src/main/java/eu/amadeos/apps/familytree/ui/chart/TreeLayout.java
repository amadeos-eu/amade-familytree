package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.DateText;
import eu.amadeos.apps.familytree.domain.Family;
import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Lays out the box charts. Every chart is a tidy tree: each subtree gets the width it needs, parents are
 * centred over their children (descendants) or children under their parents (ancestors), so nothing overlaps.
 *
 * <ul>
 * <li>{@link #hourglass}: ancestors above, the person with partners in the middle, descendants below;</li>
 * <li>{@link #ancestors}: the classic pedigree from left to right;</li>
 * <li>{@link #descendants}: from the person downwards, with all partners and their children;</li>
 * <li>{@link #relationship}: the way from one person to another through their nearest common ancestor.</li>
 * </ul>
 * A person who appears twice (cousins who married) is drawn again but not followed further, so loops end.
 */
public final class TreeLayout {
    static final double COUPLE_GAP = 44;
    static final double SIBLING_GAP = 18;
    static final double FAMILY_GAP = 30;
    static final double ROW_GAP = 64;
    static final double COLUMN_GAP = 56;
    static final double PEDIGREE_GAP = 12;
    public static final double MARGIN = 24;

    private TreeLayout() {
    }

    // ---- descendants -------------------------------------------------------------------------------------------

    private static final class Down {
        final Person person;
        final List<Family> families = new ArrayList<>();
        final List<Person> partners = new ArrayList<>();
        final List<List<Down>> kids = new ArrayList<>();
        boolean more;
        double width;

        Down(Person person) {
            this.person = person;
        }

        int partnerBoxes() {
            int n = 0;
            for (Person p : partners) if (p != null) n++;
            return n;
        }

        double own() {
            int n = partnerBoxes();
            return Chart.BOX_W * (1 + n) + COUPLE_GAP * n;
        }

        double kidsWidth() {
            double w = 0;
            boolean firstGroup = true;
            for (List<Down> group : kids) {
                if (group.isEmpty()) continue;
                if (!firstGroup) w += FAMILY_GAP;
                firstGroup = false;
                for (int i = 0; i < group.size(); i++) w += group.get(i).width + (i > 0 ? SIBLING_GAP : 0);
            }
            return w;
        }
    }

    private static Down down(Person p, int depth, int max, Set<Person> seen) {
        Down d = new Down(p);
        boolean expand = seen.add(p);
        for (Family f : p.families()) {
            d.families.add(f);
            d.partners.add(f.partnerOf(p).orElse(null));
            List<Down> group = new ArrayList<>();
            if (depth < max && expand) for (Person c : f.children()) group.add(down(c, depth + 1, max, seen));
            d.kids.add(group);
        }
        d.more = (depth >= max || !expand) && !p.children().isEmpty();
        measure(d);
        return d;
    }

    private static void measure(Down d) {
        d.width = Math.max(d.own(), d.kidsWidth());
    }

    /** Places a descendants subtree with its left edge at {@code left} and its top row at {@code y}. */
    private static void place(Down d, double left, double y, Chart chart, Person root, boolean rootAbove) {
        double own = d.own();
        double ownLeft = left + (d.width - own) / 2;
        double kidsLeft = left + (d.width - d.kidsWidth()) / 2;
        boolean isRoot = d.person.equals(root);
        chart.box(new Chart.Box(d.person, ownLeft, y, isRoot, isRoot && rootAbove, d.more));
        double personRight = ownLeft + Chart.BOX_W;
        double personCx = ownLeft + Chart.BOX_W / 2;
        double mid = y + Chart.BOX_H / 2;
        double childY = y + Chart.BOX_H + ROW_GAP;
        double busY = childY - ROW_GAP / 2;
        double cursor = kidsLeft;
        int partnerIndex = 0;
        boolean firstGroup = true;
        for (int f = 0; f < d.families.size(); f++) {
            Person partner = d.partners.get(f);
            double anchorX = personCx;
            double anchorY = y + Chart.BOX_H;
            if (partner != null) {
                double pl = ownLeft + (partnerIndex + 1) * (Chart.BOX_W + COUPLE_GAP);
                chart.box(new Chart.Box(partner, pl, y, partner.equals(root), false, false));
                chart.line(personRight, mid, pl, mid);
                double gapLeft = pl - COUPLE_GAP;
                anchorX = gapLeft + COUPLE_GAP / 2;
                anchorY = mid;
                marriageLabel(chart, d.families.get(f), anchorX, mid - 6);
                partnerIndex++;
            }
            List<Down> group = d.kids.get(f);
            if (group.isEmpty()) continue;
            if (!firstGroup) cursor += FAMILY_GAP;
            firstGroup = false;
            double minX = anchorX, maxX = anchorX;
            List<Double> centres = new ArrayList<>();
            for (int i = 0; i < group.size(); i++) {
                Down kid = group.get(i);
                if (i > 0) cursor += SIBLING_GAP;
                place(kid, cursor, childY, chart, root, false);
                double kidOwnLeft = cursor + (kid.width - kid.own()) / 2;
                double c = kidOwnLeft + Chart.BOX_W / 2;
                centres.add(c);
                minX = Math.min(minX, c);
                maxX = Math.max(maxX, c);
                cursor += kid.width;
            }
            chart.line(anchorX, anchorY, anchorX, busY);
            chart.line(minX, busY, maxX, busY);
            for (double c : centres) chart.line(c, busY, c, childY);
        }
    }

    private static void marriageLabel(Chart chart, Family f, double x, double y) {
        Optional<Fact> m = f.marriage();
        String year = m.map(fact -> DateText.year(fact.date())).orElse("");
        chart.label(year.isEmpty() ? "∞" : "∞ " + year, x, y);
    }

    public static Chart descendants(Person root, int generations) {
        Chart chart = new Chart();
        Down d = down(root, 0, generations, new HashSet<>());
        place(d, 0, 0, chart, root, !root.parents().isEmpty());
        return chart.normalized(MARGIN);
    }

    // ---- ancestors -------------------------------------------------------------------------------------------

    private static final class Up {
        final Person person;
        Up father;
        Up mother;
        boolean more;
        double size;
        double centre;

        Up(Person person) {
            this.person = person;
        }
    }

    private static Up up(Person p, int depth, int max, Set<Person> seen) {
        Up u = new Up(p);
        boolean expand = seen.add(p);
        if (depth < max && expand) {
            p.father().ifPresent(f -> u.father = up(f, depth + 1, max, seen));
            p.mother().ifPresent(m -> u.mother = up(m, depth + 1, max, seen));
        } else {
            u.more = !p.parents().isEmpty();
        }
        return u;
    }

    /** Sizes along the spread axis: {@code breadth} per box, {@code gap} between father and mother. */
    private static void measure(Up u, double breadth, double gap) {
        double parents = 0;
        if (u.father != null) {
            measure(u.father, breadth, gap);
            parents += u.father.size;
        }
        if (u.mother != null) {
            measure(u.mother, breadth, gap);
            parents += u.mother.size;
        }
        if (u.father != null && u.mother != null) parents += gap;
        u.size = Math.max(breadth, parents);
    }

    /** Sets {@code centre} along the spread axis for the subtree starting at {@code start}. */
    private static void spread(Up u, double start, double breadth, double gap) {
        double parents = (u.father == null ? 0 : u.father.size) + (u.mother == null ? 0 : u.mother.size)
                + (u.father != null && u.mother != null ? gap : 0);
        double s = start + (u.size - Math.max(parents, 0)) / 2;
        if (u.father != null) {
            spread(u.father, s, breadth, gap);
            s += u.father.size + gap;
        }
        if (u.mother != null) spread(u.mother, s, breadth, gap);
        if (u.father != null && u.mother != null) u.centre = (u.father.centre + u.mother.centre) / 2;
        else if (u.father != null) u.centre = u.father.centre;
        else if (u.mother != null) u.centre = u.mother.centre;
        else u.centre = start + u.size / 2;
    }

    /** Draws the ancestors above a person, upwards; the person's own box is drawn by the caller. */
    private static void drawUp(Up u, double y, Chart chart) {
        double parentY = y - ROW_GAP - Chart.BOX_H;
        List<Up> parents = new ArrayList<>();
        if (u.father != null) parents.add(u.father);
        if (u.mother != null) parents.add(u.mother);
        if (parents.isEmpty()) return;
        for (Up p : parents) {
            chart.box(new Chart.Box(p.person, p.centre - Chart.BOX_W / 2, parentY, false, p.more, false));
            drawUp(p, parentY, chart);
        }
        double fromX;
        double fromY;
        if (parents.size() == 2) {
            double a = u.father.centre + Chart.BOX_W / 2;
            double b = u.mother.centre - Chart.BOX_W / 2;
            double mid = parentY + Chart.BOX_H / 2;
            chart.line(a, mid, b, mid);
            fromX = (a + b) / 2;
            fromY = mid;
            u.person.parentFamilies().stream().findFirst().ifPresent(f -> marriageLabel(chart, f, (a + b) / 2, mid - 6));
        } else {
            fromX = parents.getFirst().centre;
            fromY = parentY + Chart.BOX_H;
        }
        double busY = y - ROW_GAP / 2 + 8;
        chart.line(fromX, fromY, fromX, busY, u.centre, busY, u.centre, y);
    }

    /** Draws a pedigree from left to right: the person in column 0, parents in column 1, ... */
    private static void drawRight(Up u, double x, Chart chart) {
        double parentX = x + Chart.BOX_W + COLUMN_GAP;
        double cy = u.centre;
        List<Up> parents = new ArrayList<>();
        if (u.father != null) parents.add(u.father);
        if (u.mother != null) parents.add(u.mother);
        if (parents.isEmpty()) return;
        double midX = x + Chart.BOX_W + COLUMN_GAP / 2;
        double minY = cy, maxY = cy;
        for (Up p : parents) {
            chart.box(new Chart.Box(p.person, parentX, p.centre - Chart.BOX_H / 2, false, p.more, false));
            chart.line(midX, p.centre, parentX, p.centre);
            minY = Math.min(minY, p.centre);
            maxY = Math.max(maxY, p.centre);
            drawRight(p, parentX, chart);
        }
        chart.line(x + Chart.BOX_W, cy, midX, cy);
        chart.line(midX, minY, midX, maxY);
    }

    public static Chart ancestors(Person root, int generations) {
        Chart chart = new Chart();
        Up u = up(root, 0, generations, new HashSet<>());
        measure(u, Chart.BOX_H, PEDIGREE_GAP);
        spread(u, 0, Chart.BOX_H, PEDIGREE_GAP);
        chart.box(new Chart.Box(root, 0, u.centre - Chart.BOX_H / 2, true, false, !root.children().isEmpty()));
        drawRight(u, 0, chart);
        return chart.normalized(MARGIN);
    }

    // ---- hourglass -------------------------------------------------------------------------------------------

    /**
     * Ancestors above, the person and partners in the middle, descendants below.
     *
     * @param rowTitle the title of a row by generation (-1 parents, 1 children, 0 none), or null for none
     */
    public static Chart hourglass(Person root, int up, int down, IntFunction<String> rowTitle) {
        Chart chart = new Chart();
        Down d = down(root, 0, down, new HashSet<>());
        place(d, 0, 0, chart, root, false);
        double rootCentre = chart.boxOf(root).map(b -> b.x() + Chart.BOX_W / 2).orElse(Chart.BOX_W / 2);
        Up u = up(root, 0, up, new HashSet<>());
        measure(u, Chart.BOX_W, COUPLE_GAP);
        spread(u, 0, Chart.BOX_W, COUPLE_GAP);
        double shift = rootCentre - u.centre;
        shift(u, shift);
        drawUp(u, 0, chart);
        // The root box: mark if more ancestors exist than shown.
        chart.boxes().replaceAll(b -> b.person().equals(root) && b.root()
                ? new Chart.Box(b.person(), b.x(), b.y(), true, up == 0 && !root.parents().isEmpty(), b.moreBelow())
                : b);
        if (rowTitle != null) {
            double minX = chart.bounds().getMinX();
            int top = depthUp(u);
            int bottom = depthDown(d);
            for (int g = -top; g <= bottom; g++) {
                String t = rowTitle.apply(g);
                if (t == null || t.isEmpty()) continue;
                chart.title(t, minX, g * (Chart.BOX_H + ROW_GAP) - 6);
            }
        }
        return chart.normalized(MARGIN);
    }

    private static void shift(Up u, double dx) {
        u.centre += dx;
        if (u.father != null) shift(u.father, dx);
        if (u.mother != null) shift(u.mother, dx);
    }

    private static int depthUp(Up u) {
        int d = 0;
        if (u.father != null) d = Math.max(d, 1 + depthUp(u.father));
        if (u.mother != null) d = Math.max(d, 1 + depthUp(u.mother));
        return d;
    }

    private static int depthDown(Down d) {
        int max = 0;
        for (List<Down> g : d.kids) for (Down k : g) max = Math.max(max, 1 + depthDown(k));
        return max;
    }

    // ---- relationship ------------------------------------------------------------------------------------------

    /**
     * The way from {@code from} to {@code to}, like a route: the nearest common ancestor (with the partner who is
     * the other common ancestor) at the top, the line down to {@code from} on the left and the line down to
     * {@code to} on the right. A partner on the way (in-laws) stands next to the person they are married to.
     * Each box but the first is captioned with what the person is to {@code from} ({@code caption}); {@code title}
     * (may be null) stands above the chart.
     */
    public static Chart relationship(Person from, Person to, Function<Person, String> caption, String title) {
        Chart chart = new Chart();
        Relationship r = Relationship.between(from, to);
        Map<Person, double[]> at = new LinkedHashMap<>();
        switch (r.kind()) {
            case SAME -> at.put(from, new double[]{0, 0});
            case NONE -> {
                at.put(from, new double[]{0, 0});
                at.put(to, new double[]{Chart.BOX_W + 2 * COLUMN_GAP, 0});
            }
            case PARTNER -> {
                at.put(from, new double[]{0, 0});
                at.put(to, new double[]{Chart.BOX_W + COUPLE_GAP, 0});
                couple(chart, 0, 0);
            }
            case BLOOD -> blood(chart, r.path(), r.up(), r.down(), r.half(), at);
            case PARTNERS_RELATIVE -> {
                // from - partner, then the partner's blood relative.
                Relationship in = r.inner();
                blood(chart, in.path(), in.up(), in.down(), in.half(), at);
                double[] via = at.get(r.via());
                double x = via[0] - Chart.BOX_W - COUPLE_GAP;
                at.put(from, new double[]{x, via[1]});
                couple(chart, x, via[1]);
            }
            case RELATIVES_PARTNER -> {
                // A blood relative of from, then that relative's partner.
                Relationship in = r.inner();
                blood(chart, in.path(), in.up(), in.down(), in.half(), at);
                double[] via = at.get(r.via());
                at.put(to, new double[]{via[0] + Chart.BOX_W + COUPLE_GAP, via[1]});
                couple(chart, via[0], via[1]);
            }
        }
        if (title != null && !title.isEmpty()) {
            double left = at.values().stream().mapToDouble(v -> v[0]).min().orElse(0);
            chart.title(title, left, -34);
        }
        for (Map.Entry<Person, double[]> e : at.entrySet()) {
            Person p = e.getKey();
            double x = e.getValue()[0], y = e.getValue()[1];
            chart.box(new Chart.Box(p, x, y, p.equals(from), false, false));
            if (!p.equals(from)) {
                String text = caption.apply(p);
                if (text != null && !text.isEmpty()) chart.label(text, x + Chart.BOX_W / 2, y - 8);
            }
        }
        return chart.normalized(MARGIN);
    }

    /** The line between a person at (x, y) and the partner to the right. */
    private static void couple(Chart chart, double x, double y) {
        double mid = y + Chart.BOX_H / 2;
        chart.line(x + Chart.BOX_W, mid, x + Chart.BOX_W + COUPLE_GAP, mid);
    }

    /** Places the blood line {@code path} (path[up] is the common ancestor) and connects it. */
    private static void blood(Chart chart, List<Person> path, int up, int down, boolean half,
                              Map<Person, double[]> at) {
        double step = Chart.BOX_H + ROW_GAP;
        double centre = Chart.BOX_W / 2;
        if (up == 0 || down == 0) {
            // A straight line of ancestors and descendants: the oldest at the top.
            List<Person> column = new ArrayList<>(path);
            if (down == 0) java.util.Collections.reverse(column);
            for (int i = 0; i < column.size(); i++) {
                at.put(column.get(i), new double[]{0, i * step});
                if (i > 0) chart.line(centre, (i - 1) * step + Chart.BOX_H, centre, i * step);
            }
            return;
        }
        Person common = path.get(up);
        double left = 0;
        double right = Chart.BOX_W + COLUMN_GAP;
        double mid = (left + right + Chart.BOX_W) / 2;
        Person other = half ? null : coParent(common, path.get(up - 1), path.get(up + 1));
        double anchorY;
        if (other == null) {
            at.put(common, new double[]{mid - centre, 0});
            anchorY = Chart.BOX_H;
        } else {
            double x = mid - Chart.BOX_W - COUPLE_GAP / 2;
            at.put(common, new double[]{x, 0});
            at.put(other, new double[]{mid + COUPLE_GAP / 2, 0});
            couple(chart, x, 0);
            anchorY = Chart.BOX_H / 2;
        }
        double busY = step - ROW_GAP / 2 + 6;
        chart.line(mid, anchorY, mid, busY);
        chart.line(left + centre, busY, right + centre, busY);
        for (int side = 0; side < 2; side++) {
            double x = side == 0 ? left : right;
            int n = side == 0 ? up : down;
            chart.line(x + centre, busY, x + centre, step);
            for (int k = 1; k <= n; k++) {
                Person p = side == 0 ? path.get(up - k) : path.get(up + k);
                at.put(p, new double[]{x, k * step});
                if (k > 1) chart.line(x + centre, (k - 1) * step + Chart.BOX_H, x + centre, k * step);
            }
        }
    }

    /** The other parent that two children of {@code common} share with it, if they have one in common. */
    private static Person coParent(Person common, Person a, Person b) {
        for (Person p : a.parents()) if (!p.equals(common) && b.parents().contains(p)) return p;
        return null;
    }
}
