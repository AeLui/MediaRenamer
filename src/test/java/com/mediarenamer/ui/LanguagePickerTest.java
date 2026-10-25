package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.app.I18n;
import com.mediarenamer.ui.LanguagePicker.Item;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The searchable language list of Preferences and Profiles. */
class LanguagePickerTest {

    @AfterEach
    void restore() {
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void everyLanguageIsListedByNameInTheInterfaceLanguage() {
        List<Item> es = LanguagePicker.languages(Locale.forLanguageTag("es"));
        assertTrue(es.size() > 300, "languages: " + es.size());
        assertTrue(es.contains(new Item("es-ES", "Español (España)")), "Spanish (Spain) shown in Spanish");
        assertTrue(es.stream().anyMatch(i -> i.tag().equals("es")), "plain languages too");
        assertTrue(es.stream().anyMatch(i -> i.tag().equals("pt-BR")));
        // only tags the data services take: no scripts, variants, extensions or region numbers (es-419)
        for (Item i : es) {
            assertTrue(i.tag().matches("[a-z]{2}(-[A-Z]{2})?"), i.tag());
        }
        assertEquals(es.size(), new HashSet<>(es.stream().map(Item::label).toList()).size(), "names are unique");
        List<Item> en = LanguagePicker.languages(Locale.ENGLISH);
        assertTrue(en.contains(new Item("de-DE", "German (Germany)")));
    }

    @Test
    void searchIgnoresCaseAndAccentsAndAlsoMatchesTags() {
        List<Item> es = LanguagePicker.languages(Locale.forLanguageTag("es"));
        assertTrue(LanguagePicker.matches(es, "espanol (espana)").contains(new Item("es-ES", "Español (España)")));
        assertTrue(LanguagePicker.matches(es, "ES_mx").stream().anyMatch(i -> i.tag().equals("es-MX")), "by tag, either separator");
        assertTrue(LanguagePicker.matches(es, "").size() == es.size(), "blank = all");
        assertTrue(LanguagePicker.matches(es, "qqqq").isEmpty());
    }

    @Test
    void typedTextResolvesToTheBestEntry() {
        List<Item> en = LanguagePicker.languages(Locale.ENGLISH);
        assertEquals("fr-FR", LanguagePicker.resolve(en, "French (France)").tag());
        assertEquals("it-IT", LanguagePicker.resolve(en, "IT-it").tag());
        assertTrue(LanguagePicker.resolve(en, "germ").label().startsWith("German"), "first name that starts with the text");
        assertNull(LanguagePicker.resolve(en, "qqqq"), "nothing: the caller keeps its value");
        assertNull(LanguagePicker.resolve(en, "  "));
    }

    @Test
    void pickerKeepsTagsAndGoesBackWhenNothingMatches() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            I18n.setLocale(Locale.ENGLISH);
            LanguagePicker p = new LanguagePicker("(don't change)");
            AtomicInteger changes = new AtomicInteger();
            p.onChange(changes::incrementAndGet);
            assertNull(p.getTag(), "starts empty");
            p.setTag("es-ES");
            assertEquals("es-ES", p.getTag());
            assertEquals(0, changes.get(), "setting a value is not a user change");
            JTextField editor = (JTextField) p.getEditor().getEditorComponent();
            assertEquals("Spanish (Spain)", editor.getText());

            // typing filters the list and leaving the field picks the match
            editor.setText("japanese (j");
            p.filter(editor.getText());
            assertTrue(p.getItemCount() >= 1 && p.getItemCount() < 5, "filtered: " + p.getItemCount());
            p.commit();
            assertEquals("ja-JP", p.getTag());
            assertEquals(1, changes.get());
            assertTrue(p.getItemCount() > 300, "whole list again");

            // text that matches nothing goes back to the previous value
            editor.setText("qqqq");
            p.filter("qqqq");
            p.commit();
            assertEquals("ja-JP", p.getTag());
            assertEquals("Japanese (Japan)", editor.getText());

            // an emptied field is the empty value
            editor.setText("");
            p.commit();
            assertNull(p.getTag());
            assertEquals("(don't change)", editor.getText());

            // a hand-written tag that is not in the list is kept as it is
            p.setTag("en-150");
            assertEquals("en-150", p.getTag());
            assertFalse(editor.getText().isBlank());
        });
    }
}
