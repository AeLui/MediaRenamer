package com.mediarenamer.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** Release tokens match whole words only; groups, sources and extras. */
class ReleaseInfoTest {

    @Test
    void tokensAreWholeWordsOnly() {
        assertFalse(ReleaseInfo.VIDEO_SOURCE.matcher("Tests").find());
        assertFalse(ReleaseInfo.VIDEO_FORMAT.matcher("Divxland").find());
        assertTrue(ReleaseInfo.VIDEO_SOURCE.matcher("Movie.TS.avi").find());
        assertTrue(ReleaseInfo.VIDEO_FORMAT.matcher("Movie_x264_").find());
        assertEquals("", ReleaseInfo.VIDEO_FORMAT.matcher("H.264").replaceAll(""));
        assertEquals("", ReleaseInfo.VIDEO_FORMAT.matcher("HDR10+").replaceAll(""));
    }

    @Test
    void everyCategoryKnowsItsMinimumTerms() {
        check(ReleaseInfo.VIDEO_TAGS, "Director's Cut", "Directors.Cut", "Final Cut", "Special Edition", "Collector's Edition", "Theatrical", "Extended",
                "Unrated", "Uncut", "Remastered", "IMAX");
        check(ReleaseInfo.VIDEO_S3D, "SBS", "HSBS", "TAB", "HTAB", "OU", "Half-OU");
        check(ReleaseInfo.VIDEO_REPACK, "REPACK", "PROPER", "RERIP");
        check(ReleaseInfo.VIDEO_FORMAT, "x264", "x265", "H.264", "H264", "HEVC", "AVC", "XviD", "DivX", "AV1", "AC3", "DTS", "DTS-HD.MA.5.1", "DD5.1",
                "DDP5.1", "TrueHD", "Atmos", "AAC", "FLAC", "MP3", "HDR", "HDR10", "HDR10+", "DV", "Dolby Vision", "480p", "576p", "720p", "1080p", "1080i",
                "2160p", "4320p", "10bit");
        check(ReleaseInfo.VIDEO_SOURCE, "BluRay", "Blu-Ray", "BDRip", "BRRip", "REMUX", "UHD", "DVDRip", "DVD", "HDTV", "WEB-DL", "WEBRip", "WEB", "CAM",
                "HDCAM", "TS", "TC", "SCR", "AMZN", "NF", "DSNP", "ATVP", "HMAX", "microHD", "m1080p", "m720p");
        check(ReleaseInfo.VIDEO_LANGUAGE, "Multi", "Dual", "Castellano", "Latino", "Spanish", "English", "Spa", "Eng", "VOSE", "VOS", "Sub", "Subs");
        check(ReleaseInfo.SUBTITLE_TAGS, "forced", "SDH", "HI", "CC");
        check(ReleaseInfo.RELEASE_GROUP, "YTS", "SPARKS", "KILLERS", "FGT", "FLUX", "DIMENSION");
        check(ReleaseInfo.QUERY_EXCLUDES, "www", "com", "org", "net", "torrent", "download", "complete", "4k", "SDR", "HDR", "internal", "limited");
    }

    private static void check(Pattern p, String... terms) {
        for (String t : List.of(terms)) {
            assertTrue(p.matcher(t).matches(), p + " does not match " + t);
            assertTrue(p.matcher(t.toLowerCase()).matches(), "case: " + t);
        }
    }

    @Test
    void groupIsNeverAReleaseToken() {
        assertNull(ReleaseInfo.getReleaseGroup("Movie.2010-sample.mkv"));
        assertNull(ReleaseInfo.getReleaseGroup("Movie.2010.x264-WEB.mkv"));
        assertNull(ReleaseInfo.getReleaseGroup("Breaking Bad - 1x01 - Pilot.avi"));
        assertEquals("GRP", ReleaseInfo.getReleaseGroup("Movie.2010.x264-GRP"));
        assertEquals("KILLERS", ReleaseInfo.getReleaseGroup("Show.S01E01.KILLERS.(KILLERS).mkv"));
        // the end of a release token or a disc marker is not a group
        assertNull(ReleaseInfo.getReleaseGroup("Thor.Love.and.Thunder.2022.IMAX.WEB-DL.mkv"));
        assertNull(ReleaseInfo.getReleaseGroup("matrix-cd1.avi"));
        assertEquals("YIFY", ReleaseInfo.getReleaseGroup("Inception (2010) 1080p BrRip x264 - YIFY.mp4"));
    }

    @Test
    void ambiguousSourcesOnlyCountAfterTheTitle() {
        assertNull(ReleaseInfo.getVideoSource("Mad.Max.Fury.Road.2015.mkv"));
        assertNull(ReleaseInfo.getVideoSource("Charlotte's.Web.2006.mkv"));
        assertEquals("MAX", ReleaseInfo.getVideoSource("House.of.the.Dragon.S02E08.2160p.MAX.WEB-DL.mkv"));
        assertEquals("WEB", ReleaseInfo.getVideoSource("Charlotte's.Web.2006.WEB.mkv"));
    }

    @Test
    void sourceIgnoresTheExtension() {
        assertNull(ReleaseInfo.getVideoSource("Movie.2010.ts"));
        assertEquals("TS", ReleaseInfo.getVideoSource("Movie.2010.TS.avi"));
    }

    @Test
    void extrasFollowThePlexSuffixes() {
        assertTrue(ReleaseInfo.isClutter("Movie (2010)-featurette.mkv"));
        assertTrue(ReleaseInfo.isClutter("Movie (2010)-behindthescenes.mkv"));
        assertTrue(ReleaseInfo.isClutter("Movie.2010.1080p-GRP-sample.mkv"));
        assertTrue(ReleaseInfo.isClutter("Show NCED2.mkv"));
        assertFalse(ReleaseInfo.isClutter("The Other Woman (2014).mkv"));
        assertFalse(ReleaseInfo.isClutter("Short Circuit (1986).mkv"));
        assertFalse(ReleaseInfo.isClutter("Trailer Park Boys S01E01.mkv"));
    }

    @Test
    void hostileNamesAreFast() {
        String noise = "a" + "-_. [(".repeat(5000) + "behind" + " _-".repeat(5000) + "x";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            ReleaseInfo.isClutter(noise);
            ReleaseInfo.getReleaseGroup(noise);
            ReleaseInfo.getVideoTags(noise);
            MediaDetection.stripReleaseInfo(noise, false);
        });
    }
}
