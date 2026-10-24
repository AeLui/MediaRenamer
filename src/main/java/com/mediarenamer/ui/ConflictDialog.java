package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.rename.Quality;
import com.mediarenamer.rename.RenameEngine.ConflictResolver;
import com.mediarenamer.rename.RenameEngine.Decision;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * "Ask each time": the target already exists. Shows both files with their quality (resolution,
 * codec, HDR, bitrate, audio, duration) and size, highlights the better value of each row and lets the user choose.
 */
public class ConflictDialog extends JDialog {

    private Decision decision = Decision.KEEP_EXISTING;
    private final JCheckBox applyAll = new JCheckBox(tr("conflictDialog.applyAll"));

    public ConflictDialog(Window owner, File source, File existing, Quality newQ, Quality oldQ, int remaining) {
        super(owner, tr("conflictDialog.title"), ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JLabel title = new JLabel(tr("conflictDialog.heading"));
        title.putClientProperty("FlatLaf.styleClass", "h3");
        title.setIcon(Icons.warning().withSize(22).withColor(Theme.WARNING));
        title.setIconTextGap(8);
        JLabel path = new JLabel(abbreviate(existing.getAbsolutePath(), 90));
        path.setToolTipText(existing.getAbsolutePath());
        path.setForeground(Theme.secondaryText());
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.add(title, BorderLayout.NORTH);
        head.add(path, BorderLayout.SOUTH);
        content.add(head, BorderLayout.NORTH);

        JPanel cards = new JPanel(new GridLayout(1, 2, 12, 0));
        cards.add(card(tr("conflictDialog.existing"), existing, oldQ, newQ, Icons.folder()));
        cards.add(card(tr("conflictDialog.new"), source, newQ, oldQ, Icons.rename()));
        content.add(cards, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        int cmp = Quality.QUALITY.compare(newQ, oldQ);
        JLabel hint = new JLabel(cmp > 0 ? tr("conflictDialog.newBetter") : cmp < 0 ? tr("conflictDialog.existingBetter") : tr("conflictDialog.equal"));
        hint.setIcon(Icons.info().withSize(14));
        hint.setForeground(Theme.secondaryText());
        south.add(hint, BorderLayout.NORTH);
        applyAll.setVisible(remaining > 0);
        south.add(applyAll, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton keep = new JButton(tr("conflictDialog.keep"));
        keep.setToolTipText(tr("conflictDialog.keep.tooltip"));
        keep.addActionListener(e -> close(Decision.KEEP_EXISTING));
        JButton both = new JButton(tr("conflictDialog.both"));
        both.setToolTipText(tr("conflictDialog.both.tooltip"));
        both.addActionListener(e -> close(Decision.KEEP_BOTH));
        JButton replace = new JButton(tr("conflictDialog.replace"));
        replace.setToolTipText(tr("conflictDialog.replace.tooltip"));
        replace.addActionListener(e -> close(Decision.REPLACE));
        JButton cancel = new JButton(tr("conflictDialog.cancel"));
        cancel.setToolTipText(tr("conflictDialog.cancel.tooltip"));
        cancel.putClientProperty("JButton.buttonType", "borderless");
        cancel.addActionListener(e -> close(Decision.CANCEL));
        buttons.add(cancel);
        buttons.add(keep);
        buttons.add(both);
        buttons.add(replace);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(cmp > 0 ? replace : keep);
        getRootPane().registerKeyboardAction(e -> close(Decision.KEEP_EXISTING), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    private void close(Decision d) {
        decision = d;
        dispose();
    }

    public Decision getDecision() {
        return decision;
    }

    public boolean isApplyToAll() {
        return applyAll.isSelected();
    }

    /** Card of a file; highlights in green the rows where this file is better than the other one. */
    private static JPanel card(String title, File f, Quality q, Quality other, Icons.VectorIcon icon) {
        JPanel p = new RenamePanel.RoundedPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JLabel t = new JLabel(title, icon.withSize(18).withColor(Theme.ACCENT), JLabel.LEFT);
        t.putClientProperty("FlatLaf.styleClass", "h4");
        JLabel name = new JLabel("<html><b>" + esc(abbreviate(f.getName(), 48)) + "</b><br><span style='color:#888'>"
                + esc(abbreviate(String.valueOf(f.getParent()), 48)) + "</span></html>");
        name.setToolTipText(f.getAbsolutePath());
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.setOpaque(false);
        head.add(t, BorderLayout.NORTH);
        head.add(name, BorderLayout.CENTER);
        p.add(head, BorderLayout.NORTH);

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 12);
        Map<String, String> mine = q.describe();
        int r = 0;
        for (Map.Entry<String, String> e : mine.entrySet()) {
            c.gridy = r++;
            c.gridx = 0;
            c.weightx = 0;
            JLabel k = new JLabel(Quality.label(e.getKey()));
            k.setForeground(Theme.secondaryText());
            rows.add(k, c);
            c.gridx = 1;
            c.weightx = 1;
            JLabel v = new JLabel(e.getValue());
            if (better(e.getKey(), q, other)) {
                v.setForeground(Theme.SUCCESS);
                v.setIcon(Icons.check().withSize(14).withColor(Theme.SUCCESS));
                v.setHorizontalTextPosition(JLabel.LEFT);
                v.putClientProperty("FlatLaf.style", "font: bold");
            }
            rows.add(v, c);
        }
        p.add(rows, BorderLayout.CENTER);
        return p;
    }

    /** Is this file better than the other one in that row? (size is marked as "larger"). */
    private static boolean better(String row, Quality a, Quality b) {
        return switch (row) {
            case "size" -> a.size() > b.size();
            case "resolution" -> nz(a.height()) > nz(b.height());
            case "video" -> nz(a.height()) == nz(b.height()) && hdr(a) > hdr(b);
            case "bitrate" -> bitrate(a) > bitrate(b) * 1.05;
            case "audio" -> channels(a) > channels(b);
            default -> false;
        };
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }

    private static long bitrate(Quality q) {
        return q.videoBitRate() != null ? q.videoBitRate() : q.overallBitRate() != null ? q.overallBitRate() : 0;
    }

    private static int hdr(Quality q) {
        return List.of("HLG", "HDR10", "HDR10+", "DV").indexOf(String.valueOf(q.hdr())) + 1;
    }

    private static int channels(Quality q) {
        try {
            String c = String.valueOf(q.channels()).replace("ch", "");
            String[] p = c.split("\\.");
            return Integer.parseInt(p[0]) + (p.length > 1 ? Integer.parseInt(p[1]) : 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Shortens in the middle: "C:\Users\…\dest\Interstellar (2014)". */
    static String abbreviate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int head = max / 3;
        int tail = max - head - 1;
        return s.substring(0, head) + "…" + s.substring(s.length() - tail);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Resolver for RenameEngine: shows the dialog on the Swing thread and remembers "do the same for the rest". */
    public static final class Resolver implements ConflictResolver {
        private final Window owner;
        private Decision remembered;

        public Resolver(Window owner) {
            this.owner = owner;
        }

        @Override
        public Decision resolve(File source, File existing, Object match) {
            if (remembered != null) {
                return remembered;
            }
            Quality newQ = Quality.of(source);
            Quality oldQ = Quality.of(existing);
            AtomicReference<Decision> d = new AtomicReference<>(Decision.KEEP_EXISTING);
            Runnable show = () -> {
                ConflictDialog dialog = new ConflictDialog(owner, source, existing, newQ, oldQ, 1);
                dialog.setVisible(true);
                d.set(dialog.getDecision());
                if (dialog.isApplyToAll() && dialog.getDecision() != Decision.CANCEL) {
                    remembered = dialog.getDecision();
                }
            };
            try {
                if (SwingUtilities.isEventDispatchThread()) {
                    show.run();
                } else {
                    SwingUtilities.invokeAndWait(show);
                }
            } catch (Exception e) {
                return Decision.KEEP_EXISTING;
            }
            return d.get();
        }
    }
}
