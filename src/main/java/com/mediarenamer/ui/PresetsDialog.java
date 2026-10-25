package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.Preset;
import com.mediarenamer.rename.Presets;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.JellyfinClient;
import com.mediarenamer.web.PlexClient;
import com.mediarenamer.web.SortOrder;
import com.mediarenamer.web.WebServices;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Profile management: create, edit and delete. Changes are kept per profile while moving between them (a modified
 * profile is shown in the "modified" color); Save stores all of them at once, and closing with unsaved changes asks
 * whether to save or discard them. The media type (movies, series, music) filters the data sources and shows only the
 * fields that make sense for it.
 */
public class PresetsDialog extends JDialog {

    /** "(don't change)": the preset does not set the option (for the scan combos: the Preferences one is used). */
    private final String keepText = tr("presets.keep");
    private final String yes = tr("common.yes");
    private final String no = tr("common.no");

    private final DefaultListModel<Preset> model = new DefaultListModel<>();
    final JList<Preset> list = new JList<>(model);
    final JTextField name = new JTextField(30);
    private final JTextField format = new JTextField(40);
    /** Media type of the profile: decides the data sources offered and the fields shown. */
    final JComboBox<Mode> type = new JComboBox<>(TYPES.toArray(Mode[]::new));
    final JComboBox<String> database = new JComboBox<>();
    final JComboBox<String> order = new JComboBox<>();
    final JComboBox<String> matchMode = new JComboBox<>(new String[] { keepText, "Strict", "Opportunistic" });
    final LanguagePicker language = new LanguagePicker(keepText);
    private final JTextField output = new JTextField(32);
    private final JComboBox<String> jellyfin = new JComboBox<>(new String[] { keepText, yes, no });
    private final JComboBox<String> plex = new JComboBox<>(new String[] { keepText, yes, no });
    /** List entry shown in the form (a saved profile or a new one not saved yet). */
    private Preset editing;
    /** By profile id: the form as it was when the profile was first shown (the saved values). */
    private final Map<String, Preset> baseline = new HashMap<>();
    /** By profile id: the form with unsaved changes. */
    private final Map<String, Preset> drafts = new LinkedHashMap<>();
    /** True while show() fills the form: those changes are not the user's. */
    private boolean loading;
    /** Label of each form field, to hide both when the field does not apply to the media type. */
    private final Map<JComponent, JLabel> labels = new HashMap<>();
    /** Media type whose data sources are in the combo. */
    private Mode shownType;

    /** Media types a profile can have, in the combo's order. */
    static final List<Mode> TYPES = List.of(Mode.Movie, Mode.Episode, Mode.Music);

