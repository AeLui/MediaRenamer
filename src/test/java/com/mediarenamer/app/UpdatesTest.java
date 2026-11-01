package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class UpdatesTest {

    private static byte[] resource() throws IOException {
        try (InputStream in = UpdatesTest.class.getResourceAsStream("release-latest.json")) {
            return in.readAllBytes();
        }
    }

    private static Updates.Release release() throws IOException {
        return Updates.parse(resource());
    }

    @Test
    void compareVersions() {
        assertTrue(Updates.compare("2.10.0", "2.9.0") > 0);
        assertTrue(Updates.compare("2.8.0", "2.8.0-SNAPSHOT") > 0);
        assertEquals(0, Updates.compare("2.8", "2.8.0"));
        assertTrue(Updates.isNewer("2.8.0", "2.7.1"));
        assertFalse(Updates.isNewer("2.7.1", "2.7.1"));
        assertFalse(Updates.isNewer("2.8.0", "dev"));
        assertFalse(Updates.isNewer("abc", "2.7.1"));
    }

    @Test
    void parseRelease() throws IOException {
        Updates.Release r = release();
        assertEquals("2.8.0", r.version());
        assertEquals("https://github.com/AeLui/MediaRenamer/releases/tag/v2.8.0", r.page().toString());
        Updates.Asset a = r.installer().orElseThrow();
        assertEquals("MediaRenamer-2.8.0-setup.exe", a.name());
        assertEquals("abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", a.sha256());
        assertEquals(123456789L, a.size());
        assertFalse(r.notes().isBlank());
    }

    @Test
    void installerMissing() throws IOException {
        String json = """
                {"tag_name":"v2.8.0","html_url":"https://github.com/AeLui/MediaRenamer/releases/tag/v2.8.0","body":"x",
                 "draft":false,"prerelease":false,"assets":[{"name":"MediaRenamer-2.8.0.zip","size":1,
                 "browser_download_url":"https://github.com/AeLui/MediaRenamer/releases/download/v2.8.0/MediaRenamer-2.8.0.zip"}]}
                """;
        Updates.Release r = Updates.parse(json.getBytes(StandardCharsets.UTF_8));
        assertTrue(r.installer().isEmpty());
        assertNull(r.assets().get(0).sha256());
    }

    @Test
    void installerNeedsExactName() throws IOException {
        String json = """
                {"tag_name":"v2.8.0","draft":false,"prerelease":false,"assets":[
                 {"name":"Evil-setup.exe","size":1,"browser_download_url":"https://github.com/a/b/Evil-setup.exe"},
                 {"name":"MediaRenamer-9.9.9-setup.exe","size":1,"browser_download_url":"https://github.com/a/b/x.exe"}]}
                """;
        assertTrue(Updates.parse(json.getBytes(StandardCharsets.UTF_8)).installer().isEmpty());
    }

    @Test
    void malformedAssetUrlRejected() {
        String json = """
                {"tag_name":"v2.8.0","draft":false,"prerelease":false,"assets":[{"name":"a-setup.exe","size":1,
                 "browser_download_url":"http://x/a b"}]}
                """;
        assertThrows(IOException.class, () -> Updates.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void prereleaseRejected() throws IOException {
        String json = new String(resource(), StandardCharsets.UTF_8).replace("\"prerelease\": false", "\"prerelease\": true");
        assertThrows(IOException.class, () -> Updates.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void dueForAutoCheck() {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        assertFalse(Updates.dueForAutoCheck(false, 0, now));
        assertFalse(Updates.dueForAutoCheck(true, now.minus(Duration.ofHours(23)).toEpochMilli(), now));
        assertTrue(Updates.dueForAutoCheck(true, now.minus(Duration.ofHours(25)).toEpochMilli(), now));
        assertTrue(Updates.dueForAutoCheck(true, 0, now));
        // clock moved back or garbage in the preferences: due, and no exception
        assertTrue(Updates.dueForAutoCheck(true, now.plus(Duration.ofHours(1)).toEpochMilli(), now));
        assertTrue(Updates.dueForAutoCheck(true, Long.MAX_VALUE, now));
        assertTrue(Updates.dueForAutoCheck(true, Long.MIN_VALUE, now));
        assertTrue(Updates.dueForAutoCheck(true, -5, now));
    }

    @Test
    void sweepStaleDeletesOnlyOldUpdateFolders(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        java.nio.file.Path old = java.nio.file.Files.createDirectory(tmp.resolve("MediaRenamer-update123"));
        java.nio.file.Files.writeString(old.resolve("MediaRenamer-2.8.0-setup.exe"), "x");
        java.nio.file.Path recent = java.nio.file.Files.createDirectory(tmp.resolve("MediaRenamer-update456"));
        java.nio.file.Path other = java.nio.file.Files.createDirectory(tmp.resolve("SomethingElse"));
        java.nio.file.Path plainFile = java.nio.file.Files.writeString(tmp.resolve("MediaRenamer-update.txt"), "x");
        java.nio.file.Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.from(now.minus(Duration.ofHours(2))));
        java.nio.file.Files.setLastModifiedTime(recent, java.nio.file.attribute.FileTime.from(now.minus(Duration.ofMinutes(10))));
        java.nio.file.Files.setLastModifiedTime(other, java.nio.file.attribute.FileTime.from(now.minus(Duration.ofDays(9))));

        assertEquals(1, Updates.sweepStale(tmp, now));

        assertFalse(java.nio.file.Files.exists(old));
        assertTrue(java.nio.file.Files.exists(recent));
        assertTrue(java.nio.file.Files.exists(other));
        assertTrue(java.nio.file.Files.exists(plainFile));
        assertEquals(0, Updates.sweepStale(tmp.resolve("missing"), now));
    }

    @Test
    void discardDeletesTheDownloadFolderOnly(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws IOException {
        java.nio.file.Path dir = java.nio.file.Files.createDirectory(tmp.resolve("MediaRenamer-update9"));
        java.nio.file.Path file = java.nio.file.Files.writeString(dir.resolve("MediaRenamer-2.8.0-setup.exe"), "x");
        java.nio.file.Path foreign = java.nio.file.Files.createDirectory(tmp.resolve("Documents"));
        java.nio.file.Path keep = java.nio.file.Files.writeString(foreign.resolve("a.exe"), "x");

        Updates.discard(keep); // not one of ours: untouched
        Updates.discard(null);
        assertTrue(java.nio.file.Files.exists(keep));
        Updates.discard(file);
        assertFalse(java.nio.file.Files.exists(dir));
    }

    @Test
    void skipVersion() throws IOException {
        Updates.Release r = release();
        assertFalse(Updates.offer(r, "2.7.1", "2.8.0", false));
        assertTrue(Updates.offer(r, "2.7.1", "2.8.0", true));
        assertTrue(Updates.offer(r, "2.7.1", null, false));
        assertFalse(Updates.offer(r, "2.8.0", null, true));
    }
}
