package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Modal progress layer inside the window itself (glass pane): dims the application, blocks
 * mouse and keyboard and shows a card with title, current file, progress bar and "Cancel".
 * It is not a separate window. The background work checks isCancelled() between files.
 */
public class ProgressOverlay extends JComponent {

    private final JFrame frame;
    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar();
    private final JButton cancel = new JButton(tr("common.cancel"));
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile boolean active;
    private java.awt.Component previousGlass;

    public ProgressOverlay(JFrame frame) {
        this.frame = frame;
        setLayout(new GridBagLayout());
        setOpaque(false);
        // block interaction with what is behind
        addMouseListener(new MouseAdapter() {
        });
        addMouseMotionListener(new MouseAdapter() {
        });
        addMouseWheelListener(e -> e.consume());
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE && cancel.isEnabled()) {
                    cancel.doClick();
                }
                e.consume();
            }
        });
        setFocusTraversalKeysEnabled(false);

        JPanel card = new RenamePanel.RoundedPanel(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));
        title.putClientProperty("FlatLaf.styleClass", "h3");
        title.setIcon(Icons.rename().withSize(22).withColor(Theme.ACCENT));
        title.setIconTextGap(10);
        detail.setForeground(Theme.secondaryText());
        detail.setPreferredSize(new Dimension(460, detail.getPreferredSize().height));
        bar.setPreferredSize(new Dimension(460, 8));
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(detail, BorderLayout.NORTH);
        center.add(bar, BorderLayout.CENTER);
        card.add(title, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.setOpaque(false);
        cancel.setIcon(Icons.clear().withSize(14));
        cancel.addActionListener(e -> {
            cancelled.set(true);
            cancel.setEnabled(false);
            cancel.setText(tr("progress.cancelling"));
            detail.setText(tr("progress.finishing"));
        });
        buttons.add(cancel);
        card.add(buttons, BorderLayout.SOUTH);
        add(card);
    }

    /** Shows the layer. cancellable = shows the Cancel button. */
    public void start(String titleText, boolean cancellable) {
        active = true;
        cancelled.set(false);
        title.setText(titleText);
        detail.setText(" ");
        bar.setIndeterminate(true);
        cancel.setVisible(cancellable);
        cancel.setEnabled(true);
        cancel.setText(tr("common.cancel"));
        previousGlass = frame.getGlassPane();
        frame.setGlassPane(this);
        setVisible(true);
        requestFocusInWindow();
    }

    /** Updates the progress (can be called from any thread). */
    public void progress(int done, int total, String text) {
        SwingUtilities.invokeLater(() -> {
            if (total > 0) {
                bar.setIndeterminate(false);
                bar.setMaximum(total);
                bar.setValue(done);
            }
            if (text != null && !cancelled.get()) {
                detail.setText(text);
            }
        });
    }

    /**
     * Switches to a final non-cancellable step (e.g. "Scanning libraries…" after moving the files):
     * changes the title, makes the bar indeterminate and hides Cancel. Can be called from any thread.
     */
    public void step(String titleText, String text) {
        SwingUtilities.invokeLater(() -> {
            title.setText(titleText);
            detail.setText(text == null ? " " : text);
            bar.setIndeterminate(true);
            cancel.setVisible(false);
        });
    }

    /** True from {@link #start} until {@link #finish}. */
    public boolean isActive() {
        return active;
    }

    public void finish() {
        active = false;
        setVisible(false);
        if (previousGlass != null) {
            frame.setGlassPane(previousGlass);
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Theme.isDark() ? 0.55f : 0.35f));
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }
}
