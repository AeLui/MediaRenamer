package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Copy of the FileRenamer data folder into the MediaRenamer one. */
class AppFoldersMigrationTest {

    @Test
    void copiesEverythingAndKeepsTheOldFolder(@TempDir Path dir) throws IOException {
        Path legacy = dir.resolve("FileRenamer");
        Files.createDirectories(legacy.resolve("cache/tmdb"));
        Files.writeString(legacy.resolve("settings.properties"), "ui.language=es\n");
        Files.writeString(legacy.resolve("presets.json"), "[]");
        Files.writeString(legacy.resolve("cache/tmdb/a.json"), "{}");
        Path target = dir.resolve("MediaRenamer");

        assertTrue(AppFolders.migrateLegacy(legacy, target));
        assertEquals("ui.language=es\n", Files.readString(target.resolve("settings.properties")));
        assertEquals("[]", Files.readString(target.resolve("presets.json")));
        assertEquals("{}", Files.readString(target.resolve("cache/tmdb/a.json")));
        assertTrue(Files.exists(legacy.resolve("settings.properties")), "the old folder stays as a backup");
        assertFalse(Files.exists(dir.resolve("MediaRenamer.migrating")));
    }

    @Test
    void neverOverwritesAnExistingFolder(@TempDir Path dir) throws IOException {
        Path legacy = Files.createDirectories(dir.resolve("FileRenamer"));
        Files.writeString(legacy.resolve("settings.properties"), "old=1\n");
        Path target = Files.createDirectories(dir.resolve("MediaRenamer"));
        Files.writeString(target.resolve("settings.properties"), "new=1\n");

        assertFalse(AppFolders.migrateLegacy(legacy, target));
        assertEquals("new=1\n", Files.readString(target.resolve("settings.properties")));
    }

    @Test
    void nothingToMigrate(@TempDir Path dir) {
        assertFalse(AppFolders.migrateLegacy(dir.resolve("FileRenamer"), dir.resolve("MediaRenamer")));
        assertFalse(Files.exists(dir.resolve("MediaRenamer")), "a first run still starts the setup wizard");
    }

    @Test
    void interruptedCopyIsRepeated(@TempDir Path dir) throws IOException {
        Path legacy = Files.createDirectories(dir.resolve("FileRenamer"));
        Files.writeString(legacy.resolve("history.xml"), "<history/>");
        Path leftover = Files.createDirectories(dir.resolve("MediaRenamer.migrating"));
        Files.writeString(leftover.resolve("partial.tmp"), "x");
        Path target = dir.resolve("MediaRenamer");

        assertTrue(AppFolders.migrateLegacy(legacy, target));
        assertEquals("<history/>", Files.readString(target.resolve("history.xml")));
        assertFalse(Files.exists(target.resolve("partial.tmp")));
    }
}
