package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;
import com.mediarenamer.ui.SettingsPages.Category;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

/**
 * Preferences, by category in a sidebar: General (interface language, theme), Data services (API keys, data
 * language), Target folders, Media servers (Jellyfin, Plex) and Tools. Every option has an ⓘ icon explaining what it is for. The pages are
 * shared with the setup wizard ({@link SettingsPages}); a single Save/Cancel applies to all of them.
 */
public class PreferencesDialog extends JDialog {

    /** Category open last (during the session), shown again when Preferences is reopened. */
    private static Category lastCategory = Category.GENERAL;

    private final SettingsPages pages = new SettingsPages(this);
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JList<Category> categories = new JList<>(Category.values());

    public PreferencesDialog(JFrame owner) {
        super(owner, tr("prefs.title"), true);
        for (Category c : Category.values()) {
            JComponent page = SettingsPages.withHeader(tr(c.key()), tr(c.key() + ".text"), pages.page(c), 470);
            // the page follows the width of the window (no horizontal scrolling); only vertical scrolling if needed
            WidthTrackingPanel holder = new WidthTrackingPanel();
            holder.add(page, BorderLayout.CENTER);
            holder.setBorder(BorderFactory.createEmptyBorder(18, 22, 12, 22));
            JScrollPane scroll = new JScrollPane(holder);
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            content.add(scroll, c.name());
        }

        categories.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categories.setFixedCellHeight(38);
        categories.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Category c = (Category) v;
                JLabel label = (JLabel) super.getListCellRendererComponent(l, tr(c.key()), i, s, false);
                // on the selected (accent) row the icon goes white like the text
                label.setIcon(icon(c).withSize(18).withColor(s ? java.awt.Color.WHITE : Theme.secondaryText()));
                label.setIconTextGap(10);
                label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return label;
            }
        });
        categories.addListSelectionListener(e -> {
            Category c = categories.getSelectedValue();
            if (c != null) {
                lastCategory = c;
                cards.show(content, c.name());
            }
        });
        JScrollPane side = new JScrollPane(categories);
        side.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.border()));
        side.setPreferredSize(new Dimension(210, 0));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.border()),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        JButton cancel = new JButton(tr("common.cancel"));
        cancel.addActionListener(e -> dispose());
        JButton save = new JButton(tr("common.save"));
        save.addActionListener(e -> save(owner));
        buttons.add(cancel);
        buttons.add(save);

        setLayout(new BorderLayout());
        add(side, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        categories.setSelectedValue(lastCategory, true);
        setSize(new Dimension(940, 600));
        setMinimumSize(new Dimension(760, 460));
        setLocationRelativeTo(owner);
    }

    /** Panel inside a scroll pane that always takes the viewport width (rows shrink instead of overflowing). Also used by the wizard. */
    static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int orientation, int direction) {
            return r.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Shows a category (for screenshots and tests). */
    void showCategory(Category c) {
        categories.setSelectedValue(c, true);
    }

    static Icons.VectorIcon icon(Category c) {
        return switch (c) {
            case GENERAL -> Icons.settings();
            case SERVICES -> Icons.search();
            case FOLDERS -> Icons.folder();
            case SERVERS -> Icons.tv();
            case TOOLS -> Icons.format();
        };
    }

    private void save(JFrame owner) {
        pages.save();
        Locale before = I18n.locale();
        String tag = pages.uiLanguageTag();
        Settings.get().put(Settings.UI_LANGUAGE, tag);
        Locale after = I18n.resolve(tag);
        Boolean dark = pages.darkTheme();
        boolean themeChanged = dark != null && dark != Theme.isDark();
        dispose();
        if (themeChanged) {
            Theme.toggle();
            if (owner instanceof MainFrame main) {
                main.themeChanged();
            }
        }
        if (!Objects.equals(after, before)) {
            languageChanged(owner, after);
        }
    }

    /**
     * Applies the new language: the following dialogs already use it; the main window is
     * rebuilt if the user wants (otherwise, when the application is opened again).
     */
    private static void languageChanged(JFrame owner, Locale l) {
        I18n.setLocale(l);
        I18n.applyToSwing();
        if (owner instanceof MainFrame main && JOptionPane.showConfirmDialog(owner, tr("prefs.uiLanguage.reload"), tr("prefs.uiLanguage"),
                JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            main.reload();
        }
    }
}
