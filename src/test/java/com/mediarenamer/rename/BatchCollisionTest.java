package com.mediarenamer.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two files of the same run with the same target: the second one fails and nothing is replaced, whatever the
 * conflict policy. A target that is another file of the run still to be renamed is never replaced either.
 */
class BatchCollisionTest {

    @BeforeAll
    static void isolateHistory(@TempDir Path data) {
        System.setProperty("mediarenamer.data", data.toString());
    }

    private static File file(Path dir, String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content).toFile();
    }

    private static RenameEngine.Entry entry(File from, File to) {
        return new RenameEngine.Entry(from, null, to, null);
    }

    @Test
    void secondFileWithTheSameTargetFails(@TempDir Path dir) throws IOException {
        for (ConflictAction policy : List.of(ConflictAction.OVERRIDE, ConflictAction.INDEX, ConflictAction.LARGEST, ConflictAction.SKIP)) {
            Path d = Files.createDirectories(dir.resolve(policy.name()));
            File a = file(d, "a.mkv", "AAA");
            File b = file(d, "b.mkv", "BBBBBB");
            File target = d.resolve("Target.mkv").toFile();
            RenameEngine.Result r = RenameEngine.execute(List.of(entry(a, target), entry(b, target)), RenameAction.MOVE, policy, null);
            assertEquals(1, r.renamed().size(), policy.name());
            assertTrue(r.failed().containsKey(b), policy.name());
            assertEquals("AAA", Files.readString(target.toPath()), policy.name());
            assertTrue(b.exists(), "the second file stays where it was: " + policy);
        }
    }

    @Test
    void duplicateIsReportedInTestMode(@TempDir Path dir) throws IOException {
        File a = file(dir, "a.mkv", "A");
        File b = file(dir, "b.mkv", "B");
        File target = dir.resolve("T.mkv").toFile();
        RenameEngine.Result r = RenameEngine.execute(List.of(entry(a, target), entry(b, target)), RenameAction.TEST, ConflictAction.OVERRIDE, null);
        assertEquals(1, r.renamed().size());
        assertTrue(r.failed().containsKey(b));
        assertTrue(a.exists() && b.exists() && !target.exists());
    }

    @Test
    void caseOnlyDifferenceIsTheSameTargetOnWindows(@TempDir Path dir) throws IOException {
        if (!RenameEngine.CASE_INSENSITIVE_FS) {
            return;
        }
        File a = file(dir, "a.mkv", "A");
        File b = file(dir, "b.mkv", "B");
        RenameEngine.Result r = RenameEngine.execute(List.of(entry(a, dir.resolve("T.mkv").toFile()), entry(b, dir.resolve("t.mkv").toFile())),
                RenameAction.MOVE, ConflictAction.OVERRIDE, null);
        assertTrue(r.failed().containsKey(b));
        assertTrue(b.exists());
    }

    @Test
    void pendingSourceIsNeverReplaced(@TempDir Path dir) throws IOException {
        // a -> b while b is still to be renamed to c: replacing b would lose it
        File a = file(dir, "a.mkv", "A");
        File b = file(dir, "b.mkv", "B");
        File c = dir.resolve("c.mkv").toFile();
        RenameEngine.Result r = RenameEngine.execute(List.of(entry(a, b), entry(b, c)), RenameAction.MOVE, ConflictAction.OVERRIDE, null);
        assertTrue(r.failed().containsKey(a));
        assertEquals("A", Files.readString(a.toPath()));
        assertEquals("B", Files.readString(c.toPath()));
        assertFalse(b.exists());
    }

    @Test
    void replacingAnExistingFileOutsideTheRunStillWorks(@TempDir Path dir) throws IOException {
        File a = file(dir, "a.mkv", "NEW");
        File existing = file(dir, "Target.mkv", "OLD");
        RenameEngine.Result r = RenameEngine.execute(List.of(entry(a, existing)), RenameAction.MOVE, ConflictAction.OVERRIDE, null);
        assertEquals(1, r.renamed().size());
        assertEquals("NEW", Files.readString(existing.toPath()));
    }

    @Test
    void indexSkipsTargetsReservedByTheRun(@TempDir Path dir) throws IOException {
        File existing = file(dir, "T.mkv", "X");
        File reserved = dir.resolve("T (1).mkv").toFile();
        assertEquals(dir.resolve("T (2).mkv").toFile(), RenameEngine.indexed(existing, f -> f.equals(reserved)));
        assertThrows(IOException.class, () -> RenameEngine.indexed(existing, f -> true));
    }
}
