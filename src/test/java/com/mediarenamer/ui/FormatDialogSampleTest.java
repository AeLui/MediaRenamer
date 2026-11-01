package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mediarenamer.format.ExpressionFormat;
import com.mediarenamer.format.MediaBindingBean;
import com.mediarenamer.rename.Mode;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.Movie;

import org.junit.jupiter.api.Test;

/** The format editor previews invented samples (no real titles or online ids) when no match is selected. */
class FormatDialogSampleTest {

    private static String format(String expression, Mode mode) throws Exception {
        return new ExpressionFormat(expression).format(new MediaBindingBean(FormatDialog.fictionalSample(mode), FormatDialog.fictionalSampleFile(mode)));
    }

    @Test
    void movieSampleIsInvented() throws Exception {
        assertEquals("Glass Harbor (2024)", format("{n} ({y})", Mode.Movie));
        Movie m = (Movie) FormatDialog.fictionalSample(Mode.Movie);
        // no ids: the preview never loads the data of a real title from TheMovieDB
        assertEquals(0, m.getTmdbId());
        assertEquals(0, m.getImdbId());
    }

    @Test
    void episodeSampleIsInvented() throws Exception {
        assertEquals("Lanterns of Vell - S01E01 - The Ferryman's Toll", format("{n} - {s00e00} - {t}", Mode.Episode));
        Episode e = (Episode) FormatDialog.fictionalSample(Mode.Episode);
        assertNull(e.getId());
        assertEquals(0, e.getSeriesInfo().getId());
    }

    @Test
    void musicSampleIsInvented() throws Exception {
        assertEquals("Marlow Reyes - Northbound Static", format("{artist} - {t}", Mode.Music));
    }

    @Test
    void sampleFilesMatchTheSamples() {
        assertEquals("Glass.Harbor.2024.1080p.BluRay.x264-SPARKS.mkv", FormatDialog.fictionalSampleFile(Mode.Movie).getName());
        assertEquals("Lanterns.of.Vell.S01E01.720p.BluRay.x264-DEMAND.mkv", FormatDialog.fictionalSampleFile(Mode.Episode).getName());
        assertEquals("03 northbound static.mp3", FormatDialog.fictionalSampleFile(Mode.Music).getName());
    }
}
