package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Settings;
import com.mediarenamer.format.ExpressionFileFormat;
import com.mediarenamer.format.FormatException;
import com.mediarenamer.format.MediaBindingBean;
import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.RenameEngine;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.SeriesInfo;
import com.mediarenamer.web.SimpleDate;
import com.mediarenamer.web.SortOrder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Format editor with live preview:
 * Groovy highlighting, examples, recent formats and "View Bindings".
 */
public class FormatDialog extends JDialog {

    /** Examples per mode; they include the default presets. */
    private static final Map<Mode, List<String>> EXAMPLES = new LinkedHashMap<>();

    static {
        EXAMPLES.put(Mode.Episode, List.of("{n} - {s00e00} - {t}", "{n} - {sxe} - {t}", "{n}/Season {s}/{n} - {s00e00} - {t}", "{plex}", "{plex.id}",
                "{jellyfin}", "{drive}/Media/{plex.id}", "{n.space('.')}.{s00e00}.{t.space('.')}", "{n} ({y})/Season {s.pad(2)}/{n} - {s00e00} - {t} [{vf}]",
                "{n} - {s00e00} - {t}{' [' + allOf{vf}{vc}{ac}.join(' ') + ']'}", "{n} - {absolute.pad(3)} - {t}", "{n} - {d.format('yyyy-MM-dd')} - {t}"));
        EXAMPLES.put(Mode.Movie, List.of("{n} ({y})", "{ny}", "{n} ({y})/{n} ({y})", "{n} ({y}) [{vf}]", "{plex}", "{plex.id}", "{jellyfin.id}",
                "Movies/{az}/{n} ({y})", "{collection}/{n} ({y})", "{n.colon(' - ')} ({y}) {' [' + vf + ' ' + vc + ']'}",
                "{n} ({y}) {tags}", "{fn}{ allOf{vf}{vc}{hdr}{ac}{channels}.joining(' ', ' [', ']') }"));
        EXAMPLES.put(Mode.Music, List.of("{n} - {t}", "{artist}/{album}/{pi.pad(2)} - {t}", "{plex}", "{albumArtist}/{album} ({y})/{pi.pad(2)} {t}"));
        EXAMPLES.put(Mode.File, List.of("{fn}", "{fn.clean()}", "{fn.ascii().space('_')}", "{fn} [{vf} {vc} {ac}]", "{fn} [{crc32}]",
                "{ any{ original }{ mediaTitle }{ fn } }", "{folder.name}/{fn}"));
    }

    private final RSyntaxTextArea editor = new RSyntaxTextArea(4, 60);
    private final JLabel preview = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
    private final Object sample;
    private final File sampleFile;
    private final Timer timer;
    private String result;

