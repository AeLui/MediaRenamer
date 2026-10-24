package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** API keys encrypted in settings.properties and migration of legacy plain-text files. */
class SettingsSecretsTest {

    private static final String KEY = "abc123-test-key";

    @Test
    void roundTrip(@TempDir Path dir) {
        String enc = Secrets.protect(KEY, dir);
        assertTrue(Secrets.isProtected(enc), enc);
        assertFalse(enc.contains(KEY));
        assertEquals(KEY, Secrets.unprotect(enc, dir));
        assertEquals("no-prefix", Secrets.unprotect("no-prefix", dir));
    }

    @Test
    void plainKeysAreMigratedWithoutLosingAnything(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, """
                apikey.acoustid=%s
                jellyfin.apikey=jf-%s
                jellyfin.url=http\\://nas\\:8096
                dest.movies=Z\\:\\\\Pel\\u00edculas
                ui.dark=true
                """.formatted(KEY, KEY), StandardCharsets.ISO_8859_1);

        Settings s = new Settings(file);
        assertEquals(KEY, s.get(Settings.ACOUSTID_KEY));
        assertEquals("jf-" + KEY, s.get(Settings.JELLYFIN_KEY));
        assertEquals("http://nas:8096", s.get(Settings.JELLYFIN_URL));
        assertEquals("Z:\\Películas", s.get(Settings.DEST_MOVIES));
        assertTrue(s.getBoolean("ui.dark", false));

        String saved = Files.readString(file, StandardCharsets.ISO_8859_1);
        assertFalse(saved.contains(KEY), "the key is still in plain text:\n" + saved);
        assertTrue(saved.contains("http\\://nas\\:8096"), "normal preferences stay readable");

        // on reload they are read the same and the file is not touched again
        Settings again = new Settings(file);
        assertEquals(KEY, again.get(Settings.ACOUSTID_KEY));
        assertEquals(saved, Files.readString(file, StandardCharsets.ISO_8859_1));
    }

    @Test
    void putEncryptsAndBlankRemoves(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.properties");
        Settings s = new Settings(file);
        s.put(Settings.JELLYFIN_KEY, " " + KEY + " ");
        assertEquals(KEY, s.get(Settings.JELLYFIN_KEY));
        assertFalse(Files.readString(file).contains(KEY));
        String before = Files.readString(file);
        s.put(Settings.JELLYFIN_KEY, KEY);
        assertEquals(before, Files.readString(file), "saving the same value does not encrypt again");
        s.put(Settings.JELLYFIN_KEY, "");
        assertNull(new Settings(file).get(Settings.JELLYFIN_KEY));
    }

    @Test
    void undecryptableValueIsIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "apikey.omdb=" + Secrets.DPAPI + "AAAA\n");
        assertNull(new Settings(file).get(Settings.OMDB_KEY));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void fileRenamerKeysAreReencrypted(@TempDir Path dir) throws IOException {
        // DPAPI value written by FileRenamer 1.x (other entropy)
        String legacy = Secrets.DPAPI + Base64.getEncoder().encodeToString(
                Secrets.dpapi(KEY.getBytes(StandardCharsets.UTF_8), true, Secrets.LEGACY_ENTROPY));
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "apikey.tmdb=" + legacy + "\n");

        Settings s = new Settings(file);
        assertEquals(KEY, s.get(Settings.TMDB_KEY));
        java.util.Properties saved = new java.util.Properties();
        try (var r = Files.newBufferedReader(file)) {
            saved.load(r);
        }
        String stored = saved.getProperty(Settings.TMDB_KEY);
        assertTrue(Secrets.isProtected(stored), stored);
        assertFalse(legacy.equals(stored), "the legacy value has been replaced");
        assertNull(Secrets.reprotect(stored, dir), "already current");

        String again = Files.readString(file);
        assertEquals(KEY, new Settings(file).get(Settings.TMDB_KEY));
        assertEquals(again, Files.readString(file), "a current file is not touched again");
    }
}
