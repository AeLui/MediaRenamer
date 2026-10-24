package com.mediarenamer.web;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import groovy.json.JsonSlurper;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Minimal client of Plex: scans the libraries after renaming, and signs in with plex.tv to get the token.
 *
 * Plex Media Server (with {@code Accept: application/json}, so no XML is parsed):
 * <ul>
 * <li>{@code GET /library/sections}: the libraries; {@code GET /library/sections/{key}/refresh} scans one (the same
 * as "Scan Library Files"); the scan continues on the server.</li>
 * <li>{@code GET /}: requires the token, used to check URL and token (server name and version).</li>
 * <li>{@code GET /identity}: public, used to find which address of a server answers from this PC.</li>
 * </ul>
 * plex.tv, the official PIN sign-in for applications:
 * <ol>
 * <li>{@code POST https://plex.tv/api/v2/pins?strong=true} creates a PIN (id + code);</li>
 * <li>the browser opens {@code https://app.plex.tv/auth#?clientID=..&code=..}, where the user signs in and allows
 * MediaRenamer (the password is only typed on plex.tv);</li>
 * <li>{@code GET https://plex.tv/api/v2/pins/{id}} is polled until it carries the {@code authToken} or expires;</li>
 * <li>{@code GET https://clients.plex.tv/api/v2/resources} lists the user's own servers, with their addresses and
 * the token to use with each one.</li>
 * </ol>
 * The token always goes in the {@code X-Plex-Token} header, never in the URL.
 */
public final class PlexClient {

    /** plex.tv endpoints (https; only the tests point them to a local server). */
    static String plexTv = "https://plex.tv";
    static String clientsPlexTv = "https://clients.plex.tv";
    static String authApp = "https://app.plex.tv/auth#?";
    /** Port of Plex Media Server, added when the URL has neither scheme nor port ("192.168.1.10"). */
    static final int DEFAULT_PORT = 32400;
    /** Time limit to try each address of a server, and how many addresses are tried at most. */
    static final Duration PROBE_TIMEOUT = Duration.ofSeconds(4);
    static final int MAX_CANDIDATES = 6;
    public static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    /** Tokens and PIN codes are short alphanumeric strings: anything else from a response is rejected. */
    private static final String TOKEN_PATTERN = "[A-Za-z0-9_\\-]{8,200}";

    private final String baseUrl;
    private final String token;

    public PlexClient(String url, String token) {
        this.baseUrl = normalize(url);
        if (baseUrl == null || token == null || token.isBlank()) {
            throw new IllegalArgumentException(tr("error.plexConfig"));
        }
        this.token = token.trim();
        if (MediaServers.isInsecureRemote(baseUrl)) {
            Logging.warn(tr("plex.insecure", baseUrl));
        }
    }

    /** Client with the Preferences configuration. */
    public static PlexClient fromSettings() {
        Settings s = Settings.get();
        return new PlexClient(s.get(Settings.PLEX_URL), s.get(Settings.PLEX_TOKEN));
    }

    /** true if there is a URL and a token: only then can the scan options be enabled. */
    public static boolean isConfigured() {
        Settings s = Settings.get();
        return s.get(Settings.PLEX_URL) != null && s.get(Settings.PLEX_TOKEN) != null;
    }

    /** true if a scan is needed after renaming (presetOverride: option of the active preset, null = not set). */
    public static boolean scanAfterRename(Boolean presetOverride) {
        return MediaServers.shouldScan(isConfigured(), presetOverride, Settings.get().getBoolean(Settings.PLEX_SCAN, false));
    }

    /** Starts the scan of every library (does not wait for it on the server); returns how many were asked. */
    public int refreshLibraries() throws IOException, InterruptedException {
        try {
            List<String> keys = sectionKeys();
            for (String key : keys) {
                WebRequest.get(baseUrl + "/library/sections/" + key + "/refresh", headers(token), null);
            }
            return keys.size();
        } catch (IOException e) {
            throw explain(e);
        }
    }

    /** Keys of the libraries; only plain numbers are kept, so that a response cannot change the scan URL. */
    List<String> sectionKeys() throws IOException, InterruptedException {
        Object res = WebRequest.getJson(baseUrl + "/library/sections", headers(token), null);
        List<String> keys = new ArrayList<>();
        for (Map<String, Object> d : Json.maps(Json.map(res, "MediaContainer"), "Directory")) {
            String key = Json.string(d, "key");
            if (key != null && key.matches("\\d{1,9}") && !keys.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** Checks the connection and the token and returns "Server name (version)". */
    public String systemInfo() throws IOException, InterruptedException {
        Object info;
        try {
            info = Json.map(WebRequest.getJson(baseUrl + "/", headers(token), null), "MediaContainer");
        } catch (IOException e) {
            throw explain(e);
        }
        String name = Json.string(info, "friendlyName");
        String version = Json.string(info, "version");
        return (name == null ? "Plex" : name) + (version == null ? "" : " (" + version + ")");
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    IOException explain(IOException e) {
        return MediaServers.explain(e, baseUrl, "plex");
    }

    // ------------------------------------------------------------------ plex.tv sign-in

    /** A PIN of the sign-in: the page to open in the browser and until when it can be authorized. */
    public record Pin(long id, String code, Instant expires) {
        /** Page of plex.tv where the user signs in and allows this application. */
        public String authUrl() {
            return authApp + WebRequest.query(WebRequest.params("clientID", clientId(), "code", code, "context[device][product]", Settings.APP_NAME));
        }
    }

    /** A server of the user: its name, the token to use with it and the addresses to try, best first. */
    public record Server(String name, String token, List<String> urls) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** Step 1: creates a sign-in PIN on plex.tv. */
    public static Pin requestPin() throws IOException, InterruptedException {
        Object pin = parse(WebRequest.post(plexTv + "/api/v2/pins?strong=true", identity()));
        Long id = number(Json.get(pin, "id"));
        String code = Json.string(pin, "code");
        if (id == null || code == null || !code.matches("[A-Za-z0-9]{4,64}")) {
            throw new IOException(tr("plex.login.invalid"));
        }
        Integer expiresIn = Json.integer(pin, "expiresIn");
        int seconds = Math.max(60, Math.min(1800, expiresIn == null ? 900 : expiresIn));
        return new Pin(id, code, Instant.now().plusSeconds(seconds));
    }

    /** Step 2 (once): the account token if the user has already allowed the PIN, otherwise null. */
    static String checkPin(Pin pin) throws IOException, InterruptedException {
        Object res;
        try {
            res = WebRequest.getJson(plexTv + "/api/v2/pins/" + pin.id() + "?" + WebRequest.query(WebRequest.params("code", pin.code())), identity(), null);
        } catch (WebRequest.HttpException e) {
            if (e.getStatus() == 404) {
                throw new IOException(tr("plex.login.expired"), e);
            }
            throw e;
        }
        String token = Json.string(res, "authToken");
        if (token == null) {
            return null;
        }
        if (!token.matches(TOKEN_PATTERN)) {
            throw new IOException(tr("plex.login.invalid"));
        }
        return token;
    }

    /**
     * Step 2: waits (polling every interval) until the user allows the PIN in the browser. Returns the account token,
     * or null if cancelled; fails when the PIN expires or plex.tv keeps failing. Call outside the Swing thread.
     */
    public static String waitForToken(Pin pin, BooleanSupplier cancelled, Duration interval) throws IOException, InterruptedException {
        int failures = 0;
        while (Instant.now().isBefore(pin.expires())) {
            if (cancelled.getAsBoolean()) {
                return null;
            }
            try {
                String token = checkPin(pin);
                if (token != null) {
                    return token;
                }
                failures = 0;
            } catch (WebRequest.HttpException | java.net.ConnectException | java.net.http.HttpTimeoutException e) {
                // a passing network or server error does not cancel the sign-in, three in a row do
                if (++failures >= 3) {
                    throw e;
                }
            }
            Thread.sleep(interval.toMillis());
        }
        throw new IOException(tr("plex.login.expired"));
    }

    /** Step 3: the user's own servers (not the shared ones), with the addresses to try. */
    public static List<Server> servers(String accountToken) throws IOException, InterruptedException {
        Object res = WebRequest.getJson(clientsPlexTv + "/api/v2/resources?" + WebRequest.query(WebRequest.params("includeHttps", 1, "includeRelay", 0)),
                headers(accountToken), null);
        List<Server> servers = new ArrayList<>();
        for (Map<String, Object> d : Json.maps(res, null)) {
            String provides = Json.string(d, "provides");
            if (provides == null || !List.of(provides.split(",")).contains("server") || !truthy(d.get("owned"))) {
                continue;
            }
            String token = Json.string(d, "accessToken");
            String name = Json.string(d, "name");
            servers.add(new Server(name == null ? "Plex" : name.replaceAll("\\p{Cntrl}", ""), token != null && token.matches(TOKEN_PATTERN) ? token : accountToken,
                    candidateUrls(Json.maps(d, "connections"))));
        }
        return servers;
    }

    /**
     * Addresses of a server to try, best first: local https (plex.direct certificate), local plain http, remote
     * https. Relays are left out. Only http/https URLs with a host and valid ports are accepted (the list comes from
     * the network).
     */
    static List<String> candidateUrls(List<Map<String, Object>> connections) {
        Set<String> local = new LinkedHashSet<>();
        Set<String> localHttp = new LinkedHashSet<>();
        Set<String> remote = new LinkedHashSet<>();
        for (Map<String, Object> c : connections) {
            String protocol = Json.string(c, "protocol");
            if (truthy(c.get("relay")) || protocol != null && !protocol.matches("(?i)https?")) {
                continue;
            }
            boolean isLocal = truthy(c.get("local"));
            String uri = safeUrl(Json.string(c, "uri"));
            if (uri != null) {
                (isLocal ? local : remote).add(uri);
            }
            String address = Json.string(c, "address");
            Integer port = Json.integer(c, "port");
            if (isLocal && address != null && port != null && port > 0 && port < 65536 && address.matches("[A-Za-z0-9.\\-]{1,253}|[0-9A-Fa-f:]{2,39}")) {
                String host = address.contains(":") ? "[" + address + "]" : address;
                String http = safeUrl("http://" + host + ":" + port);
                if (http != null) {
                    localHttp.add(http);
                }
            }
        }
        List<String> all = new ArrayList<>(local);
        all.addAll(localHttp);
        all.addAll(remote);
        return all.size() > MAX_CANDIDATES ? List.copyOf(all.subList(0, MAX_CANDIDATES)) : all;
    }

    /** The first address that answers {@code /identity} (no token is sent), or null. Call outside the Swing thread. */
    public static String firstReachable(List<String> urls) throws InterruptedException {
        for (String url : urls) {
            try {
                WebRequest.get(url + "/identity", identity(), null, PROBE_TIMEOUT);
                return url;
            } catch (IOException | IllegalArgumentException e) {
                Logging.debug("Plex " + url + ": " + e);
            }
        }
        return null;
    }

    /** The id of this installation for plex.tv (created and saved the first time). */
    public static synchronized String clientId() {
        String id = Settings.get().get(Settings.PLEX_CLIENT_ID);
        if (id == null || !id.matches("[A-Za-z0-9\\-]{8,64}")) {
            id = java.util.UUID.randomUUID().toString();
            Settings.get().put(Settings.PLEX_CLIENT_ID, id);
        }
        return id;
    }

    /** URL as it will be used (scheme and default port added, trailing slashes removed), or null if empty. */
    public static String normalizeUrl(String url) {
        return normalize(url);
    }

    static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.trim();
        if (!u.matches("(?i)https?://.*") && !u.matches("[^/]*:\\d+(/.*)?")) {
            int slash = u.indexOf('/');
            u = slash < 0 ? u + ":" + DEFAULT_PORT : u.substring(0, slash) + ":" + DEFAULT_PORT + u.substring(slash);
        }
        return MediaServers.normalize(u);
    }

    // ------------------------------------------------------------------ helpers

    /** Headers that identify MediaRenamer to Plex (product, version, installation id). */
    private static Map<String, String> identity() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("Accept", "application/json");
        h.put("X-Plex-Product", Settings.APP_NAME);
        h.put("X-Plex-Version", Settings.version());
        h.put("X-Plex-Client-Identifier", clientId());
        h.put("X-Plex-Platform", System.getProperty("os.name", "Windows"));
        h.put("X-Plex-Device-Name", Settings.APP_NAME);
        return h;
    }

    private static Map<String, String> headers(String token) {
        Map<String, String> h = identity();
        h.put("X-Plex-Token", token);
        return h;
    }

    private static Object parse(byte[] body) throws IOException {
        try {
            return new JsonSlurper().parse(body, "UTF-8");
        } catch (RuntimeException e) {
            throw new IOException(tr("plex.login.invalid"), e);
        }
    }

    private static Long number(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null ? null : Long.valueOf(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(Object v) {
        return v != null && (v.equals(Boolean.TRUE) || "true".equalsIgnoreCase(v.toString()) || "1".equals(v.toString()));
    }

    /** The URL without trailing slashes if it is http(s) with a host, otherwise null. */
    private static String safeUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI u = URI.create(url.trim());
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && u.getHost() != null && u.getRawUserInfo() == null && u.getRawQuery() == null
                    && u.getRawFragment() == null) {
                return url.trim().replaceAll("/+$", "");
            }
        } catch (IllegalArgumentException e) {
            // not a URL
        }
        return null;
    }
}
