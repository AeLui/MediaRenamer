package com.mediarenamer.web;

import com.mediarenamer.app.Settings;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** OMDb (www.omdbapi.com). Search by title/year and details by IMDb id. */
public class OMDbClient implements MovieLookupService {

    private static final String HOST = "https://www.omdbapi.com/";
    private static final Duration CACHE = Duration.ofDays(7);
    private static final FloodLimit LIMIT = new FloodLimit(20, 10_000);

    @Override
    public String getIdentifier() {
        return "OMDb";
    }

    private Object request(Map<String, Object> params) throws Exception {
        params.put("apikey", Settings.get().requireKey(Settings.OMDB_KEY, "OMDb"));
        LIMIT.acquire();
        Object json = WebRequest.getJson(HOST + "?" + WebRequest.query(params), null, CACHE);
        if ("False".equals(Json.string(json, "Response")) && !"Movie not found!".equals(Json.string(json, "Error"))) {
            throw new IllegalStateException("OMDb: " + Json.string(json, "Error"));
        }
        return json;
    }

    @Override
    public List<Movie> searchMovie(String query, int year, Locale locale) throws Exception {
        Map<String, Object> p = WebRequest.params("s", query, "type", "movie");
        if (year > 0) {
            p.put("y", year);
        }
        Object json = request(p);
        List<Movie> result = new ArrayList<>();
        for (Map<String, Object> m : Json.maps(json, "Search")) {
            String title = Json.string(m, "Title");
            Integer y = Json.integer(m, "Year");
            int imdb = TMDbMovieClient.imdb(Json.string(m, "imdbID"));
            if (title != null && imdb > 0) {
                result.add(new Movie(title, List.of(), y == null ? 0 : y, imdb, 0, locale).withDetails(Json.string(m, "Poster"), null, null));
            }
        }
        if (result.isEmpty() && year > 0) {
            return searchMovie(query, 0, locale);
        }
        return result;
    }

    @Override
    public Movie getMovieDescriptor(Movie movie, Locale locale) throws Exception {
        if (movie.getImdbId() <= 0) {
            return null;
        }
        Object json = request(WebRequest.params("i", String.format("tt%07d", movie.getImdbId())));
        String title = Json.string(json, "Title");
        Integer y = Json.integer(json, "Year");
        return title == null ? null : new Movie(title, List.of(), y == null ? 0 : y, movie.getImdbId(), 0, locale);
    }

    @Override
    public MovieInfo getMovieInfo(Movie movie, Locale locale) throws Exception {
        if (movie.getImdbId() <= 0) {
            return null;
        }
        Object json = request(WebRequest.params("i", String.format("tt%07d", movie.getImdbId()), "plot", "short"));
        MovieInfo info = new MovieInfo();
        info.name = Json.string(json, "Title");
        info.certification = Json.string(json, "Rated");
        info.genres = split(Json.string(json, "Genre"));
        info.directors = split(Json.string(json, "Director"));
        info.writers = split(Json.string(json, "Writer"));
        info.actors = split(Json.string(json, "Actors"));
        info.countries = split(Json.string(json, "Country"));
        info.spokenLanguages = split(Json.string(json, "Language"));
        info.overview = Json.string(json, "Plot");
        info.poster = Json.string(json, "Poster");
        info.rating = Json.decimal(json, "imdbRating");
        String votes = Json.string(json, "imdbVotes");
        if (votes != null && votes.matches("[\\d,]+")) {
            info.votes = Integer.valueOf(votes.replace(",", ""));
        }
        String runtime = Json.string(json, "Runtime");
        if (runtime != null && runtime.matches("\\d+.*")) {
            info.runtime = Integer.valueOf(runtime.replaceAll("\\D.*", ""));
        }
        info.externalIds.put("imdb", Json.string(json, "imdbID"));
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = (Map<String, Object>) json;
        info.raw.putAll(raw);
        return info;
    }

    private static List<String> split(String csv) {
        return csv == null ? new ArrayList<>() : new ArrayList<>(Arrays.stream(csv.split(",\\s*")).filter(s -> !s.isBlank()).toList());
    }
}
