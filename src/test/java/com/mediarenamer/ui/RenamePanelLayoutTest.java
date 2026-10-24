package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.mediarenamer.app.I18n;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The "Original files" and "New names" cards are equally wide and the window is symmetric, in every language. */
class RenamePanelLayoutTest {

    @AfterEach
    void restore() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void bothCardsHaveTheSameWidth() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        for (Locale l : I18n.SUPPORTED) {
            for (int width : new int[] { 1000, 1280, 1920 }) {
                List<Component> cards = new ArrayList<>();
                int[] panelWidth = new int[1];
                SwingUtilities.invokeAndWait(() -> {
                    I18n.setLocale(l);
                    JFrame f = new JFrame();
                    RenamePanel panel = new RenamePanel(f);
                    f.setContentPane(panel);
                    // displayable (native peer) but not shown: otherwise validate() does not lay anything out
                    f.addNotify();
                    f.setSize(width, 780);
                    f.validate();
                    collect(panel, cards);
                    panelWidth[0] = panel.getWidth();
                    f.dispose();
                });
                assertEquals(2, cards.size(), "cards");
                assertTrue(cards.get(0).getWidth() > 0, "laid out");
                int left = cards.get(0).getWidth();
                int right = cards.get(1).getWidth();
                assertTrue(Math.abs(left - right) <= 1, l + " @" + width + ": " + left + " vs " + right);
                // symmetric: the gap on the left of the first card equals the gap on the right of the second one
                int leftGap = SwingUtilities.convertPoint(cards.get(0), 0, 0, cards.get(0).getParent().getParent()).x;
                int rightGap = panelWidth[0] - SwingUtilities.convertPoint(cards.get(1), cards.get(1).getWidth(), 0, cards.get(1).getParent().getParent()).x;
                assertTrue(Math.abs(leftGap - rightGap) <= 1, l + " @" + width + ": gaps " + leftGap + " vs " + rightGap);
            }
        }
    }

    private static void collect(Container c, List<Component> cards) {
        for (Component child : c.getComponents()) {
            if (child instanceof RenamePanel.RoundedPanel && child.getParent().getLayout() instanceof java.awt.GridBagLayout) {
                cards.add(child);
            } else if (child instanceof Container ct) {
                collect(ct, cards);
            }
        }
    }
}
