package com.mediarenamer.web;

import com.mediarenamer.app.AppFolders;
import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import groovy.json.JsonSlurper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;

/**
 * HTTP client with disk cache. Each response is stored in %APPDATA%/MediaRenamer/cache with a per-resource expiry.
 */
public final class WebRequest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final String USER_AGENT = Settings.APP_NAME + "/" + Settings.version();

    /**
     * Largest body accepted, compressed or not (the biggest real resource, anime-list.xml, is a few MB): a hostile
     * or intercepted server cannot exhaust the memory with a huge answer or a gzip bomb.
     */
    static final int MAX_BODY = 64 * 1024 * 1024;
    /** Cached responses older than this are removed by {@link #purgeCache()} (the longest TTL is 30 days). */
    static final Duration CACHE_MAX_AGE = Duration.ofDays(31);

    private WebRequest() {
    }

    /** Builds an encoded query string (null values are omitted). */
    public static String query(Map<String, ?> params) {
        StringJoiner sj = new StringJoiner("&");
        params.forEach((k, v) -> {
            if (v != null) {
                sj.add(encode(k) + "=" + encode(v.toString()));
            }
        });
        return sj.toString();
    }

    public static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i].toString(), kv[i + 1]);
        }
        return m;
    }

    public static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** GET with cache. ttl == null disables the cache. */
    public static byte[] get(String url, Map<String, String> headers, Duration ttl) throws IOException, InterruptedException {
        Path cacheFile = ttl == null ? null : cacheFile(url, headers);
        if (cacheFile != null && Files.exists(cacheFile)) {
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            if (age < ttl.toMillis()) {
                Logging.debug("Cache " + url);
                return Files.readAllBytes(cacheFile);
            }
        }

        Logging.debug("GET " + redact(url));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Encoding", "gzip");
        if (headers != null) {
            headers.forEach(b::header);
        }
        Response res = send(b.GET().build());
        byte[] body = decode(res);
        if (res.statusCode() == 404) {
            throw new HttpException(404, "Not Found: " + redact(url));
        }
        if (res.statusCode() >= 400) {
            throw new HttpException(res.statusCode(), "HTTP " + res.statusCode() + ": " + redact(url) + "\n" + preview(body));
        }
        if (cacheFile != null) {
            Files.createDirectories(cacheFile.getParent());
            Files.write(cacheFile, body);
        }
        return body;
    }

    /** JSON POST without cache (TheTVDB login). */
    public static byte[] postJson(String url, String json, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
        if (headers != null) {
            headers.forEach(b::header);
        }
        Response res = send(b.POST(HttpRequest.BodyPublishers.ofString(json)).build());
        byte[] body = decode(res);
        if (res.statusCode() >= 400) {
            throw new HttpException(res.statusCode(), "HTTP " + res.statusCode() + ": " + url + "\n" + preview(body));
        }
        return body;
    }

    /** POST without body and without cache (commands to media servers, e.g. the Jellyfin scan). */
    public static byte[] post(String url, Map<String, String> headers) throws IOException, InterruptedException {
        Logging.debug("POST " + redact(url));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT);
        if (headers != null) {
            headers.forEach(b::header);
        }
        Response res = send(b.POST(HttpRequest.BodyPublishers.noBody()).build());
        byte[] body = decode(res);
        if (res.statusCode() >= 400) {
            throw new HttpException(res.statusCode(), "HTTP " + res.statusCode() + ": " + redact(url) + "\n" + preview(body));
        }
        return body;
    }

    public static Object getJson(String url, Map<String, String> headers, Duration ttl) throws IOException, InterruptedException {
        byte[] data = get(url, headers, ttl);
        return new JsonSlurper().parse(data, "UTF-8");
    }

    public static Document getXml(String url, Map<String, String> headers, Duration ttl) throws IOException, InterruptedException {
        return parseXml(get(url, headers, ttl));
    }

    /**
     * Parses XML without any external access (XXE): no external entities, DTDs or schemas, and secure processing
     * limits for internal entities. The AniDB API is plain HTTP, so its responses must be treated as hostile.
     * A DOCTYPE is still accepted (only its internal subset is used).
     */
    public static Document parseXml(byte[] data) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
            // any entity that still reaches the resolver (e.g. a future parser default) resolves to nothing
            builder.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            return builder.parse(new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new IOException(com.mediarenamer.app.I18n.tr("error.xml", e.getMessage()), e);
        }
    }

    public static byte[] gunzip(byte[] data) throws IOException {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return readLimited(in, MAX_BODY);
        }
    }

    /** Status, headers and body (at most {@link #MAX_BODY} bytes) of a response. */
    private record Response(int statusCode, java.net.http.HttpHeaders headers, byte[] body) {
    }

    /** Sends the request reading at most {@link #MAX_BODY} bytes of the body (read in the calling thread). */
    private static Response send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<InputStream> res = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = res.body()) {
            return new Response(res.statusCode(), res.headers(), readLimited(in, MAX_BODY));
        }
    }

    /** Reads the whole stream, failing if it is longer than max bytes. */
    static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) {
                throw new IOException("Response larger than " + (max >> 20) + " MB");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static byte[] decode(Response res) throws IOException {
        byte[] body = res.body();
        String enc = res.headers().firstValue("Content-Encoding").orElse("");
        if (enc.equalsIgnoreCase("gzip") || (body.length > 2 && (body[0] & 0xff) == 0x1f && (body[1] & 0xff) == 0x8b)) {
            return gunzip(body);
        }
        return body;
    }

    /**
     * Removes the cached responses older than {@link #CACHE_MAX_AGE} (no resource is cached longer) and the empty
     * folders. Returns the number of files removed; errors are only logged.
     */
    public static int purgeCache() {
        return purgeCache(AppFolders.cache(), CACHE_MAX_AGE);
    }

    static int purgeCache(Path cache, Duration maxAge) {
        if (!Files.isDirectory(cache)) {
            return 0;
        }
        long limit = System.currentTimeMillis() - maxAge.toMillis();
        int removed = 0;
        try (java.util.stream.Stream<Path> all = Files.walk(cache)) {
            for (Path p : (Iterable<Path>) all.sorted(java.util.Comparator.reverseOrder())::iterator) {
                try {
                    if (Files.isRegularFile(p) && Files.getLastModifiedTime(p).toMillis() < limit) {
                        Files.delete(p);
                        removed++;
                    } else if (Files.isDirectory(p) && !p.equals(cache)) {
                        try (java.util.stream.Stream<Path> children = Files.list(p)) {
                            if (children.findAny().isEmpty()) {
                                Files.delete(p);
                            }
                        }
                    }
                } catch (IOException e) {
                    Logging.debug("Cache purge " + p + ": " + e.getMessage());
                }
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            Logging.debug("Cache purge: " + e.getMessage());
        }
        return removed;
    }

    private static Path cacheFile(String url, Map<String, String> headers) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(url.getBytes(StandardCharsets.UTF_8));
            if (headers != null) {
                // Language headers are part of the key; Authorization is not (the token changes).
                headers.forEach((k, v) -> {
                    if (!k.equalsIgnoreCase("Authorization")) {
                        md.update((k + "=" + v).getBytes(StandardCharsets.UTF_8));
                    }
                });
            }
            String hash = HexFormat.of().formatHex(md.digest());
            return AppFolders.cache().resolve(hash.substring(0, 2)).resolve(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Hides API keys in the logs. */
    static String redact(String url) {
        return url.replaceAll("(?i)(api_?key|apikey|client)=[^&]+", "$1=***");
    }

    private static String preview(byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8);
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** HTTP error with a status code. */
    public static class HttpException extends IOException {
        private final int status;

        public HttpException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
