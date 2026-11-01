package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;

/**
 * "Third-party licenses": the notices of the redistributed libraries and of the Java runtime
 * (THIRD-PARTY-NOTICES.txt, bundled in the jar as {@value #RESOURCE}). Shown as plain text, never as HTML.
 * Also shows MediaRenamer's own license (LICENSE, bundled as {@value #LICENSE_RESOURCE}).
 */
public class ThirdPartyLicensesDialog extends JDialog {

    static final String RESOURCE = "/META-INF/THIRD-PARTY-NOTICES.txt";
    static final String LICENSE_RESOURCE = "/META-INF/mediarenamer/LICENSE";
    /** Upper bound of what is read: the real files are about 60 KB at most. */
    static final int MAX_BYTES = 1 << 20;

    final JTextArea text;

    public ThirdPartyLicensesDialog(Window owner) {
        this(owner, "about.licenses", RESOURCE);
    }

    /** MediaRenamer's own license (GNU GPL v3). */
    public static ThirdPartyLicensesDialog ownLicense(Window owner) {
        return new ThirdPartyLicensesDialog(owner, "about.license", LICENSE_RESOURCE);
    }

    /** {@code keys}: prefix of the {@code .title}, {@code .intro} and {@code .missing} texts. */
    private ThirdPartyLicensesDialog(Window owner, String keys, String resource) {
        super(owner, tr(keys + ".title"), ModalityType.APPLICATION_MODAL);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        JLabel intro = new JLabel("<html><div style='width:560px'>" + tr(keys + ".intro") + "</div></html>");
        intro.setForeground(Theme.secondaryText());
        content.add(intro, BorderLayout.NORTH);

        String notices = load(resource);
        text = new JTextArea(notices != null ? notices : tr(keys + ".missing"));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, text.getFont().getSize()));
        text.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(text);
        // wide enough for the longest lines of the license texts (about 96 columns)
        int columns = text.getFontMetrics(text.getFont()).charWidth('m') * 98;
        scroll.setPreferredSize(new Dimension(columns + scroll.getVerticalScrollBar().getPreferredSize().width + 8, 480));
        content.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton close = new JButton(tr("common.close"));
        close.addActionListener(e -> dispose());
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    /** The bundled notices, or {@code null} if the resource is missing, unreadable or larger than {@link #MAX_BYTES}. */
    static String load() {
        return load(RESOURCE);
    }

    /** A bundled text resource, or {@code null} if it is missing, unreadable or larger than {@link #MAX_BYTES}. */
    static String load(String resource) {
        try (InputStream in = ThirdPartyLicensesDialog.class.getResourceAsStream(resource)) {
            return in == null ? null : read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Reads at most {@link #MAX_BYTES} as UTF-8; {@code null} if the stream is longer. */
    static String read(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_BYTES + 1);
        return data.length > MAX_BYTES ? null : new String(data, StandardCharsets.UTF_8);
    }
}
