package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Jellyfin library scan against a local HTTP server that mimics its API. */
class JellyfinClientTest {

    private static final String KEY = "0123456789abcdef";

    private HttpServer server;
    private final List<String> calls = new ArrayList<>();
    private String url;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            calls.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + auth);
            if (ex.getRequestMethod().equals("GET") && ex.getRequestURI().getPath().equals("/System/Info/Public")) {
                // public endpoint: no token needed
                byte[] body = "{\"ServerName\":\"salon\",\"Version\":\"10.10.7\"}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } else if (!("MediaBrowser Token=\"" + KEY + "\"").equals(auth)) {
                ex.sendResponseHeaders(401, -1);
            } else if (ex.getRequestMethod().equals("POST") && ex.getRequestURI().getPath().equals("/Library/Refresh")) {
                ex.sendResponseHeaders(204, -1);
            } else if (ex.getRequestMethod().equals("GET") && ex.getRequestURI().getPath().equals("/System/Info")) {
                byte[] body = "{\"ServerName\":\"salon\",\"Version\":\"10.10.7\"}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            } else {
                ex.sendResponseHeaders(404, -1);
            }
            ex.close();
        });
        server.start();
        url = "127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void refreshPostsToLibraryRefreshWithTokenHeader() throws Exception {
        new JellyfinClient(url, KEY).refreshLibraries();
        assertEquals(List.of("POST /Library/Refresh MediaBrowser Token=\"" + KEY + "\""), calls);
    }

    @Test
    void systemInfoChecksTheConnection() throws Exception {
        assertEquals("salon (10.10.7)", new JellyfinClient(url, KEY).systemInfo());
    }

    @Test
    void wrongKeyIsReported() {
        IOException e = assertThrows(IOException.class, () -> new JellyfinClient(url, "mala").refreshLibraries());
        assertEquals(401, ((WebRequest.HttpException) e.getCause()).getStatus());
        assertTrue(e.getMessage().contains("API Key"), e.getMessage());
    }

    @Test
    void serverDownGivesAReadableMessage() {
        String down = url;
        server.stop(0);
        IOException e = assertThrows(IOException.class, () -> new JellyfinClient(down, KEY).refreshLibraries());
        assertNotNull(e.getMessage());
        // message in the interface language
        assertEquals(com.mediarenamer.app.I18n.tr("jellyfin.error.connect", JellyfinClient.normalize(down)), e.getMessage());
    }

    @Test
    void missingConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JellyfinClient(" ", KEY));
        assertThrows(IllegalArgumentException.class, () -> new JellyfinClient(url, null));
    }

    @Test
    void presetOptionHasPriorityAndNeedsConfiguration() {
        assertTrue(JellyfinClient.shouldScan(true, null, true), "no preset: general preference");
        assertFalse(JellyfinClient.shouldScan(true, null, false));
        assertTrue(JellyfinClient.shouldScan(true, true, false), "the preset overrides the general one");
        assertFalse(JellyfinClient.shouldScan(true, false, true));
        assertFalse(JellyfinClient.shouldScan(false, true, true), "without URL/API Key there is never a scan");
    }

    @Test
    void apiKeysPageDependsOnTheServerVersion() throws Exception {
        String base = "http://nas:8096";
        assertEquals(base + "/web/#/dashboard/keys", JellyfinClient.apiKeysPage(base, "10.10.7"));
        assertEquals(base + "/web/#/dashboard/keys", JellyfinClient.apiKeysPage(base, "10.9.0"));
        assertEquals(base + "/web/#/dashboard/keys", JellyfinClient.apiKeysPage(base, "11.0.0-rc1"));
        assertEquals(base + "/web/index.html#!/apikeys.html", JellyfinClient.apiKeysPage(base, "10.8.13"));
        assertEquals(base + "/web/", JellyfinClient.apiKeysPage(base, null));
        // real request to the public endpoint (no API Key needed)
        assertEquals("http://" + url.replaceAll("/$", "") + "/web/#/dashboard/keys", JellyfinClient.apiKeysPage(url));
        String down = url;
        server.stop(0);
        assertEquals("http://" + down.replaceAll("/$", "") + "/web/", JellyfinClient.apiKeysPage(down), "server down: web interface root");
    }

    @Test
    void urlIsNormalized() {
        assertEquals("http://nas:8096", JellyfinClient.normalize(" nas:8096// "));
        assertEquals("https://jf.example.org/jellyfin", JellyfinClient.normalize("https://jf.example.org/jellyfin/"));
    }
}
