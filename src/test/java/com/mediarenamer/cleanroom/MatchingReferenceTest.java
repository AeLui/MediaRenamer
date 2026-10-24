package com.mediarenamer.cleanroom;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mediarenamer.rename.match.EpisodeListMatcher;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.similarity.Matcher;
import com.mediarenamer.similarity.SimilarityMetric;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.SeriesInfo;
import com.mediarenamer.web.SimpleDate;
import com.mediarenamer.web.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Reference scenarios of docs/MATCHING.md, section 5: the generic matcher with synthetic metrics and episode matching
 * end to end.
 */
class MatchingReferenceTest {

    // ---- generic matcher with synthetic metrics (section 8) ----

    /** Metric given as a table: "A1" -> similarity of value A with candidate 1; missing pairs are 0. */
    private static SimilarityMetric table(Map<String, Float> t) {
        return (v, c) -> t.getOrDefault(v + "" + c, 0f);
    }

    private static String run(boolean strict, SimilarityMetric... metrics) {
        return run(List.of("A", "B"), List.of(1, 2), strict, metrics);
    }

    private static String run(List<String> values, List<Integer> candidates, boolean strict, SimilarityMetric... metrics) {
        List<Match<String, Integer>> result = new Matcher<String, Integer>(values, candidates, strict, metrics).match();
        return result.stream().map(m -> m.getValue() + m.getCandidate()).collect(Collectors.joining(","));
    }

    @Test
    void highestSimilarityWins() {
        SimilarityMetric m = table(Map.of("A1", 0.9f, "A2", 0.5f, "B1", 0.4f, "B2", 0.8f));
        assertEquals("A1,B2", run(false, m));
        assertEquals("A1,B2", run(true, m));
    }

    @Test
    void tieIsBrokenByTheNextMetric() {
        SimilarityMetric first = table(Map.of("A1", 1f, "A2", 1f));
        SimilarityMetric second = table(Map.of("A2", 1f));
        // A2 is decided by the second metric; B1 is the only pair left
        assertEquals("B1,A2", run(false, first, second));
        // strict: a pair whose first metric is 0 is never accepted
        assertEquals("A2", run(true, first, second));
    }

    @Test
    void unresolvedTiesDependOnTheMode() {
        SimilarityMetric flat = (v, c) -> 1f;
        // lenient: accepted in candidate order, each value and candidate used once
        assertEquals("A1,B2", run(false, flat, flat));
        // strict: ambiguous pairs are dropped
        assertEquals("", run(true, flat, flat));
    }

    @Test
    void tinyDifferencesCountAsTies() {
        SimilarityMetric first = table(Map.of("A1", 0.901f, "A2", 0.899f));
        SimilarityMetric second = table(Map.of("A2", 1f));
        assertEquals("A2", run(List.of("A"), List.of(1, 2), false, first, second));
    }

    @Test
    void resultFollowsCandidateOrderAndReportsLeftovers() {
        SimilarityMetric m = table(Map.of("A2", 1f, "B1", 1f));
        Matcher<String, Integer> matcher = new Matcher<>(List.of("A", "B", "C"), List.of(1, 2, 3), true, new SimilarityMetric[] { m });
        List<Match<String, Integer>> result = matcher.match();
        assertEquals("B1,A2", result.stream().map(x -> x.getValue() + x.getCandidate()).collect(Collectors.joining(",")));
        assertEquals(List.of("C"), matcher.remainingValues());
        assertEquals(List.of(3), matcher.remainingCandidates());
    }

    // ---- episode matching end to end (section 9) ----

    private static final SeriesInfo SERIES = series();

    private static SeriesInfo series() {
        SeriesInfo s = new SeriesInfo("TheTVDB", SortOrder.Airdate, Locale.US, 1);
        s.setName("Show");
        return s;
    }

    /** Two seasons of 12 episodes, absolute numbers 1-24, one air date per day from 2024-03-01. */
    private static List<Episode> episodes() {
        List<Episode> l = new ArrayList<>();
        int abs = 1;
        for (int season = 1; season <= 2; season++) {
            for (int e = 1; e <= 12; e++) {
                l.add(new Episode("Show", season, e, "Title " + season + "-" + e, abs, null, new SimpleDate(2024, 3, abs), abs, SERIES));
                abs++;
            }
        }
        return l;
    }

    private static String titles(List<File> files, List<Episode> episodes, boolean strict) {
        return EpisodeListMatcher.matchEpisodes(files, episodes, strict).stream()
                .map(m -> m.getCandidate().getName() + "=" + ((Episode) m.getValue()).getTitle()).collect(Collectors.joining(", "));
    }

    @Test
    void seasonEpisodeNumbersDecide() {
        List<File> files = List.of(new File("Show.S02E03.mkv"), new File("Show.S01E12.720p.mkv"), new File("show 2x01.avi"));
        String expected = "Show.S02E03.mkv=Title 2-3, Show.S01E12.720p.mkv=Title 1-12, show 2x01.avi=Title 2-1";
        assertEquals(expected, titles(files, episodes(), true));
        assertEquals(expected, titles(files, episodes(), false));
    }

    @Test
    void airDateIdentifiesDailyShows() {
        List<File> files = List.of(new File("Show.2024.03.15.1080p.WEB.mkv"), new File("Show.2024.03.02.mkv"));
        assertEquals("Show.2024.03.15.1080p.WEB.mkv=Title 2-3, Show.2024.03.02.mkv=Title 1-2", titles(files, episodes(), true));
    }

    @Test
    void absoluteNumberIdentifiesAnime() {
        List<File> files = List.of(new File("[Grp] Show - 15 (1080p).mkv"), new File("[Grp] Show - 05 (1080p).mkv"));
        assertEquals("[Grp] Show - 15 (1080p).mkv=Title 2-3, [Grp] Show - 05 (1080p).mkv=Title 1-5", titles(files, episodes(), true));
    }

    @Test
    void episodeTitleIdentifiesUnnumberedFiles() {
        List<Episode> eps = List.of(new Episode("Show", 1, 1, "Secrets", 1, null, null, 1, SERIES),
                new Episode("Show", 1, 2, "Lies", 2, null, null, 2, SERIES),
                new Episode("Show", 1, 3, "Ghosts", 3, null, null, 3, SERIES));
        List<File> files = List.of(new File("Show - Lies.mkv"), new File("Show - Secrets.mkv"));
        assertEquals("Show - Lies.mkv=Lies, Show - Secrets.mkv=Secrets", titles(files, eps, false));
        // strict mode needs a number or a date
        assertEquals("", titles(files, eps, true));
    }

    @Test
    void specialsMatchSpecialNumbers() {
        List<Episode> eps = new ArrayList<>(episodes());
        eps.add(new Episode("Show", null, null, "Christmas Special", null, 1, null, 100, SERIES));
        assertEquals("Show.SP01.mkv=Christmas Special", titles(List.of(new File("Show.SP01.mkv")), eps, true));
    }

    @Test
    void seasonFolderCompletesBareNumbers() {
        assertEquals("05 - Something.mkv=Title 2-5", titles(List.of(new File("C:/Show/Season 2/05 - Something.mkv")), episodes(), true));
    }
}
