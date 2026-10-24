package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.web.SortOrder;
import com.mediarenamer.web.WebServices;

import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetsTest {

    private static final Preset MOVIES = new Preset("1739994159", "Películas para Plex", "{n.colon(' - ')} ({y}){subt}", "TheMovieDB", null,
            "Opportunistic", "es", "MOVE", "Z:\\Películas");
    private static final Preset SERIES = new Preset("1739994260", "Series para Plex", "{ny}/{n} - {sxe} - {t}", "TheMovieDB::TV", "Airdate",
            "Opportunistic", "es", "MOVE");

    @Test
    void presetProperties() {
        assertEquals(WebServices.TheMovieDB, MOVIES.getDatasource());
        assertEquals(Mode.Movie, MOVIES.getMode());
        assertEquals(Boolean.FALSE, MOVIES.isStrict());
        assertEquals(Locale.forLanguageTag("es"), MOVIES.getLocale());
        assertEquals(RenameAction.MOVE, MOVIES.getAction());
        assertEquals(WebServices.TheMovieDB_TV, SERIES.getDatasource());
        assertEquals(Mode.Episode, SERIES.getMode());
        assertEquals(SortOrder.Airdate, SERIES.getSortOrder());
        assertNull(SERIES.outputFolder());
    }

    @Test
    void storeRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("presets.json");
        Presets store = new Presets(file);
        store.put(MOVIES);
        store.put(SERIES);
        Presets reloaded = new Presets(file);
        assertEquals(2, reloaded.list().size());
        assertEquals("{ny}/{n} - {sxe} - {t}", reloaded.find("series para plex").orElseThrow().format());
        assertEquals("Z:\\Películas", reloaded.find("Películas para Plex").orElseThrow().outputFolder());
        reloaded.remove(reloaded.find("1739994159").orElseThrow());
        assertFalse(new Presets(file).find("Películas para Plex").isPresent());
        assertTrue(new Presets(file).find("Series para Plex").isPresent());
    }

    @Test
    void jellyfinScanIsOptional(@TempDir Path dir) {
        Path file = dir.resolve("presets.json");
        Presets store = new Presets(file);
        store.put(new Preset("1", "With scan", "{n}", "TheMovieDB", null, null, null, null, null, true));
        store.put(new Preset("2", "Without scan", "{n}", "TheMovieDB", null, null, null, null, null, false));
        store.put(SERIES);
        Presets reloaded = new Presets(file);
        assertEquals(Boolean.TRUE, reloaded.find("With scan").orElseThrow().jellyfinScan());
        assertEquals(Boolean.FALSE, reloaded.find("Without scan").orElseThrow().jellyfinScan());
        assertNull(reloaded.find("Series para Plex").orElseThrow().jellyfinScan(), "no field = general preference");
        assertFalse(SERIES.toJson().containsKey("jellyfinScan"));
    }
}
