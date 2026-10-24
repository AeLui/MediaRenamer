package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.mediarenamer.app.I18n;
import com.mediarenamer.app.Settings;

import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** First-run setup wizard: steps, live language change and what is saved. */
class SetupWizardTest {

    @AfterEach
    void restore() {
        Settings.get().put(Settings.UI_LANGUAGE, null);
        I18n.setLocale(Locale.ENGLISH);
    }

    @Test
    void everyServiceHasLabelAndInfoText() {
        ResourceBundle base = ResourceBundle.getBundle("com.mediarenamer.i18n.messages", Locale.ROOT);
        Set<String> keys = new HashSet<>();
        for (ApiService api : ApiService.ALL) {
            assertTrue(base.containsKey(api.labelKey()), api.labelKey());
            assertTrue(base.containsKey(api.infoKey()), api.infoKey());
            keys.add(api.settingsKey());
        }
        // every API key of the preferences is offered in the wizard
        assertEquals(Set.of(Settings.TMDB_KEY, Settings.TVDB_KEY, Settings.TVDB_PIN, Settings.OMDB_KEY, Settings.ACOUSTID_KEY,
                Settings.ANIDB_CLIENT, Settings.ANIDB_CLIENT_VERSION), keys);
    }

    @Test
    void languageFirstThenServicesThenMediaServers() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        AtomicReference<String> germanTitle = new AtomicReference<>();
        AtomicReference<Set<String>> servicesFields = new AtomicReference<>();
        AtomicReference<Set<String>> serverFields = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            SetupWizard w = new SetupWizard();
            assertEquals(0, w.getStep());
            // choosing a language rebuilds the wizard in that language at once
            w.setLanguage(Locale.GERMAN);
            germanTitle.set(w.getTitle());
            w.showStep(1);
            servicesFields.set(new HashSet<>(w.fieldKeys()));
            w.showStep(2);
            serverFields.set(new HashSet<>(w.fieldKeys()));
            // skipping saves the language, so that the wizard is not shown again
            w.skip();
        });
        assertEquals("Willkommen bei MediaRenamer", germanTitle.get());
        assertTrue(servicesFields.get().contains(Settings.TMDB_KEY));
        assertFalse(servicesFields.get().contains(Settings.JELLYFIN_URL));
        assertEquals(Set.of(Settings.JELLYFIN_URL, Settings.JELLYFIN_KEY, Settings.PLEX_URL, Settings.PLEX_TOKEN), serverFields.get());
        assertEquals("de", Settings.get().get(Settings.UI_LANGUAGE));
    }
}
