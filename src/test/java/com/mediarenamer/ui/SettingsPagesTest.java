package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;
import com.mediarenamer.ui.SettingsPages.Category;

import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

import org.junit.jupiter.api.Test;

/** Preferences categories and the ⓘ help of every option. */
class SettingsPagesTest {

    private static final ResourceBundle BASE = ResourceBundle.getBundle("com.mediarenamer.i18n.messages", Locale.ROOT);

    @Test
    void everyCategoryAndInfoTextExists() throws Exception {
        for (Category c : Category.values()) {
            assertTrue(BASE.containsKey(c.key()), c.key());
            assertTrue(BASE.containsKey(c.key() + ".text"), c.key() + ".text");
        }
        // every "info.*" key referenced by the pages exists in the base language (and so in all six, see I18nTest)
        String source = Files.readString(Path.of("src/main/java/com/mediarenamer/ui/SettingsPages.java"));
        Matcher m = Pattern.compile("\"(info\\.[a-zA-Z.]+)\"").matcher(source);
        List<String> missing = new ArrayList<>();
        int count = 0;
        while (m.find()) {
            count++;
            if (!BASE.containsKey(m.group(1))) {
                missing.add(m.group(1));
            }
        }
        assertTrue(count >= 12, "info keys found: " + count);
        assertEquals(List.of(), missing);
    }

    @Test
    void everyOptionHasAnInfoIcon() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            SettingsPages pages = new SettingsPages(null);
            for (Category c : Category.values()) {
                Container page = (Container) pages.page(c);
                AtomicInteger fields = new AtomicInteger();
                AtomicInteger infos = new AtomicInteger();
                count(page, fields, infos);
                // every text field has its ⓘ (General has a combo and a checkbox, also with ⓘ)
                assertTrue(infos.get() >= Math.max(1, fields.get()), c + ": fields " + fields + ", info icons " + infos);
            }
            // the text fields cover every preference shown by the old single-page dialog
            assertTrue(pages.fields.keySet().containsAll(List.of(Settings.TMDB_KEY, Settings.LANGUAGE, Settings.DEST_MOVIES, Settings.DEST_OTHER,
                    Settings.JELLYFIN_URL, Settings.JELLYFIN_KEY, Settings.MEDIAINFO_PATH, Settings.FPCALC_PATH)), pages.fields.keySet().toString());
        });
    }

    private static void count(Container c, AtomicInteger fields, AtomicInteger infos) {
        for (Component child : c.getComponents()) {
            if (child instanceof JTextComponent) {
                fields.incrementAndGet();
            } else if (child instanceof JLabel l && l.getIcon() != null && (l.getText() == null || l.getText().isEmpty()) && l.getToolTipText() != null) {
                infos.incrementAndGet();
            }
            if (child instanceof Container ct && !(child instanceof JTextComponent) && (child instanceof JPanel || ct.getComponentCount() > 0)) {
                count(ct, fields, infos);
            }
        }
    }
}
