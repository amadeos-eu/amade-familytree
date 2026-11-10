package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.GenDate;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Sex;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Optional;
import java.util.function.Function;

/**
 * Draws one person's box: a coloured edge for the sex, the portrait (or a drawn silhouette), the call name and
 * surname, the years and the birth name or birth place; the chart's main person in the accent colour, the selected
 * person with an accent frame, small triangles for more ancestors or descendants, and a warning sign for dates the
 * program could not read.
 */
public final class BoxPainter {
    private final Font bold;
    private final Font plain;
    private final Function<String, String> nee;

    /** @param nee makes "née Keller" from "Keller" in the program's language */
    public BoxPainter(Font base, Function<String, String> nee) {
        this.bold = base.deriveFont(Font.BOLD, 12.5f);
        this.plain = base.deriveFont(Font.PLAIN, 11.5f);
        this.nee = nee;
    }

    public void paint(Graphics2D g, ChartColors c, Chart.Box b, boolean selected, Optional<Image> portrait) {
        Person p = b.person();
        double x = b.x(), y = b.y(), w = Chart.BOX_W, h = Chart.BOX_H;
        RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, 12, 12);
        Color bg = c.box();
        if (b.root()) bg = ChartColors.mix(bg, c.accent(), c.dark() ? 0.24 : 0.14);
        g.setColor(bg);
        g.fill(r);
        Shape clip = g.getClip();
        g.clip(r);
        g.setColor(c.sex(p.sex()));
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y, 4, h));
        g.setClip(clip);
        g.setStroke(new BasicStroke(b.root() || selected ? 2.2f : 1f));
        g.setColor(b.root() || selected ? c.accent() : c.line());
        g.draw(r);
        if (selected && !b.root()) {
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(x + 3, y + 3, w - 6, h - 6, 8, 8));
        }

        double s = 34;
        double ax = x + 11, ay = y + (h - s) / 2;
        avatar(g, c, p.sex(), ax, ay, s, portrait);

        double tx = ax + s + 9;
        double maxW = x + w - tx - 6;
        g.setFont(bold);
        g.setColor(c.text());
        g.drawString(fit(g, p.shortName(), maxW), (float) tx, (float) (y + 20));
        g.setFont(plain);
        g.setColor(c.muted());
        g.drawString(fit(g, p.lifespan(), maxW), (float) tx, (float) (y + 36));
        String third = p.birthSurname().isEmpty() ? firstPart(p.birth().map(Fact::place).orElse(""))
                : nee.apply(p.birthSurname());
        g.drawString(fit(g, third, maxW), (float) tx, (float) (y + 51));

        if (hasUnreadableDate(p)) {
            double bx = x + w - 18, by = y + 6;
            g.setColor(c.warning());
            g.fill(new Ellipse2D.Double(bx, by, 12, 12));
            g.setColor(Color.BLACK);
            g.setFont(bold.deriveFont(9.5f));
            g.drawString("!", (float) (bx + 4.6), (float) (by + 10));
        }
        g.setColor(c.accent());
        if (b.moreAbove()) triangle(g, x + w / 2, y - 3, -1);
        if (b.moreBelow()) triangle(g, x + w / 2, y + h + 3, 1);
    }

    private static void triangle(Graphics2D g, double cx, double y, int dir) {
        int px = (int) Math.round(cx);
        int py = (int) Math.round(y);
        g.fill(new Polygon(new int[]{px - 6, px + 6, px}, new int[]{py, py, py + dir * 7}, 3));
    }

    /** A round picture: the portrait when there is one, else a drawn head and shoulders. */
    public static void avatar(Graphics2D g, ChartColors c, Sex sex, double x, double y, double s, Optional<Image> portrait) {
        Ellipse2D circle = new Ellipse2D.Double(x, y, s, s);
        Shape clip = g.getClip();
        if (portrait.isPresent()) {
            g.clip(circle);
            g.drawImage(portrait.get(), (int) Math.round(x), (int) Math.round(y), (int) Math.round(s),
                    (int) Math.round(s), null);
            g.setClip(clip);
            return;
        }
        Color tone = c.sex(sex);
        g.setColor(ChartColors.mix(c.box(), tone, 0.33));
        g.fill(circle);
        g.clip(circle);
        g.setColor(ChartColors.mix(c.box(), tone, 0.75));
        g.fill(new Ellipse2D.Double(x + s * 0.3, y + s * 0.17, s * 0.4, s * 0.4));
        g.fill(new Ellipse2D.Double(x + s * 0.125, y + s * 0.6, s * 0.75, s * 0.7));
        g.setClip(clip);
    }

    static boolean hasUnreadableDate(Person p) {
        for (Fact f : p.facts()) {
            if (f.date().kind() == GenDate.Kind.TEXT && f.node().has("DATE") && !f.node().valueOf("DATE").isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static String firstPart(String place) {
        int comma = place.indexOf(',');
        return comma < 0 ? place : place.substring(0, comma).strip();
    }

    static String fit(Graphics2D g, String text, double max) {
        if (text == null) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= max) return text;
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "…") > max) t = t.substring(0, t.length() - 1);
        return t.strip() + "…";
    }
}
