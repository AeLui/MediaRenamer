package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mediarenamer.app.Settings;
import com.mediarenamer.media.MediaInfo;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The fpcalc path of Preferences may be the program or the folder that holds it; the automatic search ignores it. */
class FpcalcLookupTest {

    @TempDir
    Path dir;

    @Test
    void folderOrFileIsAcceptedAndDetectionIgnoresThePreference() throws Exception {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "fpcalc.exe" : "fpcalc";
        Path fpcalc = Files.writeString(dir.resolve(exe), "x");
        try {
            Settings.get().put(Settings.FPCALC_PATH, dir.toString());
            assertEquals(fpcalc, AcoustIDClient.findFpcalc());
            Settings.get().put(Settings.FPCALC_PATH, fpcalc.toString());
            assertEquals(fpcalc, AcoustIDClient.findFpcalc());
            // the placeholder of Preferences shows what would be found without the preference
            assertNotEquals(fpcalc, AcoustIDClient.detectedFpcalc());
            Settings.get().put(Settings.MEDIAINFO_PATH, dir.toString());
            Path detected = MediaInfo.detectedPath();
            assertEquals(true, detected == null || !detected.startsWith(dir), "MediaInfo detection ignores the preference: " + detected);
        } finally {
            Settings.get().put(Settings.FPCALC_PATH, null);
            Settings.get().put(Settings.MEDIAINFO_PATH, null);
        }
    }
}
