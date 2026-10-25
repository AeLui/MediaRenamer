package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.format.MediaBindingBean;

import java.awt.BorderLayout;
import java.awt.Window;
import java.io.File;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** "View Bindings": table with the value of each format variable for a matched file. */
public class BindingsDialog extends JDialog {

    public BindingsDialog(Window owner, Object match, File file) {
        super(owner, tr("bindings.title", file == null ? "" : file.getName()), ModalityType.MODELESS);
        DefaultTableModel model = new DefaultTableModel(new Object[] { tr("bindings.variable"), tr("bindings.value") }, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(160);
        JPanel content = new JPanel(new BorderLayout(4, 4));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel status = new JLabel(tr("bindings.computing"));
        content.add(status, BorderLayout.NORTH);
        content.add(new JScrollPane(table), BorderLayout.CENTER);
        add(content);
        setSize(720, 600);
        setLocationRelativeTo(owner);

        new SwingWorker<List<String[]>, Void>() {
            @Override
            protected List<String[]> doInBackground() {
                return new MediaBindingBean(match, file).describe();
            }

            @Override
            protected void done() {
                try {
                    for (String[] row : get()) {
                        model.addRow(new Object[] { "{" + row[0] + "}", row[1] });
                    }
                    status.setText(tr("bindings.object", match));
                } catch (Exception e) {
                    status.setText(e.getMessage());
                }
            }
        }.execute();
    }
}
