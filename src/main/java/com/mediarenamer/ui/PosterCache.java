package com.mediarenamer.ui;

import com.mediarenamer.app.Logging;
import com.mediarenamer.web.WebRequest;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Posters: background download (4 threads), 30-day disk cache via WebRequest and an
 * in-memory cache already scaled. get() returns null while loading and notifies through onLoaded on the Swing thread.
 */
public final class PosterCache {

    private static final Map<String, BufferedImage> ORIGINALS = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> SCALED = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "posters");
        t.setDaemon(true);
        return t;
    });

    private PosterCache() {
    }

    public static boolean failed(String url) {
        return url == null || FAILED.contains(url);
    }

    /** Image scaled to w x h (cropping to fill), or null if not ready yet. */
    public static BufferedImage get(String url, int w, int h, Runnable onLoaded) {
        if (url == null || FAILED.contains(url)) {
            return null;
        }
        String key = url + "@" + w + "x" + h;
        BufferedImage scaled = SCALED.get(key);
        if (scaled != null) {
            return scaled;
        }
        BufferedImage original = ORIGINALS.get(url);
        if (original != null) {
            scaled = cover(original, w, h);
            SCALED.put(key, scaled);
            return scaled;
        }
        if (LOADING.add(url)) {
            POOL.submit(() -> {
                try {
                    byte[] data = WebRequest.get(url, null, Duration.ofDays(30));
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                    if (img == null) {
                        FAILED.add(url);
                    } else {
                        ORIGINALS.put(url, img);
                    }
                } catch (Exception e) {
                    FAILED.add(url);
                    Logging.debug("Caratula " + url + ": " + e.getMessage());
                } finally {
                    LOADING.remove(url);
                    if (onLoaded != null) {
                        SwingUtilities.invokeLater(onLoaded);
                    }
                }
            });
        }
        return null;
    }

    /** "object-fit: cover" style quality scaling. */
    static BufferedImage cover(BufferedImage src, int w, int h) {
        double scale = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
        int sw = (int) Math.ceil(src.getWidth() * scale);
        int sh = (int) Math.ceil(src.getHeight() * scale);
        BufferedImage img = src;
        // step-wise downscaling so that it does not look pixelated
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= sw && ch / 2 >= sh) {
            cw /= 2;
            ch /= 2;
            BufferedImage half = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = half.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, cw, ch, null);
            g.dispose();
            img = half;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, (w - sw) / 2, (h - sh) / 2, sw, sh, null);
        g.dispose();
        return out;
    }

    /**
     * Paints the poster with rounded corners; if there is none (or it is loading) paints a placeholder
     * with a gradient and the type icon.
     */
    public static void paint(Graphics2D g0, String url, int x, int y, int w, int h, boolean series, Runnable onLoaded) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, 10, 10);
        BufferedImage img = get(url, w, h, onLoaded);
        if (img != null) {
            // clip() intersects with the current clip (e.g. the scroll one); setClip() would replace it
            Graphics2D gi = (Graphics2D) g.create();
            gi.clip(shape);
            gi.drawImage(img, x, y, null);
            gi.dispose();
            g.setColor(new Color(0, 0, 0, 40));
            g.draw(shape);
        } else {
            g.setPaint(new GradientPaint(x, y, Theme.mix(Logo.VIOLET, Color.WHITE, Theme.isDark() ? 0.15 : 0.55), x + w, y + h,
                    Theme.mix(Logo.CYAN, Color.WHITE, Theme.isDark() ? 0.15 : 0.55)));
            g.fill(shape);
            Icons.VectorIcon icon = (series ? Icons.tv() : Icons.film()).withSize(Math.min(w, h) / 2).withColor(new Color(255, 255, 255, 220));
            icon.paintIcon(null, g, x + (w - icon.getIconWidth()) / 2, y + (h - icon.getIconHeight()) / 2);
        }
        g.dispose();
    }
}
