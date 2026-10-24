package com.mediarenamer.web;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Settings;

import java.io.IOException;
import java.util.Map;

/**
 * Minimal client of the Jellyfin REST API: starts "Scan All Libraries" after renaming.
 *
 * The official Jellyfin API is used:
 * <ul>
 * <li>{@code POST /Library/Refresh}: same as the "Scan All Libraries" button of the
 * admin dashboard; answers 204 and the scan continues on the server.</li>
 * <li>{@code GET /System/Info}: requires authentication, used to check URL and key.</li>
 * </ul>
 * The key (Dashboard → API Keys) goes in the header
 * {@code Authorization: MediaBrowser Token="..."}, never in the URL.
 */
public final class JellyfinClient {

    private final String baseUrl;
    private final String apiKey;

    public JellyfinClient(String url, String apiKey) {
        this.baseUrl = normalize(url);
        if (baseUrl == null || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(tr("error.jellyfinConfig"));
        }
        this.apiKey = apiKey.trim();
        if (isInsecureRemote(baseUrl)) {
            com.mediarenamer.app.Logging.warn(tr("jellyfin.insecure", baseUrl));
        }
    }

    /** Client with the Preferences configuration. */
    public static JellyfinClient fromSettings() {
        Settings s = Settings.get();
        return new JellyfinClient(s.get(Settings.JELLYFIN_URL), s.get(Settings.JELLYFIN_KEY));
    }

    /** true if there is a URL and an API Key: only then can the scan options be enabled. */
    public static boolean isConfigured() {
        Settings s = Settings.get();
        return s.get(Settings.JELLYFIN_URL) != null && s.get(Settings.JELLYFIN_KEY) != null;
    }

    /**
     * true if a scan is needed after renaming. presetOverride is the option of the active preset
     * (null = the preset does not set it or there is no preset): if present, it takes precedence over the general preference.
     */
    public static boolean scanAfterRename(Boolean presetOverride) {
        return shouldScan(isConfigured(), presetOverride, Settings.get().getBoolean(Settings.JELLYFIN_SCAN, false));
    }

    static boolean shouldScan(boolean configured, Boolean presetOverride, boolean general) {
        return configured && (presetOverride != null ? presetOverride : general);
    }

    /** Starts the scan of all libraries (does not wait for it to finish on the server). */
    public void refreshLibraries() throws IOException, InterruptedException {
        try {
            WebRequest.post(baseUrl + "/Library/Refresh", headers());
        } catch (IOException e) {
            throw explain(e);
        }
    }

    /** Checks the connection and returns "Server name (version)". */
    public String systemInfo() throws IOException, InterruptedException {
        Object info;
        try {
            info = WebRequest.getJson(baseUrl + "/System/Info", headers(), null);
        } catch (IOException e) {
            throw explain(e);
        }
        String name = Json.string(info, "ServerName");
        String version = Json.string(info, "Version");
        return (name == null ? "Jellyfin" : name) + (version == null ? "" : " (" + version + ")");
    }

    /**
     * Page of the Jellyfin web interface where API keys are created, for the "Get" button. The route depends on the
     * version, read from {@code GET /System/Info/Public} (no authentication needed); if the server does not answer,
     * the web interface root. Call outside the Swing thread.
     */
    public static String apiKeysPage(String url) throws InterruptedException {
        String base = normalize(url);
        if (base == null) {
            throw new IllegalArgumentException(tr("error.jellyfinConfig"));
        }
        String version = null;
        try {
            version = Json.string(WebRequest.getJson(base + "/System/Info/Public", Map.of("Accept", "application/json"), null), "Version");
        } catch (IOException e) {
            com.mediarenamer.app.Logging.debug("Jellyfin version: " + e.getMessage());
        }
        return apiKeysPage(base, version);
    }

    /**
     * API keys page for a server version (from the Jellyfin web client routes): since 10.9 the dashboard
     * is a React app with {@code #/dashboard/keys}; before, the legacy page {@code #!/apikeys.html}. Unknown version:
     * the web interface root.
     */
    static String apiKeysPage(String base, String version) {
        if (version == null || !version.matches("\\d+\\.\\d+.*")) {
            return base + "/web/";
        }
        String[] v = version.split("\\.");
        int major = Integer.parseInt(v[0]);
        int minor = Integer.parseInt(v[1].replaceAll("\\D.*", ""));
        boolean modern = major > 10 || (major == 10 && minor >= 9);
        return base + (modern ? "/web/#/dashboard/keys" : "/web/index.html#!/apikeys.html");
    }

    /** URL as it will be used (scheme added, trailing slashes removed), or null if empty. */
    public static String normalizeUrl(String url) {
        return normalize(url);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", "MediaBrowser Token=\"" + apiKey + "\"", "Accept", "application/json");
    }

    /**
     * User-friendly message. HttpClient connection exceptions usually come without a
     * message (ConnectException with getMessage() == null), and without this the warning was empty.
     */
    IOException explain(IOException e) {
        String msg;
        if (e instanceof WebRequest.HttpException h && (h.getStatus() == 401 || h.getStatus() == 403)) {
            msg = tr("jellyfin.error.key", h.getStatus());
        } else if (e instanceof WebRequest.HttpException h) {
            msg = tr("jellyfin.error.http", h.getStatus(), baseUrl);
        } else if (e instanceof java.net.http.HttpTimeoutException) {
            msg = tr("jellyfin.error.timeout", baseUrl);
        } else if (e instanceof java.net.ConnectException || e.getMessage() == null) {
            msg = tr("jellyfin.error.connect", baseUrl);
        } else {
            msg = tr("jellyfin.error.other", baseUrl, e.getMessage());
        }
        return new IOException(msg, e);
    }

    /**
     * true if the URL is plain http to a host that does not look local: the API key would travel unencrypted over
     * the internet. Local means localhost, loopback, private or link-local IPv4 addresses, IPv6 loopback/ULA/link-local,
     * names without a dot (LAN names) and the .local, .lan, .home, .home.arpa and .internal domains. No DNS lookup.
     */
    public static boolean isInsecureRemote(String url) {
        String u = normalize(url);
        if (u == null || !u.regionMatches(true, 0, "http://", 0, 7)) {
            return false;
        }
        String host;
        try {
            host = java.net.URI.create(u).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(java.util.Locale.ROOT).replaceAll("^\\[|\\]$", "");
        if (host.equals("localhost") || host.endsWith(".localhost") || !host.contains(".") && !host.contains(":")) {
            return false;
        }
        if (host.matches(".*\\.(local|lan|home|home\\.arpa|internal)$")) {
            return false;
        }
        java.util.regex.Matcher ip = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)\\.\\d+\\.\\d+").matcher(host);
        if (ip.matches()) {
            int a = Integer.parseInt(ip.group(1));
            int b = Integer.parseInt(ip.group(2));
            return !(a == 10 || a == 127 || a == 192 && b == 168 || a == 172 && b >= 16 && b <= 31 || a == 169 && b == 254
                    || a == 100 && b >= 64 && b <= 127);
        }
        if (host.contains(":")) {
            return !(host.equals("::1") || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80"));
        }
        return true;
    }

    /** Removes spaces and trailing slashes; without a scheme http:// is assumed (usual on a local network). */
    static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.trim().replaceAll("/+$", "");
        if (!u.matches("(?i)https?://.*")) {
            u = "http://" + u;
        }
        return u;
    }
}
