package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.MediaRecord;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.TreeStore;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * The pictures of people, small and square, cut from the middle of their portrait photo. Photos are read in the
 * background; whoever asked is told with {@code onLoaded} and paints again. Only files on this computer are
 * read, never web links.
 */
public final class Portraits {
    public static final int SIZE = 96;
    private static final BufferedImage NONE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private final Executor background;
    private final Map<Path, BufferedImage> cache = new ConcurrentHashMap<>();
    private volatile StoredTree tree;
    private volatile Runnable onLoaded = () -> {
    };

    public Portraits(Executor background) {
        this.background = background;
    }

    public void setTree(StoredTree newTree) {
        tree = newTree;
    }

    public void onLoaded(Runnable r) {
        onLoaded = r;
    }

    /** Forgets a file, after it was replaced. */
    public void forget(Path file) {
        cache.remove(file);
    }

    /** The square portrait of a person, or empty (no photo, not loaded yet, or unreadable). */
    public Optional<Image> of(Person p) {
        StoredTree t = tree;
        Optional<MediaRecord> m = p.portrait();
        if (t == null || m.isEmpty()) return Optional.empty();
        Optional<Path> file = TreeStore.localFile(t, m.get());
        if (file.isEmpty()) return Optional.empty();
        return image(file.get());
    }

    public Optional<Image> image(Path file) {
        BufferedImage img = cache.get(file);
        if (img == NONE) return Optional.empty();
        if (img != null) return Optional.of(img);
        cache.put(file, NONE);
        background.execute(() -> {
            BufferedImage loaded = load(file);
            if (loaded != null) {
                cache.put(file, loaded);
                SwingUtilities.invokeLater(() -> onLoaded.run());
            }
        });
        return Optional.empty();
    }

    static BufferedImage load(Path file) {
        try {
            BufferedImage src = ImageIO.read(file.toFile());
            if (src == null) return null;
            int side = Math.min(src.getWidth(), src.getHeight());
            // Faces are usually in the upper part of a portrait: cut a little above the middle.
            int x = (src.getWidth() - side) / 2;
            int y = Math.max(0, (src.getHeight() - side) / 3);
            BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
            g.dispose();
            return out;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