    public FormatDialog(JFrame owner, Mode mode, Object sample, File sampleFile) {
        super(owner, tr("format.title"), true);
        this.sample = sample;
        this.sampleFile = sampleFile;
        setLayout(new BorderLayout(8, 8));
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel(tr("format.mode")));
        modeBox.setSelectedItem(mode);
        modeBox.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object v, int i, boolean s, boolean f) {
                return super.getListCellRendererComponent(l, v instanceof Mode m ? RenamePanel.modeLabel(m) : v, i, s, f);
            }
        });
        top.add(modeBox);
        JComboBox<String> examples = new JComboBox<>();
        JComboBox<String> recent = new JComboBox<>();
        top.add(new JLabel(tr("format.examples")));
        top.add(examples);
        top.add(new JLabel(tr("format.recent")));
        top.add(recent);
        content.add(top, BorderLayout.NORTH);

        editor.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_GROOVY);
        editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        editor.setHighlightCurrentLine(false);
        editor.setText(mode.getFormat());
        RTextScrollPane scroll = new RTextScrollPane(editor);
        scroll.setLineNumbersEnabled(false);
        content.add(scroll, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        JPanel info = new JPanel(new BorderLayout(4, 4));
        preview.setFont(preview.getFont().deriveFont(Font.BOLD, 14f));
        info.add(preview, BorderLayout.NORTH);
        info.add(status, BorderLayout.SOUTH);
        south.add(info, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton bindings = new JButton(tr("format.bindings"));
        bindings.addActionListener(e -> new BindingsDialog(owner, sampleObject(), sampleFile()).setVisible(true));
        JButton reset = new JButton(tr("format.reset"));
        reset.addActionListener(e -> editor.setText(currentMode().getDefaultFormat()));
        JButton cancel = new JButton(tr("common.cancel"));
        cancel.addActionListener(e -> dispose());
        JButton use = new JButton(tr("format.use"));
        use.addActionListener(e -> {
            result = editor.getText().trim();
            addRecent(result);
            dispose();
        });
        buttons.add(bindings);
        buttons.add(reset);
        buttons.add(cancel);
        buttons.add(use);
        south.add(buttons, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);
        add(content);
        getRootPane().setDefaultButton(use);

        Runnable fillExamples = () -> {
            examples.removeAllItems();
            examples.addItem("");
            EXAMPLES.getOrDefault(currentMode(), List.of()).forEach(examples::addItem);
            recent.removeAllItems();
            recent.addItem("");
            recentFormats().forEach(recent::addItem);
        };
        fillExamples.run();
        examples.addActionListener(e -> {
            Object s = examples.getSelectedItem();
            if (s != null && !s.toString().isEmpty()) {
                editor.setText(s.toString());
            }
        });
        recent.addActionListener(e -> {
            Object s = recent.getSelectedItem();
            if (s != null && !s.toString().isEmpty()) {
                editor.setText(s.toString());
            }
        });
        modeBox.addActionListener(e -> {
            editor.setText(currentMode().getFormat());
            fillExamples.run();
        });

        timer = new Timer(300, e -> updatePreview());
        timer.setRepeats(false);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                timer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                timer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                timer.restart();
            }
        });
        updatePreview();
        setSize(860, 360);
        setLocationRelativeTo(owner);
    }

    public String getResult() {
        return result;
    }

    public Mode getMode() {
        return currentMode();
    }

    private Mode currentMode() {
        return (Mode) modeBox.getSelectedItem();
    }

    /** Sample object: the selected match if it belongs to the current mode; otherwise a fictitious one. */
    private Object sampleObject() {
        if (sample != null && RenamePanel.modeOf(sample) == currentMode()) {
            return sample;
        }
        SeriesInfo s = new SeriesInfo("TheMovieDB::TV", SortOrder.Airdate, Locale.US, 1396);
        s.setName("Breaking Bad");
        s.setStartDate(new SimpleDate(2008, 1, 20));
        s.setGenres(List.of("Drama", "Crime"));
        return switch (currentMode()) {
            case Episode -> new Episode("Breaking Bad", 1, 1, "Pilot", 1, null, new SimpleDate(2008, 1, 20), 62085, s);
            case Movie -> new Movie("Avatar", List.of(), 2009, 499549, 19995, Locale.US);
            case Music -> new com.mediarenamer.web.AudioTrack("Queen", "Bohemian Rhapsody", "A Night at the Opera", "Queen", "Bohemian Rhapsody",
                    new SimpleDate(1975, 11, 21), 1, 1, 11, 12, null, "Rock");
            case File -> sampleFile();
        };
    }

    private File sampleFile() {
        if (sampleFile != null && sample != null && RenamePanel.modeOf(sample) == currentMode()) {
            return sampleFile;
        }
        return new File(switch (currentMode()) {
            case Episode -> "Breaking.Bad.S01E01.720p.BluRay.x264-DEMAND.mkv";
            case Movie -> "Avatar.2009.1080p.BluRay.x264-SPARKS.mkv";
            case Music -> "01 bohemian rhapsody.mp3";
            case File -> sampleFile != null ? sampleFile.getPath() : "ejemplo.mkv";
        });
    }

    /** Text and color of the preview and status labels. */
    private record Preview(String preview, String status, Color color) {
    }

    /** Evaluation in progress; a newer edit cancels it (the format code may be slow: MediaInfo, files...). */
    private SwingWorker<Preview, Void> previewWorker;

    /**
     * Evaluates the format outside the Swing thread: a block that reads a slow share, calls MediaInfo or loops must not
     * freeze the window (blocks are also stopped by the time limit of {@link com.mediarenamer.format.ExpressionFormat}).
     */
    private void updatePreview() {
        String expr = editor.getText();
        Object object = sampleObject();
        File file = sampleFile();
        if (previewWorker != null) {
            previewWorker.cancel(true);
        }
        SwingWorker<Preview, Void> worker = new SwingWorker<>() {
            @Override
            protected Preview doInBackground() {
                return evaluate(expr, object, file);
            }

            @Override
            protected void done() {
                if (isCancelled() || previewWorker != this) {
                    return;
                }
                try {
                    Preview p = get();
                    if (p.preview() != null) {
                        preview.setText(p.preview());
                        preview.setForeground(null);
                    }
                    status.setText(p.status());
                    status.setForeground(p.color());
                } catch (Exception e) {
                    status.setText(e.toString());
                    status.setForeground(new Color(0xC0392B));
                }
            }
        };
        previewWorker = worker;
        worker.execute();
    }

    private static Preview evaluate(String expr, Object object, File file) {
        try {
            ExpressionFileFormat f = new ExpressionFileFormat(expr);
            String name = f.format(new MediaBindingBean(object, file));
            File target = RenameEngine.resolve(file, name, com.mediarenamer.rename.Destinations.forType(object));
            String text = target.getName().equals(target.getPath()) ? name : relative(file, target);
            List<Throwable> suppressed = f.getSuppressed();
            return suppressed.isEmpty() ? new Preview(text, "OK", new Color(0x27AE60))
                    : new Preview(text, tr("format.emptyBlocks", describe(suppressed)), new Color(0xD68910));
        } catch (FormatException e) {
            return new Preview(" ", e.getMessage(), new Color(0xC0392B));
        } catch (RuntimeException e) {
            return new Preview(null, e.toString(), new Color(0xC0392B));
        }
    }

    private static String relative(File source, File target) {
        String dir = source.getAbsoluteFile().getParent();
        String t = target.getAbsolutePath();
        return dir != null && t.startsWith(dir) ? t.substring(dir.length() + 1) : t;
    }

    private static String describe(List<Throwable> suppressed) {
        List<String> l = new ArrayList<>();
        for (Throwable t : suppressed) {
            String m = String.valueOf(t.getMessage()).split("\\R")[0];
            l.add(m.length() > 80 ? m.substring(0, 80) + "..." : m);
        }
        return String.join(" | ", l.subList(0, Math.min(3, l.size())));
    }

    static List<String> recentFormats() {
        String s = Settings.get().get(Settings.RECENT_FORMATS);
        return s == null ? List.of() : Arrays.stream(s.split("\n")).filter(x -> !x.isBlank()).toList();
    }

    static void addRecent(String format) {
        List<String> l = new ArrayList<>(recentFormats());
        l.remove(format);
        l.add(0, format);
        Settings.get().put(Settings.RECENT_FORMATS, String.join("\n", l.subList(0, Math.min(10, l.size()))));
    }
}
