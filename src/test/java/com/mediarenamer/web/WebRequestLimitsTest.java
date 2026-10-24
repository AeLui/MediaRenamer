package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Size limit of the responses (gzip bombs) and purge of the expired cache. */
class WebRequestLimitsTest {

    @TempDir
    Path dir;

    @Test
    void readLimitedReadsUpToTheLimit() throws IOException {
        byte[] data = new byte[200_000];
        data[123] = 7;
        assertArrayEquals(data, WebRequest.readLimited(new ByteArrayInputStream(data), 200_000));
        assertThrows(IOException.class, () -> WebRequest.readLimited(new ByteArrayInputStream(data), 199_999));
    }

    @Test
    void gzipBombIsRejected() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            byte[] zeros = new byte[1 << 20];
            for (int i = 0; i < (WebRequest.MAX_BODY >> 20) + 1; i++) {
                gz.write(zeros);
            }
        }
        assertTrue(bytes.size() < 1 << 20, "the bomb is small when compressed");
        assertThrows(IOException.class, () -> WebRequest.gunzip(bytes.toByteArray()));
    }

    @Test
    void normalGzipStillWorks() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write("1|1|en|Cowboy Bebop".getBytes());
        }
        try (InputStream in = new ByteArrayInputStream(WebRequest.gunzip(bytes.toByteArray()))) {
            assertEquals("1|1|en|Cowboy Bebop", new String(in.readAllBytes()));
        }
    }

    @Test
    void purgeRemovesOnlyExpiredFilesAndEmptyFolders() throws IOException {
        Path cache = dir.resolve("cache");
        Path old = cache.resolve("ab").resolve("ab01");
        Path fresh = cache.resolve("cd").resolve("cd02");
        Files.createDirectories(old.getParent());
        Files.createDirectories(fresh.getParent());
        Files.writeString(old, "old");
        Files.writeString(fresh, "fresh");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofDays(40))));

        assertEquals(1, WebRequest.purgeCache(cache, Duration.ofDays(31)));
        assertFalse(Files.exists(old));
        assertFalse(Files.exists(old.getParent()), "empty folder removed");
        assertTrue(Files.exists(fresh));
        assertTrue(Files.isDirectory(cache));
    }

    @Test
    void purgeOfMissingCacheDoesNothing() {
        assertEquals(0, WebRequest.purgeCache(dir.resolve("none"), Duration.ofDays(31)));
    }
}
