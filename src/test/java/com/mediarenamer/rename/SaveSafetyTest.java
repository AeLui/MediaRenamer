package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mediarenamer.util.FileUtilities;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Atomic saves of the history and the presets, and unique preset ids. */
class SaveSafetyTest {

    @Test
    void atomicWriteReplacesWithoutLeftovers(@TempDir Path dir) throws IOException {
        Path f = dir.resolve("sub").resolve("a.json");
        FileUtilities.writeAtomically(f, "one");
        FileUtilities.writeAtomically(f, "two ñ");
        assertEquals("two ñ", Files.readString(f));
        assertFalse(Files.exists(dir.resolve("sub").resolve("a.json.tmp")));
    }

    @Test
    void historyRoundTripWithSpecialCharacters(@TempDir Path dir) {
        Path file = dir.resolve("history.xml");
        History h = new History(file);
        Map<File, File> renames = new LinkedHashMap<>();
        renames.put(new File(dir.toFile(), "Tom & Jerry \"<1>\".mkv"), new File(dir.toFile(), "Tom & Jerry (1940).mkv"));
        h.add(renames);
        History again = new History(file);
        again.load();
        History.Element e = again.getSequences().get(0).elements().get(0);
        assertEquals("Tom & Jerry \"<1>\".mkv", e.from());
        assertEquals("Tom & Jerry (1940).mkv", e.to());
        assertFalse(Files.exists(dir.resolve("history.xml.tmp")));
    }

    @Test
    void presetsCreatedInTheSameSecondDoNotReplaceEachOther(@TempDir Path dir) {
        Presets store = new Presets(dir.resolve("presets.json"));
        Preset a = new Preset(null, "A", "{n}", "TheMovieDB", null, null, null, null);
        Preset b = new Preset(null, "B", "{n}", "TheMovieDB", null, null, null, null);
        assertNotEquals(a.id(), b.id());
        store.put(a);
        store.put(b);
        assertEquals(2, new Presets(dir.resolve("presets.json")).list().size());
    }
}
