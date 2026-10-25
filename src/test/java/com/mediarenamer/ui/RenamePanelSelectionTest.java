package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Adding files selects the first row in both lists when nothing was selected, and keeps an existing selection. */
class RenamePanelSelectionTest {

    @TempDir
    Path dir;

    @Test
    void firstRowIsSelectedAfterAddingFiles() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        File a = Files.writeString(dir.resolve("a.mkv"), "x").toFile();
        File b = Files.writeString(dir.resolve("b.mkv"), "x").toFile();
        File c = Files.writeString(dir.resolve("c.mkv"), "x").toFile();
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame();
            try {
                RenamePanel panel = new RenamePanel(f);
                List<JList<?>> lists = new ArrayList<>();
                collect(panel, lists);
                assertEquals(2, lists.size(), "Original files and New names");
                panel.addFiles(List.of(a, b));
                for (JList<?> l : lists) {
                    assertEquals(0, l.getSelectedIndex(), "first row selected in both lists");
                }
                // a selection made by the user is kept when more files are added
                lists.get(0).setSelectedIndex(1);
                panel.addFiles(List.of(c));
                for (JList<?> l : lists) {
                    assertArrayEquals(new int[] { 1 }, l.getSelectedIndices());
                }
            } finally {
                f.dispose();
            }
        });
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
