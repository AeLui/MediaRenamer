package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;
import com.mediarenamer.app.Updates.Release;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.util.List;
import java.util.Locale;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

/** The update dialog: buttons, notes as plain text and "skip this version" (no network, no browser). */
class UpdateDialogTest {

    private static Release release(String notes) {
        return new Release("2.8.0", notes, URI.create("https://github.com/AeLui/MediaRenamer/releases/tag/v2.8.0"), List.of());
    }

    private static JTextArea textArea(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JTextArea t) {
                return t;
            }
            if (child instanceof Container inner) {
                JTextArea found = textArea(inner);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void skipHiddenInManualMode() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            UpdateDialog automatic = new UpdateDialog(null, release("x"), false);
            UpdateDialog manual = new UpdateDialog(null, release("x"), true);
            assertTrue(automatic.skip.isVisible());
            assertFalse(manual.skip.isVisible());
            assertTrue(manual.update.isVisible());
            assertTrue(manual.later.isVisible());
            automatic.dispose();
            manual.dispose();
        });
    }

    @Test
    void skipStoresVersion() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        Settings.get().put(Settings.UPDATES_SKIP_VERSION, null);
        SwingUtilities.invokeAndWait(() -> {
            UpdateDialog d = new UpdateDialog(null, release("x"), false);
            d.skip.doClick();
            assertEquals("2.8.0", Settings.get().get(Settings.UPDATES_SKIP_VERSION));
            assertFalse(d.isDisplayable());
        });
        Settings.get().put(Settings.UPDATES_SKIP_VERSION, null);
    }

    @Test
    void notesAreNotHtml() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            UpdateDialog d = new UpdateDialog(null, release("<html><b>x</b>"), false);
            JTextArea notes = textArea(d.getContentPane());
            assertEquals("<html><b>x</b>", notes.getText());
            assertFalse(notes.isEditable());
            d.dispose();
        });
    }

    @Test
    void failureTextFallsBackToTheExceptionType() {
        assertEquals("java.io.IOException", UpdateCheck.describe(new java.io.IOException()));
        assertEquals("offline", UpdateCheck.describe(new java.io.IOException("offline")));
    }

    @Test
    void busyFollowsTheMainWindowOfTheOwnerChain() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            boolean[] running = {true};
            class Frame extends javax.swing.JFrame implements UpdateDialog.Busy {
                @Override
                public boolean isBusy() {
                    return running[0];
                }
            }
            Frame frame = new Frame();
            javax.swing.JDialog about = new javax.swing.JDialog(frame);
            assertTrue(UpdateDialog.busy(about));
            assertTrue(UpdateDialog.busy(frame));
            running[0] = false;
            assertFalse(UpdateDialog.busy(about));
            assertFalse(UpdateDialog.busy(null));
            assertFalse(UpdateDialog.busy(new javax.swing.JFrame()));
            about.dispose();
            frame.dispose();
        });
    }

    @Test
    void mainWindowIsTheFrameAtTheEndOfTheOwnerChain() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JFrame frame = new javax.swing.JFrame();
            javax.swing.JDialog about = new javax.swing.JDialog(frame);
            UpdateDialog d = new UpdateDialog(about, release("x"), true);
            assertEquals(frame, UpdateDialog.mainWindow(d.getOwner()));
            assertEquals(frame, UpdateDialog.mainWindow(frame));
            d.dispose();
            about.dispose();
            frame.dispose();
        });
    }
}
