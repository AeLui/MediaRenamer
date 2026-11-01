package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.mediarenamer.app.Updates;
import com.mediarenamer.app.Updates.Asset;
import com.mediarenamer.app.Updates.Release;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;

/**
 * "A new version is available": release notes and Update / Later / Skip this version. In an installed copy "Update"
 * downloads the installer, runs it and closes MediaRenamer; anywhere else (or if that fails) it opens the release page.
 */
final class UpdateDialog extends JDialog {

    /** A main window that knows whether a long operation (rename, data fetch) is running. */
    interface Busy {
        boolean isBusy();
    }

    /** True if the window at the end of the owner chain of {@code w} is running an operation that must not be cut. */
    static boolean busy(Window w) {
        return mainWindow(w) instanceof Busy b && b.isBusy();
    }

    /** Downloads and installs the update (or opens the release page). */
    final JButton update;
    /** Closes the dialog; the same version is offered again next time. */
    final JButton later;
    /** Remembers this version so the automatic check does not offer it again; hidden for a manual check. */
    final JButton skip;

    private final Release release;

    UpdateDialog(Window owner, Release release, boolean manual) {
        super(owner, tr("update.title"), ModalityType.APPLICATION_MODAL);
        this.release = release;
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));

        JLabel head = new JLabel(tr("update.available", release.version(), Settings.version()));
        head.putClientProperty("FlatLaf.styleClass", "h3");
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.add(head, BorderLayout.NORTH);
        north.add(new JLabel(tr("update.notes")), BorderLayout.SOUTH);
        content.add(north, BorderLayout.NORTH);

        // plain text on purpose: release notes come from the network and must never be interpreted as HTML
        JTextArea notes = new JTextArea(release.notes() == null ? "" : release.notes());
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(notes);
        scroll.setPreferredSize(new Dimension(520, 240));
        content.add(scroll, BorderLayout.CENTER);

        update = new JButton(tr("update.now"));
        later = new JButton(tr("update.later"));
        skip = new JButton(tr("update.skip"));
        skip.setVisible(!manual);
        update.addActionListener(e -> install());
        later.addActionListener(e -> dispose());
        skip.addActionListener(e -> {
            Settings.get().put(Settings.UPDATES_SKIP_VERSION, release.version());
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(skip);
        buttons.add(later);
        buttons.add(update);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(update);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Offers {@code release} (blocks until the user decides). */
    static void show(Window owner, Release release, boolean manual) {
        new UpdateDialog(owner, release, manual).setVisible(true);
    }

    private void install() {
        Asset installer = release.installer().orElse(null);
        if (Updates.mode() != Updates.Mode.INSTALLED) {
            dispose();
            browse(); // portable or development copy: it cannot update itself, the release page is the way
            return;
        }
        if (installer == null) {
            dispose();
            openPage(tr("update.noInstaller"));
            return;
        }
        Window owner = getOwner();
        if (busy(owner)) {
            // closing the application now would cut a move in the middle and lose the history of the files already renamed
            JOptionPane.showMessageDialog(this, tr("update.busy"), Settings.APP_NAME, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        setVisible(false);
        Path file = null;
        try {
            file = downloadWithProgress(owner, installer);
            if (file == null) {
                dispose(); // cancelled: nothing happens and MediaRenamer keeps running
                return;
            }
            Updates.launchInstaller(file);
        } catch (Exception e) {
            Logging.info("The update failed: " + e);
            Updates.discard(file);
            dispose();
            openPage(UpdateCheck.describe(e));
            return;
        }
        dispose();
        Window main = mainWindow(owner);
        for (Window w = owner; w != null && w != main; w = w.getOwner()) {
            w.dispose(); // e.g. the About window the check was started from
        }
        if (main != null) {
            main.dispatchEvent(new WindowEvent(main, WindowEvent.WINDOW_CLOSING)); // EXIT_ON_CLOSE ends MediaRenamer
        }
    }

    /** The top-level window at the end of the owner chain (dialogs opened from dialogs), or {@code w} itself if it has none. */
    static Window mainWindow(Window w) {
        while (w != null && w.getOwner() != null) {
            w = w.getOwner();
        }
        return w;
    }

    /** Downloads with a modal progress bar; returns null if the user cancelled. */
    private static Path downloadWithProgress(Window owner, Asset installer) throws Exception {
        JDialog progress = new JDialog(owner, tr("update.title"), ModalityType.APPLICATION_MODAL);
        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        JButton cancel = new JButton(tr("common.cancel"));
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        p.add(new JLabel(tr("update.downloading")), BorderLayout.NORTH);
        p.add(bar, BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.add(cancel);
        p.add(south, BorderLayout.SOUTH);
        progress.setContentPane(p);
        progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        progress.setMinimumSize(new Dimension(380, 0));
        progress.pack();
        progress.setLocationRelativeTo(owner);

        AtomicBoolean cancelled = new AtomicBoolean();
        SwingWorker<Path, Long> worker = new SwingWorker<>() {
            @Override
            protected Path doInBackground() throws Exception {
                return Updates.download(installer, this::publish, cancelled::get);
            }

            @Override
            protected void process(List<Long> chunks) {
                if (installer.size() > 0) {
                    bar.setValue((int) Math.min(100, chunks.get(chunks.size() - 1) * 100 / installer.size()));
                }
            }

            @Override
            protected void done() {
                progress.dispose();
            }
        };
        Runnable cancelAction = () -> {
            cancelled.set(true);
            cancel.setEnabled(false);
            worker.cancel(true); // also interrupts a body read that is stalled and so never looks at the flag
        };
        cancel.addActionListener(e -> cancelAction.run());
        progress.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelAction.run();
            }
        });
        worker.execute();
        progress.setVisible(true); // returns when done() or the cancel button disposes it
        if (cancelled.get()) {
            return null;
        }
        try {
            return worker.get();
        } catch (CancellationException e) {
            return null;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Updates.CancelledException || cause instanceof InterruptedException) {
                return null;
            }
            throw cause instanceof Exception ex ? ex : e;
        }
    }

    /** Tells the user the update cannot be done here and opens the release page for a manual download. */
    private void openPage(String reason) {
        JOptionPane.showMessageDialog(getOwner(), tr("update.installFailed", reason), Settings.APP_NAME, JOptionPane.WARNING_MESSAGE);
        browse();
    }

    private void browse() {
        try {
            if (release.page() != null && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(release.page());
            }
        } catch (Exception e) {
            Logging.info("Could not open the release page: " + e.getMessage());
        }
    }
}
