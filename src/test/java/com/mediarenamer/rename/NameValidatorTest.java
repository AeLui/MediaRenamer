package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

class NameValidatorTest {

    private static RenameEngine.Entry entry(String target) {
        return new RenameEngine.Entry(new File("C:/in/" + Math.abs(target.hashCode()) + ".mkv"), null, new File(target), null);
    }

    private static NameValidator.Issue only(String target) {
        List<NameValidator.Issue> issues = NameValidator.validate(List.of(entry(target)));
        assertEquals(1, issues.size(), "issues of " + target);
        return issues.get(0);
    }

    @Test
    void cleanNamesHaveNoIssues() {
        assertTrue(NameValidator.validate(List.of(entry("Z:/Series/Dark (2017)/Dark - 1x01 - Secretos.mkv"), entry("Z:/Películas/Mufasa - El rey león (2024).mkv")))
                .isEmpty());
    }

    @Test
    void illegalCharactersAreErrorsWithSuggestion() {
        NameValidator.Issue i = only("Z:/Películas/Mission: Impossible? (1996).mkv");
        assertEquals(NameValidator.Severity.ERROR, i.severity());
        assertEquals("Mission - Impossible (1996).mkv", i.suggestion().getName());
    }

    @Test
    void reservedNamesAndTrailingDots() {
        assertEquals("CON_.mkv", only("Z:/Pelis/CON.mkv").suggestion().getName());
        NameValidator.Issue dots = only("Z:/Pelis/What If... /x.mkv");
        assertEquals(NameValidator.Severity.ERROR, dots.severity());
        assertEquals(new File("Z:/Pelis/What If/x.mkv").getAbsolutePath(), dots.suggestion().getAbsolutePath());
    }

    @Test
    void specialCharactersAreWarnings() {
        NameValidator.Issue i = only("Z:/Pelis/Amélie ✨ (2001)\u00A0[HD].mkv");
        assertEquals(NameValidator.Severity.WARNING, i.severity());
        // accents are kept; the emoji and the odd space are cleaned
        assertEquals("Amélie (2001) [HD].mkv", i.suggestion().getName());
        // "fake" slash (homoglyph) that services sometimes return
        assertEquals("AC-DC Live.mkv", NameValidator.cleanSegment("AC\u2215DC Live.mkv", true));
    }

    @Test
    void lookalikeColonFromServices() {
        assertEquals("Star Wars - Episodio IV (1977).mkv", NameValidator.cleanSegment("Star Wars\u2236 Episodio IV (1977).mkv", true));
    }

    @Test
    void duplicatesInTheSameBatch() {
        List<NameValidator.Issue> issues = NameValidator.validate(List.of(entry("Z:/Pelis/Alien (1979).mkv"),
                new RenameEngine.Entry(new File("C:/in/other.mkv"), null, new File("Z:/Pelis/Alien (1979).mkv"), null)));
        assertEquals(1, issues.size());
        assertEquals("Alien (1979) (1).mkv", issues.get(0).suggestion().getName());
    }

    @Test
    void tooLongNamesAreTruncatedKeepingExtension() {
        String longName = "A".repeat(300) + ".spa.srt";
        NameValidator.Issue i = only("Z:/Pelis/" + longName);
        assertEquals(NameValidator.Severity.ERROR, i.severity());
        assertTrue(i.suggestion().getName().endsWith(".spa.srt"));
        assertTrue(i.suggestion().getAbsolutePath().length() <= NameValidator.MAX_PATH);
    }

    @Test
    void noSuggestionWhenNothingToChange() {
        assertNull(NameValidator.validate(List.of(entry("Z:/Pelis/Ok.mkv"))).stream().findFirst().map(NameValidator.Issue::suggestion).orElse(null));
    }
}
