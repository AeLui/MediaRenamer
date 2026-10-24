package com.mediarenamer.web;

import com.mediarenamer.app.Settings;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TheTVDB API v4 (api4.thetvdb.com/v4/). Endpoints:
 * login, search, series/{id}/extended, series/{id}/episodes/{season-type}/{lang}?page=N,
 * series/{id}/translations/{lang}.
 */
public class TheTVDBClient implements EpisodeListProvider {

    private static final String HOST = "https://api4.thetvdb.com/v4/";
    private static final Duration CACHE = Duration.ofDays(7);
    private static final Duration TOKEN_TTL = Duration.ofDays(25);

    private String token;
    private long tokenTime;

    @Override
    public String getIdentifier() {
        return "TheTVDB";
    }

    @Override
    public boolean hasSeasonSupport() {
        return true;
    }

    @Override
    public List<SortOrder> getSortOrders() {
        return List.of(SortOrder.Airdate, SortOrder.DVD, SortOrder.Absolute, SortOrder.Official, SortOrder.Digital, SortOrder.Production);
    }

    private synchronized String token() throws Exception {
        if (token == null || System.currentTimeMillis() - tokenTime > TOKEN_TTL.toMillis()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("apikey", Settings.get().requireKey(Settings.TVDB_KEY, "TheTVDB"));
            String pin = Settings.get().get(Settings.TVDB_PIN);
            if (pin != null) {
                body.put("pin", pin);
            }
            byte[] res = WebRequest.postJson(HOST + "login", JsonOutput.toJson(body), null);
            token = Json.string(Json.map(new JsonSlurper().parse(res, "UTF-8"), "data"), "token");
            tokenTime = System.currentTimeMillis();
        }
        return token;
    }

    private Object request(String resource) throws Exception {
        Map<String, String> headers = Map.of("Authorization", "Bearer " + token(), "Accept", "application/json");
        return WebRequest.getJson(HOST + resource, headers, CACHE);
    }

    /** TheTVDB uses 3-letter ISO 639-2 ("spa", "eng"), with the exceptions pt / zhtw. */
    static String languageCode(Locale locale) {
        if (locale.getLanguage().equals("pt") && locale.getCountry().equals("BR")) {
            return "pt";
        }
        if (locale.getLanguage().equals("pt")) {
            return "por";
        }
        if (locale.getLanguage().equals("zh") && locale.getCountry().equals("TW")) {
            return "zhtw";
        }
        try {
            return locale.getISO3Language();
        } catch (Exception e) {
            return "eng";
        }
    }

    @Override
    public List<SearchResult> search(String query, Locale locale) throws Exception {
        Object json = request("search?" + WebRequest.query(WebRequest.params("query", query, "type", "series")));
        String lang = languageCode(locale);
        List<SearchResult> result = new ArrayList<>();
        for (Map<String, Object> m : Json.maps(json, "data")) {
            Integer id = Json.integer(m, "tvdb_id");
            if (id == null) {
                continue;
            }
            String name = Json.string(Json.map(m, "translations"), lang);
            List<String> aliases = new ArrayList<>();
            String primary = Json.string(m, "name");
            if (name == null) {
                name = primary;
            } else if (primary != null && !primary.equals(name)) {
                aliases.add(primary);
            }
            for (Object a : Json.list(m, "aliases")) {
                aliases.add(a.toString());
            }
            String overview = Json.string(Json.map(m, "overviews"), lang);
            result.add(new Series(id, name, aliases, Json.integer(m, "year"), getIdentifier())
                    .withDetails(Json.string(m, "image_url") != null ? Json.string(m, "image_url") : Json.string(m, "thumbnail"),
                            overview != null ? overview : Json.string(m, "overview"), primary));
        }
        return result;
    }

    @Override
    public SearchResult getSeries(int id, Locale locale) throws Exception {
        SeriesInfo info = getSeriesInfo(new SearchResult(id, null, null), locale);
        return new Series(id, info.getName(), info.getAliasNames(), info.getStartDate() == null ? null : info.getStartDate().getYear(), getIdentifier());
    }

    @Override
    public SeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception {
        return seriesInfo(series.getId(), SortOrder.Airdate, locale);
    }

