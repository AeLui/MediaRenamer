package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/** Main window: header with logo and shortcuts, rename panel and status bar. */
public class MainFrame extends JFrame {

    private final JLabel status = new JLabel(tr("main.ready"));
    private final java.util.function.Consumer<String> logListener = msg -> SwingUtilities.invokeLater(() -> status.setText(msg));
    private RenamePanel panel;
    private JButton themeButton;

    public static void open() {
        Theme.install();
        I18n.applyToSwing();
        // first run (no preferences file yet): setup wizard before the main window, which then opens in the chosen language
        if (Settings.get().isFirstRun()) {
            new SetupWizard().setVisible(true);
            I18n.applyToSwing();
        }
        MainFrame f = new MainFrame();
        f.setVisible(true);
    }

    /**
     * Rebuilds the window with the current language (after changing it in Preferences): same
     * position and size, keeping the file list (the matches are lost).
     */
    void reload() {
        java.util.List<java.io.File> files = panel.files();
        I18n.applyToSwing();
        MainFrame f = new MainFrame();
        f.setBounds(getBounds());
        f.setExtendedState(getExtendedState());
        dispose();
        f.setVisible(true);
        f.panel.addFiles(files);
    }

    /** Updates the theme button and repaints after switching light/dark (header button or Preferences). */
    void themeChanged() {
        themeButton.setIcon(Theme.isDark() ? Icons.sun() : Icons.moon());
        repaint();
    }

    @Override
    public void dispose() {
        Logging.removeListener(logListener);
        super.dispose();
    }

    public MainFrame() {
        super(Settings.APP_NAME);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setIconImages(Logo.windowIcons());

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.surface());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel = new RenamePanel(this);
        root.add(header(), BorderLayout.NORTH);
        root.add(panel, BorderLayout.CENTER);
        root.add(statusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        Logging.addListener(logListener);
        setMinimumSize(new Dimension(1000, 600));
        setSize(1280, 780);
        setLocationRelativeTo(null);
    }

    private JComponent header() {
        JPanel h = new JPanel(new BorderLayout());
        h.setOpaque(false);
        h.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));

        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Logo.paint((Graphics2D) g, 0, 0, 44);
            }
        };
        logo.setPreferredSize(new Dimension(44, 44));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(Settings.APP_NAME);
        title.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitle = new JLabel(tr("main.subtitle"));
        subtitle.setForeground(Theme.secondaryText());
        titles.add(title);
        titles.add(subtitle);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(logo);
        left.add(titles);
        h.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 8));
        right.setOpaque(false);
        right.add(RenamePanel.toolButton(tr("main.history"), Icons.history(), tr("main.history.tooltip"), () -> new HistoryDialog(this).setVisible(true)));
        right.add(RenamePanel.toolButton(tr("main.preferences"), Icons.settings(), tr("main.preferences.tooltip"), () -> new PreferencesDialog(this).setVisible(true)));
        JButton theme = RenamePanel.toolButton(null, Theme.isDark() ? Icons.sun() : Icons.moon(), tr("main.theme.tooltip"), null);
        theme.addActionListener(e -> {
            Theme.toggle();
            themeChanged();
        });
        themeButton = theme;
        // discreet "About" button: icon only, next to the theme toggle
        JButton about = RenamePanel.toolButton(null, Icons.info(), tr("about.title"), () -> new AboutDialog(this).setVisible(true));
        right.add(Box.createHorizontalStrut(4));
        right.add(theme);
        right.add(about);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JComponent statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 18, 8, 18));
        status.setForeground(Theme.secondaryText());
        status.setIcon(Icons.info().withSize(14));
        status.setIconTextGap(6);
        bar.add(status, BorderLayout.WEST);
        JLabel version = new JLabel("v" + Settings.version());
        version.setForeground(new Color(0x94A3B8));
        // the version also opens "About"
        version.setToolTipText(tr("about.title"));
        version.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        version.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                new AboutDialog(MainFrame.this).setVisible(true);
            }
        });
        bar.add(version, BorderLayout.EAST);
        return bar;
    }
}
