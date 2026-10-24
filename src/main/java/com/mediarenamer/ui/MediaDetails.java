package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.I18n;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.EpisodeListProvider;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MovieInfo;
import com.mediarenamer.web.MovieLookupService;
import com.mediarenamer.web.SearchResult;
import com.mediarenamer.web.SeriesInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Details of a search result for the card shown on mouse hover:
 * original title, year, director/creators, genres, runtime, rating, cast, overview...
 * Loaded on demand (one extra request to the service) and cached (per data language and interface language).
 */
public record MediaDetails(String title, String originalTitle, String year, String poster, Map<String, String> facts, String overview) {

    private static final Map<String, MediaDetails> CACHE = new ConcurrentHashMap<>();

    /** Immediate data (the one returned by the search), without extra requests. */
    public static MediaDetails basic(SearchResult r) {
        String year = r instanceof Movie m && m.getYear() > 0 ? String.valueOf(m.getYear())
                : r instanceof com.mediarenamer.web.Series s && s.getYear() != null ? String.valueOf(s.getYear()) : null;
        return new MediaDetails(r.getName(), r.getOriginalName(), year, r.getPoster(), new LinkedHashMap<>(), r.getOverview());
    }

    /** Full details; call outside the Swing thread. */
    public static MediaDetails load(SearchResult r, Datasource db, Locale locale) {
        String key = (db == null ? "" : db.getIdentifier()) + ":" + r.getId() + ":" + locale + ":" + I18n.locale();
        MediaDetails cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        MediaDetails base = basic(r);
        Map<String, String> facts = new LinkedHashMap<>();
        String original = base.originalTitle();
        String overview = base.overview();
        try {
            if (db instanceof MovieLookupService ms && r instanceof Movie m) {
                MovieInfo i = ms.getMovieInfo(m, locale);
                if (i != null) {
                    if (i.originalName != null && !i.originalName.equals(r.getName())) {
                        original = i.originalName;
                    }
                    put(facts, tr(i.directors.size() > 1 ? "details.directors" : "details.director"), join(i.directors, 2));
                    put(facts, tr("details.genre"), join(i.genres, 3));
                    put(facts, tr("details.runtime"), i.runtime == null || i.runtime == 0 ? null : duration(i.runtime));
                    put(facts, tr("details.rating"), rating(i.rating, i.votes));
                    put(facts, tr("details.certification"), i.certification);
                    put(facts, tr("details.collection"), i.collection);
                    put(facts, tr("details.cast"), join(i.actors, 4));
                    put(facts, tr("details.country"), join(i.countries, 3));
                    put(facts, tr("details.released"), i.released == null ? null : i.released.format("d MMM yyyy", locale));
                    if (i.overview != null) {
                        overview = i.overview;
                    }
                }
            } else if (db instanceof EpisodeListProvider p) {
                SeriesInfo s = p.getSeriesInfo(r, locale);
                if (s != null) {
                    String orig = s.getOriginalName();
                    if (original == null && orig != null && !orig.equals(r.getName())) {
                        original = orig;
                    }
                    put(facts, tr("details.creators"), join(s.getCreators(), 3));
                    put(facts, tr("details.network"), s.getNetwork());
                    put(facts, tr("details.genre"), join(s.getGenres(), 3));
                    put(facts, tr("details.premiered"), s.getStartDate() == null ? null : s.getStartDate().format("d MMM yyyy", locale));
                    put(facts, tr("details.status"), status(s.getStatus()));
                    put(facts, tr("details.runtime"), s.getRuntime() == null || s.getRuntime() == 0 ? null : tr("details.runtime.episode", s.getRuntime()));
                    put(facts, tr("details.rating"), rating(s.getRating(), s.getRatingCount()));
                    put(facts, tr("details.certification"), s.getCertification());
                    put(facts, tr("details.language"), s.getOriginalLanguage() == null ? null : language(s.getOriginalLanguage(), I18n.locale()));
                    if (s.getOverview() != null && !s.getOverview().isBlank()) {
                        overview = s.getOverview();
                    }
                }
            }
        } catch (Exception e) {
            put(facts, tr("details.warning"), tr("details.loadFailed", e.getMessage()));
            return new MediaDetails(base.title(), original, base.year(), base.poster(), facts, overview);
        }
        put(facts, tr("details.source"), db == null ? null : db.getName() + " #" + r.getId());
        MediaDetails d = new MediaDetails(base.title(), original, base.year(), base.poster(), facts, overview);
        CACHE.put(key, d);
        return d;
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) {
            m.put(k, v);
        }
    }

    private static String join(List<String> l, int max) {
        return l == null || l.isEmpty() ? null : String.join(", ", l.subList(0, Math.min(max, l.size())));
    }

    private static String duration(int minutes) {
        return minutes >= 60 ? minutes / 60 + " h " + minutes % 60 + " min" : minutes + " min";
    }

    private static String rating(Double r, Integer votes) {
        if (r == null || r == 0) {
            return null;
        }
        String v = votes == null || votes == 0 ? ""
                : "  (" + tr("details.votes", votes >= 1000 ? String.format(Locale.ROOT, "%.1fk", votes / 1000.0) : String.valueOf(votes)) + ")";
        return String.format(Locale.ROOT, "★ %.1f", r) + v;
    }

    private static String status(String s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "Ended" -> tr("details.status.ended");
            case "Returning Series", "Continuing", "Running" -> tr("details.status.running");
            case "Canceled", "Cancelled" -> tr("details.status.canceled");
            case "In Production" -> tr("details.status.production");
            default -> s;
        };
    }

    private static String language(String code, Locale locale) {
        String name = Locale.forLanguageTag(code).getDisplayLanguage(locale);
        return name.isBlank() ? code : name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }
}
