package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.rename.History;
import com.mediarenamer.rename.RenameEngine;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

/**
 * Rename history: a single table with one row per renamed file (Date, Source, Target), most recent first, shown in
 * pages of {@link #PAGE_SIZE} rows. The selected rows can be reverted and the whole history exported to CSV.
 */
public class HistoryDialog extends JDialog {

    static final int PAGE_SIZE = 50;

    private final DefaultTableModel model = new DefaultTableModel(columns(), 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel pageLabel = new JLabel();
    private final JButton first = pagerButton("«", "history.first");
    private final JButton previous = pagerButton("‹", "history.previous");
    private final JButton next = pagerButton("›", "history.next");
    private final JButton last = pagerButton("»", "history.last");
    private final JButton export = new JButton(tr("history.export"));
    private List<History.Entry> entries = new ArrayList<>();
    private int page;

    public HistoryDialog(JFrame owner) {
        super(owner, tr("main.history"), true);
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoCreateRowSorter(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(0).setMaxWidth(190);
        table.getColumnModel().getColumn(1).setPreferredWidth(400);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel pager = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        first.addActionListener(e -> showPage(0));
        previous.addActionListener(e -> showPage(page - 1));
        next.addActionListener(e -> showPage(page + 1));
        last.addActionListener(e -> showPage(pageCount(entries.size(), PAGE_SIZE) - 1));
        pager.add(first);
        pager.add(previous);
        pageLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        pager.add(pageLabel);
        pager.add(next);
        pager.add(last);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        export.setToolTipText(tr("history.export.tooltip"));
        export.addActionListener(e -> exportCsv());
        JButton revert = new JButton(tr("history.revert"));
        revert.setToolTipText(tr("history.revert.tooltip"));
        revert.addActionListener(e -> revert());
        JButton close = new JButton(tr("common.close"));
        close.addActionListener(e -> dispose());
        buttons.add(export);
        buttons.add(revert);
        buttons.add(close);

        JPanel south = new JPanel(new BorderLayout());
        south.add(pager, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);
        add(content);

        load();
        setSize(1080, 640);
        setLocationRelativeTo(owner);
    }

    private static String[] columns() {
        return new String[] { tr("history.date"), tr("history.source"), tr("history.target") };
    }

    private static JButton pagerButton(String text, String tooltipKey) {
        JButton b = new JButton(text);
        b.setToolTipText(tr(tooltipKey));
        b.putClientProperty("JButton.buttonType", "borderless");
        return b;
    }

    /** Number of pages for a total of rows (at least 1, also for an empty history). */
    static int pageCount(int total, int pageSize) {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /** Index of the first row of a page and of the row after the last one. */
    static int[] pageBounds(int page, int total, int pageSize) {
        int from = Math.min(page * pageSize, total);
        return new int[] { from, Math.min(from + pageSize, total) };
    }

    private void load() {
        entries = History.get().entries();
        showPage(0);
    }

    private void showPage(int p) {
        int pages = pageCount(entries.size(), PAGE_SIZE);
        page = Math.max(0, Math.min(p, pages - 1));
        int[] b = pageBounds(page, entries.size(), PAGE_SIZE);
        model.setRowCount(0);
        for (History.Entry e : entries.subList(b[0], b[1])) {
            model.addRow(new Object[] { History.DISPLAY_DATE.format(e.date()), e.element().source().getPath(), e.element().target().getPath() });
        }
        pageLabel.setText(entries.isEmpty() ? tr("history.empty")
                : tr("history.page", page + 1, pages) + "  ·  " + I18n.plural("history.total", entries.size()));
        first.setEnabled(page > 0);
        previous.setEnabled(page > 0);
        next.setEnabled(page < pages - 1);
        last.setEnabled(page < pages - 1);
        export.setEnabled(!entries.isEmpty());
    }

    private void revert() {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this, tr("history.revert.noSelection"), tr("history.revert"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int offset = page * PAGE_SIZE;
        List<History.Element> chosen = new ArrayList<>();
        for (int r : rows) {
            chosen.add(entries.get(offset + r).element());
        }
        if (JOptionPane.showConfirmDialog(this, tr("history.revert.confirm", chosen.size()), tr("history.revert"),
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        List<File> restored = RenameEngine.revert(chosen);
        JOptionPane.showMessageDialog(this, tr("history.revert.done", restored.size(), chosen.size()));
        int keep = page;
        load();
        showPage(keep);
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV", "csv"));
        chooser.setSelectedFile(new File("mediarenamer-history-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = chooser.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) {
            f = new File(f.getPath() + ".csv");
        }
        try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
            // BOM: Excel opens the file as UTF-8 (accents in the paths)
            w.write('﻿');
            History.writeCsv(entries, columns(), w);
            JOptionPane.showMessageDialog(this, tr("history.export.done", entries.size(), f.getPath()));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, tr("history.export.failed", e.getMessage()), tr("history.export"), JOptionPane.ERROR_MESSAGE);
        }
    }
}
