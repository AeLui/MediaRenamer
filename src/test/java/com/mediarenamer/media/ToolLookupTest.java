package com.mediarenamer.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.util.Processes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/** External tools: never looked up relative to the working directory, and never allowed to hang. */
class ToolLookupTest {

    private static final boolean WINDOWS = MediaInfo.isWindows();

    @Test
    void libraryPlantedInTheWorkingDirectoryIsIgnored() throws IOException {
        Path lib = Paths.get("lib");
        boolean created = !Files.exists(lib);
        Path planted = lib.resolve("mr-planted-test.dll");
        try {
            Files.createDirectories(lib);
            Files.writeString(planted, "not a dll");
            List<Path> found = MediaInfo.candidates("mr-planted-test.dll");
            assertTrue(found.stream().allMatch(Path::isAbsolute));
            assertFalse(found.contains(planted.toAbsolutePath()), found.toString());
        } finally {
            Files.deleteIfExists(planted);
            if (created) {
                Files.deleteIfExists(lib);
            }
        }
    }

    @Test
    void relativeOrMalformedPathEntriesAreSkipped() {
        assertFalse(MediaInfo.isAbsolutePath("lib"));
        assertFalse(MediaInfo.isAbsolutePath("."));
        assertTrue(MediaInfo.isAbsolutePath(Paths.get("x").toAbsolutePath().toString()));
        if (WINDOWS) {
            assertFalse(MediaInfo.isAbsolutePath("C:\\Program Files\\\"bad"));
        }
    }

    @Test
    void processOutputIsReturned() throws Exception {
        List<String> cmd = WINDOWS ? List.of("cmd", "/c", "echo hello") : List.of("sh", "-c", "echo hello");
        assertEquals("hello", Processes.run(cmd, Duration.ofSeconds(30)).trim());
    }

    @Test
    void hangingProcessIsKilled() {
        List<String> cmd = WINDOWS ? List.of("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 60") : List.of("sleep", "60");
        long start = System.nanoTime();
        assertThrows(IOException.class, () -> Processes.run(cmd, Duration.ofSeconds(2)));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 30);
    }
}
