package com.mediarenamer.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.web.Episode;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MultiEpisode;
import com.mediarenamer.web.SeriesInfo;
import com.mediarenamer.web.SimpleDate;
import com.mediarenamer.web.SortOrder;

import java.io.File;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class ExpressionFormatTest {

    private static final SeriesInfo SERIES = series();
    private static final Episode EP = new Episode("The Office", 2, 3, "Office Olympics", 9, null, new SimpleDate(2005, 10, 4), 1, SERIES);
    private static final File FILE = new File("The.Office.S02E03.720p.WEB-DL.mkv");

    private static SeriesInfo series() {
        SeriesInfo s = new SeriesInfo("TheMovieDB::TV", SortOrder.Airdate, Locale.US, 2316);
        s.setName("The Office");
        s.setStartDate(new SimpleDate(2005, 3, 24));
        s.setGenres(List.of("Comedy"));
        s.getExternalIds().put("tvdb", "73244");
        return s;
    }

    private static String format(String expr, Object info, File f) throws FormatException {
        return new ExpressionFileFormat(expr).format(new MediaBindingBean(info, f));
    }

    @Test
    void episodeFormat() throws Exception {
        assertEquals("The Office - S02E03 - Office Olympics", format("{n} - {s00e00} - {t}", EP, FILE));
        assertEquals("The Office - 2x03 - Office Olympics", format("{n} - {sxe} - {t}", EP, FILE));
        assertEquals("The Office/Season 2/The Office - S02E03", format("{n}/Season {s}/{n} - {s00e00}", EP, FILE));
        assertEquals("The Office (2005) 03", format("{ny} {e.pad(2)}", EP, FILE));
        assertEquals("2005.10.04", format("{d.format('yyyy.MM.dd')}", EP, FILE));
    }

    @Test
    void undefinedBindingsYieldEmptyBlocks() throws Exception {
        // {vf} does not exist (missing file => no MediaInfo) and the block stays empty
        assertEquals("The Office - S02E03", format("{n} - {s00e00}{' [' + vf + ']'}", EP, FILE));
    }

    @Test
    void groovyFunctions() throws Exception {
        assertEquals("the.office", format("{n.lower().space('.')}", EP, FILE));
        assertEquals("Office, The", format("{n.sortName()}", EP, FILE));
        assertEquals("O", format("{az}", EP, FILE));
        assertEquals("fallback", format("{any{vf}{'fallback'}}", EP, FILE));
        assertEquals("Comedy", format("{genres.joining(', ')}", EP, FILE));
        assertEquals("tvdb-73244", format("tvdb-{tvdbid}", EP, FILE));
    }

    @Test
    void pathSeparatorsInValuesAreReplaced() throws Exception {
        Movie m = new Movie("AC/DC: Live", List.of(), 1991, 0, 1, Locale.US);
        assertEquals("AC DC Live (1991)", format("{n} ({y})", m, new File("acdc.mkv")));
        assertEquals("AC DC - Live", format("{n.colon(' - ')}", m, new File("acdc.mkv")));
    }

    @Test
    void movieAndPlexStandard() throws Exception {
        Movie m = new Movie("Blade Runner", List.of(), 1982, 83658, 78, Locale.US);
        assertEquals("Movies/Blade Runner (1982)/Blade Runner (1982)", format("{plex}", m, new File("br.mkv")));
        assertEquals("Movies/Blade Runner (1982) {tmdb-78}/Blade Runner (1982)", format("{plex.id}", m, new File("br.mkv")));
        assertEquals("TV Shows/The Office/Season 02/The Office - S02E03 - Office Olympics", format("{plex}", EP, FILE));
    }

    @Test
    void multiEpisode() throws Exception {
        Episode e4 = new Episode("The Office", 2, 4, "The Fire", 10, null, null, 2, SERIES);
        MultiEpisode multi = new MultiEpisode(List.of(EP, e4));
        assertEquals("The Office - S02E03-E04 - Office Olympics & The Fire", format("{n} - {s00e00} - {t}", multi, FILE));
    }

    @Test
    void syntaxErrors() {
        assertThrows(FormatException.class, () -> new ExpressionFormat("{n"));
        assertThrows(FormatException.class, () -> new ExpressionFormat("n}"));
        FormatException e = assertThrows(FormatException.class, () -> format("{vf}", EP, FILE));
        assertTrue(e.getMessage().startsWith("Expression yields empty value"));
    }

    @Test
    void illegalCharactersAreRemoved() throws Exception {
        Episode e = new Episode("What If...?", 1, 1, "What If... Captain Carter Were the First Avenger?", null, null, null, 1, SERIES);
        assertEquals("What If... - S01E01 - What If... Captain Carter Were the First Avenger", format("{n} - {s00e00} - {t}", e, FILE));
    }

    /** Bean with a variable named like the imported class com.mediarenamer.web.XEM. */
    public static class XemBean {
        @Define("XEM")
        public java.util.Map<String, Object> getXem() {
            return java.util.Map.of("scene", "S01E02");
        }
    }

    @Test
    void variablesNamedLikeImportedClassesReadTheVariable() throws Exception {
        assertEquals("S01E02", new ExpressionFormat("{XEM.scene}").format(new XemBean()));
        assertEquals("S01E02", new ExpressionFormat("{any{XEM.scene}{'none'}}").format(new XemBean()));
        // a block that declares the name keeps its own variable
        assertEquals("local", new ExpressionFormat("{def XEM = 'local'; XEM}").format(new XemBean()));
        assertEquals("x", new ExpressionFormat("{['x'].collect { XEM -> XEM }.join()}").format(new XemBean()));
    }
}
