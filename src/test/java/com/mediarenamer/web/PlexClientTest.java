package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.app.I18n;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Plex library scan and plex.tv PIN sign-in against a local HTTP server that mimics both APIs. */
class PlexClientTest {

    private static final String TOKEN = "PlexToken-0123456789";
    private static final String ACCOUNT = "AccountToken-abcdefgh";
    private static final long PIN_ID = 2345678901L;
    private static final String PIN_CODE = "abcd1234efgh5678";

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<String> tokens = new CopyOnWriteArrayList<>();
    private final List<String> clientIds = new CopyOnWriteArrayList<>();
    /** Polls of the PIN that answer without a token before it is "allowed". */
    private final AtomicInteger pendingPolls = new AtomicInteger(2);
    /** false = plex.tv no longer knows the PIN (it expired). */
    private volatile boolean pinExists = true;
    private volatile String pinToken = ACCOUNT;
    private volatile String sections = "{\"MediaContainer\":{\"size\":3,\"Directory\":[{\"key\":\"1\",\"title\":\"Movies\"},{\"key\":\"2\",\"title\":\"TV\"},"
            + "{\"key\":\"../../evil?x=\",\"title\":\"Hostile\"}]}}";
    private volatile String resources;
    private String url;
    private String savedPlexTv;
    private String savedClients;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        savedPlexTv = PlexClient.plexTv;
        savedClients = PlexClient.clientsPlexTv;
        PlexClient.plexTv = url;
        PlexClient.clientsPlexTv = url;
        I18n.setLocale(java.util.Locale.ENGLISH);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        PlexClient.plexTv = savedPlexTv;
        PlexClient.clientsPlexTv = savedClients;
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String token = ex.getRequestHeaders().getFirst("X-Plex-Token");
        calls.add(method + " " + ex.getRequestURI());
        tokens.add(String.valueOf(token));
        clientIds.add(String.valueOf(ex.getRequestHeaders().getFirst("X-Plex-Client-Identifier")));
        if (method.equals("GET") && path.equals("/identity")) {
            send(ex, 200, "{\"MediaContainer\":{\"size\":0,\"machineIdentifier\":\"abc\",\"version\":\"1.41.0\"}}");
        } else if (method.equals("POST") && path.equals("/api/v2/pins")) {
            send(ex, 201, "{\"id\":" + PIN_ID + ",\"code\":\"" + PIN_CODE + "\",\"expiresIn\":900,\"authToken\":null}");
        } else if (method.equals("GET") && path.equals("/api/v2/pins/" + PIN_ID)) {
            if (!pinExists) {
                send(ex, 404, "{}");
            } else if (pendingPolls.getAndDecrement() > 0) {
                send(ex, 200, "{\"id\":" + PIN_ID + ",\"code\":\"" + PIN_CODE + "\",\"authToken\":null}");
            } else {
                send(ex, 200, "{\"id\":" + PIN_ID + ",\"code\":\"" + PIN_CODE + "\",\"authToken\":\"" + pinToken + "\"}");
            }
        } else if (method.equals("GET") && path.equals("/api/v2/resources")) {
            send(ex, ACCOUNT.equals(token) ? 200 : 401, ACCOUNT.equals(token) ? resources : "");
        } else if (!TOKEN.equals(token)) {
            send(ex, 401, "");
        } else if (method.equals("GET") && path.equals("/library/sections")) {
            send(ex, 200, sections);
        } else if (method.equals("GET") && path.matches("/library/sections/\\d+/refresh")) {
            send(ex, 200, "");
        } else if (method.equals("GET") && path.equals("/")) {
            send(ex, 200, "{\"MediaContainer\":{\"friendlyName\":\"salon\",\"version\":\"1.41.0.8992\"}}");
        } else {
            send(ex, 404, "");
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
        if (b.length > 0) {
            ex.getResponseBody().write(b);
        }
        ex.close();
    }

    // ------------------------------------------------------------------ scan

