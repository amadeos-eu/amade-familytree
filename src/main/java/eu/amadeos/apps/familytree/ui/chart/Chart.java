package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.Person;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * A laid-out box chart: boxes for people, connecting lines, small labels (marriage years) and row titles, in
 * chart units (1 unit = 1 pixel at 100 %). Made by {@link TreeLayout}, drawn by {@link ChartPanel}.
 */
public final class Chart {
    /** Size of one person's box. */
    public static final double BOX_W = 184;
    public static final double BOX_H = 58;

    /**
     * @param root      the person the chart is about (drawn with the accent colour)
     * @param moreAbove the person has parents the chart does not show
     * @param moreBelow the person has children the chart does not show
     */
    public record Box(Person person, double x, double y, boolean root, boolean moreAbove, boolean moreBelow) {
        public Rectangle2D bounds() {
            return new Rectangle2D.Double(x, y, BOX_W, BOX_H);
        }

        public Box moved(double dx, double dy) {
            return new Box(person, x + dx, y + dy, root, moreAbove, moreBelow);
        }
    }

    public record Line(List<Point2D> points) {
        public Line moved(double dx, double dy) {
            List<Point2D> p = new ArrayList<>(points.size());
            for (Point2D q : points) p.add(new Point2D.Double(q.getX() + dx, q.getY() + dy));
            return new Line(p);
        }
    }

    /** A short text centred at a point, such as "∞ 1953". */
    public record Label(String text, double x, double y) {
        public Label moved(double dx, double dy) {
            return new Label(text, x + dx, y + dy);
        }
    }

    /** A title for a row or column of the chart ("PARENTS"), at its top left. */
    public record Title(String text, double x, double y) {
        public Title moved(double dx, double dy) {
            return new Title(text, x + dx, y + dy);
        }
    }

    private final List<Box> boxes = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Title> titles = new ArrayList<>();

    public List<Box> boxes() {
        return boxes;
    }

    public List<Line> lines() {
        return lines;
    }

    public List<Label> labels() {
        return labels;
    }

    public List<Title> titles() {
        return titles;
    }

    void box(Box b) {
        boxes.add(b);
    }

    void line(double... xy) {
        List<Point2D> p = new ArrayList<>();
        for (int i = 0; i + 1 < xy.length; i += 2) p.add(new Point2D.Double(xy[i], xy[i + 1]));
        lines.add(new Line(p));
    }

    void label(String text, double x, double y) {
        labels.add(new Label(text, x, y));
    }

    void title(String text, double x, double y) {
        titles.add(new Title(text, x, y));
    }

    /** The area all boxes, lines and labels cover. */
    public Rectangle2D bounds() {
        Rectangle2D r = null;
        for (Box b : boxes) r = add(r, b.bounds());
        for (Line l : lines) for (Point2D p : l.points()) r = add(r, new Rectangle2D.Double(p.getX(), p.getY(), 0, 0));
        for (Title t : titles) r = add(r, new Rectangle2D.Double(t.x(), t.y() - 12, Math.max(120, t.text().length() * 6.5), 14));
        return r == null ? new Rectangle2D.Double(0, 0, BOX_W, BOX_H) : r;
    }

    private static Rectangle2D add(Rectangle2D r, Rectangle2D s) {
        if (r == null) return (Rectangle2D) s.clone();
        r.add(s);
        return r;
    }

    /** Moves everything so that the chart starts at (margin, margin). */
    public Chart normalized(double margin) {
        Rectangle2D b = bounds();
        double dx = margin - b.getX();
        double dy = margin - b.getY();
        Chart c = new Chart();
        for (Box x : boxes) c.boxes.add(x.moved(dx, dy));
        for (Line x : lines) c.lines.add(x.moved(dx, dy));
        for (Label x : labels) c.labels.add(x.moved(dx, dy));
        for (Title x : titles) c.titles.add(x.moved(dx, dy));
        return c;
    }

    public java.util.Optional<Box> boxAt(double x, double y) {
        for (Box b : boxes) if (b.bounds().contains(x, y)) return java.util.Optional.of(b);
        return java.util.Optional.empty();
    }

    public java.util.Optional<Box> boxOf(Person p) {
        for (Box b : boxes) if (b.person().equals(p)) return java.util.Optional.of(b);
        return java.util.Optional.empty();
    }
}
