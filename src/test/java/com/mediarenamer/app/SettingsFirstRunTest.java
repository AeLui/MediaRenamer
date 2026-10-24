package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** First run = there was no preferences file when the application started (the GUI then shows the setup wizard). */
class SettingsFirstRunTest {

    @Test
    void firstRunOnlyWithoutPreferencesFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("settings.properties");
        Settings fresh = new Settings(file);
        assertTrue(fresh.isFirstRun());
        fresh.put(Settings.UI_LANGUAGE, "es");
        // saving does not change the flag during the session, but the next start is no longer a first run
        assertTrue(fresh.isFirstRun());
        assertTrue(Files.exists(file));
        assertFalse(new Settings(file).isFirstRun());
    }
}
