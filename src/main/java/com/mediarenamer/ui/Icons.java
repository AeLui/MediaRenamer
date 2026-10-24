package com.mediarenamer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiConsumer;

import javax.swing.Icon;
import javax.swing.UIManager;

/**
 * Vector icons drawn with Java2D on a 24x24 grid ("outline" style, stroke 2).
 * They take the component's text color (or a fixed one), so they work in light and dark themes
 * and look sharp at any screen scaling.
 */
public final class Icons {

    private Icons() {
    }

    /** Vector icon: the painter receives a Graphics2D already scaled to 24x24 with stroke and color set. */
    public static final class VectorIcon implements Icon {
        private final int size;
        private final Color color;
        private final BiConsumer<Graphics2D, Color> painter;

        VectorIcon(int size, Color color, BiConsumer<Graphics2D, Color> painter) {
            this.size = size;
            this.color = color;
            this.painter = painter;
        }

        public VectorIcon withSize(int s) {
            return new VectorIcon(s, color, painter);
        }

        public VectorIcon withColor(Color c) {
            return new VectorIcon(size, c, painter);
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);
            g.scale(size / 24.0, size / 24.0);
            Color col = color != null ? color : c != null && c.isEnabled() ? c.getForeground() : UIManager.getColor("Label.disabledForeground");
            if (col == null) {
                col = Color.GRAY;
            }
            g.setColor(col);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            painter.accept(g, col);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static VectorIcon icon(BiConsumer<Graphics2D, Color> p) {
        return new VectorIcon(18, null, p);
    }

    private static Path2D path(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }
        return p;
    }

    public static VectorIcon folder() {
        return icon((g, c) -> {
            Path2D p = path(3, 7, 3, 18, 21, 18, 21, 9, 12, 9, 10, 6, 4, 6);
            p.closePath();
            g.draw(p);
        });
    }

    public static VectorIcon add() {
        return icon((g, c) -> {
            g.draw(new Line2D.Double(12, 5, 12, 19));
            g.draw(new Line2D.Double(5, 12, 19, 12));
        });
    }

    public static VectorIcon trash() {
        return icon((g, c) -> {
            g.draw(new Line2D.Double(4, 7, 20, 7));
            g.draw(path(9, 7, 9, 4, 15, 4, 15, 7));
            g.draw(path(6, 7, 7, 20, 17, 20, 18, 7));
            g.draw(new Line2D.Double(10, 11, 10, 16));
            g.draw(new Line2D.Double(14, 11, 14, 16));
        });
    }

    public static VectorIcon clear() {
        return icon((g, c) -> {
            g.draw(new Line2D.Double(6, 6, 18, 18));
            g.draw(new Line2D.Double(18, 6, 6, 18));
        });
    }

    public static VectorIcon sort() {
        return icon((g, c) -> {
            g.draw(new Line2D.Double(4, 6, 14, 6));
            g.draw(new Line2D.Double(4, 12, 11, 12));
            g.draw(new Line2D.Double(4, 18, 8, 18));
            g.draw(new Line2D.Double(18, 5, 18, 19));
            g.draw(path(15, 16, 18, 19, 21, 16));
        });
    }

    public static VectorIcon search() {
        return icon((g, c) -> {
            g.draw(new Ellipse2D.Double(4, 4, 12, 12));
            g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
        });
    }

