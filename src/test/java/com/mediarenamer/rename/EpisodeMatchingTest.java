package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.mediarenamer.rename.match.EpisodeListMatcher;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.MultiEpisode;
import com.mediarenamer.web.SeriesInfo;
import com.mediarenamer.web.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class EpisodeMatchingTest {

    private static List<Episode> episodes() {
        SeriesInfo s = new SeriesInfo("TheTVDB", SortOrder.Airdate, Locale.US, 1);
        s.setName("Dark");
        List<Episode> l = new ArrayList<>();
        int abs = 1;
        for (int season = 1; season <= 3; season++) {
            for (int e = 1; e <= 10; e++) {
                l.add(new Episode("Dark", season, e, "Title " + season + "-" + e, abs++, null, null, abs, s));
            }
        }
        return l;
    }

    @Test
    void matchesBySeasonEpisode() {
        List<File> files = List.of(new File("Dark.S01E01.mkv"), new File("Dark.S02E05.1080p.mkv"), new File("dark 3x10.avi"));
        List<Match<Object, File>> m = EpisodeListMatcher.matchEpisodes(files, episodes(), true);
        assertEquals(3, m.size());
        assertEquals("Title 1-1", ((Episode) m.get(0).getValue()).getTitle());
        assertEquals("Title 2-5", ((Episode) m.get(1).getValue()).getTitle());
        assertEquals("Title 3-10", ((Episode) m.get(2).getValue()).getTitle());
    }

    @Test
    void multiEpisodeFile() {
        List<Match<Object, File>> m = EpisodeListMatcher.matchEpisodes(List.of(new File("Dark.S01E01E02.mkv")), episodes(), true);
        assertEquals(1, m.size());
        assertInstanceOf(MultiEpisode.class, m.get(0).getValue());
        assertEquals(2, ((MultiEpisode) m.get(0).getValue()).getEpisodes().size());
    }

    @Test
    void folderSeason() {
        List<Match<Object, File>> m = EpisodeListMatcher.matchEpisodes(List.of(new File("C:/Dark/Season 2/04 - Something.mkv")), episodes(), true);
        assertEquals(1, m.size());
        assertEquals("Title 2-4", ((Episode) m.get(0).getValue()).getTitle());
    }

    @Test
    void bareNumbersMayBeAbsoluteNumbers() {
        // "Dark 205" may be 2x05 or absolute episode 205: this series has no episode 205
        List<Match<Object, File>> m = EpisodeListMatcher.matchEpisodes(List.of(new File("Dark 205.mkv")), episodes(), true);
        assertEquals("Title 2-5", ((Episode) m.get(0).getValue()).getTitle());
        // with long absolute numbering, "Show - 205" is episode 205, not 2x05
        SeriesInfo s = new SeriesInfo("TheTVDB", SortOrder.Airdate, Locale.US, 2);
        s.setName("Bleach");
        List<Episode> bleach = new ArrayList<>();
        int abs = 1;
        for (int season = 1; season <= 16; season++) {
            for (int e = 1; e <= 23; e++, abs++) {
                bleach.add(new Episode("Bleach", season, e, "T" + abs, abs, null, null, abs, s));
            }
        }
        for (String name : new String[] { "Bleach - 205.mkv", "Bleach 366.mkv" }) {
            List<Match<Object, File>> r = EpisodeListMatcher.matchEpisodes(List.of(new File(name)), bleach, true);
            assertEquals("T" + name.replaceAll("\\D", ""), ((Episode) r.get(0).getValue()).getTitle(), name);
        }
    }

    @Test
    void titlesWithNumbersAreNotReadAsSeasonEpisode() {
        SeriesInfo s = new SeriesInfo("TheTVDB", SortOrder.Airdate, Locale.US, 3);
        List<Episode> eps = new ArrayList<>();
        for (int i = 1; i <= 300; i++) {
            eps.add(new Episode("Big", 1 + i / 100, i % 100 + 1, "Title number " + i, i, null, null, i, s));
        }
        List<Match<Object, File>> m = EpisodeListMatcher.matchEpisodes(List.of(new File("Big - Title number 106.mkv")), eps, false);
        assertEquals("Title number 106", ((Episode) m.get(0).getValue()).getTitle());
    }

    @Test
    void strictModeSkipsUnidentifiableFiles() {
        List<Match<Object, File>> m = EpisodeListMatcher.matchEpisodes(List.of(new File("random video.mkv")), episodes(), true);
        assertEquals(0, m.size());
    }
}
