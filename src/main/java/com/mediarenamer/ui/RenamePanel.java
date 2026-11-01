package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.plural;
import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.mediarenamer.media.MediaDetection;
import com.mediarenamer.media.MediaTypes;
import com.mediarenamer.rename.ConflictAction;
import com.mediarenamer.rename.NameValidator;
import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.Preset;
import com.mediarenamer.rename.Presets;
import com.mediarenamer.rename.RenameAction;
import com.mediarenamer.rename.RenameEngine;
import com.mediarenamer.rename.match.AutoCompleteMatcher;
import com.mediarenamer.rename.match.EpisodeListMatcher;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.similarity.SeasonEpisodeMatcher;
import com.mediarenamer.util.FileUtilities;
import com.mediarenamer.web.AudioTrack;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.JellyfinClient;
import com.mediarenamer.web.PlexClient;
import com.mediarenamer.web.EpisodeFormat;
import com.mediarenamer.web.EpisodeListProvider;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MovieLookupService;
import com.mediarenamer.web.SearchResult;
import com.mediarenamer.web.SortOrder;
import com.mediarenamer.web.WebServices;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

/**
 * Rename tab: two synchronized lists
 * "Original files" | "New names", with data search, presets, format, action and conflicts.
 */
public class RenamePanel extends JPanel {

    /** A row: original file and, if matched, object + computed target. */
    static final class Row {
        final File source;
        Object match;
        File target;
        String error;

        Row(File source) {
            this.source = source;
        }
    }

    private static final int ROW_HEIGHT = 46;
    private static final String[] LANGUAGES = { "es-ES", "es-MX", "en-US", "en-GB", "ca-ES", "de-DE", "fr-FR", "it-IT", "pt-PT", "pt-BR", "nl-NL",
            "pl-PL", "ru-RU", "ja-JP", "ko-KR", "zh-CN", "zh-TW", "sv-SE", "da-DK", "no-NO", "fi-FI", "tr-TR", "el-GR", "he-IL", "ar-SA" };

    private final JFrame frame;
    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private final JList<Row> filesList = new EmptyStateList(tr("rename.files.empty.title"), tr("rename.files.empty.subtitle"));
    private final JList<Row> namesList = new EmptyStateList(tr("rename.names.empty.title"), tr("rename.names.empty.subtitle"));
    private final JComboBox<RenameAction> actionBox = new JComboBox<>(RenameAction.values());
    private final JComboBox<ConflictAction> conflictBox = new JComboBox<>(ConflictAction.values());
    private final JLabel presetChip = chip();
    /** Last applied preset; its target folder takes precedence over the Preferences one. */
    private Preset activePreset;
    private final JProgressBar progress = new JProgressBar();
    private final JLabel filesCount = badge();
    private final JLabel namesCount = badge();
    private final JLabel modeChip = chip();
    private final JLabel orderChip = chip();
    private final JLabel langChip = chip();
    private final JLabel sourceChip = chip();

    private Datasource lastDatasource;
    private boolean strict = !"Opportunistic".equals(Settings.get().get(Settings.MATCH_MODE));
    private SortOrder order = SortOrder.forName(Settings.get().get(Settings.EPISODE_ORDER));
    /** Modal progress layer (with Cancel) for fetching data and renaming. */
    private final ProgressOverlay overlay;
    /** Already downloaded episode lists, for the episode chooser (key: database, id, order, language). */
    private final java.util.Map<String, List<Episode>> episodeCache = new java.util.concurrent.ConcurrentHashMap<>();
    /** Adds the dropped files and folders: the lists and, through {@link #fileDropHandler()}, the whole window. */
    private TransferHandler fileDrop;

    public RenamePanel(JFrame frame) {
        this.frame = frame;
        this.overlay = new ProgressOverlay(frame);
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(4, 14, 10, 14));
        setOpaque(false);

