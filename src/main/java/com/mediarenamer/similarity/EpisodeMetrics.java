package com.mediarenamer.similarity;

import com.mediarenamer.media.MediaDetection;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.MultiEpisode;
import com.mediarenamer.web.SeriesInfo;
import com.mediarenamer.web.SimpleDate;

import java.io.File;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metrics that pair episodes (values) with files (candidates). The sequence, in cascade order:
 * <ol>
 * <li><b>identifier</b>: season and episode (or special number), air date, or anime absolute number;</li>
 * <li><b>title</b>: the episode title appears in the file name;</li>
 * <li><b>series</b>: the series name or one of its aliases appears in the file name;</li>
 * <li><b>numbers</b>: share of the file's standalone numbers that are numbers of the episode;</li>
 * <li><b>name</b>: overall similarity of the file name with the episode's display name.</li>
 * </ol>
 * The analysis of each file is computed once per instance.
 */
public class EpisodeMetrics {

    /** Season and episode equal, special number equal or air date equal. */
    static final float EXACT = 1f;
    /** The same, for the second and later codes of a multi-episode file (the first one names the match). */
    static final float EXACT_LATER = 0.95f;
    /** An episode number without season equal to the episode or absolute number. */
    static final float EPISODE_ONLY = 0.5f;
    /** An absolute number in a name without season, or whose only SxE is a bare number like "366" (anime). */
    static final float ABSOLUTE = 0.4f;

    private static final SeasonEpisodeMatcher LENIENT = new SeasonEpisodeMatcher(false);
    private static final SeasonEpisodeMatcher STRICT = new SeasonEpisodeMatcher(true);
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d{1,4}(?![\\p{L}\\p{N}])");

    /** What is known about a file name. */
    private record Analysis(List<SxE> sxe, boolean hasSeason, boolean ambiguous, SimpleDate date, List<Integer> numbers, String normalized) {
    }

    private final Map<File, Analysis> files = new ConcurrentHashMap<>();
    private final Map<Episode, EpisodeText> episodeTexts = new IdentityHashMap<>();

    /** Normalized title(s), series names and aliases, and display name of an episode. */
    private record EpisodeText(List<String> titles, List<String> seriesNames, String display) {
    }

    public EpisodeMetrics() {
    }

    /** Metrics for {@link Matcher}: the first one is an identifier, the later ones break its ties. */
    public SimilarityMetric[] matchSequence() {
        return new SimilarityMetric[] { metric(this::identifier), metric(this::title), metric(this::series), metric(this::numbers),
                metric(this::name) };
    }

    /** Above 0 when the pair is plausible: the identifier or the title matches. */
    public float verify(Episode e, File f) {
        return identifier(e, f) > 0 || titleContained(e, f) ? 1 : 0;
    }

    /** Season/episode numbers detected for a file (never null). */
    public List<SxE> sxe(File f) {
        return analysis(f).sxe();
    }

    /** Wraps a metric so it accepts an Episode and a File in either order (0 for anything else). */
    private static SimilarityMetric metric(ToDoubleBiFunction<Episode, File> m) {
        return (a, b) -> {
            if (a instanceof Episode e && b instanceof File f) {
                return (float) m.applyAsDouble(e, f);
            }
            if (b instanceof Episode e && a instanceof File f) {
                return (float) m.applyAsDouble(e, f);
            }
            return 0;
        };
    }

    // ------------------------------------------------------------------ file analysis

    private Analysis analysis(File f) {
        return files.computeIfAbsent(f, EpisodeMetrics::analyze);
    }

    private static Analysis analyze(File f) {
        String name = MediaDetection.nameWithParentSeason(f);
        String stripped = MediaDetection.stripReleaseInfo(name, false);
        List<SxE> sxe = LENIENT.match(stripped);
        if (sxe == null) {
            sxe = LENIENT.match(name);
        }
        if (sxe == null) {
            sxe = List.of();
        }
        boolean hasSeason = sxe.stream().anyMatch(SxE::hasSeason);
        Integer folderSeason = MediaDetection.folderSeason(f);
        if (folderSeason != null && !sxe.isEmpty() && !hasSeason) {
            sxe = sxe.stream().map(s -> new SxE(folderSeason, s.episode())).distinct().toList();
            hasSeason = true;
        }
        SimpleDate date = new DateMatcher().match(stripped);
        if (date == null) {
            date = new DateMatcher().match(name);
        }
        List<Integer> numbers = new ArrayList<>();
        Matcher m = NUMBER.matcher(stripped);
        while (m.find()) {
            numbers.add(Integer.valueOf(m.group()));
        }
        // only a bare number like "366" was read as SxE: it may just as well be an anime absolute number
        boolean ambiguous = !sxe.isEmpty() && STRICT.match(stripped) == null && STRICT.match(name) == null;
        return new Analysis(List.copyOf(sxe), hasSeason, ambiguous, date, List.copyOf(numbers), Normalization.normalize(stripped));
    }

