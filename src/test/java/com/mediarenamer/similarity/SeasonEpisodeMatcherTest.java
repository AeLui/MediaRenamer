package com.mediarenamer.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class SeasonEpisodeMatcherTest {

    private final SeasonEpisodeMatcher lenient = new SeasonEpisodeMatcher(false);
    private final SeasonEpisodeMatcher strict = new SeasonEpisodeMatcher(true);

    private static List<SxE> sxe(Integer s, int... eps) {
        return java.util.Arrays.stream(eps).mapToObj(e -> new SxE(s, e)).toList();
    }

    @Test
    void standardPatterns() {
        assertEquals(sxe(1, 2), lenient.match("The.Show.S01E02.720p.HDTV.x264-GRP"));
        assertEquals(sxe(1, 2), lenient.match("the show s01e02"));
        assertEquals(sxe(1, 2), lenient.match("The Show 1x02 Title"));
        assertEquals(sxe(10, 5), lenient.match("Show.S10E05"));
        assertEquals(sxe(2, 3), lenient.match("Show Season 2 Episode 3"));
        assertEquals(sxe(1, 2), lenient.match("Show [s01]_[e02]"));
    }

    @Test
    void multiEpisode() {
        assertEquals(sxe(1, 1, 2), lenient.match("Show.S01E01E02.mkv"));
        assertEquals(sxe(1, 1, 2, 3), lenient.match("Show.S01E01-E03.mkv"));
        assertEquals(sxe(1, 1, 2), lenient.match("Show 1x01-1x02"));
    }

    @Test
    void ambiguousPatternsOnlyInLenientMode() {
        assertEquals(sxe(1, 2), lenient.match("Show 102 Title"));
        assertNull(strict.match("Show 102 Title"));
    }

    @Test
    void specials() {
        assertEquals(sxe(0, 1), lenient.match("Show SP01"));
    }

    @Test
    void episodeOnly() {
        assertEquals(sxe(null, 12), lenient.match("Show Ep 12"));
        assertEquals(sxe(null, 3), lenient.match("Documentary.3of6"));
        assertEquals(sxe(null, 3), lenient.match("Documentary 3 of 6"));
        // "7 of 6" is not a part number
        assertNull(lenient.match("Documentary 7 of 6"));
    }

    @Test
    void yearsAndCodecsAreNotEpisodes() {
        assertNull(lenient.match("Movie 2010"));
        assertNull(lenient.match("Movie.2010.1080p.H.264-GRP"));
        assertNull(lenient.match("Movie.DD5.1.x265.10bit"));
        assertNull(strict.match("Video 1920x1080"));
        // a 4-digit season with an episode above 99 is a year
        assertNull(strict.match("Show.S2010E150"));
    }

    @Test
    void rangesBackwardsOrTooLongKeepTheFirstEpisode() {
        assertEquals(sxe(1, 5), lenient.match("Show.S01E05-03"));
        assertEquals(sxe(1, 1), lenient.match("Show.S01E01-E99"));
        assertEquals(sxe(1, 1, 2, 3), lenient.match("Show.1x01-03"));
    }

    @Test
    void otherLanguagesSeasonWords() {
        assertEquals(sxe(1, 3), strict.match("Serie Temporada 1 Capitulo 3"));
        assertEquals(sxe(2, 4), strict.match("Serie Staffel 2 Folge 4"));
        assertEquals(sxe(1, 5), strict.match("La.Casa.de.Papel.T1.Cap.05"));
        assertEquals(sxe(2, 7), strict.match("Serie T2E07"));
    }

    @Test
    void findPointsAtTheNotationUsed() {
        assertEquals(9, lenient.find("The Show S01E02 720p", 0));
        assertEquals(9, lenient.find("The Show S01E02 720p", 3));
        assertEquals(-1, lenient.find("The Show S01E02 720p", 10));
        assertEquals(-1, strict.find("Show 102 Title", 0));
        assertEquals(5, lenient.find("Show 102 Title", 0));
    }

    @Test
    void hostileNamesAreFast() {
        String noise = "Show s01e01" + " . _".repeat(5000) + "x s1" + " ._[]".repeat(5000) + "1x" + "1".repeat(3000) + " season 1" + " _.".repeat(5000);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            lenient.match(noise);
            strict.find(noise, 0);
        });
    }
}