        actionBox.setSelectedItem(RenameAction.forName(Settings.get().get(Settings.RENAME_ACTION)));
        // ask each time by default (if the user chose another option, it is remembered)
        conflictBox.setSelectedItem(ConflictAction.forName(Settings.get().get(Settings.CONFLICT), ConflictAction.ASK));
        actionBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v instanceof RenameAction a ? actionLabel(a) : v, i, s, f);
            }
        });
        conflictBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v instanceof ConflictAction c ? conflictLabel(c) : v, i, s, f);
            }
        });
        conflictBox.setMaximumRowCount(ConflictAction.values().length);
        actionBox.addActionListener(e -> Settings.get().put(Settings.RENAME_ACTION, ((RenameAction) actionBox.getSelectedItem()).name()));
        conflictBox.addActionListener(e -> Settings.get().put(Settings.CONFLICT, ((ConflictAction) conflictBox.getSelectedItem()).name()));
        presetChip.setVisible(false);
        presetChip.setToolTipText(tr("rename.preset.chip.tooltip"));
        presetChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        presetChip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setActivePreset(null);
                reformat();
            }
        });

        setupLists();

        JPanel lists = new JPanel(new GridBagLayout());
        lists.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        JPanel filesCard = card(tr("rename.files.title"), Icons.folder(), filesCount, filesList, leftButtons());
        JPanel namesCard = card(tr("rename.names.title"), Icons.edit(), namesCount, namesList, rightButtons());
        // GridBag shares only the extra space by weight: with the same base width (the wider of the two button bars)
        // both cards are always equally wide and centered, whatever the language of the buttons
        Dimension base = new Dimension(Math.max(filesCard.getPreferredSize().width, namesCard.getPreferredSize().width), 200);
        for (JPanel card : List.of(filesCard, namesCard)) {
            card.setPreferredSize(base);
            card.setMinimumSize(base);
        }
        lists.add(filesCard, c);
        c.weightx = 0;
        c.insets = new Insets(0, 12, 0, 12);
        lists.add(centerButtons(), c);
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        lists.add(namesCard, c);
        add(lists, BorderLayout.CENTER);
        add(bottomBar(), BorderLayout.SOUTH);
        rows.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                updateCounts();
            }

            @Override
            public void intervalRemoved(javax.swing.event.ListDataEvent e) {
                updateCounts();
            }

            @Override
            public void contentsChanged(javax.swing.event.ListDataEvent e) {
                updateCounts();
            }
        });
        updateCounts();
        updateModeLabel();
    }

    // ------------------------------------------------------------------ UI construction

    private static JLabel badge() {
        JLabel l = new JLabel("0");
        l.setOpaque(false);
        l.putClientProperty("FlatLaf.style", "font: -1 bold");
        return l;
    }

    private static JLabel chip() {
        JLabel l = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.accentSoft());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                setForeground(Theme.accentText());
                super.paintComponent(g);
            }
        };
        l.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        l.setForeground(Theme.ACCENT);
        l.putClientProperty("FlatLaf.style", "font: -1");
        return l;
    }

    /** Card with a header (icon, title, counter), list and button bar. */
    private JPanel card(String title, Icon icon, JLabel count, JList<Row> list, JPanel buttons) {
        JPanel p = new RoundedPanel(new BorderLayout(0, 6));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel l = new JLabel(title, ((Icons.VectorIcon) icon).withColor(Theme.ACCENT), SwingConstants.LEFT);
        l.setIconTextGap(8);
        l.putClientProperty("FlatLaf.styleClass", "h4");
        head.add(l, BorderLayout.WEST);
        count.setForeground(Theme.secondaryText());
        head.add(count, BorderLayout.EAST);
        head.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 6));
        p.add(head, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        p.add(scroll, BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private JPanel leftButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        p.setOpaque(false);
        p.add(toolButton(tr("rename.add"), Icons.add(), tr("rename.add.tooltip"), this::load));
        p.add(toolButton(tr("rename.remove"), Icons.trash(), tr("rename.remove.tooltip"), this::removeSelected));
        p.add(toolButton(tr("rename.clear"), Icons.clear(), tr("rename.clear.tooltip"), rows::clear));
        p.add(toolButton(tr("rename.sort"), Icons.sort(), tr("rename.sort.tooltip"), this::sort));
        return p;
    }

    private JPanel rightButtons() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        p.setOpaque(false);
        p.add(toolButton(tr("rename.format"), Icons.format(), tr("rename.format.tooltip"), this::editFormat));
        p.add(toolButton(tr("rename.manualSearch"), Icons.search(), tr("rename.manualSearch.tooltip"),
                this::manualSearch));
        p.add(toolButton(tr("rename.episode"), Icons.tv(), tr("rename.episode.tooltip"), this::chooseEpisode));
        p.add(toolButton(tr("rename.bindings"), Icons.info(), tr("rename.bindings.tooltip"), this::showBindings));
        return p;
    }

    static JButton toolButton(String text, Icon icon, String tooltip, Runnable r) {
        JButton b = new JButton(text, icon);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.setToolTipText(tooltip);
        b.setFocusable(false);
        if (r != null) {
            b.addActionListener(e -> r.run());
        }
        return b;
    }

    private JPanel centerButtons() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(Box.createVerticalGlue());

        JButton presets = bigButton(tr("rename.presets"), Icons.bookmark(), tr("rename.presets.tooltip"));
        presets.addActionListener(e -> presetsMenu().show(presets, presets.getWidth(), 0));
        JButton fetch = bigButton(tr("rename.fetch"), Icons.search(), tr("rename.fetch.tooltip"));
        fetch.addActionListener(e -> fetchMenu().show(fetch, fetch.getWidth(), 0));
        JButton rename = bigButton(tr("rename.rename"), Icons.check().withColor(Color.WHITE), tr("rename.rename.tooltip"));
        rename.putClientProperty("JButton.buttonType", "default");
        rename.setBackground(Theme.ACCENT);
        rename.setForeground(Color.WHITE);
        rename.addActionListener(e -> rename());

        // same width for the three, that of the longest text (translations are longer than Spanish)
        int width = 118;
        for (JButton b : List.of(presets, fetch, rename)) {
            width = Math.max(width, b.getPreferredSize().width + 8);
        }
        Dimension d = new Dimension(width, 64);
        for (JButton b : List.of(presets, fetch, rename)) {
            b.setPreferredSize(d);
            b.setMaximumSize(d);
            b.setMinimumSize(d);
        }
        for (JButton b : List.of(presets, fetch)) {
            p.add(b);
            p.add(Box.createVerticalStrut(8));
        }
        p.add(Box.createVerticalStrut(16));
        p.add(rename);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private static JButton bigButton(String text, Icons.VectorIcon icon, String tooltip) {
        JButton b = new JButton(text, icon.withSize(22));
        b.setToolTipText(tooltip);
        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        b.setHorizontalTextPosition(SwingConstants.CENTER);
        b.setIconTextGap(4);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setFocusable(false);
        return b;
    }

    private JPanel bottomBar() {
        JPanel bar = new RoundedPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(new JLabel(tr("rename.action")));
        left.add(actionBox);
        left.add(Box.createHorizontalStrut(6));
        left.add(new JLabel(tr("rename.conflict")));
        left.add(conflictBox);
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        progress.setVisible(false);
        progress.setPreferredSize(new Dimension(120, 6));
        right.add(progress);
        for (JLabel chip : List.of(presetChip, sourceChip, modeChip, orderChip, langChip)) {
            right.add(chip);
        }
        modeChip.setToolTipText(tr("rename.chip.mode.tooltip"));
        orderChip.setToolTipText(tr("rename.chip.order.tooltip"));
        langChip.setToolTipText(tr("rename.chip.language.tooltip"));
        sourceChip.setToolTipText(tr("rename.chip.source.tooltip"));
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void updateCounts() {
        int matched = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).target != null) {
                matched++;
            }
        }
        filesCount.setText(plural("count.files", rows.size()));
        namesCount.setText(tr("rename.count.ready", matched, rows.size()));
    }

    // ------------------------------------------------------------------ lists

    private void setupLists() {
        filesList.setModel(rows);
        namesList.setModel(rows);
        filesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        namesList.setSelectionModel(filesList.getSelectionModel());
        filesList.setFixedCellHeight(ROW_HEIGHT);
        namesList.setFixedCellHeight(ROW_HEIGHT);
        filesList.setCellRenderer(new RowRenderer(true));
        namesList.setCellRenderer(new RowRenderer(false));
        filesList.setOpaque(false);
        namesList.setOpaque(false);

        fileDrop = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport s) {
                return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                try {
                    addFiles((List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                    return true;
                } catch (Exception e) {
                    Logging.warn("Drop: " + e.getMessage());
                    return false;
                }
            }
        };
        filesList.setTransferHandler(fileDrop);
        // "New names": accepts dropping files and also dragging a row to reorder the matches
        namesList.setTransferHandler(new ReorderHandler(fileDrop));
        namesList.setDragEnabled(true);
        namesList.setDropMode(javax.swing.DropMode.INSERT);

        filesList.addMouseListener(popup(this::filesMenu));
        namesList.addMouseListener(popup(this::namesMenu));
        namesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editName();
                }
            }
        });
        filesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (rows.isEmpty() && e.getClickCount() == 1) {
                    load();
                }
            }
        });
    }

    /** Internal drag data type: index of the "New names" row being moved. */
    private static final DataFlavor ROW_FLAVOR = new DataFlavor(Integer.class, "New names row");

    /**
     * Dragging in "New names": the dragged match is inserted at the
     * target position and the others shift; the original files do not move.
     */
    private final class ReorderHandler extends TransferHandler {
        private final TransferHandler files;

        ReorderHandler(TransferHandler files) {
            this.files = files;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            int i = namesList.getSelectedIndex();
            if (i < 0 || rows.get(i).match == null) {
                return null;
            }
            return new java.awt.datatransfer.Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { ROW_FLAVOR };
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor f) {
                    return ROW_FLAVOR.equals(f);
                }

                @Override
                public Object getTransferData(DataFlavor f) {
                    return i;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport s) {
            if (s.isDataFlavorSupported(ROW_FLAVOR)) {
                return s.isDrop();
            }
            return files.canImport(s);
        }

        @Override
        public boolean importData(TransferSupport s) {
            if (!s.isDataFlavorSupported(ROW_FLAVOR)) {
                return files.importData(s);
            }
            try {
                int from = (Integer) s.getTransferable().getTransferData(ROW_FLAVOR);
                int to = ((JList.DropLocation) s.getDropLocation()).getIndex();
                moveMatch(from, to);
                return true;
            } catch (Exception e) {
                Logging.warn("Reordenar: " + e.getMessage());
                return false;
            }
        }
    }

    /** Moves the match of row from to position to (insertion index) and recomputes the names. */
    void moveMatch(int from, int to) {
        List<Row> all = allRows();
        if (from < 0 || from >= all.size() || to < 0 || to > all.size() || to == from || to == from + 1) {
            return;
        }
        List<Object> matches = new ArrayList<>();
        for (Row r : all) {
            matches.add(r.match);
        }
        Object moved = matches.remove(from);
        int insert = to > from ? to - 1 : to;
        matches.add(insert, moved);
        for (int i = 0; i < all.size(); i++) {
            Row r = all.get(i);
            if (r.match != matches.get(i)) {
                r.match = matches.get(i);
                r.target = null;
                r.error = null;
            }
        }
        filesList.setSelectedIndex(insert);
        reformat();
    }

    /** List with a large message and icon when it is empty. */
    static final class EmptyStateList extends JList<Row> {
        private final String title;
        private final String subtitle;

        EmptyStateList(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (getModel().getSize() > 0) {
                return;
            }
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            int w = getWidth();
            int h = getHeight();
            int box = Math.min(w - 40, 300);
            int bx = (w - box) / 2;
            int by = Math.max(10, h / 2 - 95);
            g.setColor(Theme.mix(Theme.border(), Theme.ACCENT, 0.35));
            g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND, 1f, new float[] { 6f, 6f }, 0));
            g.drawRoundRect(bx, by, box, 170, 22, 22);
            Icons.VectorIcon icon = Icons.drop().withSize(44).withColor(Theme.mix(Theme.ACCENT, Theme.card(), 0.25));
            icon.paintIcon(this, g, (w - 44) / 2, by + 26);
            g.setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() + 1f));
            g.setColor(UIManager.getColor("Label.foreground"));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, (w - fm.stringWidth(title)) / 2, by + 104);
            g.setFont(getFont());
            g.setColor(Theme.secondaryText());
            fm = g.getFontMetrics();
            g.drawString(subtitle, (w - fm.stringWidth(subtitle)) / 2, by + 128);
            g.dispose();
        }
    }

    /** Two-line row with the type icon: main name and secondary detail. */
    private final class RowRenderer extends JComponent implements ListCellRenderer<Row> {
        private final boolean original;
        private Row row;
        private boolean selected;

        RowRenderer(boolean original) {
            this.original = original;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Row> list, Row value, int index, boolean isSelected, boolean focus) {
            row = value;
            selected = isSelected;
            setToolTipText(original ? row.source.getAbsolutePath() : row.target != null ? row.target.getAbsolutePath() : row.error);
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
                g.fillRoundRect(2, 2, w - 4, h - 4, 12, 12);
            }
            Color fg = UIManager.getColor("List.foreground");
            Color muted = Theme.secondaryText();
            Font font = filesList.getFont();

            String main;
            String sub;
            Icons.VectorIcon icon;
            Color iconColor;
            if (original) {
                main = row.source.getName();
                File parent = row.source.getAbsoluteFile().getParentFile();
                sub = (parent == null ? "" : parent.getPath()) + "  ·  " + (row.source.isFile() ? FileUtilities.formatSize(row.source.length()) : "");
                icon = fileIcon(row.source);
                iconColor = row.match != null ? Theme.ACCENT : muted;
            } else if (row.error != null) {
                main = row.error.split("\\R")[0];
                sub = tr("rename.row.formatError");
                icon = Icons.warning();
                iconColor = Theme.ERROR;
            } else if (row.target != null) {
                main = displayName(row);
                sub = describe(row.match);
                String folder = targetFolder(row);
                if (folder != null) {
                    sub = sub + "  →  " + folder;
                }
                icon = matchIcon(row.match);
                iconColor = Theme.SUCCESS;
            } else if (row.match != null) {
                main = describe(row.match);
                sub = tr("rename.row.computing");
                icon = matchIcon(row.match);
                iconColor = muted;
            } else {
                main = tr("rename.row.unmatched");
                sub = "";
                icon = null;
                iconColor = muted;
            }

            int x = 10;
            if (icon != null) {
                Icons.VectorIcon ic = icon.withSize(20).withColor(iconColor);
                ic.paintIcon(this, g, x, (h - 20) / 2);
            }
            x += 30;
            g.setFont(font.deriveFont(row.error != null ? Font.PLAIN : Font.BOLD));
            g.setColor(row.error != null ? Theme.ERROR : row.match == null && !original ? muted : fg);
            g.drawString(SearchDialog.clip(g, main, w - x - 10), x, 20);
            g.setFont(font.deriveFont(font.getSize2D() - 1.5f));
            g.setColor(muted);
            g.drawString(SearchDialog.clip(g, sub, w - x - 10), x, 37);
            g.dispose();
        }
    }

    private static Icons.VectorIcon fileIcon(File f) {
        if (MediaTypes.isVideo(f)) {
            return Icons.film();
        }
        if (MediaTypes.isSubtitle(f)) {
            return Icons.subtitle();
        }
        if (MediaTypes.isAudio(f)) {
            return Icons.music();
        }
        return Icons.file();
    }

    private static Icons.VectorIcon matchIcon(Object m) {
        if (m instanceof Episode) {
            return Icons.tv();
        }
        if (m instanceof Movie) {
            return Icons.film();
        }
        if (m instanceof AudioTrack) {
            return Icons.music();
        }
        return Icons.file();
    }

    /** Short description of the matched object: "Lanterns of Vell · S01E01 · The Ferryman's Toll", "Glass Harbor (2024)". */
    static String describe(Object m) {
        if (m instanceof Episode e) {
            return e.getSeriesName() + "  ·  " + EpisodeFormat.S00E00.format(e) + (e.getTitle() == null ? "" : "  ·  " + e.getTitle());
        }
        if (m instanceof File f) {
            return tr("rename.describe.file", f.getName());
        }
        return String.valueOf(m);
    }

    /** Target name (file name only). */
    private static String displayName(Row r) {
        return r.target.getName();
    }

    /** Target folder if it differs from the source one. */
    private static String targetFolder(Row r) {
        File src = r.source.getAbsoluteFile().getParentFile();
        File dst = r.target.getAbsoluteFile().getParentFile();
        if (dst == null || dst.equals(src)) {
            return null;
        }
        String d = dst.getAbsolutePath();
        String s = src.getAbsolutePath() + File.separator;
        return d.startsWith(s) ? "." + File.separator + d.substring(s.length()) : d;
    }

    static String actionLabel(RenameAction a) {
        return tr("action." + a.name());
    }

    static String conflictLabel(ConflictAction c) {
        return tr("conflict." + c.name());
    }

    /** Mode name in the interface: "Series", "Movies", "Music", "File". */
    static String modeLabel(Mode m) {
        return tr("mode." + m.name());
    }

    // ------------------------------------------------------------------ "Fetch data" menu

    private JPopupMenu fetchMenu() {
        JPopupMenu m = new JPopupMenu();
        // movies first, then series
        for (Mode mode : List.of(Mode.Movie, Mode.Episode, Mode.Music, Mode.File)) {
            String header = modeLabel(mode);
            Icons.VectorIcon icon = switch (mode) {
                case Episode -> Icons.tv();
                case Movie -> Icons.film();
                case Music -> Icons.music();
                case File -> Icons.file();
            };
            JLabel h = new JLabel(header, icon.withSize(16).withColor(Theme.ACCENT), SwingConstants.LEFT);
            h.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 10));
            h.putClientProperty("FlatLaf.style", "font: -1 bold");
            m.add(h);
            for (Datasource d : mode.getDatasources()) {
                JMenuItem item = new JMenuItem(d.getName());
                item.setBorder(BorderFactory.createEmptyBorder(4, 26, 4, 10));
                item.addActionListener(e -> {
                    if (mode != Mode.File) {
                        mode.setDefaultDatasource(d);
                    }
                    // a manually chosen search stops using the preset (and its target folder)
                    setActivePreset(null);
                    fetch(d);
                });
                m.add(item);
            }
            m.addSeparator();
        }
        JMenu matchMode = new JMenu(tr("match.mode"));
        ButtonGroup g1 = new ButtonGroup();
        for (String s : List.of("Strict", "Opportunistic")) {
            JRadioButtonMenuItem i = new JRadioButtonMenuItem(s.equals("Strict") ? tr("match.strict.long") : tr("match.opportunistic.long"), strict == s.equals("Strict"));
            i.addActionListener(e -> {
                strict = s.equals("Strict");
                Settings.get().put(Settings.MATCH_MODE, s);
                updateModeLabel();
            });
            g1.add(i);
            matchMode.add(i);
        }
        m.add(matchMode);
        JMenu orderMenu = new JMenu(tr("episode.order"));
        ButtonGroup g2 = new ButtonGroup();
        for (SortOrder o : SortOrder.values()) {
            JRadioButtonMenuItem i = new JRadioButtonMenuItem(o.name(), o == order);
            i.addActionListener(e -> {
                order = o;
                Settings.get().put(Settings.EPISODE_ORDER, o.name());
                updateModeLabel();
            });
            g2.add(i);
            orderMenu.add(i);
        }
        m.add(orderMenu);
        JMenu lang = new JMenu(tr("rename.fetch.language"));
        ButtonGroup g3 = new ButtonGroup();
        String current = Settings.get().getLocale().toLanguageTag();
        for (String tag : LANGUAGES) {
            Locale l = Locale.forLanguageTag(tag);
            JRadioButtonMenuItem i = new JRadioButtonMenuItem(l.getDisplayName(I18n.locale()) + " (" + tag + ")", tag.equals(current));
            i.addActionListener(e -> {
                Settings.get().put(Settings.LANGUAGE, tag);
                updateModeLabel();
            });
            g3.add(i);
            lang.add(i);
        }
        m.add(lang);
        return m;
    }

    // ------------------------------------------------------------------ presets

    private JPopupMenu presetsMenu() {
        JPopupMenu m = new JPopupMenu();
        List<Preset> presets = Presets.get().list();
        if (presets.isEmpty()) {
            JMenuItem none = new JMenuItem(tr("rename.presets.none"));
            none.setEnabled(false);
            m.add(none);
        }
        for (Preset p : presets) {
            Icons.VectorIcon icon = switch (p.getMode()) {
                case Episode -> Icons.tv();
                case Movie -> Icons.film();
                case Music -> Icons.music();
                case File -> Icons.file();
            };
            JMenuItem item = new JMenuItem(p.name(), icon.withSize(16));
            item.setToolTipText(p.format() + "  [" + (p.database() == null ? "file" : p.database()) + "]");
            item.addActionListener(e -> applyPreset(p));
            m.add(item);
        }
        m.addSeparator();
        JMenuItem manage = new JMenuItem(tr("rename.presets.edit"), Icons.edit().withSize(16));
        manage.addActionListener(e -> new PresetsDialog(frame).setVisible(true));
        m.add(manage);
        return m;
    }

    /** Applies the preset (format, order, mode, language) and starts the search with its source. */
    void applyPreset(Preset p) {
        Mode mode = p.getMode();
        mode.setFormat(p.format());
        if (p.getSortOrder() != null) {
            order = p.getSortOrder();
            Settings.get().put(Settings.EPISODE_ORDER, order.name());
        }
        if (p.isStrict() != null) {
            strict = p.isStrict();
            Settings.get().put(Settings.MATCH_MODE, strict ? "Strict" : "Opportunistic");
        }
        if (p.getLocale() != null) {
            Settings.get().put(Settings.LANGUAGE, p.language());
        }
        Datasource d = p.getDatasource();
        if (d != null) {
            mode.setDefaultDatasource(d);
        }
        Logging.info(tr("profile.log", p.name()));
        setActivePreset(p);
        if (rows.isEmpty()) {
            lastDatasource = d == null ? Mode.LOCAL : d;
            updateModeLabel();
            JOptionPane.showMessageDialog(frame, tr("rename.preset.appliedEmpty", p.name()));
            return;
        }
        fetch(d == null ? Mode.LOCAL : d);
    }

    private void setActivePreset(Preset p) {
        activePreset = p;
        presetChip.setVisible(p != null);
        presetChip.setText(p == null ? "" : tr("rename.preset.chip", p.name()));
        revalidate();
    }

    /** Base folder of each object: the active preset's; if it has none, the Preferences one; otherwise the file's. */
    private java.util.function.Function<Object, File> destinations() {
        String presetFolder = activePreset == null ? null : activePreset.outputFolder();
        return match -> com.mediarenamer.rename.Destinations.resolve(match, presetFolder);
    }

    private void updateModeLabel() {
        modeChip.setText(strict ? tr("match.strict") : tr("match.opportunistic"));
        orderChip.setText(order.name());
        langChip.setText(Settings.get().getLocale().toLanguageTag());
        sourceChip.setText(lastDatasource == null ? tr("rename.chip.noSource") : lastDatasource.getName());
    }

    // ------------------------------------------------------------------ actions

    void load() {
        JFileChooser fc = new JFileChooser(Settings.get().get("ui.lastdir"));
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fc.setMultiSelectionEnabled(true);
        fc.setDialogTitle(tr("rename.add.tooltip"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File[] sel = fc.getSelectedFiles();
            if (sel.length > 0) {
                Settings.get().put("ui.lastdir", sel[0].getAbsoluteFile().getParent());
            }
            addFiles(List.of(sel));
        }
    }

    TransferHandler fileDropHandler() {
        return fileDrop;
    }

    void addFiles(List<File> input) {
        List<File> files = new ArrayList<>();
        for (File f : input) {
            if (f.isDirectory()) {
                try (Stream<java.nio.file.Path> s = Files.walk(f.toPath())) {
                    s.filter(Files::isRegularFile).map(java.nio.file.Path::toFile).filter(RenamePanel::accept).sorted().forEach(files::add);
                } catch (Exception e) {
                    Logging.warn(f + ": " + e.getMessage());
                }
            } else if (f.isFile()) {
                files.add(f);
            }
        }
        List<File> existing = sourceFiles();
        for (File f : files) {
            if (!existing.contains(f)) {
                rows.addElement(new Row(f));
            }
        }
        // nothing selected yet: the first row (both lists share the selection); an existing selection is kept
        if (filesList.isSelectionEmpty() && !rows.isEmpty()) {
            filesList.setSelectedIndex(0);
        }
        Logging.info(plural("rename.log.loaded", rows.size()));
    }

    /** Ignores hidden/system files. */
    private static boolean accept(File f) {
        String n = f.getName();
        return !f.isHidden() && !n.startsWith(".") && !n.equalsIgnoreCase("Thumbs.db") && !n.equalsIgnoreCase("desktop.ini");
    }

    private void removeSelected() {
        int[] idx = filesList.getSelectedIndices();
        for (int i = idx.length - 1; i >= 0; i--) {
            rows.remove(idx[i]);
        }
    }

    private void sort() {
        List<Row> l = allRows();
        l.sort(Comparator.comparing(r -> r.source.getAbsolutePath().toLowerCase()));
        rows.clear();
        l.forEach(rows::addElement);
    }

    private List<Row> allRows() {
        List<Row> l = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            l.add(rows.get(i));
        }
        return l;
    }

    /** Original files of the list, in order. */
    List<File> files() {
        return sourceFiles();
    }

    private List<File> sourceFiles() {
        return allRows().stream().map(r -> r.source).toList();
    }

    private void fetch(Datasource d) {
        List<File> files = sourceFiles();
        if (files.isEmpty()) {
            JOptionPane.showMessageDialog(frame, tr("rename.fetch.noFiles"));
            return;
        }
        lastDatasource = d;
        updateModeLabel();
        AutoCompleteMatcher matcher = Mode.matcher(d);
        Locale locale = Settings.get().getLocale();
        SortOrder o = d instanceof EpisodeListProvider p && !p.getSortOrders().contains(order) ? p.getSortOrders().get(0) : order;
        overlay.start(tr("rename.fetch.progress", d.getName()), true);
        overlay.progress(0, 0, plural("count.files", files.size()));
        new SwingWorker<List<Match<Object, File>>, Void>() {
            @Override
            protected List<Match<Object, File>> doInBackground() throws Exception {
                Logging.info(tr("rename.log.searching", d.getName()));
                return matcher.match(files, new AutoCompleteMatcher.Options(strict, o, locale, new SearchDialog.SwingSelector(frame), overlay::isCancelled));
            }

            @Override
            protected void done() {
                overlay.finish();
                try {
                    apply(get(), true);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    Logging.warn(cause.getMessage());
                    JOptionPane.showMessageDialog(frame, cause.getMessage(), d.getName(), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Applies matches. With replaceAll the previous ones are cleared and the list is reordered (matched first);
     * otherwise only the affected rows are updated (manual search).
     */
    private void apply(List<Match<Object, File>> matches, boolean replaceAll) {
        List<Row> matched = new ArrayList<>();
        List<Row> unmatched = new ArrayList<>();
        for (Row r : allRows()) {
            Object found = null;
            for (Match<Object, File> m : matches) {
                if (m.getCandidate().equals(r.source)) {
                    found = m.getValue();
                }
            }
            if (found != null || replaceAll) {
                r.match = found;
                r.target = null;
                r.error = null;
            }
            (r.match != null ? matched : unmatched).add(r);
        }
        if (replaceAll) {
            rows.clear();
            matched.forEach(rows::addElement);
            unmatched.forEach(rows::addElement);
        }
        reformat();
        Logging.info(tr("rename.log.matched", matched.size(), matched.size() + unmatched.size()));
    }

    /** Recomputes the new names with the current format (in the background: it may read MediaInfo or the network). */
    void reformat() {
        List<Match<Object, File>> matches = new ArrayList<>();
        List<Row> withMatch = new ArrayList<>();
        for (Row r : allRows()) {
            if (r.match != null) {
                matches.add(new Match<>(r.match, r.source));
                withMatch.add(r);
            }
        }
        RenameEngine.Formats formats = RenameEngine.Formats.fromSettings();
        java.util.function.Function<Object, File> dest = destinations();
        busy(true);
        new SwingWorker<List<RenameEngine.Entry>, Void>() {
            @Override
            protected List<RenameEngine.Entry> doInBackground() {
                return RenameEngine.format(matches, formats, dest);
            }

            @Override
            protected void done() {
                busy(false);
                try {
                    List<RenameEngine.Entry> entries = get();
                    for (int i = 0; i < entries.size(); i++) {
                        withMatch.get(i).target = entries.get(i).target();
                        withMatch.get(i).error = entries.get(i).error();
                    }
                } catch (Exception e) {
                    Logging.warn(e.getMessage());
                }
                updateCounts();
                filesList.repaint();
                namesList.repaint();
            }
        }.execute();
    }

    /**
     * Manual search: opens the poster search for the selected files (or the unmatched
     * ones) and applies the chosen movie or series.
     */
    private void manualSearch() {
        List<Row> targets = new ArrayList<>();
        for (int i : filesList.getSelectedIndices()) {
            targets.add(rows.get(i));
        }
        if (targets.isEmpty()) {
            allRows().stream().filter(r -> r.match == null).forEach(targets::add);
        }
        if (targets.isEmpty()) {
            JOptionPane.showMessageDialog(frame, rows.isEmpty() ? tr("rename.manualSearch.noFiles") : tr("rename.manualSearch.noSelection"));
            return;
        }
        List<File> files = targets.stream().map(r -> r.source).toList();
        boolean episodes = files.stream().filter(f -> new SeasonEpisodeMatcher(true).match(f.getName()) != null).count() * 2 > files.size()
                || lastDatasource instanceof EpisodeListProvider;
        if (lastDatasource instanceof MovieLookupService) {
            episodes = false;
        }
        Mode mode = episodes ? Mode.Episode : Mode.Movie;
        Datasource source = lastDatasource != null && Mode.forDatasource(lastDatasource) == mode ? lastDatasource : mode.getDefaultDatasource();
        String query;
        if (episodes) {
            query = MediaDetection.detectSeriesName(files.get(0));
        } else {
            MediaDetection.MovieQuery q = MediaDetection.detectMovie(files.get(0));
            query = q == null ? null : q.toString();
        }
        List<Datasource> sources = new ArrayList<>();
        sources.addAll(WebServices.getMovieLookupServices());
        sources.addAll(WebServices.getEpisodeListProviders());
        Locale locale = Settings.get().getLocale();
        SearchDialog d = new SearchDialog(frame, tr("rename.manualSearch.heading"), query == null ? "" : query, files.stream().map(File::getName).toList(), List.of(),
                sources, source, locale, null);
        d.setVisible(true);
        SearchResult chosen = d.getSelected();
        Datasource db = d.getDatasource();
        if (chosen == null || db == null) {
            return;
        }
        lastDatasource = db;
        updateModeLabel();
        busy(true);
        new SwingWorker<List<Match<Object, File>>, Void>() {
            @Override
            protected List<Match<Object, File>> doInBackground() throws Exception {
                if (db instanceof EpisodeListProvider p) {
                    SortOrder o = p.getSortOrders().contains(order) ? order : p.getSortOrders().get(0);
                    List<Episode> eps = p.getEpisodeList(chosen, o, locale);
                    List<File> primary = AutoCompleteMatcher.primary(files);
                    return AutoCompleteMatcher.addCompanions(files, EpisodeListMatcher.matchEpisodes(primary, eps, false));
                }
                Movie m = (Movie) chosen;
                Movie full = db instanceof MovieLookupService ms && m.getTmdbId() > 0 ? ms.getMovieDescriptor(m, locale) : m;
                Movie movie = full == null ? m : full;
                List<Match<Object, File>> l = new ArrayList<>();
                for (File f : files) {
                    l.add(new Match<>(movie, f));
                }
                return l;
            }

            @Override
            protected void done() {
                busy(false);
                try {
                    List<Match<Object, File>> result = get();
                    apply(result, false);
                    if (result.size() < files.size()) {
                        Logging.info(tr("rename.log.noEpisode", files.size() - result.size(), chosen.getName()));
                    }
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(frame, c.getMessage(), tr("rename.manualSearch"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Manually choose the episode of the selected row. The series is taken from that row's episode or,
     * if it has none, from any other row's (same provider, order and language).
     */
    private void chooseEpisode() {
        int i = filesList.getSelectedIndex();
        if (i < 0) {
            JOptionPane.showMessageDialog(frame, tr("rename.episode.noSelection"));
            return;
        }
        Row row = rows.get(i);
        Episode ref = row.match instanceof Episode e ? e : null;
        if (ref == null) {
            ref = allRows().stream().filter(r -> r.match instanceof Episode).map(r -> (Episode) r.match).findFirst().orElse(null);
        }
        if (ref == null || ref.getSeriesInfo() == null || !(WebServices.get(ref.getSeriesInfo().getDatabase()) instanceof EpisodeListProvider)) {
            JOptionPane.showMessageDialog(frame, tr("rename.episode.noSeries"));
            return;
        }
        com.mediarenamer.web.SeriesInfo si = ref.getSeriesInfo();
        EpisodeListProvider provider = (EpisodeListProvider) WebServices.get(si.getDatabase());
        SortOrder o = si.getOrder() == null ? order : si.getOrder();
        Locale locale = si.getLanguage() == null ? Settings.get().getLocale() : si.getLanguage();
        String key = si.getDatabase() + ":" + si.getId() + ":" + o + ":" + locale.toLanguageTag();
        Episode current = row.match instanceof Episode e ? e : null;
        String seriesName = si.getName() != null ? si.getName() : ref.getSeriesName();

        overlay.start(tr("rename.episode.loading", seriesName), false);
        new SwingWorker<List<Episode>, Void>() {
            @Override
            protected List<Episode> doInBackground() throws Exception {
                List<Episode> cached = episodeCache.get(key);
                if (cached != null) {
                    return cached;
                }
                List<Episode> eps = provider.getEpisodeList(new com.mediarenamer.web.SearchResult(si.getId(), seriesName, null), o, locale);
                episodeCache.put(key, eps);
                return eps;
            }

            @Override
            protected void done() {
                overlay.finish();
                try {
                    EpisodeChooserDialog d = new EpisodeChooserDialog(frame, row.source.getName(), seriesName, get(), current);
                    d.setVisible(true);
                    if (d.getResult() != null) {
                        row.match = d.getResult();
                        row.target = null;
                        row.error = null;
                        reformat();
                    }
                } catch (Exception e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(frame, c.getMessage(), tr("episode.chooser.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void editFormat() {
        Mode mode = Mode.Episode;
        Row sample = null;
        int sel = filesList.getSelectedIndex();
        for (int i = Math.max(0, sel); i < rows.size(); i++) {
            if (rows.get(i).match != null) {
                sample = rows.get(i);
                break;
            }
        }
        if (sample != null) {
            mode = modeOf(sample.match);
        } else if (lastDatasource != null) {
            mode = Mode.forDatasource(lastDatasource);
        }
        FormatDialog d = new FormatDialog(frame, mode, sample == null ? null : sample.match, sample == null ? null : sample.source);
        d.setVisible(true);
        if (d.getResult() != null) {
            d.getMode().setFormat(d.getResult());
            reformat();
        }
    }

    private void showBindings() {
        int i = filesList.getSelectedIndex();
        if (i < 0) {
            JOptionPane.showMessageDialog(frame, tr("rename.bindings.noSelection"));
            return;
        }
        Row r = rows.get(i);
        new BindingsDialog(frame, r.match != null ? r.match : r.source, r.source).setVisible(true);
    }

    static Mode modeOf(Object match) {
        if (match instanceof Episode) {
            return Mode.Episode;
        }
        if (match instanceof Movie) {
            return Mode.Movie;
        }
        if (match instanceof AudioTrack) {
            return Mode.Music;
        }
        return Mode.File;
    }

    private void editName() {
        int i = namesList.getSelectedIndex();
        if (i < 0) {
            return;
        }
        Row r = rows.get(i);
        String current = r.target != null ? r.target.getName() : r.source.getName();
        String value = (String) JOptionPane.showInputDialog(frame, tr("rename.editName.prompt"), tr("rename.editName"), JOptionPane.PLAIN_MESSAGE, Icons.edit().withSize(24), null,
                current);
        if (value != null && !value.isBlank()) {
            if (r.match == null) {
                r.match = r.source;
            }
            File dir = r.target != null ? r.target.getParentFile() : r.source.getAbsoluteFile().getParentFile();
            r.target = new File(dir, FileUtilities.validateFileName(value.trim()));
            r.error = null;
            updateCounts();
            namesList.repaint();
        }
    }

    private void rename() {
        List<RenameEngine.Entry> entries = new ArrayList<>();
        int[] selected = filesList.getSelectedIndices();
        List<Row> chosen = new ArrayList<>();
        if (selected.length > 1) {
            for (int i : selected) {
                chosen.add(rows.get(i));
            }
        } else {
            chosen.addAll(allRows());
        }
        int formatErrors = 0;
        for (Row r : chosen) {
            if (r.target != null) {
                entries.add(new RenameEngine.Entry(r.source, r.match, r.target, null));
            } else if (r.error != null) {
                formatErrors++;
            }
        }
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(frame, tr("rename.rename.nothing"));
            return;
        }

        // pre-check: invalid characters, reserved names, lengths, duplicates...
        List<NameValidator.Issue> issues = NameValidator.validate(entries);
        if (!issues.isEmpty()) {
            ValidateDialog check = new ValidateDialog(frame, issues, formatErrors);
            check.setVisible(true);
            switch (check.getChoice()) {
                case CANCEL -> {
                    return;
                }
                case CLEAN -> {
                    entries = applySuggestions(entries, issues);
                    filesList.repaint();
                    namesList.repaint();
                }
                case KEEP -> {
                    // renamed as is
                }
            }
        }

        List<RenameEngine.Entry> work = entries;
        RenameAction action = (RenameAction) actionBox.getSelectedItem();
        ConflictAction conflict = (ConflictAction) conflictBox.getSelectedItem();
        boolean jellyfinScan = action != RenameAction.TEST && JellyfinClient.scanAfterRename(activePreset == null ? null : activePreset.jellyfinScan());
        boolean plexScan = action != RenameAction.TEST && PlexClient.scanAfterRename(activePreset == null ? null : activePreset.plexScan());
        // server name -> message of a scan that could not be started
        java.util.Map<String, String> scanErrors = java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
        overlay.start(action == RenameAction.TEST ? tr("rename.rename.testing") : actionLabel(action) + "…", true);
        new SwingWorker<RenameEngine.Result, Integer>() {
            @Override
            protected RenameEngine.Result doInBackground() {
                RenameEngine.Result r = RenameEngine.execute(work, action, conflict, new ConflictDialog.Resolver(frame),
                        (i, n, f) -> overlay.progress(i - 1, n, tr("rename.rename.progress", i, n, f.getName())), overlay::isCancelled);
                // extra step: notify the media servers once all files have been moved (not if cancelled); a failure
                // of one does not stop the other
                if (jellyfinScan && !r.cancelled() && !r.renamed().isEmpty()) {
                    try {
                        JellyfinClient jellyfin = JellyfinClient.fromSettings();
                        overlay.step(tr("rename.jellyfin.scanning"), "Jellyfin · " + jellyfin.getBaseUrl());
                        jellyfin.refreshLibraries();
                        Logging.info(tr("rename.jellyfin.started"));
                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                        scanErrors.put("Jellyfin", tr("rename.jellyfin.failed", msg));
                        Logging.warn("Jellyfin: " + msg);
                    }
                }
                if (plexScan && !r.cancelled() && !r.renamed().isEmpty()) {
                    try {
                        PlexClient plex = PlexClient.fromSettings();
                        overlay.step(tr("rename.jellyfin.scanning"), "Plex · " + plex.getBaseUrl());
                        Logging.info(tr("rename.plex.started", plex.refreshLibraries()));
                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                        scanErrors.put("Plex", tr("rename.plex.failed", msg));
                        Logging.warn("Plex: " + msg);
                    }
                }
                return r;
            }

            @Override
            protected void done() {
                overlay.finish();
                try {
                    RenameEngine.Result r = get();
                    if (action != RenameAction.TEST) {
                        for (int i = rows.size() - 1; i >= 0; i--) {
                            if (r.renamed().containsKey(rows.get(i).source)) {
                                rows.remove(i);
                            }
                        }
                    }
                    String msg = actionLabel(action) + ": " + plural("count.files", r.renamed().size())
                            + (r.skipped().isEmpty() ? "" : ", " + plural("count.skipped", r.skipped().size()))
                            + (r.failed().isEmpty() ? "" : ", " + plural("count.errors", r.failed().size()));
                    Logging.info(msg);
                    if (r.cancelled()) {
                        JOptionPane.showMessageDialog(frame, tr("rename.rename.cancelled", msg), tr("rename.rename.cancelled.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                    } else if (!r.failed().isEmpty()) {
                        StringBuilder sb = new StringBuilder(msg + "\n\n");
                        r.failed().forEach((f, e) -> sb.append(f.getName()).append(": ").append(e).append('\n'));
                        JOptionPane.showMessageDialog(frame, sb.toString(), tr("rename.rename"), JOptionPane.WARNING_MESSAGE);
                    } else if (action == RenameAction.TEST) {
                        JOptionPane.showMessageDialog(frame, tr("rename.rename.testDone", r.renamed().size()), tr("rename.rename.testDone.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    scanErrors.forEach((server, error) -> JOptionPane.showMessageDialog(frame, error, server, JOptionPane.WARNING_MESSAGE));
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(frame, e.getMessage(), tr("rename.rename"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** Replaces the targets with the cleanup suggestions (in the list and in the entries to rename). */
    private List<RenameEngine.Entry> applySuggestions(List<RenameEngine.Entry> entries, List<NameValidator.Issue> issues) {
        java.util.Map<File, File> fixes = new java.util.HashMap<>();
        for (NameValidator.Issue i : issues) {
            if (i.suggestion() != null) {
                fixes.put(i.entry().source(), i.suggestion());
            }
        }
        for (Row r : allRows()) {
            if (fixes.containsKey(r.source)) {
                r.target = fixes.get(r.source);
            }
        }
        List<RenameEngine.Entry> out = new ArrayList<>();
        for (RenameEngine.Entry e : entries) {
            File fixed = fixes.get(e.source());
            out.add(fixed == null ? e : new RenameEngine.Entry(e.source(), e.match(), fixed, null));
        }
        return out;
    }

    private void busy(boolean b) {
        progress.setVisible(b);
        progress.setIndeterminate(b);
        setCursor(b ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
        revalidate();
    }

    // ------------------------------------------------------------------ context menus

    private JPopupMenu filesMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(menuItem(tr("rename.manualSearch") + "…", Icons.search(), this::manualSearch));
        m.add(menuItem(tr("rename.menu.openFolder"), Icons.folder(), () -> {
            int i = filesList.getSelectedIndex();
            if (i >= 0) {
                try {
                    Desktop.getDesktop().open(rows.get(i).source.getAbsoluteFile().getParentFile());
                } catch (Exception ex) {
                    Logging.warn(ex.getMessage());
                }
            }
        }));
        m.add(menuItem(tr("rename.menu.properties"), Icons.info(), this::showBindings));
        m.addSeparator();
        m.add(menuItem(tr("rename.menu.remove"), Icons.trash(), this::removeSelected));
        return m;
    }

    private JPopupMenu namesMenu() {
        JPopupMenu m = new JPopupMenu();
        m.add(menuItem(tr("rename.editName"), Icons.edit(), this::editName));
        m.add(menuItem(tr("episode.chooser.title") + "…", Icons.tv(), this::chooseEpisode));
        m.add(menuItem(tr("rename.manualSearch") + "…", Icons.search(), this::manualSearch));
        m.add(menuItem(tr("rename.menu.bindings"), Icons.info(), this::showBindings));
        m.addSeparator();
        m.add(menuItem(tr("rename.menu.unmatch"), Icons.clear(), () -> {
            for (int i : namesList.getSelectedIndices()) {
                Row r = rows.get(i);
                r.match = null;
                r.target = null;
                r.error = null;
            }
            updateCounts();
            namesList.repaint();
            filesList.repaint();
        }));
        return m;
    }

    private static JMenuItem menuItem(String text, Icons.VectorIcon icon, Runnable r) {
        JMenuItem item = new JMenuItem(new AbstractAction(text, icon.withSize(16)) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                r.run();
            }
        });
        return item;
    }

    private static MouseAdapter popup(java.util.function.Supplier<JPopupMenu> menu) {
        return new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                show(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                show(e);
            }

            private void show(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    JList<?> list = (JList<?>) e.getSource();
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0 && !list.isSelectedIndex(i)) {
                        list.setSelectedIndex(i);
                    }
                    if (list.getModel().getSize() > 0) {
                        menu.get().show(list, e.getX(), e.getY());
                    }
                }
            }
        };
    }

    /** Panel with a "card" background and rounded corners. */
    static class RoundedPanel extends JPanel {
        RoundedPanel(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.card());
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g.setColor(Theme.border());
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g.dispose();
            super.paintComponent(g0);
        }
    }
}
