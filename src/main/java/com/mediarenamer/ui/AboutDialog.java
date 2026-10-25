package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.AppFolders;
import com.mediarenamer.app.Settings;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

/** "About MediaRenamer": version, author, credits, runtime, data folder and third-party licenses. */
public class AboutDialog extends JDialog {

    static final String AUTHOR = "AeLiu";

    /** Opens the third-party license notices. */
    final JButton licenses;

    public AboutDialog(JFrame owner) {
        super(owner, tr("about.title"), true);
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setBorder(BorderFactory.createEmptyBorder(22, 28, 16, 28));

        // logo, name, version and description
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JComponent logo = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Logo.paint((Graphics2D) g, 0, 0, 80);
            }
        };
        Dimension logoSize = new Dimension(80, 80);
        logo.setPreferredSize(logoSize);
        logo.setMaximumSize(logoSize);
        JLabel name = new JLabel(Settings.APP_NAME);
        name.putClientProperty("FlatLaf.styleClass", "h1");
        JLabel version = new JLabel(tr("about.version", Settings.version()));
        version.setForeground(Theme.accentText());
        version.putClientProperty("FlatLaf.style", "font: bold");
        JLabel description = new JLabel("<html><div style='width:380px;text-align:center'>" + tr("about.description") + "</div></html>");
        description.setHorizontalAlignment(SwingConstants.CENTER);
        description.setForeground(Theme.secondaryText());
        for (JComponent c : new JComponent[] { logo, name, version, description }) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            head.add(c);
            head.add(Box.createVerticalStrut(c == logo ? 10 : 4));
        }
        content.add(head, BorderLayout.NORTH);

        // details
        JPanel facts = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 6, 3, 6);
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        row = fact(facts, c, row, tr("about.author"), new JLabel(AUTHOR));
        row = fact(facts, c, row, "Java", new JLabel(System.getProperty("java.version") + " · " + System.getProperty("java.vendor")));
        JPanel data = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel folder = new JLabel(ConflictDialog.abbreviate(AppFolders.data().toAbsolutePath().toString(), 52));
        folder.setToolTipText(AppFolders.data().toAbsolutePath().toString());
        data.add(folder);
        data.add(Box.createHorizontalStrut(8));
        JButton open = new JButton(tr("about.openFolder"), Icons.folder().withSize(14));
        open.putClientProperty("JButton.buttonType", "borderless");
        open.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().open(AppFolders.data().toFile());
            } catch (Exception ex) {
                // the folder may not exist yet: nothing to open
            }
        });
        data.add(open);
        row = fact(facts, c, row, tr("about.dataFolder"), data);
        row = fact(facts, c, row, tr("about.libraries"), new JLabel("Groovy · FlatLaf · RSyntaxTextArea · JNA · MediaInfo"));
        JPanel factsWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        factsWrap.add(facts);
        content.add(factsWrap, BorderLayout.CENTER);

        // notices and close button
        JPanel south = new JPanel(new BorderLayout(0, 10));
        JLabel notice = new JLabel("<html><div style='width:440px;text-align:center'>" + tr("about.notice") + "</div></html>");
        notice.setHorizontalAlignment(SwingConstants.CENTER);
        notice.setForeground(Theme.secondaryText());
        notice.putClientProperty("FlatLaf.style", "font: -1");
        south.add(notice, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new BorderLayout());
        licenses = new JButton(tr("about.licenses"));
        licenses.putClientProperty("JButton.buttonType", "borderless");
        licenses.setForeground(Theme.accentText());
        licenses.addActionListener(e -> new ThirdPartyLicensesDialog(this).setVisible(true));
        buttons.add(licenses, BorderLayout.WEST);
        JButton close = new JButton(tr("common.close"));
        close.addActionListener(e -> dispose());
        buttons.add(close, BorderLayout.EAST);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static int fact(JPanel p, GridBagConstraints c, int row, String label, JComponent value) {
        c.gridy = row;
        c.gridx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(Theme.secondaryText());
        p.add(l, c);
        c.gridx = 1;
        p.add(value, c);
        return row + 1;
    }
}
