package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mediarenamer.app.Settings;
import com.mediarenamer.web.Movie;

import java.io.File;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class RenameEngineTest {

    private static String resolved(String source, String formatted) {
        return RenameEngine.resolve(new File("C:/in/" + source), formatted, null).getName();
    }

    @Test
    void addsOriginalExtensionByDefault() {
        assertEquals("Avatar (2009).mkv", resolved("avatar.2009.mkv", "Avatar (2009)"));
        // a dot in the title is not an extension
        assertEquals("Mr. Robot.mkv", resolved("mr.robot.mkv", "Mr. Robot"));
    }

    @Test
    void formatCanChangeTheExtension() {
        assertEquals("Avatar (2009).mp4", resolved("avatar.2009.mkv", "Avatar (2009).mp4"));
        assertEquals("Avatar (2009).mkv", resolved("avatar.2009.mkv", "Avatar (2009).mkv"));
    }

    @Test
    void subtitleKeepsLanguage() {
        assertEquals("Avatar (2009).spa.srt", resolved("avatar.2009.spa.srt", "Avatar (2009)"));
    }

    /** Format "{n.colon(' - ')} ({y}){subt}": {subt} already provides ".spa", it is not duplicated. */
    @Test
    void subtitleLanguageFromFormatIsNotDuplicated() {
        assertEquals("Avatar (2009).spa.srt", resolved("avatar.2009.spa.srt", "Avatar (2009).spa"));
        assertEquals("Avatar (2009).spa.srt", resolved("avatar.2009.spa.srt", "Avatar (2009).spa.srt"));
    }

    @Test
    void baseFolderPlusFormatWithSubfolders() {
        File f = RenameEngine.resolve(new File("C:/in/dark.s01e01.mkv"), "Dark (2017)/Dark - 1x01 - Secretos", new File("Z:/Series"));
        assertEquals(new File("Z:/Series/Dark (2017)/Dark - 1x01 - Secretos.mkv").getAbsolutePath(), f.getAbsolutePath());
    }

    @Test
    void withoutBaseFolderUsesTheFileFolder() {
        File f = RenameEngine.resolve(new File("C:/in/a.mkv"), "Sub/Avatar (2009)", null);
        assertEquals(new File("C:/in/Sub/Avatar (2009).mkv").getAbsolutePath(), f.getAbsolutePath());
    }

    @Test
    void absoluteFormatIgnoresBaseFolder() {
        File f = RenameEngine.resolve(new File("C:/in/a.mkv"), "Z:/Movies/Avatar (2009)", new File("D:/Other"));
        assertEquals(new File("Z:/Movies/Avatar (2009).mkv").getAbsolutePath(), f.getAbsolutePath());
    }

    @Test
    void presetFolderHasPriorityOverPreferences() {
        System.setProperty("mediarenamer." + Settings.DEST_MOVIES, "D:/Preferences/Movies");
        try {
            Movie m = new Movie("Avatar", List.of(), 2009, 0, 19995, Locale.US);
            assertEquals(new File("D:/Preferences/Movies"), Destinations.resolve(m, null));
            assertEquals(new File("D:/Preferences/Movies"), Destinations.resolve(m, "  "));
            assertEquals(new File("E:/Preset"), Destinations.resolve(m, "E:/Preset"));
        } finally {
            System.clearProperty("mediarenamer." + Settings.DEST_MOVIES);
        }
    }
}
