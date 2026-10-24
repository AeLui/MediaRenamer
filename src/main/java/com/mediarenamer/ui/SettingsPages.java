package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;
import com.mediarenamer.media.MediaInfo;
import com.mediarenamer.web.JellyfinClient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * The settings pages shared by Preferences (one per category of its sidebar) and the setup wizard (data services and
 * Jellyfin): every option has a label, an ⓘ icon whose tooltip says what it is for (the same text is the tooltip of the
 * field) and, when it applies, a button ("Get", "Choose…", "Test connection").
 *
 * The values start from the preferences, or from {@code initial} (the wizard keeps what was typed when it is rebuilt
 * in another language); {@link #save()} writes the text fields and the Jellyfin scan option.
 */
final class SettingsPages {

    /** Preferences categories, in the order of the sidebar. */
    enum Category {
        GENERAL, SERVICES, FOLDERS, JELLYFIN, TOOLS;

        String key() {
            return "prefs.cat." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Window owner;
    private final Map<String, String> initial;
    final Map<String, JTextComponent> fields = new LinkedHashMap<>();
    private JCheckBox jellyfinScan;
    private boolean scanInitial;
    /** Interface language: Locale.ROOT = automatic (the system's). Only on the General page. */
    private JComboBox<Locale> uiLanguage;
    private JCheckBox darkTheme;

    SettingsPages(Window owner) {
        this(owner, Map.of(), null);
    }

    /** @param initial typed values that take precedence over the preferences; scan: null = the saved preference */
    SettingsPages(Window owner, Map<String, String> initial, Boolean scan) {
        this.owner = owner;
        this.initial = initial;
        this.scanInitial = scan != null ? scan : Settings.get().getBoolean(Settings.JELLYFIN_SCAN, false);
    }

    /** The page of a category (rows only: the caller adds its own title). */
    JComponent page(Category c) {
        return switch (c) {
            case GENERAL -> general();
            case SERVICES -> services();
            case FOLDERS -> folders();
            case JELLYFIN -> jellyfin();
            case TOOLS -> tools();
        };
    }

    // ------------------------------------------------------------------ pages

    private JComponent general() {
        Grid g = new Grid();
        uiLanguage = new JComboBox<>();
        uiLanguage.addItem(Locale.ROOT);
        I18n.SUPPORTED.forEach(uiLanguage::addItem);
        String saved = Settings.get().get(Settings.UI_LANGUAGE);
        uiLanguage.setSelectedItem(saved == null ? Locale.ROOT : I18n.resolve(saved));
        uiLanguage.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                String text = v instanceof Locale loc && loc != Locale.ROOT ? I18n.nativeName(loc) : tr("prefs.uiLanguage.auto", I18n.nativeName(I18n.systemLocale()));
                return super.getListCellRendererComponent(l, text, i, s, f);
            }
        });
        g.row(tr("prefs.uiLanguage"), "info.uiLanguage", uiLanguage, null, false);
        darkTheme = new JCheckBox(tr("prefs.theme.dark"), Theme.isDark());
        g.row(tr("prefs.theme"), "info.theme", darkTheme, null, false);
        return g.done();
    }

    private JComponent services() {
        Grid g = new Grid();
        for (ApiService api : ApiService.ALL) {
            JTextComponent f = text(api.settingsKey(), api.secret());
            g.row(tr(api.labelKey()), api.infoKey(), f, api.url() == null ? null : ApiService.getKeyButton(api.url()), true);
        }
        g.row(tr("prefs.dataLanguage"), "info.dataLanguage", text(Settings.LANGUAGE, false), null, true);
        return g.done();
    }

    private JComponent folders() {
        Grid g = new Grid();
        String[][] rows = { { "prefs.folder.movies", Settings.DEST_MOVIES, "info.folder.movies" }, { "prefs.folder.series", Settings.DEST_SERIES, "info.folder.series" },
                { "prefs.folder.anime", Settings.DEST_ANIME, "info.folder.anime" }, { "prefs.folder.music", Settings.DEST_MUSIC, "info.folder.music" },
                { "prefs.folder.other", Settings.DEST_OTHER, "info.folder.other" } };
        for (String[] r : rows) {
            JTextField f = (JTextField) text(r[1], false);
            f.putClientProperty("JTextField.placeholderText", tr("prefs.folder.placeholder"));
            g.row(tr(r[0]), r[2], PresetsDialog.folderField(owner, f), null, true);
            f.setToolTipText(tooltip(r[2]));
        }
        // wrapped to a fixed width so that it never makes the page wider than the window
        String hintText = tr("prefs.folders.hint").replace("<html>", "").replace("</html>", "");
        JLabel hint = new JLabel("<html><div style='width:320px'>" + hintText + "</div></html>");
        hint.setForeground(Theme.secondaryText());
        g.wide(hint);
        return g.done();
    }

    private JComponent jellyfin() {
        Grid g = new Grid();
        JTextComponent url = text(Settings.JELLYFIN_URL, false);
        url.putClientProperty("JTextField.placeholderText", "http://192.168.1.10:8096");
        g.row(tr("prefs.jellyfin.url"), "info.jellyfin.url", url, null, true);
        // plain http to a host that is not local: the API key would travel unencrypted
        JLabel insecure = new JLabel("<html><div style='width:320px'>" + tr("jellyfin.insecure.hint") + "</div></html>");
        insecure.setForeground(new Color(0xD68910));
        g.wide(insecure);
        JTextComponent key = text(Settings.JELLYFIN_KEY, true);
        JButton get = new JButton(tr("prefs.get"));
        get.addActionListener(e -> openApiKeysPage(url.getText(), get));
        g.row(tr("prefs.jellyfin.key"), "info.jellyfin.key", key, get, true);

        JLabel status = new JLabel(" ");
        status.setForeground(Theme.secondaryText());
        JButton test = ApiService.jellyfinTestButton(url::getText, key::getText, status, () -> { });
        JPanel testLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        testLine.setOpaque(false);
        testLine.add(test);
        testLine.add(Box.createHorizontalStrut(10));
        testLine.add(status);
        g.row("", null, testLine, null, false);

        jellyfinScan = new JCheckBox(tr("jellyfin.scan"), scanInitial);
        g.row(tr("prefs.jellyfin.scanLabel"), "info.jellyfin.scan", jellyfinScan, null, false);

        // "Get" needs the URL; "Test connection" and the scan option need the URL and the key
        Runnable update = () -> {
            boolean hasUrl = JellyfinClient.normalizeUrl(url.getText()) != null;
            boolean ready = hasUrl && !key.getText().isBlank();
            get.setEnabled(hasUrl);
            insecure.setVisible(JellyfinClient.isInsecureRemote(url.getText()));
            get.setToolTipText(hasUrl ? tr("prefs.jellyfin.get.tooltip") : tr("prefs.jellyfin.get.disabled"));
            test.setEnabled(ready);
            jellyfinScan.setEnabled(ready);
        };
        DocumentListener onChange = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update.run();
            }
        };
        url.getDocument().addDocumentListener(onChange);
        key.getDocument().addDocumentListener(onChange);
        update.run();
        return g.done();
    }

    private JComponent tools() {
        Grid g = new Grid();
        g.row(tr("prefs.mediainfo"), "info.mediainfo", text(Settings.MEDIAINFO_PATH, false), null, true);
        g.row(tr("prefs.fpcalc"), "info.fpcalc", text(Settings.FPCALC_PATH, false), null, true);
        JLabel status = new JLabel(tr("prefs.mediainfo.status", MediaInfo.isAvailable() ? tr("prefs.mediainfo.available") : tr("prefs.mediainfo.missing")));
        status.setForeground(Theme.secondaryText());
        g.wide(status);
        return g.done();
    }

    /** Opens the API keys page of the Jellyfin server (the route depends on its version: asked in the background). */
    private static void openApiKeysPage(String url, JButton button) {
        button.setEnabled(false);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return JellyfinClient.apiKeysPage(url);
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(get()));
                } catch (Exception ex) {
                    // no browser available (or invalid URL): nothing to open
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ values

    private JTextComponent text(String key, boolean secret) {
        // few columns: the fields grow with the window (GridBag), but they never force it to be wider
        JTextComponent f = secret ? new JPasswordField(16) : new JTextField(16);
        f.setText(initial.containsKey(key) ? initial.get(key) : Settings.get().get(key, ""));
        fields.put(key, f);
        return f;
    }

    /** Typed values of the pages built so far (by settings key). */
    Map<String, String> values() {
        Map<String, String> v = new HashMap<>(initial);
        fields.forEach((k, f) -> v.put(k, f.getText().trim()));
        return v;
    }

    /** Jellyfin scan option as currently shown (or the initial value if the page was not built). */
    boolean jellyfinScan() {
        return jellyfinScan != null ? jellyfinScan.isSelected() : scanInitial;
    }

    /** Selected interface language tag (null = automatic), or the saved one if the General page was not built. */
    String uiLanguageTag() {
        if (uiLanguage == null) {
            return Settings.get().get(Settings.UI_LANGUAGE);
        }
        Locale l = (Locale) uiLanguage.getSelectedItem();
        return l == null || l == Locale.ROOT ? null : l.toLanguageTag();
    }

    /** Dark theme as chosen on the General page (null if the page was not built). */
    Boolean darkTheme() {
        return darkTheme == null ? null : darkTheme.isSelected();
    }

    /** Saves the text fields of the built pages and the Jellyfin scan option (only possible with URL and key). */
    void save() {
        Map<String, String> v = values();
        v.forEach((k, value) -> Settings.get().put(k, value));
        boolean ready = !v.getOrDefault(Settings.JELLYFIN_URL, Settings.get().get(Settings.JELLYFIN_URL, "")).isBlank()
                && !v.getOrDefault(Settings.JELLYFIN_KEY, Settings.get().get(Settings.JELLYFIN_KEY, "")).isBlank();
        Settings.get().put(Settings.JELLYFIN_SCAN, Boolean.toString(jellyfinScan() && ready));
    }

    private static String tooltip(String infoKey) {
        return "<html><div style='width:300px'>" + tr(infoKey) + "</div></html>";
    }

    // ------------------------------------------------------------------ layout

    /** Rows of label · ⓘ · field · optional button. */
    private static final class Grid {
        private final JPanel form = new JPanel(new GridBagLayout());
        private final GridBagConstraints c = new GridBagConstraints();
        private int row;

        Grid() {
            form.setOpaque(false);
            c.insets = new Insets(5, 4, 5, 4);
            c.anchor = GridBagConstraints.WEST;
        }

        void row(String label, String infoKey, JComponent field, JComponent button, boolean stretch) {
            c.gridy = row++;
            c.gridx = 0;
            c.gridwidth = 1;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            form.add(new JLabel(label), c);
            c.gridx = 1;
            if (infoKey != null) {
                form.add(ApiService.infoIcon(infoKey), c);
                field.setToolTipText(tooltip(infoKey));
            }
            c.gridx = 2;
            c.gridwidth = button == null ? 2 : 1;
            c.fill = stretch ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            c.weightx = 1;
            form.add(field, c);
            if (button != null) {
                c.gridx = 3;
                c.gridwidth = 1;
                c.fill = GridBagConstraints.NONE;
                c.weightx = 0;
                form.add(button, c);
            }
        }

        /** A line under the fields (hints, status). */
        void wide(JComponent comp) {
            c.gridy = row++;
            c.gridx = 2;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            form.add(comp, c);
        }

        JComponent done() {
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(form, BorderLayout.NORTH);
            return wrap;
        }
    }

    /** Page header: title and description (used by Preferences and the wizard). */
    static JComponent withHeader(String title, String text, JComponent body, int textWidth) {
        JPanel p = new JPanel(new BorderLayout(0, 14));
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.putClientProperty("FlatLaf.styleClass", "h3");
        JLabel d = new JLabel("<html><div style='width:" + textWidth + "px'>" + text + "</div></html>");
        d.setForeground(Theme.secondaryText());
        head.add(t);
        head.add(Box.createVerticalStrut(6));
        head.add(d);
        p.add(head, BorderLayout.NORTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }
}
