package com.mediarenamer.app;

import com.mediarenamer.web.WebRequest;
import groovy.json.JsonSlurper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.LongConsumer;

/**
 * Update check against the latest GitHub release: release model, version rules and the decisions
 * (is it due, is it worth offering). No UI and no download here.
 */
public final class Updates {

    /** GitHub API endpoint of the latest published (non-draft, non-prerelease) release. */
    static final String API_URL = "https://api.github.com/repos/AeLui/MediaRenamer/releases/latest";
    /** Minimum time between two automatic checks. */
    static final Duration AUTO_CHECK_INTERVAL = Duration.ofHours(24);
    /** Prefix of the temp folders that hold downloaded installers. */
    static final String TEMP_PREFIX = "MediaRenamer-update";
    /** Age after which a leftover installer folder is swept at start-up. */
    static final Duration STALE_AFTER = Duration.ofHours(1);

    /** Hosts a release asset may be downloaded from (https only), checked on the request and on the final URI. */
    private static final Set<String> DOWNLOAD_HOSTS =
            Set.of("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com");
    private static final String USER_AGENT = Settings.APP_NAME + "/" + Settings.version();

    private Updates() {
    }

    /** How this copy was installed; only an installed copy can update itself. */
    public enum Mode { INSTALLED, PORTABLE, DEVELOPMENT }

    /** The download was cancelled by the user. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("Download cancelled");
        }
    }

    /** INSTALLED with the Inno Setup copy, PORTABLE for the jpackage app-image, DEVELOPMENT otherwise. */
    public static Mode mode() {
        if (ContextMenu.installedExe() != null) {
            return Mode.INSTALLED;
        }
        return System.getProperty("jpackage.app-path") != null ? Mode.PORTABLE : Mode.DEVELOPMENT;
    }