    public PresetsDialog(JFrame owner) {
        super(owner, tr("rename.presets"), true);
        type.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v == null ? "" : tr("mode." + ((Mode) v).name()), i, s, f);
            }
        });
        type.addActionListener(e -> typeChanged());
        order.addItem(keepText);
        for (SortOrder o : SortOrder.values()) {
            order.addItem(o.name());
        }

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                show(list.getSelectedValue());
            }
        });
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Preset p = (Preset) v;
                Preset shown = drafts.getOrDefault(p.id(), p);
                boolean dirty = drafts.containsKey(p.id());
                String text = shown.name() == null || shown.name().isBlank() ? tr("presets.untitled") : shown.name();
                JLabel label = (JLabel) super.getListCellRendererComponent(l, text + (dirty ? "  •" : ""), i, s, f);
                if (dirty && !s) {
                    label.setForeground(Theme.MODIFIED);
                }
                return label;
            }
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new java.awt.Dimension(220, 300));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        row = row(form, c, row, tr("presets.name"), name);
        row = row(form, c, row, tr("presets.type"), type);
        row = row(form, c, row, tr("presets.database"), database);
        output.putClientProperty("JTextField.placeholderText", tr("presets.folder.placeholder"));
        row = row(form, c, row, tr("presets.folder"), folderField(this, output));
        format.putClientProperty("JTextField.placeholderText", "{ny}/{n} - {sxe} - {t}");
        row = row(form, c, row, tr("rename.format"), formatField());
        JLabel hint = new JLabel(tr("presets.hint"));
        hint.setForeground(Theme.secondaryText());
        c.gridx = 1;
        c.gridy = row++;
        form.add(hint, c);
        row = row(form, c, row, tr("episode.order"), order);
        row = row(form, c, row, tr("match.mode"), matchMode);
        // empty = do not change the language, shown as "(don't change)" like in the combos; typing searches the list
        row = row(form, c, row, tr("presets.language"), language);
        // the preset option takes precedence over the Preferences one; it can only be changed with Jellyfin configured
        row = row(form, c, row, tr("jellyfin.scan"), jellyfin);
        boolean jellyfinReady = JellyfinClient.isConfigured();
        jellyfin.setEnabled(jellyfinReady);
        jellyfin.setToolTipText(jellyfinReady
                ? tr("presets.jellyfin.tooltip", com.mediarenamer.app.Settings.get().getBoolean(com.mediarenamer.app.Settings.JELLYFIN_SCAN, false) ? yes : no)
                : tr("presets.jellyfin.disabled"));
        // same for Plex, independent of Jellyfin
        row(form, c, row, tr("plex.scan"), plex);
        boolean plexReady = PlexClient.isConfigured();
        plex.setEnabled(plexReady);
        plex.setToolTipText(plexReady
                ? tr("presets.jellyfin.tooltip", com.mediarenamer.app.Settings.get().getBoolean(com.mediarenamer.app.Settings.PLEX_SCAN, false) ? yes : no)
                : tr("presets.plex.disabled"));
        listenToForm();

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(button(tr("presets.new"), this::addNew));
        buttons.add(button(tr("presets.delete"), this::delete));
        buttons.add(button(tr("common.save"), this::save));
        buttons.add(button(tr("common.close"), this::close));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(listScroll, BorderLayout.WEST);
        // at the top: hiding the rows of another media type does not move the others
        JPanel formTop = new JPanel(new BorderLayout());
        formTop.add(form, BorderLayout.NORTH);
        content.add(formTop, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        add(content);
        // Close, Escape and the window's X ask first when there are unsaved changes
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                close();
            }
        });
        getRootPane().registerKeyboardAction(e -> close(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        reload();
        // sized with every row visible (series), so a type with more rows fits after switching
        applyType(Mode.Episode);
        pack();
        if (model.isEmpty()) {
            addNew();
        } else {
            list.setSelectedIndex(0);
        }
        setLocationRelativeTo(owner);
    }

    private int row(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        JLabel l = new JLabel(label);
        labels.put(field, l);
        form.add(l, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(field, c);
        return row + 1;
    }

    /** Text field with a "Choose…" button that opens a folder chooser. */
    static JPanel folderField(java.awt.Component parent, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);
        JButton choose = new JButton(tr("presets.folder.choose"), Icons.folder().withSize(16));
        choose.addActionListener(e -> {
            javax.swing.JFileChooser fc = new javax.swing.JFileChooser(field.getText().isBlank() ? null : field.getText());
            fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle(tr("presets.folder.dialog"));
            if (fc.showOpenDialog(parent) == javax.swing.JFileChooser.APPROVE_OPTION) {
                field.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        field.putClientProperty("JTextField.showClearButton", true);
        p.add(field, BorderLayout.CENTER);
        p.add(choose, BorderLayout.EAST);
        return p;
    }

    /** Format field with an "Editor…" button: the main window's format editor, with the mode of the chosen data source. */
    private JPanel formatField() {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setOpaque(false);
        JButton edit = new JButton(tr("presets.format.edit"), Icons.format().withSize(16));
        edit.setToolTipText(tr("presets.format.edit.tooltip"));
        edit.addActionListener(e -> {
            FormatDialog d = FormatDialog.forProfile(this, modeOf((String) database.getSelectedItem()), format.getText().trim());
            d.setVisible(true);
            if (d.getResult() != null) {
                format.setText(d.getResult());
            }
        });
        p.add(format, BorderLayout.CENTER);
        p.add(edit, BorderLayout.EAST);
        return p;
    }

    /** Data sources offered for a media type (identifiers, in menu order). */
    static List<String> sourcesFor(Mode type) {
        return type.getDatasources().stream().map(Datasource::getIdentifier).toList();
    }

    /** Media type of a profile: the one of its data source (old "file" profiles and unknown sources: movies). */
    static Mode typeOf(Preset p) {
        Mode m = p == null ? Mode.Movie : p.getMode();
        return TYPES.contains(m) ? m : Mode.Movie;
    }

    /** True if the field applies to the media type: episode order only for series; matching mode and language not for music. */
    static boolean applies(Mode type, String field) {
        return switch (field) {
            case "order" -> type == Mode.Episode;
            case "matchMode", "language" -> type != Mode.Music;
            default -> true;
        };
    }

    /** The user chose another type: its sources, its default format if the format was the previous type's, its fields. */
    private void typeChanged() {
        Mode t = (Mode) type.getSelectedItem();
        if (loading || t == null || t == shownType) {
            return;
        }
        Mode previous = shownType;
        String f = format.getText().trim();
        applyType(t);
        database.setSelectedItem(t.getDefaultDatasource().getIdentifier());
        if (f.isEmpty() || previous != null && (f.equals(previous.getFormat()) || f.equals(previous.getDefaultFormat()))) {
            format.setText(t.getFormat());
        }
        formChanged();
    }

    /** Fills the source combo with the type's sources and shows only the fields that apply to it (the hidden ones are cleared). */
    private void applyType(Mode t) {
        shownType = t;
        boolean wasLoading = loading;
        loading = true;
        try {
            database.removeAllItems();
            sourcesFor(t).forEach(database::addItem);
            setApplies(order, applies(t, "order"));
            setApplies(matchMode, applies(t, "matchMode"));
            setApplies(language, applies(t, "language"));
            format.putClientProperty("JTextField.placeholderText", t.getDefaultFormat());
        } finally {
            loading = wasLoading;
        }
    }

    private void setApplies(JComponent field, boolean applies) {
        field.setVisible(applies);
        labels.get(field).setVisible(applies);
        if (!applies) {
            if (field instanceof JComboBox<?> box) {
                box.setSelectedItem(keepText);
            } else if (field == language) {
                language.setTag(null);
            }
        }
    }

    /** Mode of a data source name (the "file" entry and unknown names: File mode). */
    static Mode modeOf(String database) {
        Datasource d = database == null || database.equalsIgnoreCase("file") ? null : WebServices.get(database);
        return d == null ? Mode.File : Mode.forDatasource(d);
    }

    private static JButton button(String text, Runnable r) {
        JButton b = new JButton(text);
        b.addActionListener(e -> r.run());
        return b;
    }

    /** Every form change updates the draft of the profile being edited (and its mark in the list). */
    private void listenToForm() {
        DocumentListener doc = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                formChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                formChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                formChanged();
            }
        };
        for (JTextField f : new JTextField[] { name, format, output }) {
            f.getDocument().addDocumentListener(doc);
        }
        for (JComboBox<?> box : new JComboBox<?>[] { database, order, matchMode, jellyfin, plex }) {
            box.addActionListener(e -> formChanged());
        }
        language.onChange(this::formChanged);
    }

    private void formChanged() {
        if (loading || editing == null) {
            return;
        }
        Preset f = formPreset();
        if (f.equals(baseline.get(editing.id()))) {
            drafts.remove(editing.id());
        } else {
            drafts.put(editing.id(), f);
        }
        list.repaint();
    }

    /** The form as a profile, with the id of the entry being edited. */
    private Preset formPreset() {
        Mode t = (Mode) type.getSelectedItem();
        return new Preset(editing.id(), name.getText().trim(), format.getText().trim(), (String) database.getSelectedItem(),
                applies(t, "order") ? keep(order) : null, applies(t, "matchMode") ? keep(matchMode) : null, applies(t, "language") ? language.getTag() : null,
                output.getText().trim().isEmpty() ? null : output.getText().trim(), scanOption(jellyfin), scanOption(plex));
    }

    private void reload() {
        model.clear();
        Presets.get().list().forEach(model::addElement);
    }

    /** Shows an entry in the form: its unsaved changes if it has them, otherwise its saved values. */
    private void show(Preset entry) {
        editing = entry;
        Preset p = entry == null ? null : drafts.getOrDefault(entry.id(), entry);
        loading = true;
        try {
            name.setText(p == null ? "" : p.name());
            format.setText(p == null ? "" : p.format());
            Mode t = typeOf(p);
            type.setSelectedItem(t);
            applyType(t);
            Datasource d = p == null ? null : p.getDatasource();
            // a source that no longer exists (or the old "file" one) shows the type's first source
            database.setSelectedItem(d == null ? sourcesFor(t).get(0) : d.getIdentifier());
            order.setSelectedItem(p == null || p.sortOrder() == null ? keepText : p.sortOrder());
            matchMode.setSelectedItem(p == null || p.matchMode() == null ? keepText : p.matchMode());
            language.setTag(p == null ? null : p.language());
            output.setText(p == null || p.outputFolder() == null ? "" : p.outputFolder());
            jellyfin.setSelectedItem(p == null || p.jellyfinScan() == null ? keepText : p.jellyfinScan() ? yes : no);
            plex.setSelectedItem(p == null || p.plexScan() == null ? keepText : p.plexScan() ? yes : no);
        } finally {
            loading = false;
        }
        if (entry != null) {
            // the first time an entry is shown its form is the reference: later differences are unsaved changes
            baseline.putIfAbsent(entry.id(), formPreset());
        }
        for (JComponent f : new JComponent[] { name, type, format, database, order, matchMode, language, output }) {
            f.setEnabled(entry != null);
        }
    }

    /** New profile (not saved until Save), with the current Movie format. */
    private void addNew() {
        Preset p = new Preset(null, "", Mode.Movie.getFormat(), "TheMovieDB", null, null, null);
        model.addElement(p);
        list.setSelectedValue(p, true);
        name.requestFocusInWindow();
    }

    /** True if a profile has unsaved changes (for tests and screenshots). */
    boolean hasUnsavedChanges() {
        return !drafts.isEmpty();
    }

    /** Saves every profile with changes; false (and nothing saved) if one lacks its name or format. */
    boolean save() {
        boolean pristineNew = editing != null && !drafts.containsKey(editing.id()) && !isSaved(editing);
        for (Preset d : drafts.values()) {
            if (d.name().isBlank() || d.format().isBlank()) {
                list.setSelectedValue(entry(d.id()), true);
                JOptionPane.showMessageDialog(this, tr("presets.required"));
                return false;
            }
        }
        if (pristineNew && drafts.isEmpty()) {
            JOptionPane.showMessageDialog(this, tr("presets.required"));
            return false;
        }
        String selected = editing == null ? null : editing.id();
        drafts.values().forEach(Presets.get()::put);
        drafts.clear();
        baseline.clear();
        reload();
        Preset again = selected == null ? null : entry(selected);
        if (again != null) {
            list.setSelectedValue(again, true);
            // the selection did not change if it is the same object: show it again to take its new saved values
            show(again);
        } else if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            show(null);
        }
        list.repaint();
        return true;
    }

    private boolean isSaved(Preset p) {
        return Presets.get().list().stream().anyMatch(s -> s.id().equals(p.id()));
    }

    private Preset entry(String id) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).id().equals(id)) {
                return model.get(i);
            }
        }
        return null;
    }

    /** Closes; with unsaved changes it first asks: Save, Discard or go back. */
    private void close() {
        PreferencesDialog.UnsavedChoice choice = drafts.isEmpty() ? PreferencesDialog.UnsavedChoice.DISCARD : PreferencesDialog.askUnsaved(this);
        if (choice == PreferencesDialog.UnsavedChoice.DISCARD || choice == PreferencesDialog.UnsavedChoice.SAVE && save()) {
            dispose();
        }
    }

    /** null = the preset does not set it (Preferences option), otherwise Yes/No. */
    private Boolean scanOption(JComboBox<String> box) {
        return keepText.equals(box.getSelectedItem()) ? null : yes.equals(box.getSelectedItem());
    }

    private String keep(JComboBox<String> box) {
        Object v = box.getSelectedItem();
        return v == null || keepText.equals(v) ? null : v.toString();
    }

    private void delete() {
        Preset p = list.getSelectedValue();
        if (p == null) {
            return;
        }
        Preset shown = drafts.getOrDefault(p.id(), p);
        String label = shown.name().isBlank() ? tr("presets.untitled") : shown.name();
        if (JOptionPane.showConfirmDialog(this, tr("presets.delete.confirm", label), tr("rename.presets"), JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            if (isSaved(p)) {
                Presets.get().remove(p);
            }
            drafts.remove(p.id());
            baseline.remove(p.id());
            int index = list.getSelectedIndex();
            model.removeElement(p);
            if (model.isEmpty()) {
                show(null);
            } else {
                list.setSelectedIndex(Math.min(index, model.size() - 1));
            }
        }
    }
}
