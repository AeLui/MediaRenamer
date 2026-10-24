package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Lines of the AniDB anime-titles dump: "aid|type|language|title"; comments and malformed lines are skipped. */
class AniDBTitleDumpTest {

    @Test
    void validLines() {
        assertArrayEquals(new String[] { "1", "1", "x-jat", "Seikai no Monshou" }, AniDBClient.parseTitleLine("1|1|x-jat|Seikai no Monshou"));
        assertArrayEquals(new String[] { "52", "4", "en", "Fate|Zero" }, AniDBClient.parseTitleLine("52|4|en|Fate|Zero"));
        assertArrayEquals(new String[] { "7", "2", "ja", "星界の紋章" }, AniDBClient.parseTitleLine("7|2|ja|星界の紋章"));
    }

    @Test
    void commentsAndMalformedLinesAreSkipped() {
        for (String line : new String[] { null, "", "   ", "# created: Fri Oct 24 2026", "1|1|x-jat", "x|1|en|Title", "1|y|en|Title", "1|1||Title",
                "1|1|en|", "99999999999999|1|en|Overflow" }) {
            assertNull(AniDBClient.parseTitleLine(line), String.valueOf(line));
        }
    }
}