    /** Two links: match. */
    public static VectorIcon match() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Double(3, 8, 10, 8, 8, 8));
            g.draw(new RoundRectangle2D.Double(11, 8, 10, 8, 8, 8));
        });
    }

    /** Pencil: rename / edit. */
    public static VectorIcon edit() {
        return icon((g, c) -> {
            Path2D p = path(4, 20, 5, 15, 16, 4, 20, 8, 9, 19);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(14, 6, 18, 10));
        });
    }

    /** Rename arrow: document -> document. */
    public static VectorIcon rename() {
        return icon((g, c) -> {
            g.draw(new Line2D.Double(4, 12, 18, 12));
            g.draw(path(13, 7, 18, 12, 13, 17));
            g.draw(new Line2D.Double(21, 5, 21, 19));
        });
    }

    public static VectorIcon bookmark() {
        return icon((g, c) -> {
            Path2D p = path(6, 3, 18, 3, 18, 21, 12, 16, 6, 21);
            p.closePath();
            g.draw(p);
        });
    }

    /** Braces {}: format. */
    public static VectorIcon format() {
        return icon((g, c) -> {
            Path2D l = new Path2D.Double();
            l.moveTo(9, 4);
            l.quadTo(6, 4, 6, 7);
            l.lineTo(6, 10);
            l.quadTo(6, 12, 4, 12);
            l.quadTo(6, 12, 6, 14);
            l.lineTo(6, 17);
            l.quadTo(6, 20, 9, 20);
            g.draw(l);
            Path2D r = new Path2D.Double();
            r.moveTo(15, 4);
            r.quadTo(18, 4, 18, 7);
            r.lineTo(18, 10);
            r.quadTo(18, 12, 20, 12);
            r.quadTo(18, 12, 18, 14);
            r.lineTo(18, 17);
            r.quadTo(18, 20, 15, 20);
            g.draw(r);
        });
    }

    public static VectorIcon settings() {
        return icon((g, c) -> {
            g.draw(new Ellipse2D.Double(9, 9, 6, 6));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                g.draw(new Line2D.Double(12 + Math.cos(a) * 6.5, 12 + Math.sin(a) * 6.5, 12 + Math.cos(a) * 9, 12 + Math.sin(a) * 9));
            }
            g.draw(new Ellipse2D.Double(5.5, 5.5, 13, 13));
        });
    }

    public static VectorIcon history() {
        return icon((g, c) -> {
            g.draw(new Arc2D.Double(4, 4, 16, 16, 120, 290, Arc2D.OPEN));
            g.draw(path(12, 8, 12, 12, 15, 14));
            g.draw(path(4, 5, 5, 9, 9, 8));
        });
    }

    public static VectorIcon sun() {
        return icon((g, c) -> {
            g.draw(new Ellipse2D.Double(8, 8, 8, 8));
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                g.draw(new Line2D.Double(12 + Math.cos(a) * 7, 12 + Math.sin(a) * 7, 12 + Math.cos(a) * 9.5, 12 + Math.sin(a) * 9.5));
            }
        });
    }

    public static VectorIcon moon() {
        return icon((g, c) -> {
            Path2D p = new Path2D.Double();
            p.moveTo(19, 14);
            p.curveTo(15, 18, 7, 16, 6.5, 10);
            p.curveTo(6.3, 7, 8, 4.5, 10, 4);
            p.curveTo(8, 9, 13, 15, 19, 14);
            p.closePath();
            g.draw(p);
        });
    }

    public static VectorIcon film() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Double(4, 3, 16, 18, 3, 3));
            g.draw(new Line2D.Double(8, 3, 8, 21));
            g.draw(new Line2D.Double(16, 3, 16, 21));
            g.draw(new Line2D.Double(8, 12, 16, 12));
            g.draw(new Line2D.Double(4, 8, 8, 8));
            g.draw(new Line2D.Double(4, 16, 8, 16));
            g.draw(new Line2D.Double(16, 8, 20, 8));
            g.draw(new Line2D.Double(16, 16, 20, 16));
        });
    }

    public static VectorIcon tv() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Double(3, 7, 18, 13, 3, 3));
            g.draw(path(8, 3, 12, 7, 16, 3));
        });
    }

    public static VectorIcon music() {
        return icon((g, c) -> {
            g.draw(new Ellipse2D.Double(4, 15, 5, 5));
            g.draw(new Ellipse2D.Double(15, 13, 5, 5));
            g.draw(path(9, 17.5, 9, 5, 20, 3, 20, 15.5));
        });
    }

    public static VectorIcon subtitle() {
        return icon((g, c) -> {
            g.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 3, 3));
            g.draw(new Line2D.Double(7, 13, 11, 13));
            g.draw(new Line2D.Double(14, 13, 17, 13));
            g.draw(new Line2D.Double(7, 16, 15, 16));
        });
    }

    public static VectorIcon file() {
        return icon((g, c) -> {
            Path2D p = path(6, 3, 14, 3, 19, 8, 19, 21, 6, 21);
            p.closePath();
            g.draw(p);
            g.draw(path(14, 3, 14, 8, 19, 8));
        });
    }

    public static VectorIcon info() {
        return icon((g, c) -> {
            g.draw(new Ellipse2D.Double(3, 3, 18, 18));
            g.draw(new Line2D.Double(12, 11, 12, 16));
            g.fill(new Ellipse2D.Double(11, 7, 2, 2));
        });
    }

    public static VectorIcon check() {
        return icon((g, c) -> g.draw(path(5, 12, 10, 17, 19, 7)));
    }

    public static VectorIcon warning() {
        return icon((g, c) -> {
            Path2D p = path(12, 3, 22, 20, 2, 20);
            p.closePath();
            g.draw(p);
            g.draw(new Line2D.Double(12, 9, 12, 14));
            g.fill(new Ellipse2D.Double(11, 16, 2, 2));
        });
    }

    /** Large icon for the empty list state: tray with a down arrow. */
    public static VectorIcon drop() {
        return icon((g, c) -> {
            g.draw(path(3, 14, 3, 20, 21, 20, 21, 14));
            g.draw(new Line2D.Double(12, 3, 12, 15));
            g.draw(path(7, 10, 12, 15, 17, 10));
        });
    }
}