    /** True for https URIs on the GitHub download hosts. */
    static boolean isAllowed(URI uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                && DOWNLOAD_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT));
    }

    /**
     * Runs the installer silently; it restarts MediaRenamer when done. Fire-and-forget on purpose: there is no timeout
     * because the application exits right after and the installer outlives it.
     */
    public static void launchInstaller(Path setup) throws IOException {
        new ProcessBuilder(setup.toString(), "/SILENT", "/NORESTART", "/LAUNCH")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    /** Deletes a downloaded installer and its temp folder (only a folder that {@link #download} created); best effort. */
    public static void discard(Path setup) {
        Path dir = setup == null ? null : setup.getParent();
        if (dir != null && dir.getFileName() != null && dir.getFileName().toString().startsWith(TEMP_PREFIX)) {
            deleteTree(dir);
        }
    }

    /** Deletes the leftovers of earlier updates in the system temp folder; call off the Swing thread. */
    public static void sweepStale() {
        sweepStale(Path.of(System.getProperty("java.io.tmpdir")), Instant.now());
    }

    /** Deletes the {@code MediaRenamer-update*} folders of {@code tmp} not modified for an hour; returns how many. */
    static int sweepStale(Path tmp, Instant now) {
        int deleted = 0;
        try (var stream = Files.newDirectoryStream(tmp, TEMP_PREFIX + "*")) {
            for (Path dir : stream) {
                try {
                    if (Files.isDirectory(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                            && Files.getLastModifiedTime(dir, java.nio.file.LinkOption.NOFOLLOW_LINKS).toInstant()
                                    .isBefore(now.minus(STALE_AFTER))) {
                        deleteTree(dir);
                        if (!Files.exists(dir)) {
                            deleted++;
                        }
                    }
                } catch (IOException e) {
                    // best effort: the next start tries again
                }
            }
        } catch (IOException | RuntimeException e) {
            // no temp folder to read: nothing to sweep
        }
        return deleted;
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) { // does not follow links: a link is deleted, not what it points to
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException | RuntimeException e) {
            // best effort (e.g. the installer is still running)
        }
    }

    /**
     * Downloads {@code a} into a new temp folder and verifies its size and SHA-256; anything unverifiable (no digest,
     * wrong size or digest, disallowed host) is deleted and rejected with an IOException.
     */
    public static Path download(Asset a, LongConsumer progress, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        return download(a, progress, cancelled, Updates::isAllowed);
    }

    static Path download(Asset a, LongConsumer progress, BooleanSupplier cancelled, Predicate<URI> allowed)
            throws IOException, InterruptedException {
        if (a.sha256() == null || a.size() <= 0) {
            throw new IOException("The release gives no size or SHA-256 for " + a.name());
        }
        if (!a.name().matches("MediaRenamer-[A-Za-z0-9._+-]+-setup[.]exe") || a.name().contains("..")) {
            throw new IOException("Unexpected installer file name: " + a.name());
        }
        if (!allowed.test(a.url())) {
            throw new IOException("Download host not allowed: " + a.url());
        }
        Path dir = Files.createTempDirectory(TEMP_PREFIX);
        Path file = dir.resolve(a.name());
        try {
            if (!dir.equals(file.getParent())) {
                throw new IOException("Unexpected installer file name: " + a.name());
            }
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder(a.url()).header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (!allowed.test(response.uri())) {
                    throw new IOException("Download redirected to a host that is not allowed: " + response.uri());
                }
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + " downloading " + a.name());
                }
                MessageDigest md = sha256Digest();
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                try (OutputStream out = Files.newOutputStream(file)) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        total += n;
                        if (total > a.size()) {
                            throw new IOException("The download is larger than announced");
                        }
                        md.update(buf, 0, n);
                        out.write(buf, 0, n);
                        progress.accept(total);
                        if (cancelled.getAsBoolean()) {
                            throw new CancelledException();
                        }
                    }
                }
                if (total != a.size()) {
                    throw new IOException("Incomplete download: " + total + " of " + a.size() + " bytes");
                }
                String hex = java.util.HexFormat.of().formatHex(md.digest());
                if (!hex.equals(a.sha256())) {
                    throw new IOException("SHA-256 mismatch for " + a.name());
                }
            }
            return file;
        } catch (IOException | InterruptedException | RuntimeException e) {
            try {
                Files.deleteIfExists(file);
                Files.deleteIfExists(dir);
            } catch (IOException ignored) {
                // best effort
            }
            throw e;
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A downloadable file of a release; {@code sha256} is lower-case hex or null when GitHub gave no digest. */
    public record Asset(String name, URI url, long size, String sha256) {
    }

    /** A published release; {@code version} has no leading "v". */
    public record Release(String version, String notes, URI page, List<Asset> assets) {

        /** The Windows installer ({@code MediaRenamer-<version>-setup.exe}), if the release has one. */
        public Optional<Asset> installer() {
            String expected = "MediaRenamer-" + version + "-setup.exe";
            return assets.stream().filter(a -> a.name().equals(expected)).findFirst();
        }
    }

    /** Fetches the latest release (no cache). The URL can be overridden with {@code -Dmediarenamer.updates.url}. */
    public static Release latest() throws IOException, InterruptedException {
        String url = System.getProperty("mediarenamer.updates.url", API_URL);
        byte[] json = WebRequest.get(url, Map.of("Accept", "application/vnd.github+json"), null, Duration.ofSeconds(15));
        return parse(json);
    }

    /** Parses a GitHub release; drafts and prereleases are rejected. */
    static Release parse(byte[] json) throws IOException {
        Object root;
        try {
            root = new JsonSlurper().parse(json, "UTF-8");
        } catch (RuntimeException e) {
            throw new IOException("Invalid release JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> m)) {
            throw new IOException("Unexpected release JSON");
        }
        if (Boolean.TRUE.equals(m.get("draft")) || Boolean.TRUE.equals(m.get("prerelease"))) {
            throw new IOException("The release is a draft or a prerelease");
        }
        String tag = str(m.get("tag_name"));
        if (tag == null || tag.isBlank()) {
            throw new IOException("Release without tag_name");
        }
        String version = tag.strip();
        if (version.startsWith("v") || version.startsWith("V")) {
            version = version.substring(1);
        }
        String page = str(m.get("html_url"));
        String notes = str(m.get("body"));
        try {
            List<Asset> assets = new ArrayList<>();
            if (m.get("assets") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> a && str(a.get("name")) != null && str(a.get("browser_download_url")) != null) {
                        long size = a.get("size") instanceof Number n ? n.longValue() : -1;
                        assets.add(new Asset(str(a.get("name")), URI.create(str(a.get("browser_download_url"))), size,
                                sha256(str(a.get("digest")))));
                    }
                }
            }
            return new Release(version, notes == null ? "" : notes, page == null ? null : URI.create(page), List.copyOf(assets));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL in release: " + e.getMessage(), e);
        }
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** "sha256:ABC..." -> "abc..."; null for a missing or different digest. */
    private static String sha256(String digest) {
        if (digest == null || !digest.toLowerCase(Locale.ROOT).startsWith("sha256:")) {
            return null;
        }
        String hex = digest.substring("sha256:".length()).strip().toLowerCase(Locale.ROOT);
        return hex.matches("[0-9a-f]{64}") ? hex : null;
    }

    /**
     * Compares two versions numerically ("2.10.0" > "2.9.0", "2.8" == "2.8.0"); a suffix after "-" ranks below
     * the same numbers without one. Unparseable parts count as 0.
     */
    public static int compare(String a, String b) {
        int[] na = numbers(a);
        int[] nb = numbers(b);
        for (int i = 0; i < Math.max(na.length, nb.length); i++) {
            int x = i < na.length ? na[i] : 0;
            int y = i < nb.length ? nb[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return Boolean.compare(!hasSuffix(a), !hasSuffix(b));
    }

    /** True when {@code candidate} is newer than {@code current}; false if {@code current} is "dev" or either is unparseable. */
    public static boolean isNewer(String candidate, String current) {
        return parseable(candidate) && parseable(current) && compare(candidate, current) > 0;
    }

    /**
     * True when the automatic check is enabled and the last one was at least 24 hours ago (0 = never); a value in the
     * future or not a valid date also counts as due.
     */
    public static boolean dueForAutoCheck(boolean enabled, long lastCheckMillis, Instant now) {
        if (!enabled) {
            return false;
        }
        // a value from the future (clock moved back, bad value) or one too large to be a date must not block the check
        long nowMillis = now.toEpochMilli();
        return lastCheckMillis <= 0 || lastCheckMillis > nowMillis || nowMillis - lastCheckMillis >= AUTO_CHECK_INTERVAL.toMillis();
    }

    /** Whether to show {@code r} to the user; a manual check ignores the skipped version. */
    public static boolean offer(Release r, String current, String skipped, boolean manual) {
        if (!isNewer(r.version(), current)) {
            return false;
        }
        return manual || skipped == null || !r.version().equals(skipped.strip().replaceFirst("^[vV]", ""));
    }

    private static String core(String v) {
        String s = v == null ? "" : v.strip().replaceFirst("^[vV]", "");
        int dash = s.indexOf('-');
        return dash >= 0 ? s.substring(0, dash) : s;
    }

    private static boolean hasSuffix(String v) {
        return v != null && v.indexOf('-') >= 0;
    }

    private static boolean parseable(String v) {
        return v != null && core(v).matches("[0-9]+(\\.[0-9]+)*");
    }

    private static int[] numbers(String v) {
        String[] parts = core(v).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
