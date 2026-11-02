package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dragging a row in "Original files" moves the file and leaves the matches in place; "New names" does the reverse. */
class RenamePanelReorderTest {

    @TempDir
    Path dir;

    @Test
    void movingAFileKeepsTheMatchesInPlace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<File> files = files("a", "b", "c", "d");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                RenamePanel panel = panel(f, files);
                // a down to the end (insertion index 4)
                panel.moveFile(0, 4);
                assertEquals(List.of("b", "c", "d", "a"), names(panel));
                assertEquals(Arrays.asList("A", "B", "C", "D"), matches(panel));
                // back up to the top
                panel.moveFile(3, 0);
                assertEquals(List.of("a", "b", "c", "d"), names(panel));
                assertEquals(Arrays.asList("A", "B", "C", "D"), matches(panel));
                // c above b: only those two rows change
                panel.moveFile(2, 1);
                assertEquals(List.of("a", "c", "b", "d"), names(panel));
                assertEquals(Arrays.asList("A", "B", "C", "D"), matches(panel));
                assertEquals(1, lists(panel).get(0).getSelectedIndex(), "the moved file stays selected");
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void movingAMatchKeepsTheFilesInPlace() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<File> files = files("a", "b", "c");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                RenamePanel panel = panel(f, files);
                panel.moveMatch(0, 3);
                assertEquals(List.of("a", "b", "c"), names(panel));
                assertEquals(Arrays.asList("B", "C", "A"), matches(panel));
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void outOfRangeOrNoOpMovesChangeNothing() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<File> files = files("a", "b");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                RenamePanel panel = panel(f, files);
                for (int[] m : new int[][] { { -1, 0 }, { 2, 0 }, { 0, 3 }, { 0, -1 }, { 0, 0 }, { 0, 1 }, { 1, 2 } }) {
                    panel.moveFile(m[0], m[1]);
                    panel.moveMatch(m[0], m[1]);
                }
                assertEquals(List.of("a", "b"), names(panel));
                assertEquals(Arrays.asList("A", "B"), matches(panel));
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void movingAnUnmatchedFileCarriesNoMatch() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        List<File> files = files("a", "b", "c");
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                RenamePanel panel = panel(f, files);
                panel.rowList().get(2).match = null;
                panel.moveFile(0, 3);
                assertEquals(List.of("b", "c", "a"), names(panel));
                assertEquals(Arrays.asList("A", "B", null), matches(panel));
                assertNull(panel.rowList().get(2).target);
            } finally {
                f.dispose();
            }
        });
    }

    @Test
    void bothListsAreDragSourcesAndRejectPastedText() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                RenamePanel panel = new RenamePanel(f);
                for (JList<?> l : lists(panel)) {
                    assertEquals(true, l.getDragEnabled());
                    TransferHandler h = l.getTransferHandler();
                    assertEquals(TransferHandler.MOVE, h.getSourceActions(l));
                    // text is neither a file list nor an internal row
                    assertFalse(h.canImport(new TransferHandler.TransferSupport(l, new StringSelection("some text"))));
                }
            } finally {
                f.dispose();
            }
        });
    }

    private List<File> files(String... names) throws Exception {
        List<File> out = new ArrayList<>();
        for (String n : names) {
            out.add(Files.writeString(dir.resolve(n + ".mkv"), "x").toFile());
        }
        return out;
    }

    /** Panel with the files and the match "A", "B"… on each row (plain strings stand in for episodes). */
    private static RenamePanel panel(JFrame f, List<File> files) {
        RenamePanel panel = new RenamePanel(f);
        panel.addFiles(files);
        List<RenamePanel.Row> rows = panel.rowList();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).match = String.valueOf((char) ('A' + i));
        }
        return panel;
    }

    private static List<String> names(RenamePanel panel) {
        return panel.files().stream().map(x -> x.getName().replace(".mkv", "")).toList();
    }

    private static List<Object> matches(RenamePanel panel) {
        List<Object> out = new ArrayList<>();
        panel.rowList().forEach(r -> out.add(r.match));
        return out;
    }

    private static List<JList<?>> lists(Container c) {
        List<JList<?>> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Container c, List<JList<?>> out) {
        for (Component child : c.getComponents()) {
            if (child instanceof JList<?> l) {
                out.add(l);
            } else if (child instanceof Container ct) {
                collect(ct, out);
            }
        }
    }
}
