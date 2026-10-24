package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One row per renamed file (history window) and the CSV export. */
class HistoryTest {

    @Test
    void entriesAreOneRowPerFileMostRecentSequenceFirst(@TempDir Path dir) {
        History h = new History(dir.resolve("history.xml"));
        Map<File, File> first = new LinkedHashMap<>();
        first.put(new File(dir.toFile(), "a.mkv"), new File(dir.toFile(), "A (2001).mkv"));
        first.put(new File(dir.toFile(), "b.mkv"), new File(dir.toFile(), "B (2002).mkv"));
        h.add(first);
        h.add(Map.of(new File(dir.toFile(), "c.mkv"), new File(dir.toFile(), "C (2003).mkv")));

        List<History.Entry> e = h.entries();
        assertEquals(3, e.size());
        assertEquals("c.mkv", e.get(0).element().source().getName());
        assertEquals("a.mkv", e.get(1).element().source().getName());
        assertEquals("B (2002).mkv", e.get(2).element().target().getName());
        // it is stored: a new instance reads the same rows
        History again = new History(dir.resolve("history.xml"));
        again.load();
        assertEquals(3, again.entries().size());
    }

    @Test
    void csvQuotesOnlyWhenNeeded() throws IOException {
        OffsetDateTime date = OffsetDateTime.of(2026, 10, 24, 9, 5, 7, 0, ZoneOffset.UTC);
        List<History.Entry> entries = List.of(
                new History.Entry(date, new History.Element("C:/in", "Movie, The.mkv", "Z:/Movies/The \"Movie\" (2001).mkv")),
                new History.Entry(date, new History.Element("C:/in", "plain.mkv", "plain (2002).mkv")));
        StringWriter w = new StringWriter();
        History.writeCsv(entries, new String[] { "Date", "Source", "Target" }, w);
        String[] lines = w.toString().replace(File.separatorChar, '/').split("\r\n");
        assertEquals("Date,Source,Target", lines[0]);
        assertEquals("2026-10-24 09:05:07,\"C:/in/Movie, The.mkv\",\"Z:/Movies/The \"\"Movie\"\" (2001).mkv\"", lines[1]);
        assertEquals("2026-10-24 09:05:07,C:/in/plain.mkv,C:/in/plain (2002).mkv", lines[2]);
    }

    @Test
    void removedActionsCopyInsteadOfMoving() {
        assertEquals(RenameAction.COPY, RenameAction.forName("HARDLINK"));
        assertEquals(RenameAction.COPY, RenameAction.forName("duplicate"));
        assertEquals(RenameAction.COPY, RenameAction.forName("Clone"));
        assertEquals(RenameAction.SYMLINK, RenameAction.forName("symlink"));
        assertEquals(List.of(RenameAction.MOVE, RenameAction.COPY, RenameAction.KEEPLINK, RenameAction.SYMLINK, RenameAction.TEST),
                List.of(RenameAction.values()));
    }
}
