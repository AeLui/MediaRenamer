package com.mediarenamer.web;

import static com.mediarenamer.app.I18n.tr;

import java.io.IOException;

/**
 * What the media server clients ({@link JellyfinClient}, {@link PlexClient}) share: URL normalization, the plain-http
 * warning, readable error messages and the precedence of the profile option over the general one.
 */
public final class MediaServers {

    private MediaServers() {
    }

    /** Removes spaces and trailing slashes; without a scheme http:// is assumed (usual on a local network). */
    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.trim().replaceAll("/+$", "");
        if (!u.matches("(?i)https?://.*")) {
            u = "http://" + u;
        }
        return u;
    }

    /**
     * true if a scan is needed after renaming: only with the server configured; the option of the active profile
     * (null = it does not set it) takes precedence over the general preference.
     */
    public static boolean shouldScan(boolean configured, Boolean presetOverride, boolean general) {
        return configured && (presetOverride != null ? presetOverride : general);
    }

    /**
     * true if the URL is plain http to a host that does not look local: the key would travel unencrypted over
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

    /**
     * User-friendly message for a failed request to a media server; prefix selects the texts ("jellyfin", "plex").
     * HttpClient connection exceptions usually come without a message (ConnectException with getMessage() == null),
     * and without this the warning was empty.
     */
    static IOException explain(IOException e, String baseUrl, String prefix) {
        String msg;
        if (e instanceof WebRequest.HttpException h && (h.getStatus() == 401 || h.getStatus() == 403)) {
            msg = tr(prefix + ".error.key", h.getStatus());
        } else if (e instanceof WebRequest.HttpException h) {
            msg = tr(prefix + ".error.http", h.getStatus(), baseUrl);
        } else if (e instanceof java.net.http.HttpTimeoutException) {
            msg = tr(prefix + ".error.timeout", baseUrl);
        } else if (e instanceof java.net.ConnectException || e.getMessage() == null) {
            msg = tr(prefix + ".error.connect", baseUrl);
        } else {
            msg = tr(prefix + ".error.other", baseUrl, e.getMessage());
        }
        return new IOException(msg, e);
    }
}
