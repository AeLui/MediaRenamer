package com.mediarenamer.web;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TheMovieDB: series (identifier "TheMovieDB::TV").
 *
 * The alternative orders (DVD, Digital, Story, Production, Absolute) come from the
 * TMDb "episode groups" (tv/{id}/episode_groups).
 */
public class TMDbTVClient extends TMDbClient implements EpisodeListProvider {

    @Override
    public String getIdentifier() {
        return "TheMovieDB::TV";
    }

    @Override
    public String getName() {
        return "TheMovieDB (TV)";
    }

    @Override
    public boolean hasSeasonSupport() {
        return true;
    }

    @Override
    public List<SortOrder> getSortOrders() {
        return List.of(SortOrder.Airdate, SortOrder.DVD, SortOrder.Absolute, SortOrder.Digital, SortOrder.Story, SortOrder.Production);
    }

    @Override
    public List<SearchResult> search(String query, Locale locale) throws Exception {
        Object json = request("search/tv", WebRequest.params("query", query, "include_adult", "false"), locale, CACHE);
        List<SearchResult> result = new ArrayList<>();
        for (Map<String, Object> m : Json.maps(json, "results")) {
            Integer id = Json.integer(m, "id");
            String name = Json.string(m, "name");
            if (id == null || name == null) {
                continue;
            }
            List<String> aliases = new ArrayList<>();
            String original = Json.string(m, "original_name");
            if (original != null && !original.equals(name)) {
                aliases.add(original);
            }
            SimpleDate d = SimpleDate.parse(Json.string(m, "first_air_date"));
            result.add(new Series(id, name, aliases, d == null ? null : d.getYear(), getIdentifier())
                    .withDetails(posterUrl(Json.string(m, "poster_path")), Json.string(m, "overview"), original));
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
        return seriesInfo(series.getId(), SortOrder.Airdate, locale, data(series.getId(), locale));
    }

    private Object data(int id, Locale locale) throws Exception {
        return request("tv/" + id, WebRequest.params("append_to_response", "alternative_titles,content_ratings,keywords,external_ids"), locale, CACHE);
    }

    private SeriesInfo seriesInfo(int id, SortOrder order, Locale locale, Object json) {
        SeriesInfo info = new SeriesInfo(getIdentifier(), order, locale, id);
        info.setName(Json.string(json, "name"));
        List<String> aliases = new ArrayList<>();
        String original = Json.string(json, "original_name");
        info.setOriginalName(original);
        if (original != null && !original.equals(info.getName())) {
            aliases.add(original);
        }
        aliases.addAll(Json.strings(Json.map(json, "alternative_titles"), "results", "title"));
        info.setAliasNames(aliases.stream().distinct().toList());
        info.setStartDate(SimpleDate.parse(Json.string(json, "first_air_date")));
        info.setGenres(Json.strings(json, "genres", "name"));
        info.setNetwork(Json.strings(json, "networks", "name").stream().findFirst().orElse(null));
        info.setRating(Json.decimal(json, "vote_average"));
        info.setRatingCount(Json.integer(json, "vote_count"));
        List<Object> runtimes = Json.list(json, "episode_run_time");
        if (!runtimes.isEmpty() && runtimes.get(0) instanceof Number n) {
            info.setRuntime(n.intValue());
        }
        info.setStatus(Json.string(json, "status"));
        info.setType(Json.string(json, "type"));
        info.setOriginalLanguage(Json.string(json, "original_language"));
        info.setCountry(Json.list(json, "origin_country").stream().map(Object::toString).findFirst().orElse(null));
        info.setOverview(Json.string(json, "overview"));
        info.setCreators(Json.strings(json, "created_by", "name"));
        for (Map<String, Object> r : Json.maps(Json.map(json, "content_ratings"), "results")) {
            if (locale.getCountry().equalsIgnoreCase(Json.string(r, "iso_3166_1")) || ("US".equals(Json.string(r, "iso_3166_1")) && info.getCertification() == null)) {
                info.setCertification(Json.string(r, "rating"));
            }
        }
        Map<String, Object> ext = Json.map(json, "external_ids");
        info.getExternalIds().put("tmdb", String.valueOf(id));
        putIfPresent(info, "imdb", Json.string(ext, "imdb_id"));
        Integer tvdb = Json.integer(ext, "tvdb_id");
        putIfPresent(info, "tvdb", tvdb == null ? null : tvdb.toString());
        return info;
    }

    private static void putIfPresent(SeriesInfo info, String key, String value) {
        if (value != null) {
            info.getExternalIds().put(key, value);
        }
    }

    @Override
    public List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) throws Exception {
        Object json = data(series.getId(), locale);
        SeriesInfo info = seriesInfo(series.getId(), order, locale, json);

        if (order != SortOrder.Airdate) {
            List<Episode> grouped = episodeGroups(series.getId(), order, locale, info);
            if (grouped != null) {
                return grouped;
            }
        }

        List<Episode> regular = new ArrayList<>();
        List<Episode> specials = new ArrayList<>();
        List<Map<String, Object>> seasons = new ArrayList<>(Json.maps(json, "seasons"));
        seasons.sort(Comparator.comparing(s -> Json.integer(s, "season_number") == null ? 0 : Json.integer(s, "season_number")));
        for (Map<String, Object> s : seasons) {
            Integer sn = Json.integer(s, "season_number");
            if (sn == null) {
                continue;
            }
            Object season = request("tv/" + series.getId() + "/season/" + sn, Map.of(), locale, CACHE);
            for (Map<String, Object> e : Json.maps(season, "episodes")) {
                Integer en = Json.integer(e, "episode_number");
                String title = Json.string(e, "name");
                SimpleDate airdate = SimpleDate.parse(Json.string(e, "air_date"));
                Integer id = Json.integer(e, "id");
                if (sn == 0) {
                    specials.add(new Episode(info.getName(), null, null, title, null, en, airdate, id, info));
                } else {
                    regular.add(new Episode(info.getName(), sn, en, title, regular.size() + 1, null, airdate, id, info));
                }
            }
        }

        List<Episode> episodes = new ArrayList<>();
        if (order == SortOrder.Absolute) {
            // Computed absolute order: no season, episode = absolute number
            for (Episode e : regular) {
                episodes.add(new Episode(e.getSeriesName(), null, e.getAbsolute(), e.getTitle(), e.getAbsolute(), null, e.getAirdate(), e.getId(), info));
            }
        } else if (order == SortOrder.Date) {
            regular.sort(Comparator.comparing(e -> e.getAirdate() == null ? new SimpleDate(9999, 1, 1) : e.getAirdate()));
            episodes.addAll(regular);
        } else {
            episodes.addAll(regular);
        }
        episodes.addAll(specials);
        return episodes;
    }

