package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * "If it exists" policies. The test files are not media files, so quality is
 * decided by size (last criterion of the quality order). The TEST or COPY action is used so as not
 * to move anything outside the temporary directory.
 */
class ConflictTest {

    @BeforeAll
    static void isolateHistory(@TempDir Path data) {
        // the history (COPY action) is written to a temporary folder, not the user's
        System.setProperty("mediarenamer.data", data.toString());
    }

    private static File file(Path dir, String name, int size) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, new byte[size]);
        return p.toFile();
    }

    private static RenameEngine.Decision decide(File from, File to, ConflictAction c) throws IOException {
        return RenameEngine.decide(from, to, null, c, null, false);
    }

    @Test
    void sizeAndQualityPolicies(@TempDir Path dir) throws IOException {
        File small = file(dir, "small.mkv", 100);
        File big = file(dir, "big.mkv", 1000);
        assertEquals(RenameEngine.Decision.REPLACE, decide(big, small, ConflictAction.LARGEST));
        assertEquals(RenameEngine.Decision.KEEP_EXISTING, decide(small, big, ConflictAction.LARGEST));
        assertEquals(RenameEngine.Decision.REPLACE, decide(small, big, ConflictAction.SMALLEST));
        assertEquals(RenameEngine.Decision.KEEP_EXISTING, decide(big, small, ConflictAction.SMALLEST));
        assertEquals(RenameEngine.Decision.REPLACE, decide(big, small, ConflictAction.BEST_QUALITY));
        assertEquals(RenameEngine.Decision.REPLACE, decide(small, big, ConflictAction.LOWEST_QUALITY));
        // tie: the existing one is kept
        File same = file(dir, "same.mkv", 100);
        assertEquals(RenameEngine.Decision.KEEP_EXISTING, decide(same, small, ConflictAction.LARGEST));
        assertEquals(RenameEngine.Decision.KEEP_EXISTING, decide(same, small, ConflictAction.BEST_QUALITY));
    }

    @Test
    void cliAliases() {
        assertEquals(ConflictAction.BEST_QUALITY, ConflictAction.forName("auto"));
        assertEquals(ConflictAction.LOWEST_QUALITY, ConflictAction.forName("worst"));
        assertEquals(ConflictAction.SMALLEST, ConflictAction.forName("smallest"));
        assertEquals(ConflictAction.ASK, ConflictAction.forName("ASK"));
        assertEquals(ConflictAction.SKIP, ConflictAction.forName("desconocido"));
        // the interface uses "Ask each time" as the default option and first in the list
        assertEquals(ConflictAction.ASK, ConflictAction.forName(null, ConflictAction.ASK));
        assertEquals(ConflictAction.ASK, ConflictAction.values()[0]);
    }

    @Test
    void testModeNeverTouchesTheDisk(@TempDir Path dir) throws IOException {
        File source = file(dir, "nuevo.mkv", 1000);
        File existing = file(dir, "Destino.mkv", 10);
        AtomicInteger asked = new AtomicInteger();
        for (ConflictAction c : List.of(ConflictAction.OVERRIDE, ConflictAction.BEST_QUALITY, ConflictAction.LARGEST, ConflictAction.ASK)) {
            RenameEngine.execute(List.of(new RenameEngine.Entry(source, null, existing, null)), RenameAction.TEST, c, (s, e, m) -> {
                asked.incrementAndGet();
                return RenameEngine.Decision.REPLACE;
            }, null);
        }
        assertTrue(source.exists());
        assertEquals(10, existing.length(), "the existing one has not been touched");
        assertEquals(0, asked.get(), "in test mode nobody is asked");
    }

    @Test
    void cancellationStopsBetweenFiles(@TempDir Path dir) throws IOException {
        File a = file(dir, "a.mkv", 10);
        File b = file(dir, "b.mkv", 10);
        File c = file(dir, "c.mkv", 10);
        Path out = dir.resolve("out");
        AtomicInteger processed = new AtomicInteger();
        List<RenameEngine.Entry> entries = List.of(new RenameEngine.Entry(a, null, out.resolve("A.mkv").toFile(), null),
                new RenameEngine.Entry(b, null, out.resolve("B.mkv").toFile(), null), new RenameEngine.Entry(c, null, out.resolve("C.mkv").toFile(), null));
        // cancelled after the first file
        RenameEngine.Result r = RenameEngine.execute(entries, RenameAction.COPY, ConflictAction.SKIP, null, (i, n, f) -> processed.set(i),
                () -> processed.get() >= 1);
        assertTrue(r.cancelled());
        assertEquals(1, r.renamed().size());
        assertTrue(out.resolve("A.mkv").toFile().exists());
        assertFalse(out.resolve("B.mkv").toFile().exists());
    }

    @Test
    void askUsesTheResolverDecision(@TempDir Path dir) throws IOException {
        File source = file(dir, "nuevo.mkv", 1000);
        File existing = file(dir, "Destino.mkv", 10);
        RenameEngine.Result r = RenameEngine.execute(List.of(new RenameEngine.Entry(source, null, existing, null)), RenameAction.COPY, ConflictAction.ASK,
                (s, e, m) -> RenameEngine.Decision.KEEP_BOTH, null);
        assertEquals(new File(dir.toFile(), "Destino (1).mkv"), r.renamed().get(source));
        assertTrue(new File(dir.toFile(), "Destino (1).mkv").exists());
        assertEquals(10, existing.length());

        File other = file(dir, "other.mkv", 5);
        RenameEngine.Result keep = RenameEngine.execute(List.of(new RenameEngine.Entry(other, null, existing, null)), RenameAction.COPY, ConflictAction.ASK,
                (s, e, m) -> RenameEngine.Decision.KEEP_EXISTING, null);
        assertTrue(keep.renamed().isEmpty());
        assertEquals(List.of(other), keep.skipped());
        assertFalse(new File(dir.toFile(), "Destino (2).mkv").exists());
    }
}