    @Test
    void refreshScansEveryLibraryWithTheTokenInAHeader() throws Exception {
        assertEquals(2, new PlexClient(url, TOKEN).refreshLibraries());
        assertEquals(List.of("GET /library/sections", "GET /library/sections/1/refresh", "GET /library/sections/2/refresh"), calls);
        assertTrue(tokens.stream().allMatch(TOKEN::equals), tokens.toString());
        // the token never goes in the URL, and the hostile library key does not reach the scan URL
        assertTrue(calls.stream().noneMatch(c -> c.contains(TOKEN) || c.contains("evil")), calls.toString());
    }

    @Test
    void systemInfoChecksTheConnection() throws Exception {
        assertEquals("salon (1.41.0.8992)", new PlexClient(url, TOKEN).systemInfo());
    }

    @Test
    void wrongTokenIsReported() {
        IOException e = assertThrows(IOException.class, () -> new PlexClient(url, "WrongToken-123456").refreshLibraries());
        assertEquals(401, ((WebRequest.HttpException) e.getCause()).getStatus());
        assertEquals(I18n.tr("plex.error.key", 401), e.getMessage());
    }

    @Test
    void serverDownGivesAReadableMessage() {
        String down = url;
        server.stop(0);
        IOException e = assertThrows(IOException.class, () -> new PlexClient(down, TOKEN).refreshLibraries());
        assertEquals(I18n.tr("plex.error.connect", down), e.getMessage());
    }

