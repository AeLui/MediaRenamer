package com.mediarenamer.rename.match;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.media.MediaDetection;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.similarity.NameSimilarityMetric;
import com.mediarenamer.similarity.Normalization;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MovieLookupService;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Movie mode: detects "Title (Year)" in the name of the
 * file or its folder, searches TheMovieDB/OMDb and picks the best result (title + year).
 * If there is an IMDb/TMDb id in the name or in a .nfo next to the file, it is used directly.
 */
public class MovieMatcher implements AutoCompleteMatcher {

    private static final Pattern IMDB = Pattern.compile("(?<!\\p{Alnum})tt(\\d{7,9})(?!\\p{Alnum})");
    private static final Pattern TMDB = Pattern.compile("(?i)(?:tmdb(?:id)?[-=:_ ]?|themoviedb\\.org/movie/)(\\d+)");

    private final MovieLookupService service;

    public MovieMatcher(MovieLookupService service) {
        this.service = service;
    }

    @Override
    public List<Match<Object, File>> match(List<File> files, Options o) throws Exception {
        List<Match<Object, File>> result = new ArrayList<>();
        // cache per query: several parts (CD1/CD2) or files of the same folder share a movie
        Map<String, Movie> resolved = new LinkedHashMap<>();
        for (File f : AutoCompleteMatcher.primary(files)) {
            if (o.cancelled().getAsBoolean()) {
                break;
            }
            Movie movie = o.hasQuery() ? null : byId(f, o);
            if (movie == null) {
                MediaDetection.MovieQuery q = o.hasQuery() ? queryOverride(o.query()) : MediaDetection.detectMovie(f);
                String key = q == null ? "" : Normalization.normalize(q.toString());
                if (resolved.containsKey(key)) {
                    movie = resolved.get(key);
                } else {
                    movie = search(q, f, o);
                    resolved.put(key, movie);
                }
            }
            if (movie != null) {
                Logging.info(f.getName() + " => " + movie);
                result.add(new Match<>(movie, f));
            }
        }
        return AutoCompleteMatcher.addCompanions(files, result);
    }

    /** "--q 'Alien 1979'" or "--q 'Alien (1979)'" -> name + year. */
    public static MediaDetection.MovieQuery queryOverride(String q) {
        java.util.regex.Matcher m = Pattern.compile("^(.+?)[\\s(\\[]+((?:19|20)\\d{2})[)\\]]?$").matcher(q.trim());
        return m.matches() ? new MediaDetection.MovieQuery(m.group(1).trim(), Integer.parseInt(m.group(2))) : new MediaDetection.MovieQuery(q.trim(), 0);
    }

    /** Explicit ids in the name ("tt0111161", "tmdb-278") or in a .nfo of the same folder. */
    private Movie byId(File f, Options o) throws Exception {
        String text = f.getName();
        File nfo = new File(f.getParentFile(), com.mediarenamer.util.FileUtilities.getNameWithoutExtension(f.getName()) + ".nfo");
        if (nfo.isFile() && nfo.length() < 1_000_000) {
            text += "\n" + java.nio.file.Files.readString(nfo.toPath(), java.nio.charset.StandardCharsets.ISO_8859_1);
        }
        java.util.regex.Matcher tmdb = TMDB.matcher(text);
        if (tmdb.find() && service.getIdentifier().equals("TheMovieDB")) {
            return service.getMovieDescriptor(new Movie(null, null, 0, 0, Integer.parseInt(tmdb.group(1)), o.locale()), o.locale());
        }
        java.util.regex.Matcher imdb = IMDB.matcher(text);
        if (imdb.find()) {
            return service.getMovieDescriptor(new Movie(null, null, 0, Integer.parseInt(imdb.group(1)), 0, o.locale()), o.locale());
        }
        return null;
    }

    private Movie search(MediaDetection.MovieQuery q, File f, Options o) throws Exception {
        String query = q == null ? null : q.name();
        int year = q == null ? 0 : q.year();
        if (query == null || query.isBlank()) {
            query = o.selector().input(tr("prompt.movieName"), f.getName());
            if (query == null || query.isBlank()) {
                return null;
            }
        }
        List<Movie> results = new ArrayList<>(service.searchMovie(query, year, o.locale()));
        if (results.isEmpty()) {
            Logging.info(tr("log.noResults", query));
            if (o.strict()) {
                return null;
            }
            String manual = o.selector().input(tr("prompt.notFound", query), query);
            return manual == null || manual.isBlank() ? null : search(new MediaDetection.MovieQuery(manual, year), f, o);
        }
        String nq = Normalization.normalize(query);
        results.sort(Comparator.comparingDouble((Movie m) -> -score(nq, year, m)));
        double best = score(nq, year, results.get(0));
        double second = results.size() > 1 ? score(nq, year, results.get(1)) : 0;
        if (best >= 0.9 && best - second >= 0.1 || results.size() == 1 && best >= 0.6) {
            return results.get(0);
        }
        // a single result of the same year is unambiguous even if the title is longer ("Mufasa" -> "Mufasa: The Lion King")
        if (results.size() == 1 && year > 0 && results.get(0).getYear() == year && best >= 0.4) {
            return results.get(0);
        }
        if (o.strict() && best < 0.9) {
            Logging.info(tr("log.ambiguous", query));
        }
        return o.selector().select(new Selector.Request<>(q == null ? query : q.toString(), List.of(f.getName()), results, service, o.locale(),
                text -> searchText(text, o.locale())));
    }

    /** Free search from the dialog: accepts "Title (Year)" or "Title Year". */
    public List<Movie> searchText(String text, java.util.Locale locale) throws Exception {
        MediaDetection.MovieQuery q = queryOverride(text);
        return service.searchMovie(q.name(), q.year(), locale);
    }

    /** Title similarity (aliases included) with a year bonus/penalty. */
    static double score(String normalizedQuery, int year, Movie m) {
        double s = 0;
        for (String n : m.getEffectiveNames()) {
            String norm = Normalization.normalize(n);
            s = Math.max(s, norm.equals(normalizedQuery) ? 1 : NameSimilarityMetric.similarity(normalizedQuery, norm) * 0.9);
            // the title starts with the whole query (title with subtitle): "mufasa" -> "mufasa the lion king"
            if (!normalizedQuery.isEmpty() && norm.startsWith(normalizedQuery + " ")) {
                s = Math.max(s, 0.85);
            }
        }
        if (year > 0 && m.getYear() > 0) {
            int diff = Math.abs(year - m.getYear());
            s = diff == 0 ? s : diff == 1 ? s - 0.05 : s - 0.3;
        }
        return s;
    }
}
