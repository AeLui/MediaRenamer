package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;
import com.mediarenamer.media.MediaInfo;
import com.mediarenamer.media.Tools;
import com.mediarenamer.web.AcoustIDClient;
import com.mediarenamer.web.MediaServers;
import com.mediarenamer.web.JellyfinClient;
import com.mediarenamer.web.PlexClient;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
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
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * The settings pages shared by Preferences (one per category of its sidebar) and the setup wizard (data services and
 * media servers): every option has a label, an ⓘ icon whose tooltip says what it is for (the same text is the tooltip of the
 * field) and, when it applies, a button ("Get", "Choose…", "Test connection").
 *
 * The values start from the preferences, or from {@code initial} (the wizard keeps what was typed when it is rebuilt
 * in another language); {@link #save()} writes the text fields and the scan options of the media servers. Every option
 * remembers its starting value, so {@link #isDirty(Category)} tells which categories have unsaved changes.
 */
final class SettingsPages {

    /** Preferences categories, in the order of the sidebar. */
    enum Category {
        GENERAL, SERVICES, FOLDERS, SERVERS, TOOLS;

        String key() {
            return "prefs.cat." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final Window owner;
    private final Map<String, String> initial;
    final Map<String, JTextComponent> fields = new LinkedHashMap<>();
    /** Scan option of each media server, by settings key (JELLYFIN_SCAN, PLEX_SCAN). */
    private final Map<String, JCheckBox> scanBoxes = new LinkedHashMap<>();
    private final Map<String, Boolean> scanInitial = new HashMap<>();
    /** Interface language: Locale.ROOT = automatic (the system's). Only on the General page. */
    private JComboBox<Locale> uiLanguage;
    private JCheckBox darkTheme;
    /** Automatic update check at start-up (General page). */
    JCheckBox autoUpdate;
    /** Language of the downloaded data (Data services page). */
    private LanguagePicker dataLanguage;
    /** Category whose page is being built: the options created now belong to it. */
    private Category building;
    /** Per category, one check per option: true when it differs from its starting value. */
    private final Map<Category, List<BooleanSupplier>> dirtyChecks = new EnumMap<>(Category.class);
    private Runnable changeListener = () -> { };

    SettingsPages(Window owner) {
        this(owner, Map.of(), null);
    }

    /**
     * @param initial typed values that take precedence over the preferences; scans: scan options already chosen
     *            (missing = the saved preference)
     */
    SettingsPages(Window owner, Map<String, String> initial, Map<String, Boolean> scans) {
        this.owner = owner;
        this.initial = initial;
        for (String key : SCAN_NEEDS.keySet()) {
            Boolean v = scans == null ? null : scans.get(key);
            scanInitial.put(key, v != null ? v : Settings.get().getBoolean(key, false));
        }
    }

    /** Scan option of each server and the URL and key it needs. */
    private static final Map<String, List<String>> SCAN_NEEDS = Map.of(Settings.JELLYFIN_SCAN, List.of(Settings.JELLYFIN_URL, Settings.JELLYFIN_KEY),
            Settings.PLEX_SCAN, List.of(Settings.PLEX_URL, Settings.PLEX_TOKEN));

    /** The page of a category (rows only: the caller adds its own title). */
    JComponent page(Category c) {
        building = c;
        return switch (c) {
            case GENERAL -> general();
            case SERVICES -> services();
            case FOLDERS -> folders();
            case SERVERS -> servers();
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
        track(uiLanguage::getSelectedItem, r -> uiLanguage.addActionListener(e -> r.run()));
        darkTheme = new JCheckBox(tr("prefs.theme.dark"), Theme.isDark());
        track(darkTheme::isSelected, r -> darkTheme.addItemListener(e -> r.run()));
        g.row(tr("prefs.theme"), "info.theme", darkTheme, null, false);
        autoUpdate = new JCheckBox(tr("prefs.updates.auto"), Settings.get().getBoolean(Settings.UPDATES_AUTO_CHECK, true));
        track(autoUpdate::isSelected, r -> autoUpdate.addItemListener(e -> r.run()));
        g.row(tr("prefs.updates"), "info.updates", autoUpdate, null, false);
        return g.done();
    }

    private JComponent services() {
        Grid g = new Grid();
        for (ApiService api : ApiService.ALL) {
            JTextComponent f = text(api.settingsKey(), api.secret());
            g.row(tr(api.labelKey()), api.infoKey(), f, api.url() == null ? null : ApiService.getKeyButton(api.url()), true);
        }
        // searchable list of every language; empty = the system language
        dataLanguage = new LanguagePicker(tr("prefs.dataLanguage.auto", I18n.displayName(Locale.getDefault())));
        dataLanguage.setTag(initial.containsKey(Settings.LANGUAGE) ? initial.get(Settings.LANGUAGE) : Settings.get().get(Settings.LANGUAGE));
        track(dataLanguage::getTag, dataLanguage::onChange);
        g.row(tr("prefs.dataLanguage"), "info.dataLanguage", dataLanguage, null, true);
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

    /** Jellyfin, a separator and Plex: URL, key or token, "Test connection" and the automatic scan of each one. */
    private JComponent servers() {
        Grid g = new Grid();
        g.section("Jellyfin", false);
        JTextComponent url = text(Settings.JELLYFIN_URL, false);
        url.putClientProperty("JTextField.placeholderText", "http://192.168.1.10:8096");
        g.row(tr("prefs.jellyfin.url"), "info.jellyfin.url", url, null, true);
        // plain http to a host that is not local: the API key would travel unencrypted
        JLabel insecure = warning(tr("jellyfin.insecure.hint"));
        g.wide(insecure);
        JTextComponent key = text(Settings.JELLYFIN_KEY, true);
        JButton get = new JButton(tr("prefs.get"));
        get.addActionListener(e -> openApiKeysPage(url.getText(), get));
        g.row(tr("prefs.jellyfin.key"), "info.jellyfin.key", key, get, true);
        JLabel status = statusLabel();
        JButton test = ApiService.serverTestButton(() -> {
            String u = url.getText();
            String k = key.getText();
            return () -> new JellyfinClient(u, k).systemInfo();
        }, status, () -> { });
        g.row("", null, test, null, false);
        g.wide(status);
        JCheckBox jellyfinScan = scanBox(Settings.JELLYFIN_SCAN, tr("jellyfin.scan"));
        g.row(tr("prefs.jellyfin.scanLabel"), "info.jellyfin.scan", jellyfinScan, null, false);

        // "Get" needs the URL; "Test connection" and the scan option need the URL and the key
        onChange(() -> {
            boolean hasUrl = JellyfinClient.normalizeUrl(url.getText()) != null;
            boolean ready = hasUrl && !key.getText().isBlank();
            get.setEnabled(hasUrl);
            insecure.setVisible(JellyfinClient.isInsecureRemote(url.getText()));
            get.setToolTipText(hasUrl ? tr("prefs.jellyfin.get.tooltip") : tr("prefs.jellyfin.get.disabled"));
            test.setEnabled(ready);
            jellyfinScan.setEnabled(ready);
        }, url, key);

        g.section("Plex", true);
        JTextComponent plexUrl = text(Settings.PLEX_URL, false);
        plexUrl.putClientProperty("JTextField.placeholderText", "http://192.168.1.10:32400");
        g.row(tr("prefs.plex.url"), "info.plex.url", plexUrl, null, true);
        JLabel plexInsecure = warning(tr("plex.insecure.hint"));
        g.wide(plexInsecure);
        JTextComponent token = text(Settings.PLEX_TOKEN, true);
        JLabel plexStatus = statusLabel();
        // the sign-in fills the token (and the URL if empty): no need to look for it by hand
        g.row(tr("prefs.plex.token"), "info.plex.token", token, PlexSignIn.button(owner, plexUrl, token, plexStatus), true);
        JButton plexTest = ApiService.serverTestButton(() -> {
            String u = plexUrl.getText();
            String t = token.getText();
            return () -> new PlexClient(u, t).systemInfo();
        }, plexStatus, () -> { });
        g.row("", null, plexTest, null, false);
        g.wide(plexStatus);
        JCheckBox plexScan = scanBox(Settings.PLEX_SCAN, tr("plex.scan"));
        g.row(tr("prefs.jellyfin.scanLabel"), "info.plex.scan", plexScan, null, false);

        onChange(() -> {
            boolean ready = PlexClient.normalizeUrl(plexUrl.getText()) != null && !token.getText().isBlank();
            plexInsecure.setVisible(MediaServers.isInsecureRemote(PlexClient.normalizeUrl(plexUrl.getText())));
            plexTest.setEnabled(ready);
            plexScan.setEnabled(ready);
        }, plexUrl, token);
        return g.done();
    }

    private JCheckBox scanBox(String key, String text) {
        JCheckBox box = new JCheckBox(text, scanInitial.get(key));
        scanBoxes.put(key, box);
        track(box::isSelected, r -> box.addItemListener(e -> r.run()));
        return box;
    }

    private static JLabel warning(String text) {
        JLabel l = new JLabel("<html><div style='width:320px'>" + text + "</div></html>");
        l.setForeground(new Color(0xD68910));
        return l;
    }

    private static JLabel statusLabel() {
        JLabel status = new StatusLabel();
        status.setForeground(Theme.secondaryText());
        return status;
    }

    /**
     * Status line under "Test connection": wrapped to the page width (long messages would otherwise be cut) and hidden
     * while empty. The text
     * can carry names and errors from the server, so it is escaped and never interpreted as HTML.
     */
    static final class StatusLabel extends JLabel {
        /** Wrap width (CSS px: Swing draws them about a third wider). */
        private final int width;

        StatusLabel() {
            this(320);
        }

        StatusLabel(int width) {
            super(" ");
            this.width = width;
        }

        @Override
        public void setText(String text) {
            boolean empty = text == null || text.isBlank();
            super.setText(empty ? " " : "<html><div style='width:" + width + "px'>" + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</div></html>");
            // hidden while empty, so that it takes no room on the page
            setVisible(!empty);
        }
    }

    /** Runs update now and whenever one of the fields changes. */
    private static void onChange(Runnable update, JTextComponent... fields) {
        DocumentListener listener = new DocumentListener() {
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
        for (JTextComponent f : fields) {
            f.getDocument().addDocumentListener(listener);
        }
        update.run();
    }

    private JComponent tools() {
        Grid g = new Grid();
        toolRow(g, "prefs.mediainfo", "info.mediainfo", Settings.MEDIAINFO_PATH, Tools.MEDIAINFO, MediaInfo.detectedPath(), "MediaInfo");
        toolRow(g, "prefs.fpcalc", "info.fpcalc", Settings.FPCALC_PATH, Tools.FPCALC, AcoustIDClient.detectedFpcalc(), "fpcalc");
        JLabel status = new JLabel(tr("prefs.mediainfo.status", MediaInfo.isAvailable() ? tr("prefs.mediainfo.available") : tr("prefs.mediainfo.missing")));
        status.setForeground(Theme.secondaryText());
        g.wide(status);
        return g.done();
    }

    /**
     * Path of an external tool: the field (empty = automatic, the placeholder says what was found), a "Choose…" button that
     * opens a file chooser limited to the tool's file names, and a line that checks the typed path as it changes. The
     * tool is never run from here: only its name and existence are checked.
     */
    private void toolRow(Grid g, String labelKey, String infoKey, String key, List<String> names, Path detected, String tool) {
        JTextField f = (JTextField) text(key, false);
        f.putClientProperty("JTextField.placeholderText", tr("prefs.tool.placeholder"));
        f.putClientProperty("JTextField.showClearButton", true);
        JButton choose = new JButton(tr("presets.folder.choose"), Icons.folder().withSize(16));
        choose.addActionListener(e -> {
            String start = f.getText().isBlank() ? (detected == null ? null : detected.toString()) : f.getText().trim();
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
            if (start != null) {
                java.io.File s = new java.io.File(start);
                fc.setCurrentDirectory(s.isDirectory() ? s : s.getParentFile());
            }
            fc.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
            fc.setAcceptAllFileFilterUsed(false);
            fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override
                public boolean accept(java.io.File file) {
                    return file.isDirectory() || Tools.hasName(file.getName(), names);
                }

                @Override
                public String getDescription() {
                    return tool + " (" + String.join(", ", Tools.platformNames(names)) + ")";
                }
            });
            fc.setDialogTitle(tr("prefs.tool.dialog", tool));
            if (fc.showOpenDialog(owner) == javax.swing.JFileChooser.APPROVE_OPTION) {
                f.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        g.row(tr(labelKey), infoKey, f, choose, true);
        // narrower than the other status lines: it sits under the field and its button, in a page that must not scroll sideways
        JLabel check = new StatusLabel(240);
        g.wide(check);
        onChange(() -> {
            Tools.Status st = Tools.check(f.getText(), names);
            // empty: the line says what the automatic search finds (the full path does not fit in the placeholder)
            check.setText(switch (st) {
                case AUTO -> detected == null ? tr("prefs.tool.auto.none") : tr("prefs.tool.auto", detected);
                case OK -> tr("prefs.tool.ok", tool);
                case MISSING -> tr("prefs.tool.missing");
                case WRONG -> tr("prefs.tool.wrong", tool, String.join(", ", Tools.platformNames(names)));
            });
            check.setForeground(switch (st) {
                case AUTO -> Theme.secondaryText();
                case OK -> new Color(0x27AE60);
                default -> new Color(0xD68910);
            });
        }, f);
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
        track(() -> f.getText().trim(), r -> onChange(r, f));
        return f;
    }

    /**
     * Remembers the starting value of an option of the page being built; listen registers the given runnable as its
     * change listener (it tells the dialog to repaint its marks).
     */
    private void track(Supplier<Object> value, Consumer<Runnable> listen) {
        Object start = value.get();
        dirtyChecks.computeIfAbsent(building, c -> new ArrayList<>()).add(() -> !Objects.equals(start, value.get()));
        listen.accept(() -> changeListener.run());
    }

    /** Runs r whenever any option changes (after the change). */
    void setChangeListener(Runnable r) {
        changeListener = r;
    }

    /** True if an option of the category differs from the value it had when the page was built. */
    boolean isDirty(Category c) {
        return dirtyChecks.getOrDefault(c, List.of()).stream().anyMatch(BooleanSupplier::getAsBoolean);
    }

    /** True if any category has unsaved changes. */
    boolean isDirty() {
        return dirtyChecks.keySet().stream().anyMatch(this::isDirty);
    }

    /** Typed values of the pages built so far (by settings key). */
    Map<String, String> values() {
        Map<String, String> v = new HashMap<>(initial);
        fields.forEach((k, f) -> v.put(k, f.getText().trim()));
        if (dataLanguage != null) {
            v.put(Settings.LANGUAGE, dataLanguage.getTag() == null ? "" : dataLanguage.getTag());
        }
        return v;
    }

    /** Scan options of the media servers as currently shown (or the initial values if the page was not built). */
    Map<String, Boolean> scans() {
        Map<String, Boolean> m = new HashMap<>(scanInitial);
        scanBoxes.forEach((k, box) -> m.put(k, box.isSelected()));
        return m;
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

    /** Saves the text fields of the built pages and the scan options (each one only possible with its URL and key). */
    void save() {
        if (autoUpdate != null) {
            Settings.get().put(Settings.UPDATES_AUTO_CHECK, Boolean.toString(autoUpdate.isSelected()));
        }
        Map<String, String> v = values();
        v.forEach((k, value) -> Settings.get().put(k, value));
        scans().forEach((scanKey, on) -> {
            boolean ready = SCAN_NEEDS.get(scanKey).stream().noneMatch(k -> v.getOrDefault(k, Settings.get().get(k, "")).isBlank());
            Settings.get().put(scanKey, Boolean.toString(on && ready));
        });
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

        /** Title of a group of rows (a server), with a separator above if it is not the first. */
        void section(String title, boolean separator) {
            c.gridy = row++;
            c.gridx = 0;
            c.gridwidth = 4;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            JPanel head = new JPanel(new BorderLayout(0, 8));
            head.setOpaque(false);
            if (separator) {
                head.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
                head.add(new JSeparator(), BorderLayout.NORTH);
            }
            JLabel t = new JLabel(title);
            t.putClientProperty("FlatLaf.styleClass", "h4");
            head.add(t, BorderLayout.CENTER);
            form.add(head, c);
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