    @Test
    void missingConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PlexClient(" ", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> new PlexClient(url, ""));
    }

    @Test
    void urlGetsTheDefaultPortOnlyWithoutSchemeAndPort() {
        assertEquals("http://192.168.1.10:32400", PlexClient.normalize(" 192.168.1.10 "));
        assertEquals("http://nas:32400", PlexClient.normalize("nas/"));
        assertEquals("http://nas:32401", PlexClient.normalize("nas:32401"));
        assertEquals("https://plex.example.org", PlexClient.normalize("https://plex.example.org/"));
        assertEquals("http://nas:32400/plex", PlexClient.normalize("nas/plex"));
        assertNull(PlexClient.normalize(""));
    }

    // ------------------------------------------------------------------ sign-in

    @Test
    void pinSignInWaitsUntilTheUserAllowsIt() throws Exception {
        PlexClient.Pin pin = PlexClient.requestPin();
        assertEquals(PIN_ID, pin.id());
        assertEquals(PIN_CODE, pin.code());
        String auth = pin.authUrl();
        assertTrue(auth.startsWith("https://app.plex.tv/auth#?clientID=" + PlexClient.clientId() + "&code=" + PIN_CODE), auth);
        assertTrue(auth.endsWith("&context%5Bdevice%5D%5Bproduct%5D=MediaRenamer"), auth);

        assertEquals(ACCOUNT, PlexClient.waitForToken(pin, () -> false, Duration.ofMillis(10)));
        assertEquals(4, calls.size(), "one POST and three polls: " + calls);
        // every request identifies the installation, always with the same id
        assertTrue(clientIds.stream().allMatch(PlexClient.clientId()::equals), clientIds.toString());
        assertEquals(PlexClient.clientId(), PlexClient.clientId());
    }

    @Test
    void cancelledSignInReturnsNull() throws Exception {
        pendingPolls.set(1000);
        PlexClient.Pin pin = PlexClient.requestPin();
        AtomicInteger checks = new AtomicInteger();
        assertNull(PlexClient.waitForToken(pin, () -> checks.incrementAndGet() > 2, Duration.ofMillis(10)));
    }

    @Test
    void expiredPinIsReported() throws Exception {
        PlexClient.Pin pin = PlexClient.requestPin();
        pinExists = false;
        IOException e = assertThrows(IOException.class, () -> PlexClient.waitForToken(pin, () -> false, Duration.ofMillis(10)));
        assertEquals(I18n.tr("plex.login.expired"), e.getMessage());
        // a PIN past its expiry time is not polled any more
        PlexClient.Pin old = new PlexClient.Pin(PIN_ID, PIN_CODE, java.time.Instant.now().minusSeconds(1));
        assertThrows(IOException.class, () -> PlexClient.waitForToken(old, () -> false, Duration.ofMillis(10)));
    }

    @Test
    void hostileTokenFromPlexTvIsRejected() throws Exception {
        pendingPolls.set(0);
        for (String bad : new String[] { "short", "tok\\r\\nX-Injected: 1abcdefgh", "<script>alert(1)</script>", "a".repeat(300) }) {
            pinToken = bad;
            PlexClient.Pin pin = PlexClient.requestPin();
            IOException e = assertThrows(IOException.class, () -> PlexClient.checkPin(pin), bad);
            assertEquals(I18n.tr("plex.login.invalid"), e.getMessage());
        }
    }

    @Test
    void serversAreTheOwnOnesWithSafeAddressesBestFirst() throws Exception {
        resources = """
                [
                 {"name":"salon","product":"Plex Media Server","provides":"server","owned":true,"accessToken":"ServerToken-12345678",
                  "connections":[
                   {"protocol":"https","address":"203.0.113.7","port":32400,"uri":"https://203-0-113-7.abc.plex.direct:32400","local":false,"relay":false},
                   {"protocol":"https","address":"192.168.1.10","port":32400,"uri":"https://192-168-1-10.abc.plex.direct:32400","local":true,"relay":false},
                   {"protocol":"https","address":"10.0.0.1","port":8443,"uri":"https://relay.plex.direct:8443","local":false,"relay":true},
                   {"protocol":"file","address":"x","port":1,"uri":"file:///etc/passwd","local":true,"relay":false},
                   {"protocol":"http","address":"evil.example","port":80,"uri":"http://user:pw@evil.example/","local":false,"relay":false}
                  ]},
                 {"name":"friend","provides":"server","owned":false,"accessToken":"FriendToken-12345678","connections":[]},
                 {"name":"phone","provides":"client,player","owned":true,"connections":[]},
                 {"name":"nas","provides":"server","owned":"1","accessToken":"bad token!","connections":[]}
                ]
                """;
        List<PlexClient.Server> servers = PlexClient.servers(ACCOUNT);
        assertEquals(List.of("salon", "nas"), servers.stream().map(PlexClient.Server::name).toList());
        PlexClient.Server salon = servers.get(0);
        assertEquals("ServerToken-12345678", salon.token());
        assertEquals(List.of("https://192-168-1-10.abc.plex.direct:32400", "http://192.168.1.10:32400", "https://203-0-113-7.abc.plex.direct:32400"),
                salon.urls());
        // an invalid server token is not used: the account token works with the user's own servers
        assertEquals(ACCOUNT, servers.get(1).token());
        assertTrue(calls.get(0).startsWith("GET /api/v2/resources?includeHttps=1&includeRelay=0"), calls.toString());
        assertFalse(calls.get(0).contains(ACCOUNT));
    }

    @Test
    void firstReachableAddressIsUsedAndProbedWithoutToken() throws Exception {
        int closed;
        try (ServerSocket s = new ServerSocket(0)) {
            closed = s.getLocalPort();
        }
        assertEquals(url, PlexClient.firstReachable(List.of("http://127.0.0.1:" + closed, url)));
        assertEquals(List.of("GET /identity"), calls);
        assertEquals(List.of("null"), tokens);
        assertNull(PlexClient.firstReachable(List.of("http://127.0.0.1:" + closed)));
    }

    @Test
    void candidateListIsBounded() {
        List<Map<String, Object>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            many.add(Map.of("uri", "https://h" + i + ".plex.direct:32400", "local", false, "relay", false));
        }
        assertEquals(PlexClient.MAX_CANDIDATES, PlexClient.candidateUrls(many).size());
    }

    @Test
    void logsHideTokensAndSignInCodes() {
        String logged = WebRequest.redact("https://plex.tv/api/v2/pins/1?code=" + PIN_CODE + "&X-Plex-Token=" + TOKEN);
        assertFalse(logged.contains(PIN_CODE) || logged.contains(TOKEN), logged);
    }

    @Test
    void presetOptionHasPriorityAndNeedsConfiguration() {
        assertTrue(MediaServers.shouldScan(true, null, true));
        assertTrue(MediaServers.shouldScan(true, true, false));
        assertFalse(MediaServers.shouldScan(true, false, true));
        assertFalse(MediaServers.shouldScan(false, true, true), "without URL/token there is never a scan");
    }
}
