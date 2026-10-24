package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;
import com.mediarenamer.ui.SettingsPages.Category;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Setup wizard shown on the first run of the GUI, when there is no preferences file yet. Three steps: interface language, data services (the API keys, each with an ⓘ explaining what it is
 * for and a "Get" button) and media servers (Jellyfin, Plex). The last two are the same pages as in Preferences ({@link SettingsPages}).
 * Everything is optional and can be changed later in Preferences.
 *
 * Choosing a language rebuilds the wizard in that language right away. "Finish" saves everything; "Skip setup" or
 * closing the window saves only the language, so that the wizard is not shown again.
 */
public class SetupWizard extends JDialog {

    private static final int STEPS = 3;

    private int step;
    private Locale language;
    /** Typed values by settings key (they survive going back and forth and the rebuild after a language change). */
    private Map<String, String> values = new HashMap<>();
    private Map<String, Boolean> scans;
    private SettingsPages pages;

    public SetupWizard() {
        super((java.awt.Frame) null, ModalityType.APPLICATION_MODAL);
        setIconImages(Logo.windowIcons());
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                skip();
            }
        });
        // the language the interface is actually shown in (system, preference or MEDIARENAMER_UI_LANGUAGE)
        language = I18n.locale();
        rebuild();
        setLocationRelativeTo(null);
    }

    /** Current step (0 = language, 1 = data services, 2 = media servers); for screenshots and tests. */
    int getStep() {
        return step;
    }

    /** Goes to a step keeping the typed values. */
    void showStep(int s) {
        step = Math.max(0, Math.min(STEPS - 1, s));
        rebuild();
    }

    // ------------------------------------------------------------------ layout

    private void rebuild() {
        if (pages != null) {
            values = pages.values();
            scans = pages.scans();
        }
        pages = new SettingsPages(this, values, scans);
        setTitle(tr("wizard.title"));
        JPanel root = new JPanel(new BorderLayout());
        root.add(header(), BorderLayout.NORTH);
        root.add(steps(), BorderLayout.WEST);
        JComponent page = switch (step) {
            case 0 -> languagePage();
            case 1 -> SettingsPages.withHeader(tr("wizard.services.title"), tr("wizard.services.text"), pages.page(Category.SERVICES), 430);
            default -> SettingsPages.withHeader(tr("wizard.servers.title"), tr("wizard.servers.text"), pages.page(Category.SERVERS), 430);
        };
        page.setBorder(BorderFactory.createEmptyBorder(18, 22, 12, 24));
        // the media servers step is taller than the window: vertical scrolling only, as in Preferences
        PreferencesDialog.WidthTrackingPanel holder = new PreferencesDialog.WidthTrackingPanel();
        holder.add(page, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);
        root.add(buttons(), BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().registerKeyboardAction(e -> skip(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Dimension size = new Dimension(900, 620);
        setMinimumSize(size);
        setSize(size);
        revalidate();
        repaint();
    }

    private JComponent header() {
        JPanel h = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.surface());
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        h.setBorder(BorderFactory.createEmptyBorder(16, 8, 14, 16));
        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Logo.paint((Graphics2D) g, 0, 0, 48);
            }
        };
        logo.setPreferredSize(new Dimension(48, 48));
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(tr("wizard.title"));
        title.putClientProperty("FlatLaf.styleClass", "h2");
        JLabel subtitle = new JLabel(tr("wizard.subtitle"));
        subtitle.setForeground(Theme.secondaryText());
        titles.add(title);
        titles.add(subtitle);
        h.add(logo);
        h.add(titles);
        return h;
    }

    /** Left column with the steps; the current one highlighted, the done ones with a check. */
    private JComponent steps() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.border()),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)));
        p.setPreferredSize(new Dimension(210, 0));
        String[] names = { tr("wizard.step.language"), tr("wizard.step.services"), tr("wizard.step.servers") };
        for (int i = 0; i < names.length; i++) {
            p.add(new StepLabel(i, names[i], i == step, i < step));
            p.add(Box.createVerticalStrut(10));
        }
        return p;
    }

    /** "(1) Language" with a round number badge. */
    private static final class StepLabel extends JComponent {
        private final int index;
        private final String text;
        private final boolean current;
        private final boolean done;

        StepLabel(int index, String text, boolean current, boolean done) {
            this.index = index;
            this.text = text;
            this.current = current;
            this.done = done;
            setPreferredSize(new Dimension(180, 30));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            Font f = getFont() != null ? getFont() : javax.swing.UIManager.getFont("Label.font");
            g.setColor(current || done ? Theme.ACCENT : Theme.border());
            g.fillOval(0, 3, 24, 24);
            g.setColor(current || done ? Color.WHITE : Theme.secondaryText());
            if (done) {
                Icons.check().withSize(14).withColor(Color.WHITE).paintIcon(this, g, 5, 8);
            } else {
                g.setFont(f.deriveFont(Font.BOLD));
                String n = String.valueOf(index + 1);
                g.drawString(n, 12 - g.getFontMetrics().stringWidth(n) / 2, 20);
            }
            g.setFont(f.deriveFont(current ? Font.BOLD : Font.PLAIN));
            g.setColor(current ? javax.swing.UIManager.getColor("Label.foreground") : Theme.secondaryText());
            g.drawString(text, 34, 20);
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ step 1: language

    private JComponent languagePage() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        ButtonGroup group = new ButtonGroup();
        for (Locale l : I18n.SUPPORTED) {
            JRadioButton r = new JRadioButton(I18n.nativeName(l), l.equals(language));
            r.putClientProperty("FlatLaf.style", "font: +2");
            r.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
            r.addActionListener(e -> setLanguage(l));
            group.add(r);
            list.add(r);
        }
        return SettingsPages.withHeader(tr("wizard.language.title"), tr("wizard.language.text"), list, 430);
    }

    /** Applies the language at once: the wizard (and then the whole application) is shown in it. */
    void setLanguage(Locale l) {
        if (l.equals(language)) {
            return;
        }
        language = l;
        I18n.setLocale(l);
        I18n.applyToSwing();
        rebuild();
    }

    // ------------------------------------------------------------------ buttons and saving

    private JComponent buttons() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JButton skip = new JButton(tr("wizard.skip"));
        skip.setToolTipText(tr("wizard.skip.tooltip"));
        skip.putClientProperty("JButton.buttonType", "borderless");
        skip.addActionListener(e -> skip());
        bar.add(skip, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton back = new JButton(tr("wizard.back"));
        back.setEnabled(step > 0);
        back.addActionListener(e -> showStep(step - 1));
        boolean last = step == STEPS - 1;
        JButton next = new JButton(tr(last ? "wizard.finish" : "wizard.next"));
        next.setIcon(last ? Icons.check().withSize(16) : null);
        next.setHorizontalTextPosition(SwingConstants.LEADING);
        next.addActionListener(e -> {
            if (last) {
                finish();
            } else {
                showStep(step + 1);
            }
        });
        right.add(back);
        right.add(next);
        bar.add(right, BorderLayout.EAST);
        getRootPane().setDefaultButton(next);
        return bar;
    }

    /** Saves the language, the keys and the media servers, and closes. */
    void finish() {
        pages.save();
        saveLanguage();
        dispose();
    }

    /** Saves only the language (so that the wizard is not shown again) and closes. */
    void skip() {
        saveLanguage();
        dispose();
    }

    private void saveLanguage() {
        Settings.get().put(Settings.UI_LANGUAGE, language.toLanguageTag());
    }

    /** Settings keys of the fields of the current step (for tests). */
    List<String> fieldKeys() {
        return List.copyOf(pages.fields.keySet());
    }
}
