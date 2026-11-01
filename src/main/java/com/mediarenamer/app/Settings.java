package com.mediarenamer.app;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Persistent application preferences.
 *
 * Stored in {@code %APPDATA%/MediaRenamer/settings.properties}. Any key can be
 * overridden with an environment variable: {@code apikey.tmdb} -> {@code MEDIARENAMER_APIKEY_TMDB}.
 *
 * No API key is embedded in the application: each user configures their own (see docs/CONFIGURATION.md).
 *
 * The keys in {@link #SECRET_KEYS} are stored encrypted ({@link Secrets}: DPAPI on Windows). A legacy
 * plain-text value is encrypted automatically when the file is loaded.
 */
public final class Settings {

    public static final String APP_NAME = "MediaRenamer";

    // API keys per service
    public static final String TMDB_KEY = "apikey.tmdb";
    public static final String TVDB_KEY = "apikey.tvdb";
    public static final String TVDB_PIN = "apikey.tvdb.pin";
    public static final String ACOUSTID_KEY = "apikey.acoustid";
    public static final String ANIDB_CLIENT = "apikey.anidb.client";
    public static final String ANIDB_CLIENT_VERSION = "apikey.anidb.clientver";

    /** Interface language ("es", "en"...; empty = the system's). See {@link I18n}. */
    public static final String UI_LANGUAGE = "ui.language";

    // Rename tab preferences
    public static final String LANGUAGE = "rename.language";
    public static final String EPISODE_ORDER = "rename.episode.order";
    public static final String MATCH_MODE = "rename.match.mode";
    public static final String RENAME_ACTION = "rename.action";
    public static final String CONFLICT = "rename.conflict";
    public static final String FORMAT_EPISODE = "rename.format.episode";
    public static final String FORMAT_MOVIE = "rename.format.movie";
    public static final String FORMAT_MUSIC = "rename.format.music";
    public static final String FORMAT_FILE = "rename.format.file";
    public static final String RECENT_FORMATS = "rename.format.recent";
    public static final String DB_EPISODE = "rename.db.episode";
    public static final String DB_MOVIE = "rename.db.movie";
    public static final String DB_MUSIC = "rename.db.music";

    // Default target folders (target = folder/format); empty = folder of the original file
    public static final String DEST_MOVIES = "dest.movies";
    public static final String DEST_SERIES = "dest.series";
    public static final String DEST_ANIME = "dest.anime";
    public static final String DEST_MUSIC = "dest.music";
    public static final String DEST_OTHER = "dest.other";

    // Jellyfin: library scan after renaming
    public static final String JELLYFIN_URL = "jellyfin.url";
    public static final String JELLYFIN_KEY = "jellyfin.apikey";
    public static final String JELLYFIN_SCAN = "jellyfin.scan";

    // Plex: library scan after renaming; the token comes from the plex.tv sign-in (or is pasted)
    public static final String PLEX_URL = "plex.url";
    public static final String PLEX_TOKEN = "plex.token";
    public static final String PLEX_SCAN = "plex.scan";
    /** Random id of this installation for plex.tv ("X-Plex-Client-Identifier"); not a secret. */
    public static final String PLEX_CLIENT_ID = "plex.clientid";

    /** Keys stored encrypted in the file (environment variables and -D stay in plain text). */
    public static final java.util.Set<String> SECRET_KEYS = java.util.Set.of(TMDB_KEY, TVDB_KEY, TVDB_PIN, ACOUSTID_KEY, JELLYFIN_KEY,
            PLEX_TOKEN);

    /** Keys of removed features (the OMDb API key, source removed in 2.5): deleted so no stale secret stays on disk. */
    static final java.util.Set<String> OBSOLETE_KEYS = java.util.Set.of("apikey.omdb");

    // Update check against the GitHub releases
    /** "false" disables the automatic check at startup (default: enabled). */
    public static final String UPDATES_AUTO_CHECK = "updates.autoCheck";
    /** Epoch milliseconds of the last automatic check. */
    public static final String UPDATES_LAST_CHECK = "updates.lastCheck";
    /** Version the user chose to skip ("Skip this version"); a manual check still offers it. */
    public static final String UPDATES_SKIP_VERSION = "updates.skipVersion";

    // External tools
    public static final String MEDIAINFO_PATH = "tool.mediainfo";
    public static final String FPCALC_PATH = "tool.fpcalc";

    /** Application version (the pom.xml one, via the jar manifest); "dev" when running from the classes. */
    public static String version() {
        String v = Settings.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }

    private static final Settings INSTANCE = new Settings();

    public static Settings get() {
        return INSTANCE;
    }

    private final Properties props = new Properties();
    private final Path file;
    /** The preferences file did not exist when the application started (first run: setup wizard). */
    private final boolean firstRun;

    private Settings() {
        this(AppFolders.data().resolve("settings.properties"));
    }

    /** Preferences of a specific file (tests and migration). */
    Settings(Path file) {
        this.file = file;
        this.firstRun = !Files.exists(file);
        if (!firstRun) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
            } catch (IOException e) {
                Logging.warn(I18n.tr("log.readFailed", file, e));
            }
        }
        removeObsoleteKeys();
        migrateSecrets();
    }

    private void removeObsoleteKeys() {
        if (props.keySet().removeIf(OBSOLETE_KEYS::contains)) {
            save();
        }
    }

    /**
     * Encrypts the keys still in plain text (files from versions before 0.7) and encrypts again the DPAPI keys of
     * FileRenamer (1.x, another entropy: see {@link Secrets#reprotect(String, Path)}).
     */
    private void migrateSecrets() {
        int migrated = 0;
        int reencrypted = 0;
        for (String key : SECRET_KEYS) {
            String v = props.getProperty(key);
            if (v == null || v.isBlank()) {
                continue;
            }
            if (!Secrets.isProtected(v)) {
                props.setProperty(key, Secrets.protect(v.trim(), file.getParent()));
                migrated++;
            } else {
                String current = Secrets.reprotect(v, file.getParent());
                if (current != null) {
                    props.setProperty(key, current);
                    reencrypted++;
                }
            }
        }
        if (migrated + reencrypted > 0) {
            save();
        }
        if (migrated > 0) {
            Logging.info(I18n.tr("log.keysEncrypted", migrated, file.getFileName()));
        }
        if (reencrypted > 0) {
            Logging.info(I18n.tr("log.keysReencrypted", reencrypted, file.getFileName()));
        }
    }

    /**
     * true if there was no preferences file when the application started: the GUI shows the setup wizard.
     * It stays true for the whole session even after the wizard saves the preferences.
     */
    public boolean isFirstRun() {
        return firstRun;
    }

    public synchronized String get(String key) {
        String env = System.getenv(envName(key));
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String sys = System.getProperty("mediarenamer." + key);
        if (sys != null && !sys.isBlank()) {
            return sys.trim();
        }
        String v = props.getProperty(key);
        if (v == null || v.isBlank()) {
            return null;
        }
        if (SECRET_KEYS.contains(key)) {
            try {
                return Secrets.unprotect(v, file.getParent());
            } catch (IllegalStateException e) {
                Logging.warn(key + ": " + e.getMessage());
                return null;
            }
        }
        return v;
    }

    public String get(String key, String defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : v;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = get(key);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public synchronized void put(String key, String value) {
        if (value == null || value.isBlank()) {
            props.remove(key);
        } else if (!SECRET_KEYS.contains(key)) {
            props.setProperty(key, value);
        } else if (!value.trim().equals(storedSecret(key))) {
            props.setProperty(key, Secrets.protect(value.trim(), file.getParent()));
        }
        save();
    }

    /** Decrypted value from the file (without environment variables); null if missing or undecryptable. */
    private String storedSecret(String key) {
        try {
            String v = props.getProperty(key);
            return v == null ? null : Secrets.unprotect(v, file.getParent());
        } catch (IllegalStateException e) {
            return null;
        }
    }

    public Locale getLocale() {
        String tag = get(LANGUAGE);
        return tag == null ? Locale.getDefault() : Locale.forLanguageTag(tag);
    }

    /** Returns the API key or throws an explanatory exception if it is not configured. */
    public String requireKey(String key, String service) {
        String v = get(key);
        if (v == null) {
            throw new MissingApiKeyException(service, key, envName(key));
        }
        return v;
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            // atomic write: a failure halfway does not leave the preferences file half-written
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                props.store(w, APP_NAME + " settings (API keys are encrypted: do not edit them by hand)");
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Logging.warn(I18n.tr("log.saveFailed", file, e));
        }
    }

    static String envName(String key) {
        return "MEDIARENAMER_" + key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    /** Thrown when a service needs an API key the user has not configured. */
    public static class MissingApiKeyException extends IllegalStateException {
        public MissingApiKeyException(String service, String key, String env) {
            super(I18n.tr("error.missingApiKey", service, key, env));
        }
    }
}
