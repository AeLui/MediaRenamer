package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.mediarenamer.app.Updates;
import com.mediarenamer.app.Updates.Release;

import java.awt.Window;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Runs the update check in the background and tells the user on the EDT: the automatic one at start-up (silent unless
 * there is something new) and the manual one (always answers). The decisions live in {@link #check}, which has no threads.
 */
final class UpdateCheck {

    /** Wait after start-up before the automatic check, so that it does not compete with the window opening. */
    private static final int START_DELAY_MS = 5000;

    private UpdateCheck() {
    }

    /** Checks once a day, if enabled in the preferences; never blocks the EDT and never complains. */
    static void automatic(JFrame owner) {
        boolean due = Updates.dueForAutoCheck(Settings.get().getBoolean(Settings.UPDATES_AUTO_CHECK, true),
                lastCheck(), Instant.now());
        if (!due) {
            return;
        }
        Timer delay = new Timer(START_DELAY_MS, e -> start(check(Updates::latest, Settings.version(),
                Settings.get().get(Settings.UPDATES_SKIP_VERSION), false, r -> SwingUtilities.invokeLater(() -> showWhenIdle(owner, r)),
                () -> { }, ex -> { })));
        delay.setRepeats(false);
        delay.start();
    }

    /** Deletes installers left in the temp folder by earlier updates, off the Swing thread. */
    static void sweepLeftovers() {
        start(Updates::sweepStale);
    }

    /** Retry interval while the main window is busy. */
    private static final int BUSY_RETRY_MS = 3000;

    /** Shows the automatic dialog once the main window is idle (never in the middle of a rename or a data fetch). */
    private static void showWhenIdle(JFrame owner, Release r) {
        Timer retry = new Timer(BUSY_RETRY_MS, null);
        retry.addActionListener(e -> {
            if (!owner.isDisplayable() || showIfIdle(UpdateDialog.busy(owner), () -> UpdateDialog.show(owner, r, false))) {
                retry.stop();
            }
        });
        if (!showIfIdle(UpdateDialog.busy(owner), () -> UpdateDialog.show(owner, r, false))) {
            retry.start();
        }
    }

    /** Runs {@code show} unless {@code busy}; returns whether it ran. */
    static boolean showIfIdle(boolean busy, Runnable show) {
        if (busy) {
            return false;
        }
        show.run();
        return true;
    }

    /** Checks now and always reports: new version, "up to date" or the error. */
    static void manual(Window owner) {
        manual(owner, () -> { });
    }

    /** As {@link #manual(Window)}; {@code done} runs on the EDT when the check has finished, before its outcome is shown. */
    static void manual(Window owner, Runnable done) {
        start(check(Updates::latest, Settings.version(), Settings.get().get(Settings.UPDATES_SKIP_VERSION), true,
                r -> SwingUtilities.invokeLater(() -> {
                    done.run();
                    UpdateDialog.show(shown(owner), r, true);
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    done.run();
                    JOptionPane.showMessageDialog(shown(owner), tr("update.upToDate"));
                }),
                ex -> SwingUtilities.invokeLater(() -> {
                    done.run();
                    JOptionPane.showMessageDialog(shown(owner), tr("update.failed", describe(ex)), Settings.APP_NAME, JOptionPane.WARNING_MESSAGE);
                })));
    }

    /** {@code owner} if it is still on screen; the About window may have been closed while the check ran. */
    static Window shown(Window owner) {
        return owner != null && owner.isDisplayable() ? owner : UpdateDialog.mainWindow(owner);
    }

    private static void start(Runnable job) {
        Thread t = new Thread(job, "update-check");
        t.setDaemon(true);
        t.start();
    }

    /**
     * The decision logic. The returned job fetches the release and calls at most one callback; the callbacks that touch
     * Swing must hop to the EDT themselves. Automatic: the attempt is recorded even on failure (so a failing check is not
     * retried at every start), a failure is only logged, and nothing is said unless {@code show}. Manual: the skipped
     * version is ignored and every outcome is reported.
     */
    static Runnable check(Callable<Release> source, String current, String skipped, boolean manual,
            Consumer<Release> show, Runnable upToDate, Consumer<Exception> failed) {
        return () -> {
            if (!manual) {
                recordAttempt();
            }
            try {
                Release r = source.call();
                if (Updates.offer(r, current, skipped, manual)) {
                    show.accept(r);
                } else if (manual) {
                    upToDate.run();
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (manual) {
                    failed.accept(e);
                } else {
                    Logging.info("Update check failed: " + e.getMessage());
                }
            }
        };
    }

    private static void recordAttempt() {
        Settings.get().put(Settings.UPDATES_LAST_CHECK, Long.toString(System.currentTimeMillis()));
    }

    private static long lastCheck() {
        try {
            return Long.parseLong(Settings.get().get(Settings.UPDATES_LAST_CHECK, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** The message of {@code e}, or its type when it has none. */
    static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
