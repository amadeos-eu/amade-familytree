package eu.amadeos.apps.familytree.ui.text;

import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.ui.chart.ChartColors;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/** Small helpers for the HTML pages of the program (family sheet, timeline, the person's summary). */
public final class Html {
    /** Links to people are written as {@code person:I12}. */
    public static final String PERSON = "person:";

    private Html() {
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\n' -> b.append("<br>");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    public static String link(Person p, String text) {
        return "<a href=\"" + PERSON + p.id() + "\">" + esc(text) + "</a>";
    }

    public static String link(Person p) {
        return link(p, p.fullName());
    }

    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** The start of a page in the theme's colours and font. */
    public static String page(ChartColors c) {
        Font f = UIManager.getFont("Label.font");
        String family = f == null ? "sans-serif" : f.getFamily();
        int size = f == null ? 12 : f.getSize();
        return "<html><head><style>"
                + "body{font-family:'" + family + "';font-size:" + size + "pt;color:" + hex(c.text()) + ";background:"
                + hex(c.background()) + ";margin:10px}"
                + "a{color:" + hex(c.accent()) + ";text-decoration:none}"
                + "h1{font-size:" + (size + 5) + "pt;margin:0 0 2px 0}"
                + "h2{font-size:" + (size + 1) + "pt;color:" + hex(c.accent()) + ";margin:12px 0 4px 0}"
                + ".m{color:" + hex(c.muted()) + "}"
                + "td{padding:3px 8px 3px 0;vertical-align:top}"
                + "th{text-align:left;color:" + hex(c.muted()) + ";font-weight:normal;padding:3px 8px 3px 0}"
                + "</style></head><body>";
    }

    public static String end() {
        return "</body></html>";
    }
}
