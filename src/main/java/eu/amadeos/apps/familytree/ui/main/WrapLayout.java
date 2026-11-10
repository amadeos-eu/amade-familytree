package eu.amadeos.apps.familytree.ui.main;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * A {@link FlowLayout} that asks for the height of all its rows, so that buttons which do not fit in one row move
 * to a second one instead of disappearing (long view names in some languages).
 */
final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return size(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension d = size(target, false);
        d.width = 0;
        return d;
    }

    /** Once the row knows its width, the bar asks again for the height it needs. */
    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int wanted = size(target, true).height;
        if (target.getWidth() > 0 && wanted != target.getHeight()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (target.getParent() instanceof javax.swing.JComponent parent) parent.revalidate();
            });
        }
    }

    private Dimension size(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth();
            Container c = target;
            while (width == 0 && c.getParent() != null) {
                c = c.getParent();
                width = c.getWidth();
            }
            if (width == 0) width = Integer.MAX_VALUE;
            Insets in = target.getInsets();
            int max = width - in.left - in.right - getHgap() * 2;
            int rowWidth = 0, rowHeight = 0, totalWidth = 0, totalHeight = 0;
            for (Component m : target.getComponents()) {
                if (!m.isVisible()) continue;
                Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + d.width > max) {
                    totalWidth = Math.max(totalWidth, rowWidth);
                    totalHeight += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                rowWidth += (rowWidth > 0 ? getHgap() : 0) + d.width;
                rowHeight = Math.max(rowHeight, d.height);
            }
            totalWidth = Math.max(totalWidth, rowWidth);
            totalHeight += rowHeight;
            return new Dimension(totalWidth + in.left + in.right + getHgap() * 2,
                    totalHeight + in.top + in.bottom + getVgap() * 2);
        }
    }
}
