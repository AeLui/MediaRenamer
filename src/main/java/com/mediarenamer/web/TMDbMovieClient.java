package com.mediarenamer.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** TheMovieDB: movies. */
public class TMDbMovieClient extends TMDbClient implements MovieLookupService {

    @Override
    public String getIdentifier() {
        return "TheMovieDB";
    }

    @Override
    public List<Movie> searchMovie(String query, int year, Locale locale) throws Exception {
        Map<String, Object> p = WebRequest.params("query", query, "include_adult", "false");
        if (year > 0) {
            p.put("primary_release_year", year);
        }
        Object json = request("search/movie", p, locale, CACHE);
        List<Movie> result = new ArrayList<>();
        for (Map<String, Object> m : Json.maps(json, "results")) {
            Integer id = Json.integer(m, "id");
            String title = Json.string(m, "title");
            if (id == null || title == null) {
                continue;
            }
            List<String> aliases = new ArrayList<>();
            String original = Json.string(m, "original_title");
            if (original != null && !original.equals(title)) {
                aliases.add(original);
            }
            SimpleDate d = SimpleDate.parse(Json.string(m, "release_date"));
            result.add(new Movie(title, aliases, d == null ? 0 : d.getYear(), 0, id, locale)
                    .withDetails(posterUrl(Json.string(m, "poster_path")), Json.string(m, "overview"), original));
        }
        // If the search with year returns nothing, it is repeated without year (release years vary by country).
        if (result.isEmpty() && year > 0) {
            return searchMovie(query, 0, locale);
        }
        return result;
    }

    @Override
    public Movie getMovieDescriptor(Movie movie, Locale locale) throws Exception {
        if (movie.getTmdbId() > 0) {
            Object json = request("movie/" + movie.getTmdbId(), Map.of(), locale, CACHE);
            return toMovie(json, locale);
        }
        if (movie.getImdbId() > 0) {
            Object json = request("find/" + String.format("tt%07d", movie.getImdbId()), Map.of("external_source", "imdb_id"), locale, CACHE);
            List<Map<String, Object>> results = Json.maps(json, "movie_results");
            if (!results.isEmpty()) {
                return getMovieDescriptor(new Movie(null, null, 0, 0, Json.integer(results.get(0), "id"), locale), locale);
            }
        }
        return null;
    }

    private Movie toMovie(Object json, Locale locale) {
        String title = Json.string(json, "title");
        SimpleDate d = SimpleDate.parse(Json.string(json, "release_date"));
        List<String> aliases = new ArrayList<>();
        String original = Json.string(json, "original_title");
        if (original != null && !original.equals(title)) {
            aliases.add(original);
        }
        return new Movie(title, aliases, d == null ? 0 : d.getYear(), imdb(Json.string(json, "imdb_id")), Json.integer(json, "id"), locale);
    }

    static int imdb(String tt) {
        if (tt == null) {
            return 0;
        }
        String digits = tt.replaceAll("\\D", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    @Override
    public MovieInfo getMovieInfo(Movie movie, Locale locale) throws Exception {
        Movie m = movie.getTmdbId() > 0 ? movie : getMovieDescriptor(movie, locale);
        if (m == null) {
            return null;
        }
        Object json = request("movie/" + m.getTmdbId(),
                Map.of("append_to_response", "translations,alternative_titles,release_dates,keywords,credits"), locale, CACHE);
        MovieInfo info = new MovieInfo();
        info.name = Json.string(json, "title");
        info.originalName = Json.string(json, "original_title");
        info.originalLanguage = Json.string(json, "original_language");
        info.spokenLanguages = Json.strings(json, "spoken_languages", "iso_639_1");
        info.released = SimpleDate.parse(Json.string(json, "release_date"));
        info.runtime = Json.integer(json, "runtime");
        info.genres = Json.strings(json, "genres", "name");
        info.rating = Json.decimal(json, "vote_average");
        info.votes = Json.integer(json, "vote_count");
        info.collection = Json.string(Json.map(json, "belongs_to_collection"), "name");
        info.overview = Json.string(json, "overview");
        info.tagline = Json.string(json, "tagline");
        info.countries = Json.strings(json, "production_countries", "iso_3166_1");
        info.productionCompanies = Json.strings(json, "production_companies", "name");
        info.poster = imageUrl(Json.string(json, "poster_path"));
        info.externalIds.put("tmdb", String.valueOf(m.getTmdbId()));
        String imdb = Json.string(json, "imdb_id");
        if (imdb != null) {
            info.externalIds.put("imdb", imdb);
        }
        Map<String, Object> credits = Json.map(json, "credits");
        for (Map<String, Object> c : Json.maps(credits, "crew")) {
            if ("Director".equals(Json.string(c, "job"))) {
                info.directors.add(Json.string(c, "name"));
            } else if ("Writing".equals(Json.string(c, "department"))) {
                info.writers.add(Json.string(c, "name"));
            }
        }
        Json.maps(credits, "cast").stream().map(c -> Json.string(c, "name")).filter(Objects::nonNull).limit(20).forEach(info.actors::add);
        info.certification = certification(json, locale);
        info.raw.putAll(asMap(json));
        return info;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object json) {
        return json instanceof Map ? (Map<String, Object>) json : Map.of();
    }

    /** Age rating of the locale's country (or US as a fallback). */
    private static String certification(Object json, Locale locale) {
        List<Map<String, Object>> countries = Json.maps(Json.map(json, "release_dates"), "results");
        for (String country : List.of(locale.getCountry(), "US")) {
            for (Map<String, Object> c : countries) {
                if (country.equalsIgnoreCase(Json.string(c, "iso_3166_1"))) {
                    for (Map<String, Object> r : Json.maps(c, "release_dates")) {
                        String cert = Json.string(r, "certification");
                        if (cert != null) {
                            return cert;
                        }
                    }
                }
            }
        }
        return null;
    }
}
