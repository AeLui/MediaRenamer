package com.mediarenamer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * MediaRenamer logo drawn as vectors (Java2D), so that it is sharp at any size:
 * rounded square with a violet-cyan gradient, a white "tag" with the play triangle
 * (media) and text lines (the name), and a yellow sparkle (renaming).
 * tools/GenerateIcons.java uses it to generate the PNGs and the executable's .ico.
 */
public final class Logo {

    public static final Color VIOLET = new Color(0x7C3AED);
    public static final Color CYAN = new Color(0x06B6D4);
    public static final Color SPARK = new Color(0xFACC15);

    private Logo() {
    }

    /** Paints the logo in the square (x, y, size). */
    public static void paint(Graphics2D g0, double x, double y, double size) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.scale(size / 100.0, size / 100.0);
            boolean tiny = size < 24;

            // background
            g.setPaint(new GradientPaint(0, 0, VIOLET, 100, 100, CYAN));
            g.fill(new RoundRectangle2D.Double(2, 2, 96, 96, 26, 26));
            // top highlight
            g.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 60), 0, 50, new Color(255, 255, 255, 0)));
            g.fill(new RoundRectangle2D.Double(2, 2, 96, 50, 26, 26));

            // white tag with a notch
            Path2D tag = new Path2D.Double();
            tag.moveTo(22, 34);
            tag.lineTo(74, 34);
            tag.quadTo(82, 34, 82, 42);
            tag.lineTo(82, 70);
            tag.quadTo(82, 78, 74, 78);
            tag.lineTo(22, 78);
            tag.quadTo(14, 78, 14, 70);
            tag.lineTo(14, 42);
            tag.quadTo(14, 34, 22, 34);
            tag.closePath();
            g.setColor(new Color(0, 0, 0, 40));
            g.translate(0, 2.5);
            g.fill(tag);
            g.translate(0, -2.5);
            g.setColor(Color.WHITE);
            g.fill(tag);

            // "play" triangle
            Path2D play = new Path2D.Double();
            play.moveTo(25, 45);
            play.lineTo(25, 67);
            play.lineTo(43, 56);
            play.closePath();
            g.setPaint(new GradientPaint(25, 45, VIOLET, 43, 67, CYAN));
            g.fill(play);

            if (!tiny) {
                // text lines (the name)
                g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(VIOLET);
                g.drawLine(51, 50, 72, 50);
                g.setColor(new Color(0x94A3B8));
                g.drawLine(51, 62, 65, 62);
            }

            // sparkle
            g.setColor(SPARK);
            g.fill(star(78, 24, tiny ? 16 : 13, tiny ? 5 : 3.6));
            if (!tiny) {
                g.fill(new Ellipse2D.Double(60, 14, 6, 6));
            }
        } finally {
            g.dispose();
        }
    }

    /** 4-pointed star centered at (cx, cy). */
    private static Path2D star(double cx, double cy, double r, double inner) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i - Math.PI / 2;
            double rr = i % 2 == 0 ? r : inner;
            double px = cx + Math.cos(a) * rr;
            double py = cy + Math.sin(a) * rr;
            if (i == 0) {
                p.moveTo(px, py);
            } else {
                p.lineTo(px, py);
            }
        }
        p.closePath();
        return p;
    }

    public static BufferedImage image(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        paint(g, 0, 0, size);
        g.dispose();
        return img;
    }

    /** Window icons in several sizes (Windows picks the right one for the taskbar and title bar). */
    public static List<Image> windowIcons() {
        List<Image> l = new ArrayList<>();
        for (int s : new int[] { 16, 20, 24, 32, 40, 48, 64, 128, 256 }) {
            l.add(image(s));
        }
        return l;
    }
}
