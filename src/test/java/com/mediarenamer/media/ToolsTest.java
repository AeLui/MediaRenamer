package com.mediarenamer.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.media.Tools.Status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Check of the tool paths chosen in Preferences → Tools (names and existence only; nothing is run). */
class ToolsTest {

    @TempDir
    Path dir;

    @Test
    void emptyMeansAutomatic() {
        assertEquals(Status.AUTO, Tools.check(null, Tools.MEDIAINFO));
        assertEquals(Status.AUTO, Tools.check("  ", Tools.FPCALC));
    }

    @Test
    void theToolItselfOrItsFolderIsAccepted() throws Exception {
        Path dll = Files.writeString(dir.resolve("mediainfo.DLL"), "x");
        assertEquals(Status.OK, Tools.check(dll.toString(), Tools.MEDIAINFO), "file name case is ignored");
        Path tools = Files.createDirectories(dir.resolve("chromaprint"));
        Files.writeString(tools.resolve("fpcalc.exe"), "x");
        assertEquals(Status.OK, Tools.check(tools.toString(), Tools.FPCALC));
        assertEquals(tools.resolve("fpcalc.exe"), Tools.inFolder(tools, Tools.FPCALC));
    }

    @Test
    void otherFilesMissingPathsAndRelativePathsAreRejected() throws Exception {
        Path other = Files.writeString(dir.resolve("notepad.exe"), "x");
        assertEquals(Status.WRONG, Tools.check(other.toString(), Tools.FPCALC), "another program");
        assertEquals(Status.WRONG, Tools.check(dir.toString(), Tools.MEDIAINFO), "a folder without the tool");
        assertEquals(Status.MISSING, Tools.check(dir.resolve("gone").resolve("fpcalc.exe").toString(), Tools.FPCALC));
        // relative to the working directory: a planted executable could be picked up
        assertEquals(Status.WRONG, Tools.check("lib/fpcalc.exe", Tools.FPCALC));
        assertEquals(Status.MISSING, Tools.check("C:\\bad\"name\u0000", Tools.FPCALC));
    }

    @Test
    void chooserNamesFitThePlatform() {
        boolean windows = MediaInfo.isWindows();
        assertTrue(Tools.platformNames(Tools.MEDIAINFO).contains(windows ? "MediaInfo.dll" : "mediainfo"));
        assertTrue(Tools.platformNames(Tools.FPCALC).stream().allMatch(n -> windows == n.endsWith(".exe")));
    }
}