    /** TMDb episode group types: 1 Original, 2 Absolute, 3 DVD, 4 Digital, 5 Story arc, 6 Production, 7 TV. */
    private List<Episode> episodeGroups(int id, SortOrder order, Locale locale, SeriesInfo info) throws Exception {
        int type = switch (order) {
            case Absolute -> 2;
            case DVD -> 3;
            case Digital -> 4;
            case Story -> 5;
            case Production -> 6;
            default -> -1;
        };
        if (type < 0) {
            return null;
        }
        Object groups = request("tv/" + id + "/episode_groups", Map.of(), locale, CACHE);
        Map<String, Object> best = null;
        for (Map<String, Object> g : Json.maps(groups, "results")) {
            if (Integer.valueOf(type).equals(Json.integer(g, "type"))) {
                if (best == null || Json.integer(g, "episode_count") > Json.integer(best, "episode_count")) {
                    best = g;
                }
            }
        }
        if (best == null) {
            return null;
        }
        Object group = request("tv/episode_group/" + Json.string(best, "id"), Map.of(), locale, CACHE);
        List<Episode> episodes = new ArrayList<>();
        int absolute = 0;
        List<Map<String, Object>> parts = new ArrayList<>(Json.maps(group, "groups"));
        parts.sort(Comparator.comparing(p -> Json.integer(p, "order") == null ? 0 : Json.integer(p, "order")));
        for (Map<String, Object> part : parts) {
            Integer season = Json.integer(part, "order");
            boolean specials = season != null && season == 0 || String.valueOf(Json.string(part, "name")).toLowerCase().contains("special");
            List<Map<String, Object>> eps = new ArrayList<>(Json.maps(part, "episodes"));
            eps.sort(Comparator.comparing(e -> Json.integer(e, "order") == null ? 0 : Json.integer(e, "order")));
            for (Map<String, Object> e : eps) {
                int n = (Json.integer(e, "order") == null ? 0 : Json.integer(e, "order")) + 1;
                String title = Json.string(e, "name");
                SimpleDate airdate = SimpleDate.parse(Json.string(e, "air_date"));
                if (specials) {
                    episodes.add(new Episode(info.getName(), null, null, title, null, n, airdate, Json.integer(e, "id"), info));
                } else if (order == SortOrder.Absolute) {
                    absolute++;
                    episodes.add(new Episode(info.getName(), null, absolute, title, absolute, null, airdate, Json.integer(e, "id"), info));
                } else {
                    absolute++;
                    episodes.add(new Episode(info.getName(), season, n, title, absolute, null, airdate, Json.integer(e, "id"), info));
                }
            }
        }
        return episodes;
    }
}
