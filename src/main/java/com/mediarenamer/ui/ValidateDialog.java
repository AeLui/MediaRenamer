package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.plural;
import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.rename.NameValidator.Issue;
import com.mediarenamer.rename.NameValidator.Severity;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Pre-check of the new names: lists the
 * problems (errors that would make the rename fail and warnings) with the cleanup suggestion.
 */
public class ValidateDialog extends JDialog {

    /** What the user decided. */
    public enum Choice {
        /** Apply the cleanup suggestions and rename. */
        CLEAN,
        /** Rename without changing anything. */
        KEEP,
        /** Go back without renaming. */
        CANCEL
    }

    private Choice choice = Choice.CANCEL;

    public ValidateDialog(Window owner, List<Issue> issues, int formatErrors) {
        super(owner, tr("validate.title"), ModalityType.APPLICATION_MODAL);
        long errors = issues.stream().filter(i -> i.severity() == Severity.ERROR).count();
        long warnings = issues.size() - errors;

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JLabel title = new JLabel(errors > 0 ? tr("validate.heading.errors") : tr("validate.heading.warnings"));
        title.putClientProperty("FlatLaf.styleClass", "h3");
        title.setIcon(Icons.warning().withSize(22).withColor(errors > 0 ? Theme.ERROR : Theme.WARNING));
        title.setIconTextGap(8);
        StringBuilder sub = new StringBuilder();
        if (errors > 0) {
            sub.append(plural("validate.errors", errors));
        }
        if (warnings > 0) {
            sub.append(sub.length() > 0 ? " · " : "").append(plural("validate.warnings", warnings));
        }
        if (formatErrors > 0) {
            sub.append(" · ").append(plural("validate.formatErrors", formatErrors));
        }
        sub.append(tr("validate.suggestion"));
        JLabel subtitle = new JLabel(sub.toString());
        subtitle.setForeground(Theme.secondaryText());
        JPanel head = new JPanel(new BorderLayout(0, 4));
        head.add(title, BorderLayout.NORTH);
        head.add(subtitle, BorderLayout.SOUTH);
        content.add(head, BorderLayout.NORTH);

        JTable table = new JTable(new Model(issues));
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        // width of "Error"/"Warning" in the interface language, plus the icon
        java.awt.FontMetrics fm = table.getFontMetrics(table.getFont());
        int severityWidth = 34 + Math.max(fm.stringWidth(tr("validate.error")), fm.stringWidth(tr("validate.warning")));
        table.getColumnModel().getColumn(0).setMaxWidth(severityWidth);
        table.getColumnModel().getColumn(0).setPreferredWidth(severityWidth);
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                boolean error = v == Severity.ERROR;
                setText(error ? tr("validate.error") : tr("validate.warning"));
                setIcon((error ? Icons.warning() : Icons.info()).withSize(14).withColor(error ? Theme.ERROR : Theme.WARNING));
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(980, Math.min(380, 60 + issues.size() * 26)));
        content.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancel = new JButton(tr("validate.back"));
        cancel.addActionListener(e -> close(Choice.CANCEL));
        JButton keep = new JButton(tr("validate.keep"));
        keep.setToolTipText(errors > 0 ? tr("validate.keep.tooltip.errors") : tr("validate.keep.tooltip"));
        keep.addActionListener(e -> close(Choice.KEEP));
        JButton clean = new JButton(tr("validate.clean"));
        clean.setIcon(Icons.check().withSize(16));
        clean.setToolTipText(tr("validate.clean.tooltip"));
        clean.addActionListener(e -> close(Choice.CLEAN));
        buttons.add(cancel);
        buttons.add(keep);
        buttons.add(clean);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(clean);
        getRootPane().registerKeyboardAction(e -> close(Choice.CANCEL), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    private void close(Choice c) {
        choice = c;
        dispose();
    }

    public Choice getChoice() {
        return choice;
    }

    private static final class Model extends AbstractTableModel {
        private final String[] cols = { "", tr("validate.col.file"), tr("validate.col.problem"), tr("validate.col.suggestion") };
        private final List<Issue> issues;

        Model(List<Issue> issues) {
            this.issues = issues;
        }

        @Override
        public int getRowCount() {
            return issues.size();
        }

        @Override
        public int getColumnCount() {
            return cols.length;
        }

        @Override
        public String getColumnName(int c) {
            return cols[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Issue i = issues.get(r);
            return switch (c) {
                case 0 -> i.severity();
                case 1 -> i.entry().target().getName();
                case 2 -> i.problem();
                default -> i.suggestion() == null ? tr("validate.noChange") : relative(i);
            };
        }

        /** Shows the suggestion relative to the original target folder if they share the folder. */
        private static String relative(Issue i) {
            java.io.File s = i.suggestion();
            java.io.File t = i.entry().target();
            return s.getParentFile() != null && s.getParentFile().equals(t.getParentFile()) ? s.getName() : s.getPath();
        }
    }
}
