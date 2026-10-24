package com.mediarenamer.web;

import com.mediarenamer.app.Settings;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Common TheMovieDB core (api.themoviedb.org/3).
 *
 * Accepts both the "API Key (v3)" and the "API Read Access Token (v4)": if the key starts with
 * "eyJ" (JWT) it is sent as Bearer, otherwise as the api_key parameter.
 */
public class TMDbClient {

    static final String HOST = "https://api.themoviedb.org/3/";
    static final Duration CACHE = Duration.ofDays(7);
    private static final FloodLimit LIMIT = new FloodLimit(40, 10_000);

    Object request(String resource, Map<String, Object> params, Locale locale, Duration ttl) throws Exception {
        String key = Settings.get().requireKey(Settings.TMDB_KEY, "TheMovieDB");
        Map<String, Object> p = new LinkedHashMap<>(params);
        if (locale != null) {
            p.put("language", languageCode(locale));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (key.startsWith("eyJ")) {
            headers.put("Authorization", "Bearer " + key);
        } else {
            p.put("api_key", key);
        }
        String url = HOST + resource + (p.isEmpty() ? "" : "?" + WebRequest.query(p));
        LIMIT.acquire();
        return WebRequest.getJson(url, headers, ttl);
    }

    /** TMDb uses "es-ES"; the old codes he/iw and in/id are mapped. */
    static String languageCode(Locale locale) {
        String lang = locale.getLanguage();
        if (lang.equals("iw")) {
            lang = "he";
        } else if (lang.equals("in")) {
            lang = "id";
        }
        return locale.getCountry().isEmpty() ? lang : lang + "-" + locale.getCountry();
    }

    static String imageUrl(String path) {
        return path == null ? null : "https://image.tmdb.org/t/p/original" + path;
    }

    /** Poster in size w342 (enough for the list and the details card). */
    static String posterUrl(String path) {
        return path == null ? null : "https://image.tmdb.org/t/p/w342" + path;
    }
}
