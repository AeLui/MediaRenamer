package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.plural;
import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.rename.match.MovieMatcher;
import com.mediarenamer.rename.match.Selector;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.EpisodeListProvider;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MovieLookupService;
import com.mediarenamer.web.SearchResult;
import com.mediarenamer.web.Series;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;

/**
 * Movie/series search and selection dialog:
 * list with posters, search box to repeat the search, source selector and a card with
 * more data (original title, director, cast, rating, overview...) on mouse hover.
 */
public class SearchDialog extends JDialog {

    private static final int POSTER_W = 60;
    private static final int POSTER_H = 90;

    private final DefaultListModel<SearchResult> model = new DefaultListModel<>();
    private final JList<SearchResult> list = new JList<>(model);
    private final JTextField queryField = new JTextField(30);
    private final JComboBox<Datasource> sourceBox = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final Locale locale;
    private final Datasource initialSource;
    private final Selector.Search<? extends SearchResult> research;
    private final HoverCard hover;
    private SearchResult selected;

    public SearchDialog(Window owner, String heading, String query, List<String> files, List<? extends SearchResult> initial, List<Datasource> sources,
            Datasource source, Locale locale, Selector.Search<? extends SearchResult> research) {
        super(owner, tr("search.title"), ModalityType.APPLICATION_MODAL);
        this.locale = locale;
        this.initialSource = source;
        this.research = research;

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        // header
        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(heading);
        title.putClientProperty("FlatLaf.styleClass", "h3");
        title.setIcon(Icons.search().withSize(20).withColor(Theme.ACCENT));
        title.setIconTextGap(8);
        head.add(title);
        if (files != null && !files.isEmpty()) {
            String f = files.size() > 3 ? String.join("  ·  ", files.subList(0, 3)) + "  · … (" + plural("count.files", files.size()) + ")" : String.join("  ·  ", files);
            JLabel fl = new JLabel(f);
            fl.setForeground(Theme.secondaryText());
            fl.setBorder(BorderFactory.createEmptyBorder(4, 28, 8, 0));
            head.add(fl);
        }
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        for (Datasource d : sources) {
            sourceBox.addItem(d);
        }
        sourceBox.setSelectedItem(source);
        sourceBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v instanceof Datasource d ? d.getName() : v, i, s, f);
            }
        });
        sourceBox.setVisible(sources.size() > 1);
        queryField.setText(query);
        queryField.putClientProperty("JTextField.placeholderText", tr("search.placeholder"));
        queryField.putClientProperty("JTextField.leadingIcon", Icons.search().withSize(16));
        queryField.putClientProperty("JTextField.showClearButton", true);
        queryField.addActionListener(e -> search());
        JButton go = new JButton(tr("search.go"));
        go.addActionListener(e -> search());
        bar.add(sourceBox, BorderLayout.WEST);
        bar.add(queryField, BorderLayout.CENTER);
        bar.add(go, BorderLayout.EAST);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(bar);
        content.add(head, BorderLayout.NORTH);

        // results
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ResultRenderer());
        list.setFixedCellHeight(POSTER_H + 16);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    choose();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.border()));
        scroll.setPreferredSize(new Dimension(640, 430));
        scroll.getViewport().addChangeListener(e -> hideHover());
        content.add(scroll, BorderLayout.CENTER);
        hover = new HoverCard();

        // footer
        JPanel south = new JPanel(new BorderLayout());
        status.setForeground(Theme.secondaryText());
        status.setIcon(Icons.info().withSize(14));
        south.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton skip = new JButton(tr("search.skip"));
        skip.setToolTipText(tr("search.skip.tooltip"));
        skip.addActionListener(e -> {
            selected = null;
            dispose();
        });
        JButton ok = new JButton(tr("search.select"));
        ok.setIcon(Icons.check().withSize(16));
        ok.addActionListener(e -> choose());
        buttons.add(skip);
        buttons.add(ok);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                hideHover();
            }
        });

        setResults(initial);
        if (initial == null || initial.isEmpty()) {
            if (query != null && !query.isBlank()) {
                SwingUtilities.invokeLater(this::search);
            }
        }
        pack();
        setLocationRelativeTo(owner);
    }

    public SearchResult getSelected() {
        return selected;
    }

    public Datasource getDatasource() {
        return (Datasource) sourceBox.getSelectedItem();
    }

    private void choose() {
        selected = list.getSelectedValue();
        if (selected != null) {
            dispose();
        }
    }

    private void setResults(List<? extends SearchResult> results) {
        model.clear();
        if (results != null) {
            results.forEach(model::addElement);
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
        status.setText(model.isEmpty() ? tr("search.noResults") : tr("search.results", model.size()));
    }

    @SuppressWarnings("unchecked")
    private void search() {
        String q = queryField.getText().trim();
        Datasource db = getDatasource();
        if (q.isEmpty() || db == null) {
            return;
        }
        hideHover();
        status.setText(tr("search.searching", q, db.getName()));
        new SwingWorker<List<SearchResult>, Void>() {
            @Override
            protected List<SearchResult> doInBackground() throws Exception {
                if (research != null && db == initialSource) {
                    return new ArrayList<>((List<SearchResult>) research.search(q));
                }
                if (db instanceof MovieLookupService ms) {
                    return new ArrayList<>(new MovieMatcher(ms).searchText(q, locale));
                }
                if (db instanceof EpisodeListProvider p) {
                    return new ArrayList<>(p.search(q, locale));
                }
                return List.of();
            }

            @Override
            protected void done() {
                try {
                    setResults(get());
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    status.setText(tr("common.error", c.getMessage()));
                }
            }
        }.execute();
    }

    private void hideHover() {
        if (hover != null) {
            hover.hide();
        }
    }

    // ------------------------------------------------------------------ result row

    private final class ResultRenderer extends JComponent implements ListCellRenderer<SearchResult> {
        private SearchResult value;
        private boolean isSelected;

        @Override
        public Component getListCellRendererComponent(JList<? extends SearchResult> l, SearchResult v, int index, boolean sel, boolean focus) {
            value = v;
            isSelected = sel;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int w = getWidth();
            int h = getHeight();
            if (isSelected) {
                g.setColor(Theme.accentSoft());
                g.fillRoundRect(4, 2, w - 8, h - 4, 14, 14);
                g.setColor(Theme.ACCENT);
                g.fillRoundRect(4, 10, 4, h - 20, 4, 4);
            }
            PosterCache.paint(g, value.getPoster(), 14, 8, POSTER_W, POSTER_H, value instanceof Series, list::repaint);

            int x = 14 + POSTER_W + 14;
            int y = 26;
            Font base = list.getFont();
            Color fg = UIManager.getColor("List.foreground");
            Color muted = Theme.secondaryText();

            // title + year
            g.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
            g.setColor(fg);
            String t = value.getName();
            g.drawString(clip(g, t, w - x - 90), x, y);
            int tw = g.getFontMetrics().stringWidth(clip(g, t, w - x - 90));
            String year = year(value);
            if (year != null) {
                g.setFont(base.deriveFont(Font.BOLD, base.getSize2D() - 1f));
                int yw = g.getFontMetrics().stringWidth(year) + 14;
                g.setColor(isSelected ? Theme.ACCENT : Theme.accentSoft());
                g.fillRoundRect(x + tw + 8, y - 14, yw, 19, 19, 19);
                g.setColor(isSelected ? Color.WHITE : Theme.accentText());
                g.drawString(year, x + tw + 15, y);
            }

            // original title
            y += 20;
            g.setFont(base.deriveFont(Font.ITALIC));
            g.setColor(muted);
            String orig = value.getOriginalName();
            if (orig == null && !value.getAliasNames().isEmpty() && !value.getAliasNames().get(0).equals(t)) {
                orig = value.getAliasNames().get(0);
            }
            if (orig != null) {
                g.drawString(clip(g, tr("search.originalTitle", orig), w - x - 12), x, y);
                y += 18;
            }

            // overview (2 lines)
            g.setFont(base.deriveFont(base.getSize2D() - 1f));
            String ov = value.getOverview();
            if (ov != null && !ov.isBlank()) {
                for (String line : wrap(g, ov, w - x - 16, orig == null ? 3 : 2)) {
                    g.drawString(line, x, y);
                    y += 16;
                }
            }
            g.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(560, POSTER_H + 16);
        }
    }

    static String year(SearchResult r) {
        if (r instanceof Movie m && m.getYear() > 0) {
            return String.valueOf(m.getYear());
        }
        if (r instanceof Series s && s.getYear() != null) {
            return String.valueOf(s.getYear());
        }
        return null;
    }

    static String clip(Graphics2D g, String s, int max) {
        if (g.getFontMetrics().stringWidth(s) <= max) {
            return s;
        }
        String e = "…";
        int n = s.length();
        while (n > 0 && g.getFontMetrics().stringWidth(s.substring(0, n) + e) > max) {
            n--;
        }
        return s.substring(0, n).trim() + e;
    }

    /** Splits the text into lines that fit in max px; the last one with "…" if cut. */
    static List<String> wrap(Graphics2D g, String text, int max, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String[] words = text.replaceAll("\\s+", " ").trim().split(" ");
        for (int i = 0; i < words.length; i++) {
            String test = cur.isEmpty() ? words[i] : cur + " " + words[i];
            if (g.getFontMetrics().stringWidth(test) > max && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(words[i]);
                if (lines.size() == maxLines) {
                    break;
                }
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (lines.size() < maxLines && !cur.isEmpty()) {
            lines.add(cur.toString());
        } else if (lines.size() == maxLines) {
            String last = lines.get(maxLines - 1);
            lines.set(maxLines - 1, clip(g, last + " " + "…", max));
        }
        return lines;
    }

    // ------------------------------------------------------------------ details card on mouse hover

    /** Floating card with the details of the result under the mouse (appears after 350 ms). */
    private final class HoverCard extends MouseAdapter {
        private final Timer timer = new Timer(350, e -> show());
        private int index = -1;
        private Popup popup;
        private SwingWorker<MediaDetails, Void> loader;

        HoverCard() {
            timer.setRepeats(false);
            list.addMouseMotionListener(this);
            list.addMouseListener(this);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int i = list.locationToIndex(e.getPoint());
            Rectangle cell = i < 0 ? null : list.getCellBounds(i, i);
            if (cell == null || !cell.contains(e.getPoint())) {
                hide();
                return;
            }
            if (i != index) {
                hide();
                index = i;
                timer.restart();
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hide();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            hide();
        }

        void hide() {
            timer.stop();
            index = -1;
            if (loader != null) {
                loader.cancel(true);
                loader = null;
            }
            if (popup != null) {
                popup.hide();
                popup = null;
            }
        }

        private void show() {
            if (index < 0 || index >= model.size() || !list.isShowing()) {
                return;
            }
            int i = index;
            SearchResult r = model.get(i);
            display(i, MediaDetails.basic(r), true);
            Datasource db = getDatasource();
            loader = new SwingWorker<>() {
                @Override
                protected MediaDetails doInBackground() {
                    return MediaDetails.load(r, db, locale);
                }

                @Override
                protected void done() {
                    if (!isCancelled() && index == i) {
                        try {
                            display(i, get(), false);
                        } catch (Exception ignored) {
                            // the basic card is already being shown
                        }
                    }
                }
            };
            loader.execute();
        }

        private void display(int i, MediaDetails d, boolean loading) {
            if (popup != null) {
                popup.hide();
            }
            DetailCard card = new DetailCard(d, loading, model.get(i) instanceof Series, () -> {
                if (popup != null && index == i) {
                    display(i, d, loading);
                }
            });
            Dimension size = card.getPreferredSize();
            Rectangle cell = list.getCellBounds(i, i);
            Point p = new Point(cell.x + cell.width, cell.y);
            SwingUtilities.convertPointToScreen(p, list);
            Rectangle screen = getGraphicsConfiguration().getBounds();
            Point dlg = SearchDialog.this.getLocationOnScreen();
            int x = dlg.x + SearchDialog.this.getWidth() + 6;
            if (x + size.width > screen.x + screen.width) {
                x = Math.max(screen.x, dlg.x - size.width - 6);
            }
            int y = Math.max(screen.y, Math.min(p.y - 10, screen.y + screen.height - size.height - 10));
            popup = PopupFactory.getSharedInstance().getPopup(list, card, x, y);
            popup.show();
        }
    }

    /** Card content: large poster + title + data + overview. */
    static final class DetailCard extends JPanel {
        private static final int PW = 154;
        private static final int PH = 231;

        DetailCard(MediaDetails d, boolean loading, boolean series, Runnable onPoster) {
            super(new BorderLayout(14, 0));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.border()), BorderFactory.createEmptyBorder(14, 14, 14, 16)));
            setBackground(UIManager.getColor("Panel.background"));

            JComponent poster = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    PosterCache.paint((Graphics2D) g, d.poster(), 0, 0, PW, PH, series, onPoster);
                }
            };
            poster.setPreferredSize(new Dimension(PW, PH));
            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.add(poster, BorderLayout.NORTH);
            add(left, BorderLayout.WEST);

            JPanel info = new JPanel();
            info.setOpaque(false);
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
            JLabel title = new JLabel("<html><div style='width:250px'>" + esc(d.title()) + (d.year() == null ? "" : " <span style='color:" + hex(Theme.accentText()) + "'>(" + d.year() + ")</span>")
                    + "</div></html>");
            title.putClientProperty("FlatLaf.styleClass", "h3");
            info.add(title);
            if (d.originalTitle() != null) {
                JLabel o = new JLabel("<html><div style='width:250px'><i>" + esc(d.originalTitle()) + "</i></div></html>");
                o.setForeground(Theme.secondaryText());
                info.add(o);
            }
            info.add(Box.createVerticalStrut(8));
            if (!d.facts().isEmpty()) {
                StringBuilder sb = new StringBuilder("<html><table cellpadding=1 cellspacing=0 style='width:260px'>");
                String muted = hex(Theme.secondaryText());
                for (Map.Entry<String, String> e : d.facts().entrySet()) {
                    sb.append("<tr><td valign=top style='color:").append(muted).append(";padding-right:8px'>").append(esc(e.getKey()))
                            .append("</td><td>").append(esc(e.getValue())).append("</td></tr>");
                }
                sb.append("</table></html>");
                info.add(new JLabel(sb.toString()));
            }
            if (loading) {
                JLabel l = new JLabel(tr("search.loadingDetails"));
                l.setForeground(Theme.secondaryText());
                l.setIcon(Icons.info().withSize(14));
                info.add(l);
            }
            if (d.overview() != null && !d.overview().isBlank()) {
                info.add(Box.createVerticalStrut(8));
                // HTML JLabel with a fixed width: computes the wrapped text height correctly (JTextArea does not in a BoxLayout)
                JLabel ov = new JLabel("<html><div style='width:260px'>" + esc(trim(d.overview(), 420)) + "</div></html>");
                ov.setFont(UIManager.getFont("Label.font").deriveFont(12f));
                info.add(ov);
            }
            for (Component c : info.getComponents()) {
                ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            add(info, BorderLayout.CENTER);
        }

        private static String trim(String s, int max) {
            return s.length() <= max ? s : s.substring(0, s.lastIndexOf(' ', max) > 0 ? s.lastIndexOf(' ', max) : max) + "…";
        }

        private static String esc(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static String hex(Color c) {
            return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
        }
    }

    // ------------------------------------------------------------------ Selector for the matchers

    /** Selector that shows the dialogs on the Swing thread even when called from a worker. */
    public static final class SwingSelector implements Selector {
        private final Window owner;

        public SwingSelector(Window owner) {
            this.owner = owner;
        }

        @Override
        public <T> T select(String query, List<String> files, List<T> options) {
            return select(new Request<>(query, files, options, null, Locale.getDefault(), null));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T select(Request<T> r) {
            if (!r.options().stream().allMatch(o -> o instanceof SearchResult)) {
                AtomicReference<Object> ref = new AtomicReference<>();
                onEdt(() -> ref.set(JOptionPane.showInputDialog(owner, tr("search.selectFor", r.query()), tr("search.select"),
                        JOptionPane.QUESTION_MESSAGE, null, r.options().toArray(), r.options().get(0))));
                return (T) ref.get();
            }
            AtomicReference<SearchResult> ref = new AtomicReference<>();
            onEdt(() -> {
                List<Datasource> sources = r.datasource() == null ? List.of() : List.of(r.datasource());
                SearchDialog d = new SearchDialog(owner, tr("search.chooseFor", r.query()), r.query(), r.files(),
                        (List<? extends SearchResult>) r.options(), sources, r.datasource(), r.locale(), (Search<? extends SearchResult>) r.research());
                d.setVisible(true);
                ref.set(d.getSelected());
            });
            return (T) ref.get();
        }

        @Override
        public String input(String message, String initialValue) {
            AtomicReference<String> ref = new AtomicReference<>();
            onEdt(() -> ref.set(JOptionPane.showInputDialog(owner, message, initialValue)));
            return ref.get();
        }

        private static void onEdt(Runnable r) {
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
                return;
            }
            try {
                SwingUtilities.invokeAndWait(r);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
