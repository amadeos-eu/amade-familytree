package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.Person;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ancestors as a half circle: the person in the middle, parents in the first ring, grandparents in the
 * second, and so on. Each of the four grandparents' lines has its own colour; empty places show where research
 * can go on. Click selects, double-click puts that person in the middle.
 */
public final class FanChart extends JComponent implements Printable {
    static final double CENTRE = 72;
    static final double[] RING = {98, 104, 108, 112, 100, 90, 80, 72};

    private Person root;
    private int generations = 5;
    private Person selected;
    private ChartListener listener;
    private final List<Segment> segments = new ArrayList<>();
    private final String emptyText;

    /** One place in the fan: generation (1 = parents), index from the left, the person or null. */
    record Segment(int generation, int index, Person person, Area shape) {
    }

    public FanChart(String emptyText) {
        this.emptyText = emptyText;
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (listener == null || !SwingUtilities.isLeftMouseButton(e)) return;
                personAt(e.getPoint()).ifPresent(p -> {
                    if (e.getClickCount() >= 2) listener.centre(p);
                    else listener.select(p);
                });
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) popup(e);
            }
        };
        addMouseListener(mouse);
    }

    private void popup(MouseEvent e) {
        if (listener == null) return;
        personAt(e.getPoint()).ifPresent(p -> {
            listener.select(p);
            listener.popup(p, this, e.getX(), e.getY());
        });
    }

    public void setListener(ChartListener l) {
        listener = l;
    }

    public void setRoot(Person p, int gens) {
        root = p;
        int wanted = Math.max(2, Math.min(RING.length, gens));
        // One empty ring beyond the known ancestors invites to research; more would only waste room.
        generations = p == null ? wanted : Math.max(2, Math.min(wanted, depth(p, 0, wanted) + 1));
        revalidate();
        repaint();
    }

    private static int depth(Person p, int level, int max) {
        if (level >= max) return level;
        int d = level;
        for (Person parent : p.parents()) d = Math.max(d, depth(parent, level + 1, max));
        return d;
    }

    public void setSelected(Person p) {
        selected = p;
        repaint();
    }

    public int generations() {
        return generations;
    }

    double radius() {
        double r = CENTRE;
        for (int g = 0; g < generations; g++) r += RING[g];
        return r;
    }

    @Override
    public Dimension getPreferredSize() {
        double r = radius();
        return new Dimension((int) (2 * r + 60), (int) (r + 70));
    }

    public Optional<Person> personAt(Point p) {
        for (Segment s : segments) if (s.person() != null && s.shape().contains(p)) return Optional.of(s.person());
        double r = CENTRE;
        double cx = getWidth() / 2.0, cy = getHeight() - 30.0;
        if (root != null && p.distance(cx, cy) <= r && p.y <= cy) return Optional.of(root);
        return Optional.empty();
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        return personAt(e.getPoint()).map(Person::fullName).orElse(null);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        ChartColors c = ChartColors.current();
        g.setColor(c.background());
        g.fillRect(0, 0, getWidth(), getHeight());
        draw(g, c, getWidth() / 2.0, getHeight() - 30.0, true);
        g.dispose();
    }

    void draw(Graphics2D g, ChartColors c, double cx, double cy, boolean withSelection) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        segments.clear();
        if (root == null) return;
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        Color[] lines = {c.male(), c.warning(), c.accent(), new Color(0x8A, 0x6E, 0xE6)};
        // People of each generation, left (father's side) to right.
        List<Person> current = new ArrayList<>();
        current.add(root);
        double r0 = CENTRE;
        for (int gen = 1; gen <= generations; gen++) {
            List<Person> next = new ArrayList<>();
            for (Person p : current) {
                next.add(p == null ? null : p.father().orElse(null));
                next.add(p == null ? null : p.mother().orElse(null));
            }
            double r1 = r0 + RING[gen - 1];
            int n = next.size();
            boolean anyParentKnown = false;
            for (int i = 0; i < n; i++) {
                Person child = current.get(i / 2);
                double a0 = 180 - 180.0 * i / n, ext = -180.0 / n;
                Area seg = new Area(new Arc2D.Double(cx - r1, cy - r1, 2 * r1, 2 * r1, a0, ext, Arc2D.PIE));
                seg.subtract(new Area(new Ellipse2D.Double(cx - r0, cy - r0, 2 * r0, 2 * r0)));
                Person p = next.get(i);
                if (p != null) anyParentKnown = true;
                if (child == null) continue;
                segments.add(new Segment(gen, i, p, seg));
                int line = gen == 1 ? i * 2 : i * 4 / n;
                Color fill = p == null ? ChartColors.mix(c.background(), c.box(), 0.6)
                        : ChartColors.mix(c.box(), lines[Math.min(3, line)], c.dark() ? 0.30 : 0.22 + 0.04 * Math.max(0, 4 - gen));
                g.setColor(fill);
                g.fill(seg);
                g.setColor(c.background());
                g.setStroke(new BasicStroke(2f));
                g.draw(seg);
                if (withSelection && p != null && p.equals(selected)) {
                    g.setColor(c.accent());
                    g.setStroke(new BasicStroke(2.5f));
                    g.draw(seg);
                }
                text(g, c, base, p, gen, a0 + ext / 2, (r0 + r1) / 2, cx, cy, r1 - r0, Math.toRadians(-ext) * (r0 + r1) / 2);
            }
            current = next;
            r0 = r1;
            if (!anyParentKnown) break;
        }
        double r = CENTRE;
        Arc2D centre = new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 0, 180, Arc2D.PIE);
        g.setColor(ChartColors.mix(c.box(), c.accent(), 0.35));
        g.fill(centre);
        g.setColor(c.accent());
        g.setStroke(new BasicStroke(2f));
        g.draw(centre);
        g.setColor(c.text());
        g.setFont(base.deriveFont(Font.BOLD, 13f));
        centred(g, BoxPainter.fit(g, root.calledName(), 2 * r - 24), cx, cy - 34);
        centred(g, BoxPainter.fit(g, root.surname(), 2 * r - 24), cx, cy - 18);
        g.setFont(base.deriveFont(Font.PLAIN, 11f));
        g.setColor(c.muted());
        centred(g, root.lifespan(), cx, cy - 3);
    }

    private void text(Graphics2D g0, ChartColors c, Font base, Person p, int gen, double angleDeg, double rm, double cx,
                      double cy, double depth, double arc) {
        double mid = Math.toRadians(angleDeg);
        double tx = cx + Math.cos(mid) * rm, ty = cy - Math.sin(mid) * rm;
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(tx, ty);
        // Inner rings write across the ring, outer rings along the radius, so the text fits.
        boolean radial = gen >= 3;
        double room;
        if (radial) {
            double rot = -mid;
            if (Math.cos(mid) < 0) rot += Math.PI;
            g.rotate(rot);
            room = depth - 10;
        } else {
            g.rotate(Math.PI / 2 - mid);
            room = arc - 12;
        }
        if (p == null) {
            g.setColor(c.muted());
            g.setFont(base.deriveFont(Font.PLAIN, gen >= 5 ? 9f : 10.5f));
            if (gen <= 5) centred(g, BoxPainter.fit(g, emptyText, room), 0, 4);
            g.dispose();
            return;
        }
        float size = gen <= 2 ? 12f : gen == 3 ? 11f : gen == 4 ? 10f : 9f;
        g.setFont(base.deriveFont(Font.BOLD, size));
        g.setColor(c.text());
        boolean two = gen <= 4;
        centred(g, BoxPainter.fit(g, p.shortName(), room), 0, two ? -2 : 4);
        if (two) {
            g.setFont(base.deriveFont(Font.PLAIN, size - 1.5f));
            g.setColor(c.muted());
            centred(g, BoxPainter.fit(g, p.lifespan(), room), 0, size + 1);
        }
        g.dispose();
    }

    private static void centred(Graphics2D g, String s, double x, double y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (float) (x - fm.stringWidth(s) / 2.0), (float) y);
    }

    public BufferedImage image(double scale) {
        Dimension d = getPreferredSize();
        BufferedImage img = new BufferedImage((int) (d.width * scale), (int) (d.height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        ChartColors c = ChartColors.current();
        g.setColor(c.background());
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(scale, scale);
        draw(g, c, d.width / 2.0, d.height - 30.0, false);
        g.dispose();
        segments.clear();
        repaint();
        return img;
    }

    @Override
    public int print(Graphics g0, PageFormat pf, int page) {
        if (page > 0) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) g0.create();
        Dimension d = getPreferredSize();
        double scale = Math.min(1, Math.min(pf.getImageableWidth() / d.width, pf.getImageableHeight() / d.height));
        g.translate(pf.getImageableX(), pf.getImageableY());
        g.scale(scale, scale);
        draw(g, ChartColors.current().forPrint(), d.width / 2.0, d.height - 30.0, false);
        g.dispose();
        repaint();
        return PAGE_EXISTS;
    }
}
