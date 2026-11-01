package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verified update download against a local HTTP server. */
class UpdatesDownloadTest {

    private static final int SIZE = 300 * 1024;

    private HttpServer server;
    private byte[] data;
    private String digest;
    private int served;

    @BeforeEach
    void start() throws Exception {
        data = new byte[SIZE];
        new Random(42).nextBytes(data);
        digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        served = SIZE;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/setup.exe", ex -> {
            int n = Math.min(served, data.length);
            byte[] body = served <= data.length ? java.util.Arrays.copyOf(data, n) : java.util.Arrays.copyOf(data, served);
            ex.sendResponseHeaders(200, body.length);
            try {
                ex.getResponseBody().write(body);
            } catch (IOException ignored) {
                // the client aborted
            } finally {
                ex.close();
            }
        });
        server.createContext("/redir", ex -> {
            ex.getResponseHeaders().add("Location", "/setup.exe");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Updates.Asset asset(long size, String sha) {
        return new Updates.Asset("MediaRenamer-9.9.9-setup.exe",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/setup.exe"), size, sha);
    }

    private static List<String> tempFolders() throws IOException {
        try (Stream<Path> s = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("MediaRenamer-update")).sorted()
                    .collect(Collectors.toList());
        }
    }

    private void assertRejected(Updates.Asset a) throws IOException {
        List<String> before = tempFolders();
        assertThrows(IOException.class, () -> Updates.download(a, p -> { }, () -> false, u -> true));
        assertEquals(before, tempFolders());
    }

    @Test
    void allowList() {
        assertTrue(Updates.isAllowed(URI.create("https://github.com/AeLui/MediaRenamer/releases/download/v1/x.exe")));
        assertTrue(Updates.isAllowed(URI.create("https://objects.githubusercontent.com/abc")));
        assertTrue(Updates.isAllowed(URI.create("https://release-assets.githubusercontent.com/abc")));
        assertFalse(Updates.isAllowed(URI.create("http://github.com/x.exe")));
        assertFalse(Updates.isAllowed(URI.create("https://github.com.evil.example/x.exe")));
        assertFalse(Updates.isAllowed(URI.create("https://127.0.0.1/x.exe")));
    }

    @Test
    void downloadVerified() throws Exception {
        AtomicLong last = new AtomicLong();
        Path file = Updates.download(asset(SIZE, digest), last::set, () -> false, u -> true);
        try {
            assertEquals("MediaRenamer-9.9.9-setup.exe", file.getFileName().toString());
            assertArrayEquals(data, Files.readAllBytes(file));
            assertEquals(SIZE, last.get());
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        }
    }

    @Test
    void wrongDigestRejected() throws Exception {
        assertRejected(asset(SIZE, "0".repeat(64)));
    }

    @Test
    void missingDigestRejected() throws Exception {
        assertRejected(asset(SIZE, null));
    }

    @Test
    void oversizedRejected() throws Exception {
        served = SIZE + 1;
        assertRejected(asset(SIZE, digest));
    }

    @Test
    void truncatedRejected() throws Exception {
        served = SIZE - 1;
        assertRejected(asset(SIZE, digest));
    }

    @Test
    void disallowedFinalUriRejected() throws Exception {
        List<String> before = tempFolders();
        assertThrows(IOException.class, () -> Updates.download(asset(SIZE, digest), p -> { }, () -> false, u -> false));
        assertEquals(before, tempFolders());
    }

    @Test
    void disallowedRedirectTargetRejected() throws Exception {
        List<String> before = tempFolders();
        Updates.Asset a = new Updates.Asset("MediaRenamer-9.9.9-setup.exe",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/redir"), SIZE, digest);
        assertThrows(IOException.class, () -> Updates.download(a, p -> { }, () -> false, u -> u.getPath().equals("/redir")));
        assertEquals(before, tempFolders());
    }

    @Test
    void traversalNameRejected() throws Exception {
        List<String> before = tempFolders();
        String[] names = {"..\\..\\x-setup.exe", "../x-setup.exe", "C:\\x\\MediaRenamer-1-setup.exe",
                "Evil-setup.exe", "MediaRenamer-..\\..\\x-setup.exe"};
        for (String name : names) {
            Updates.Asset a = new Updates.Asset(name, asset(SIZE, digest).url(), SIZE, digest);
            assertThrows(IOException.class, () -> Updates.download(a, p -> { }, () -> false, u -> true), name);
        }
        assertEquals(before, tempFolders());
    }

    @Test
    void cancelDeletesFile() throws Exception {
        List<String> before = tempFolders();
        AtomicInteger chunks = new AtomicInteger();
        assertThrows(Updates.CancelledException.class,
                () -> Updates.download(asset(SIZE, digest), p -> chunks.incrementAndGet(), () -> chunks.get() >= 1, u -> true));
        assertEquals(before, tempFolders());
    }

    @Test
    void developmentModeUnderTests() {
        assertEquals(Updates.Mode.DEVELOPMENT, Updates.mode());
    }
}
