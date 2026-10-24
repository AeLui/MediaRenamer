package com.mediarenamer.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mediarenamer.web.Movie;

import java.io.File;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/** The format result does not leave the target folder by accident (dot segments, empty leading blocks). */
class FilePathSafetyTest {

    private static final File FILE = new File("Avatar.2009.1080p.mkv");

    private static String format(String expr, String name) throws FormatException {
        return new ExpressionFileFormat(expr).format(new MediaBindingBean(new Movie(name, List.of(), 2009, 0, 19995, Locale.US), FILE));
    }

    @Test
    void dotOnlyValuesDoNotClimbFolders() throws Exception {
        assertEquals("2009", format("{n}/{y}", ".."));
        assertEquals("2009", format("{n}/{y}", "."));
        assertEquals("Movies/2009", format("Movies/{n}/{y}", " .. "));
        assertEquals("Movies/2009", format("Movies/{n.replace('X', '..')}/{y}", "X"));
    }

    @Test
    void dotSegmentsWrittenInBlockCodeDoNotClimbFolders() throws Exception {
        assertEquals("evil/Avatar", format("{'../evil'}/{n}", "Avatar"));
        assertEquals("Movies/x/Avatar", format("Movies/{'..\\\\x'}/{n}", "Avatar"));
        assertEquals("Movies/Avatar", format("Movies/{'../..'}/{n}", "Avatar"));
    }

    @Test
    void controlCharactersAndLineBreaksAreRemoved() throws Exception {
        assertEquals("AvatarX 2009", format("{n + '\\r\\n' + 'X'}\t{y}", "Avatar"));
        assertEquals("Avatar", format("{n + '\\u0007'}", "Avatar"));
    }

    @Test
    void literalParentFolderIsKept() throws Exception {
        assertEquals("../Sorted/Avatar", format("../Sorted/{n}", "Avatar"));
    }

    @Test
    void emptyLeadingBlockDoesNotMakeTheTargetAbsolute() throws Exception {
        // {x} does not exist: without the fix the result was "/Avatar" (root of the drive)
        assertEquals("Avatar", format("{x}/{n}", "Avatar"));
        assertEquals("Avatar/2009", format("{x}{x}/{n}/{y}", "Avatar"));
    }

    @Test
    void absoluteFormatsStillWork() throws Exception {
        assertEquals("Z:/Movies/Avatar (2009)", format("Z:\\Movies\\{n} ({y})", "Avatar"));
        assertEquals("/media/movies/Avatar", format("/media/movies/{n}", "Avatar"));
    }

    @Test
    void ellipsisInsideNamesIsUntouched() throws Exception {
        assertEquals("...And Justice for All (2009)", format("{n} ({y})", "...And Justice for All"));
        assertEquals("Movies/Avatar... (2009)", format("Movies/{n} ({y})", "Avatar..."));
    }
}
