package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.Preset;
import com.mediarenamer.rename.Presets;
import com.mediarenamer.rename.RenameAction;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.JellyfinClient;
import com.mediarenamer.web.PlexClient;
import com.mediarenamer.web.SortOrder;
import com.mediarenamer.web.WebServices;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
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
import javax.swing.ListSelectionModel;

/** Preset management: create, edit and delete. */
public class PresetsDialog extends JDialog {

    private final String keepText = tr("presets.keep");
    private final String yes = tr("common.yes");
    private final String no = tr("common.no");
    /** Empty option (default) of the scan combos: the preset does not set it and the Preferences one is used. */
    private static final String SCAN_GENERAL = "";

    private final DefaultListModel<Preset> model = new DefaultListModel<>();
    private final JList<Preset> list = new JList<>(model);
    private final JTextField name = new JTextField(30);
    private final JTextField format = new JTextField(40);
    private final JComboBox<String> database = new JComboBox<>();
    private final JComboBox<String> order = new JComboBox<>();
    private final JComboBox<String> matchMode = new JComboBox<>(new String[] { keepText, "Strict", "Opportunistic" });
    private final JTextField language = new JTextField(8);
    private final JComboBox<String> action = new JComboBox<>();
    private final JTextField output = new JTextField(32);
    private final JComboBox<String> jellyfin = new JComboBox<>(new String[] { SCAN_GENERAL, yes, no });
    private final JComboBox<String> plex = new JComboBox<>(new String[] { SCAN_GENERAL, yes, no });
    private Preset editing;

    public PresetsDialog(JFrame owner) {
        super(owner, tr("rename.presets"), true);
        database.addItem("file");
        for (Datasource d : WebServices.all()) {
            database.addItem(d.getIdentifier());
        }
        order.addItem(keepText);
        for (SortOrder o : SortOrder.values()) {
            order.addItem(o.name());
        }
        action.addItem(keepText);
        for (RenameAction a : RenameAction.values()) {
            action.addItem(a.name());
        }

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                show(list.getSelectedValue());
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
        output.putClientProperty("JTextField.placeholderText", tr("presets.folder.placeholder"));
        row = row(form, c, row, tr("presets.folder"), folderField(this, output));
        format.putClientProperty("JTextField.placeholderText", "{ny}/{n} - {sxe} - {t}");
        row = row(form, c, row, tr("rename.format"), format);
        JLabel hint = new JLabel(tr("presets.hint"));
        hint.setForeground(Theme.secondaryText());
        c.gridx = 1;
        c.gridy = row++;
        form.add(hint, c);
        row = row(form, c, row, tr("presets.database"), database);
        row = row(form, c, row, tr("episode.order"), order);
        row = row(form, c, row, tr("match.mode"), matchMode);
        row = row(form, c, row, tr("presets.language"), language);
        row = row(form, c, row, tr("rename.action"), action);
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

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(button(tr("presets.new"), () -> {
            list.clearSelection();
            show(null);
        }));
        buttons.add(button(tr("presets.delete"), this::delete));
        buttons.add(button(tr("common.save"), this::save));
        buttons.add(button(tr("common.close"), this::dispose));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(listScroll, BorderLayout.WEST);
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        add(content);

        reload();
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        } else {
            show(null);
        }
        pack();
        setLocationRelativeTo(owner);
    }

    private static int row(JPanel form, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridx = 0;
        c.gridy = row;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(new JLabel(label), c);
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

    private static JButton button(String text, Runnable r) {
        JButton b = new JButton(text);
        b.addActionListener(e -> r.run());
        return b;
    }

    private void reload() {
        model.clear();
        Presets.get().list().forEach(model::addElement);
    }

    /** Shows a preset in the form; null = new preset with the current settings of the Movie mode. */
    private void show(Preset p) {
        editing = p;
        name.setText(p == null ? "" : p.name());
        format.setText(p == null ? Mode.Movie.getFormat() : p.format());
        database.setSelectedItem(p == null || p.database() == null ? "TheMovieDB" : p.database());
        order.setSelectedItem(p == null || p.sortOrder() == null ? keepText : p.sortOrder());
        matchMode.setSelectedItem(p == null || p.matchMode() == null ? keepText : p.matchMode());
        language.setText(p == null || p.language() == null ? "" : p.language());
        action.setSelectedItem(p == null || p.action() == null ? keepText : p.action());
        output.setText(p == null || p.outputFolder() == null ? "" : p.outputFolder());
        jellyfin.setSelectedItem(p == null || p.jellyfinScan() == null ? SCAN_GENERAL : p.jellyfinScan() ? yes : no);
        plex.setSelectedItem(p == null || p.plexScan() == null ? SCAN_GENERAL : p.plexScan() ? yes : no);
    }

    private void save() {
        if (name.getText().isBlank() || format.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, tr("presets.required"));
            return;
        }
        Preset p = new Preset(editing == null ? null : editing.id(), name.getText().trim(), format.getText().trim(), (String) database.getSelectedItem(),
                keep(order), keep(matchMode), language.getText().trim().isEmpty() ? null : language.getText().trim(), keep(action),
                output.getText().trim().isEmpty() ? null : output.getText().trim(),
                scanOption(jellyfin), scanOption(plex));
        Presets.get().put(p);
        reload();
        list.setSelectedValue(p, true);
    }

    /** null = the preset does not set it (Preferences option), otherwise Yes/No. */
    private Boolean scanOption(JComboBox<String> box) {
        return SCAN_GENERAL.equals(box.getSelectedItem()) ? null : yes.equals(box.getSelectedItem());
    }

    private String keep(JComboBox<String> box) {
        Object v = box.getSelectedItem();
        return v == null || keepText.equals(v) ? null : v.toString();
    }

    private void delete() {
        Preset p = list.getSelectedValue();
        if (p != null && JOptionPane.showConfirmDialog(this, tr("presets.delete.confirm", p.name()), tr("rename.presets"),
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            Presets.get().remove(p);
            reload();
            show(null);
        }
    }
}