    private SeriesInfo seriesInfo(int id, SortOrder order, Locale locale) throws Exception {
        Object data = Json.map(request("series/" + id + "/extended?meta=translations&short=true"), "data");
        SeriesInfo info = new SeriesInfo(getIdentifier(), order, locale, id);
        String lang = languageCode(locale);
        String name = null;
        for (Map<String, Object> t : Json.maps(Json.map(data, "translations"), "nameTranslations")) {
            if (lang.equals(Json.string(t, "language"))) {
                name = Json.string(t, "name");
            }
        }
        List<String> aliases = new ArrayList<>();
        String primary = Json.string(data, "name");
        if (name == null) {
            name = primary;
        } else if (primary != null && !primary.equals(name)) {
            aliases.add(primary);
        }
        for (Map<String, Object> a : Json.maps(data, "aliases")) {
            aliases.add(Json.string(a, "name"));
        }
        info.setName(name);
        info.setOriginalName(primary);
        info.setAliasNames(aliases.stream().filter(a -> a != null).distinct().toList());
        info.setStartDate(SimpleDate.parse(Json.string(data, "firstAired")));
        info.setGenres(Json.strings(data, "genres", "name"));
        info.setNetwork(Json.string(Json.map(data, "originalNetwork"), "name"));
        info.setRuntime(Json.integer(data, "averageRuntime"));
        info.setStatus(Json.string(Json.map(data, "status"), "name"));
        info.setOriginalLanguage(Json.string(data, "originalLanguage"));
        info.setCountry(Json.string(data, "originalCountry"));
        info.setOverview(Json.string(data, "overview"));
        info.getExternalIds().put("tvdb", String.valueOf(id));
        for (Map<String, Object> r : Json.maps(data, "remoteIds")) {
            String source = String.valueOf(Json.string(r, "sourceName")).toLowerCase();
            if (source.contains("imdb")) {
                info.getExternalIds().put("imdb", Json.string(r, "id"));
            } else if (source.contains("themoviedb")) {
                info.getExternalIds().put("tmdb", Json.string(r, "id"));
            }
        }
        return info;
    }

    @Override
    public List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) throws Exception {
        SeriesInfo info = seriesInfo(series.getId(), order, locale);
        String seasonType = switch (order) {
            case DVD -> "dvd";
            case Absolute -> "absolute";
            case Digital -> "alternate";
            case Production -> "regional";
            default -> "default";
        };

        List<Map<String, Object>> raw = pages(series.getId(), seasonType, languageCode(locale));
        // If there is no translation of the titles, the original titles are used.
        if (raw.isEmpty()) {
            raw = pages(series.getId(), seasonType, null);
        }

        List<Episode> episodes = new ArrayList<>();
        List<Episode> specials = new ArrayList<>();
        for (Map<String, Object> e : raw) {
            Integer season = Json.integer(e, "seasonNumber");
            Integer number = Json.integer(e, "number");
            Integer absolute = Json.integer(e, "absoluteNumber");
            String title = Json.string(e, "name");
            SimpleDate airdate = SimpleDate.parse(Json.string(e, "aired"));
            Integer id = Json.integer(e, "id");
            if (season != null && season == 0) {
                specials.add(new Episode(info.getName(), null, null, title, null, number, airdate, id, info));
            } else if (order == SortOrder.Absolute) {
                Integer abs = absolute != null && absolute > 0 ? absolute : number;
                episodes.add(new Episode(info.getName(), null, abs, title, abs, null, airdate, id, info));
            } else {
                episodes.add(new Episode(info.getName(), season, number, title, absolute != null && absolute > 0 ? absolute : null, null, airdate, id, info));
            }
        }
        episodes.addAll(specials);
        return episodes;
    }

    private List<Map<String, Object>> pages(int id, String seasonType, String lang) throws Exception {
        List<Map<String, Object>> all = new ArrayList<>();
        for (int page = 0; page < 100; page++) {
            String path = "series/" + id + "/episodes/" + seasonType + (lang == null ? "" : "/" + lang) + "?page=" + page;
            Object json;
            try {
                json = request(path);
            } catch (WebRequest.HttpException e) {
                if (e.getStatus() == 404 && lang != null) {
                    return List.of();
                }
                throw e;
            }
            Map<String, Object> data = Json.map(json, "data");
            all.addAll(Json.maps(data, "episodes"));
            if (Json.string(Json.map(json, "links"), "next") == null) {
                break;
            }
        }
        return all;
    }
}
