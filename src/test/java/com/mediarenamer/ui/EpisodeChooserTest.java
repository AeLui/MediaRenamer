package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.similarity.Normalization;
import com.mediarenamer.web.Episode;

import org.junit.jupiter.api.Test;

/** Episode chooser filter: by number (1x02, S01E02, 102, 2) or by title. */
class EpisodeChooserTest {

    private static final Episode EP = new Episode("Dark", 1, 2, "Mentiras", 2, null, null, 1, null);

    private static boolean match(String q) {
        return EpisodeChooserDialog.matches(EP, Normalization.normalize(q));
    }

    @Test
    void byNumber() {
        assertTrue(match("1x02"));
        assertTrue(match("S01E02"));
        assertTrue(match("s01e02"));
        assertTrue(match("2"));
        assertFalse(match("3"));
    }

    @Test
    void byTitle() {
        assertTrue(match("menti"));
        assertTrue(match("Mentiras"));
        assertFalse(match("secretos"));
    }
}
