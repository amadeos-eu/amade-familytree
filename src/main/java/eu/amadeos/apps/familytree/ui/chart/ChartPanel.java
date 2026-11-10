package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.Person;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Optional;
import java.util.function.Function;

/**
 * Shows a {@link Chart}: zoom with Ctrl and the mouse wheel or the menu, move by dragging, click to select,
 * double-click to put a person in the middle, right-click for the person's menu. Prints itself on one page.
 */
public final class ChartPanel extends JComponent implements Scrollable, Printable {
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 3;

    private Chart chart = new Chart();
    private double zoom = 1;
    private Person selected;
    private final Portraits portraits;
    private final BoxPainter painter;
    private ChartListener listener;
    private Point dragFrom;

    public ChartPanel(Portraits portraits, Function<String, String> nee) {
        this.portraits = portraits;
        Font base = UIManager.getFont("Label.font");
        this.painter = new BoxPainter(base == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : base, nee);
        setOpaque(true);
        setFocusable(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                dragFrom = e.getPoint();
                if (e.isPopupTrigger()) popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) popup(e);
                dragFrom = null;
                setCursor(java.awt.Cursor.getDefaultCursor());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || listener == null) return;
                personAt(e.getPoint()).ifPresent(p -> {
                    if (e.getClickCount() >= 2) listener.centre(p);
                    else listener.select(p);
                });
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragFrom == null || !(getParent() instanceof JViewport vp)) return;
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.MOVE_CURSOR));
                Rectangle view = vp.getViewRect();
                view.translate(dragFrom.x - e.getX(), dragFrom.y - e.getY());
                scrollRectToVisible(view);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.isControlDown()) {
                    setZoom(zoom * (e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1), e.getPoint());
                } else if (getParent() != null) {
                    getParent().dispatchEvent(SwingUtilities.convertMouseEvent(ChartPanel.this, e, getParent()));
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
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

    public void setChart(Chart newChart) {
        chart = newChart == null ? new Chart() : newChart;
        revalidate();
        repaint();
    }

    public Chart chart() {
        return chart;
    }

    public void setSelected(Person p) {
        selected = p;
        repaint();
    }

    public double zoom() {
        return zoom;
    }

    public void setZoom(double z) {
        setZoom(z, null);
    }

    /** Zooms, keeping the chart point under {@code anchor} (or the middle of the view) in place. */
    public void setZoom(double z, Point anchor) {
        double nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, z));
        if (nz == zoom) return;
        Rectangle view = getParent() instanceof JViewport vp ? vp.getViewRect() : getVisibleRect();
        Point a = anchor != null ? anchor : new Point(view.x + view.width / 2, view.y + view.height / 2);
        double cx = a.x / zoom, cy = a.y / zoom;
        zoom = nz;
        revalidate();
        Dimension d = getPreferredSize();
        setSize(d);
        int nx = (int) Math.round(cx * zoom) - (a.x - view.x);
        int ny = (int) Math.round(cy * zoom) - (a.y - view.y);
        scrollRectToVisible(new Rectangle(nx, ny, view.width, view.height));
        repaint();
    }

    /** Scrolls so that the person's box is in the middle of the view. */
    public void reveal(Person p) {
        chart.boxOf(p).ifPresent(b -> {
            Rectangle view = getParent() instanceof JViewport vp ? vp.getViewRect() : getVisibleRect();
            Rectangle2D r = b.bounds();
            int x = (int) Math.round(r.getCenterX() * zoom - view.width / 2.0);
            int y = (int) Math.round(r.getCenterY() * zoom - view.height / 2.0);
            scrollRectToVisible(new Rectangle(x, y, view.width, view.height));
        });
    }

    public Optional<Person> personAt(Point p) {
        return chart.boxAt((p.x - offset.getX()) / zoom, (p.y - offset.getY()) / zoom).map(Chart.Box::person);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        return personAt(e.getPoint()).map(Person::fullName).orElse(null);
    }

    @Override
    public Dimension getPreferredSize() {
        Rectangle2D b = chart.bounds();
        double w = b.getMaxX() + TreeLayout.MARGIN;
        double h = b.getMaxY() + TreeLayout.MARGIN;
        return new Dimension((int) Math.ceil(w * zoom), (int) Math.ceil(h * zoom));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        ChartColors c = ChartColors.current();
        g.setColor(c.background());
        g.fillRect(0, 0, getWidth(), getHeight());
        // Centre a chart smaller than the view.
        Dimension pref = getPreferredSize();
        double ox = Math.max(0, (getWidth() - pref.width) / 2.0);
        double oy = Math.max(0, (getHeight() - pref.height) / 2.0);
        offset = new Point2D.Double(ox, oy);
        g.translate(ox, oy);
        g.scale(zoom, zoom);
        draw(g, c, true);
        g.dispose();
    }

    private Point2D offset = new Point2D.Double();

    /** Draws the chart in chart units. */
    public void draw(Graphics2D g, ChartColors c, boolean withSelection) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        g.setFont(base.deriveFont(Font.PLAIN, 10f));
        g.setColor(c.muted());
        for (Chart.Title t : chart.titles()) g.drawString(t.text(), (float) t.x(), (float) t.y());
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(c.line());
        for (Chart.Line l : chart.lines()) {
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (Point2D p : l.points()) {
                if (first) path.moveTo(p.getX(), p.getY());
                else path.lineTo(p.getX(), p.getY());
                first = false;
            }
            g.draw(path);
        }
        g.setFont(base.deriveFont(Font.PLAIN, 10f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(c.muted());
        for (Chart.Label l : chart.labels()) {
            g.drawString(l.text(), (float) (l.x() - fm.stringWidth(l.text()) / 2.0), (float) l.y());
        }
        for (Chart.Box b : chart.boxes()) {
            painter.paint(g, c, b, withSelection && b.person().equals(selected), portraits.of(b.person()));
        }
    }

    /** The whole chart as a picture, for "Save Chart as Picture". */
    public BufferedImage image(double scale) {
        Rectangle2D b = chart.bounds();
        int w = (int) Math.ceil((b.getMaxX() + TreeLayout.MARGIN) * scale);
        int h = (int) Math.ceil((b.getMaxY() + TreeLayout.MARGIN) * scale);
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        ChartColors c = ChartColors.current();
        g.setColor(c.background());
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(scale, scale);
        draw(g, c, false);
        g.dispose();
        return img;
    }

    /** Prints the chart on one page, scaled to fit, always in light colours. */
    @Override
    public int print(Graphics g0, PageFormat pf, int page) {
        if (page > 0) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) g0.create();
        Rectangle2D b = chart.bounds();
        double w = b.getMaxX() + TreeLayout.MARGIN, h = b.getMaxY() + TreeLayout.MARGIN;
        double scale = Math.min(1, Math.min(pf.getImageableWidth() / w, pf.getImageableHeight() / h));
        g.translate(pf.getImageableX(), pf.getImageableY());
        g.scale(scale, scale);
        draw(g, ChartColors.of(eu.amadeos.commons.theme.DesktopTheme.WARM_MOTIF).forPrint(), false);
        g.dispose();
        return PAGE_EXISTS;
    }

    // ---- Scrollable ------------------------------------------------------------------------------------------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(800, 560);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL ? visible.height - 48 : visible.width - 48;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport vp && vp.getWidth() > getPreferredSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport vp && vp.getHeight() > getPreferredSize().height;
    }
}
