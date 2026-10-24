package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.similarity.Normalization;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.EpisodeFormat;
import com.mediarenamer.web.MultiEpisode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Manually choose the episode of a row:
 * list of the series' episodes with a filter (number or title). Selecting several with
 * Ctrl/Shift assigns a multi-episode (S01E01-E02).
 */
public class EpisodeChooserDialog extends JDialog {

    private final List<Episode> all;
    private final DefaultListModel<Episode> model = new DefaultListModel<>();
    private final JList<Episode> list = new JList<>(model);
    private final JTextField filter = new JTextField(28);
    private Episode result;

    public EpisodeChooserDialog(Window owner, String fileName, String seriesName, List<Episode> episodes, Episode current) {
        super(owner, tr("episode.chooser.title"), ModalityType.APPLICATION_MODAL);
        this.all = episodes;
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        JLabel title = new JLabel(tr("episode.chooser.heading", seriesName));
        title.putClientProperty("FlatLaf.styleClass", "h3");
        title.setIcon(Icons.tv().withSize(20).withColor(Theme.ACCENT));
        title.setIconTextGap(8);
        JLabel file = new JLabel(fileName);
        file.setForeground(Theme.secondaryText());
        filter.putClientProperty("JTextField.placeholderText", tr("episode.chooser.filter"));
        filter.putClientProperty("JTextField.leadingIcon", Icons.search().withSize(16));
        filter.putClientProperty("JTextField.showClearButton", true);
        JPanel head = new JPanel(new BorderLayout(0, 6));
        head.add(title, BorderLayout.NORTH);
        head.add(file, BorderLayout.CENTER);
        head.add(filter, BorderLayout.SOUTH);
        content.add(head, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(40);
        list.setVisibleRowCount(12);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    choose();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new java.awt.Dimension(620, 440));
        content.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JLabel hint = new JLabel(tr("episode.chooser.hint"));
        hint.setForeground(Theme.secondaryText());
        hint.setIcon(Icons.info().withSize(14));
        south.add(hint, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton cancel = new JButton(tr("common.cancel"));
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton(tr("episode.chooser.choose"));
        ok.setIcon(Icons.check().withSize(16));
        ok.addActionListener(e -> choose());
        buttons.add(cancel);
        buttons.add(ok);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);

        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refilter();
            }
        });
        filter.addActionListener(e -> {
            if (list.getSelectedIndex() < 0 && !model.isEmpty()) {
                list.setSelectedIndex(0);
            }
            choose();
        });

        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        refilter();
        select(current);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Chosen episode (MultiEpisode if several were chosen), or null if cancelled. */
    public Episode getResult() {
        return result;
    }

    private void choose() {
        List<Episode> sel = list.getSelectedValuesList();
        if (sel.isEmpty()) {
            return;
        }
        result = sel.size() == 1 ? sel.get(0) : new MultiEpisode(sel);
        dispose();
    }

    private void select(Episode current) {
        if (current == null) {
            return;
        }
        List<Episode> wanted = current instanceof MultiEpisode m ? m.getEpisodes() : List.of(current);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            if (wanted.contains(model.get(i))) {
                idx.add(i);
            }
        }
        list.setSelectedIndices(idx.stream().mapToInt(Integer::intValue).toArray());
        if (!idx.isEmpty()) {
            list.ensureIndexIsVisible(idx.get(0));
        }
    }

    private void refilter() {
        String q = Normalization.normalize(filter.getText());
        List<Episode> keep = new ArrayList<>(list.getSelectedValuesList());
        model.clear();
        for (Episode e : all) {
            if (q.isEmpty() || matches(e, q)) {
                model.addElement(e);
            }
        }
        if (!keep.isEmpty()) {
            select(keep.size() == 1 ? keep.get(0) : new MultiEpisode(keep));
        }
    }

    /** Matches by number ("1x02", "s01e02", "12", "102") or by title text. */
    static boolean matches(Episode e, String q) {
        String compact = q.replace(" ", "");
        String sxe = EpisodeFormat.SxE.format(e).toLowerCase();
        String s00 = EpisodeFormat.S00E00.format(e).toLowerCase();
        if (compact.equals(sxe) || compact.equals(s00) || compact.equals(sxe.replace("x", ""))) {
            return true;
        }
        if (compact.matches("\\d+")) {
            int n = Integer.parseInt(compact);
            return Integer.valueOf(n).equals(e.getEpisode()) || Integer.valueOf(n).equals(e.getAbsolute()) || Integer.valueOf(n).equals(e.getSpecial());
        }
        return Normalization.normalize(String.valueOf(e.getTitle())).contains(q) || s00.contains(compact);
    }

    /** Row: number in a "chip", title and air date. */
    private static final class Renderer extends JComponent implements ListCellRenderer<Episode> {
        private Episode value;
        private boolean selected;

        @Override
        public Component getListCellRendererComponent(JList<? extends Episode> l, Episode v, int i, boolean s, boolean f) {
            value = v;
            selected = s;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int w = getWidth();
            int h = getHeight();
            if (selected) {
                g.setColor(Theme.accentSoft());
                g.fillRoundRect(3, 2, w - 6, h - 4, 12, 12);
            }
            Font font = UIManager.getFont("List.font");
            String num = value.isSpecial() ? "SP" + String.format("%02d", value.getSpecial()) : EpisodeFormat.S00E00.format(value);
            g.setFont(font.deriveFont(Font.BOLD, font.getSize2D() - 1f));
            int nw = g.getFontMetrics().stringWidth(num) + 16;
            g.setColor(selected ? Theme.ACCENT : Theme.accentSoft());
            g.fillRoundRect(10, (h - 22) / 2, nw, 22, 22, 22);
            g.setColor(selected ? java.awt.Color.WHITE : Theme.accentText());
            g.drawString(num, 18, h / 2 + 5);
            int x = 10 + nw + 12;
            g.setFont(font.deriveFont(Font.BOLD));
            g.setColor(UIManager.getColor("List.foreground"));
            String t = value.getTitle() == null ? tr("episode.chooser.untitled") : value.getTitle();
            String date = value.getAirdate() == null ? "" : value.getAirdate().toString();
            int dw = g.getFontMetrics().stringWidth(date) + 14;
            g.drawString(SearchDialog.clip(g, t, w - x - dw - 10), x, h / 2 + 5);
            g.setFont(font.deriveFont(font.getSize2D() - 1f));
            g.setColor(Theme.secondaryText());
            g.drawString(date, w - dw, h / 2 + 5);
            g.dispose();
        }
    }
}
