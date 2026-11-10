package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.commons.swing.AmadeUI;
import eu.amadeos.commons.theme.DesktopTheme;

import java.awt.Color;

/** The colours of the charts, worked out from the desktop theme (light and dark, Motif and Horizon). */
public record ChartColors(Color background, Color box, Color line, Color text, Color muted, Color accent,
                          Color male, Color female, Color other, Color warning, boolean dark) {
    public static ChartColors current() {
        return of(AmadeUI.theme());
    }

    public static ChartColors of(DesktopTheme t) {
        boolean dark = t.isDark();
        Color bg = t.fieldBackground();
        Color text = t.fieldText();
        Color box = dark ? mix(bg, text, 0.07) : mix(bg, Color.WHITE, 0.6);
        if (!dark && bg.equals(Color.WHITE)) box = Color.WHITE;
        return new ChartColors(bg, box, mix(bg, text, dark ? 0.32 : 0.38), text, mix(bg, text, 0.58),
                t.activeTitleBg(), new Color(0x5B, 0x8D, 0xD9), new Color(0xD9, 0x6F, 0x9B), new Color(0x9A, 0x9A, 0xA8),
                new Color(0xE6, 0x9A, 0x2E), dark);
    }

    /** Colours for paper: white background, dark text. */
    public ChartColors forPrint() {
        return new ChartColors(Color.WHITE, Color.WHITE, new Color(0x88, 0x88, 0x88), Color.BLACK,
                new Color(0x55, 0x55, 0x55), new Color(0x1F, 0x7A, 0x75), male, female, other, warning, false);
    }

    public Color sex(Sex s) {
        return switch (s) {
            case MALE -> male;
            case FEMALE -> female;
            default -> other;
        };
    }

    public static Color mix(Color a, Color b, double f) {
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * f),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * f),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * f));
    }
}