    private static List<Episode> parts(Episode e) {
        return e instanceof MultiEpisode m ? m.getEpisodes() : List.of(e);
    }

    // ------------------------------------------------------------------ metrics

    private double identifier(Episode e, File f) {
        Analysis a = analysis(f);
        double best = 0;
        for (Episode ep : parts(e)) {
            for (int i = 0; i < a.sxe().size(); i++) {
                SxE s = a.sxe().get(i);
                // a bare "205" may be 2x05 or the absolute episode 205: as weak as an absolute number, the next metrics decide
                float exact = a.ambiguous() ? ABSOLUTE : i == 0 ? EXACT : EXACT_LATER;
                if (s.hasSeason()) {
                    boolean special = s.season() == 0 && ep.getSpecial() != null && ep.getSpecial() == s.episode();
                    boolean same = Objects.equals(ep.getSeason(), s.season()) && Objects.equals(ep.getEpisode(), s.episode());
                    if (special || same) {
                        best = Math.max(best, exact);
                    }
                } else if (Objects.equals(ep.getEpisode(), s.episode()) || Objects.equals(ep.getAbsolute(), s.episode())) {
                    best = Math.max(best, EPISODE_ONLY);
                }
            }
            if (a.date() != null && a.date().equals(ep.getAirdate())) {
                best = Math.max(best, EXACT);
            }
            if (a.date() == null && (!a.hasSeason() || a.ambiguous()) && ep.getAbsolute() != null && a.numbers().contains(ep.getAbsolute())) {
                best = Math.max(best, ABSOLUTE);
            }
        }
        return best;
    }

    private double title(Episode e, File f) {
        Analysis a = analysis(f);
        double best = 0;
        for (String t : text(e).titles()) {
            if (t.length() >= 3) {
                best = Math.max(best, containsWords(a.normalized(), t) ? 1 : 0);
            } else {
                best = Math.max(best, NameSimilarityMetric.similarity(t, a.normalized()));
            }
        }
        return best;
    }

    private boolean titleContained(Episode e, File f) {
        Analysis a = analysis(f);
        for (String t : text(e).titles()) {
            if (t.length() >= 3 && containsWords(a.normalized(), t)) {
                return true;
            }
        }
        return false;
    }

    private double series(Episode e, File f) {
        Analysis a = analysis(f);
        for (String n : text(e).seriesNames()) {
            if (containsWords(a.normalized(), n)) {
                return 1;
            }
        }
        return 0;
    }

    private double numbers(Episode e, File f) {
        Analysis a = analysis(f);
        if (a.numbers().isEmpty()) {
            return 0;
        }
        Set<Integer> own = new LinkedHashSet<>();
        for (Episode ep : parts(e)) {
            for (Integer n : new Integer[] { ep.getSeason(), ep.getEpisode(), ep.getAbsolute(), ep.getSpecial() }) {
                if (n != null) {
                    own.add(n);
                }
            }
        }
        long shared = a.numbers().stream().filter(own::contains).count();
        return (double) shared / a.numbers().size();
    }

    private double name(Episode e, File f) {
        return NameSimilarityMetric.similarity(analysis(f).normalized(), text(e).display());
    }

    /** Normalized texts of an episode, computed once (the metrics compare them with every file). */
    private EpisodeText text(Episode e) {
        synchronized (episodeTexts) {
            return episodeTexts.computeIfAbsent(e, EpisodeMetrics::episodeText);
        }
    }

    private static EpisodeText episodeText(Episode e) {
        Set<String> titles = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        for (Episode ep : parts(e)) {
            String t = Normalization.normalize(ep.getTitle());
            if (!t.isEmpty()) {
                titles.add(t);
            }
            List<String> raw = new ArrayList<>();
            raw.add(ep.getSeriesName());
            SeriesInfo info = ep.getSeriesInfo();
            if (info != null) {
                raw.add(info.getName());
                if (info.getAliasNames() != null) {
                    raw.addAll(info.getAliasNames());
                }
            }
            for (String n : raw) {
                String norm = Normalization.normalize(n);
                if (!norm.isEmpty()) {
                    names.add(norm);
                }
            }
        }
        return new EpisodeText(List.copyOf(titles), List.copyOf(names), Normalization.normalize(e.toString()));
    }

    /** True when the words of part appear together in text (not inside longer words). */
    private static boolean containsWords(String text, String part) {
        return (" " + text + " ").contains(" " + part + " ");
    }
}
