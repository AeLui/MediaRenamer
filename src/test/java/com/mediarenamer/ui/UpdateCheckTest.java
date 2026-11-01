package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.app.Settings;
import com.mediarenamer.app.Updates.Release;
import com.mediarenamer.web.WebRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Decision logic of the update check (no UI, no network). */
class UpdateCheckTest {

    private static Release release(String version) {
        return new Release(version, "", URI.create("https://github.com/AeLui/MediaRenamer/releases/tag/v" + version), List.of());
    }

    @Test
    void automaticDialogWaitsWhileBusy() {
        List<String> calls = new ArrayList<>();
        assertFalse(UpdateCheck.showIfIdle(true, () -> calls.add("show")));
        assertEquals(List.of(), calls);
        assertTrue(UpdateCheck.showIfIdle(false, () -> calls.add("show")));
        assertEquals(List.of("show"), calls);
    }

    @Test
    void closedOwnerFallsBackToTheMainWindow() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame();
            javax.swing.JDialog about = new javax.swing.JDialog(frame);
            about.pack();
            assertTrue(about.isDisplayable());
            assertEquals(about, UpdateCheck.shown(about));
            about.dispose();
            assertEquals(frame, UpdateCheck.shown(about));
            frame.dispose();
        });
    }

    @Test
    void autoCheckFailureStillRecordsAttempt() {
        Settings.get().put(Settings.UPDATES_LAST_CHECK, null);
        List<String> calls = new ArrayList<>();
        UpdateCheck.check(() -> {
            throw new WebRequest.HttpException(403, "rate limited");
        }, "2.0.0", null, false, r -> calls.add("show"), () -> calls.add("upToDate"), e -> calls.add("failed")).run();
        assertEquals(List.of(), calls);
        assertTrue(Long.parseLong(Settings.get().get(Settings.UPDATES_LAST_CHECK, "0")) > 0);
    }

    @Test
    void skippedVersionNotShownAutomatically() {
        List<String> calls = new ArrayList<>();
        UpdateCheck.check(() -> release("2.5.0"), "2.0.0", "2.5.0", false, r -> calls.add("show"), () -> calls.add("upToDate"),
                e -> calls.add("failed")).run();
        assertEquals(List.of(), calls);
    }

    @Test
    void skippedVersionShownManually() {
        List<String> calls = new ArrayList<>();
        UpdateCheck.check(() -> release("2.5.0"), "2.0.0", "2.5.0", true, r -> calls.add("show " + r.version()), () -> calls.add("upToDate"),
                e -> calls.add("failed")).run();
        assertEquals(List.of("show 2.5.0"), calls);
    }

    @Test
    void manualReportsUpToDateAndFailure() {
        List<String> calls = new ArrayList<>();
        UpdateCheck.check(() -> release("2.0.0"), "2.0.0", null, true, r -> calls.add("show"), () -> calls.add("upToDate"),
                e -> calls.add("failed")).run();
        UpdateCheck.check(() -> {
            throw new java.io.IOException("offline");
        }, "2.0.0", null, true, r -> calls.add("show"), () -> calls.add("upToDate"), e -> calls.add("failed " + e.getMessage())).run();
        assertEquals(List.of("upToDate", "failed offline"), calls);
    }

    @Test
    void newerReleaseIsShownAutomatically() {
        List<String> calls = new ArrayList<>();
        UpdateCheck.check(() -> release("2.5.0"), "2.0.0", null, false, r -> calls.add("show"), () -> calls.add("upToDate"),
                e -> calls.add("failed")).run();
        assertEquals(List.of("show"), calls);
    }
}
