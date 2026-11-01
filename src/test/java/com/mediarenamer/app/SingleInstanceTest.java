package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A later start hands its files (and the profile chosen in the context menu) to the window already open. */
class SingleInstanceTest {

    @TempDir
    Path dir;

    private static SingleInstance.Request files(String profile, String... paths) {
        return new SingleInstance.Request(java.util.Arrays.stream(paths).map(File::new).toList(), profile);
    }

    @Test
    void withoutARunningWindowNothingIsForwarded() throws Exception {
        SingleInstance si = new SingleInstance(dir.resolve("i.sock"));
        assertFalse(si.send(files(null, "a.mkv")));
        // a file left over by a window closed abruptly: nobody answers
        Files.writeString(dir.resolve("i.sock"), "");
        assertFalse(si.send(files(null, "a.mkv")));
    }

    @Test
    void theRunningWindowReceivesTheFiles() throws Exception {
        Path socket = dir.resolve("i.sock");
        // left over by a previous run: listening replaces it
        Files.writeString(socket, "");
        BlockingQueue<SingleInstance.Request> received = new ArrayBlockingQueue<>(4);
        new SingleInstance(socket).start(received::add);

        SingleInstance.Request request = files(null, "D:\\Series\\Mi serie 1x01 – Piloto.mkv", "D:\\Películas");
        assertTrue(new SingleInstance(socket).send(request));
        assertEquals(request, received.poll(5, TimeUnit.SECONDS));
        // again: the server keeps serving later starts, also without files (only brings the window forward)
        assertTrue(new SingleInstance(socket).send(files(null)));
        assertEquals(files(null), received.poll(5, TimeUnit.SECONDS));
        // with the profile chosen in the context menu
        assertTrue(new SingleInstance(socket).send(files("4f0c-id", "D:\\a.mkv")));
        assertEquals(files("4f0c-id", "D:\\a.mkv"), received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void theFirstOfSimultaneousStartsOpensTheWindowAndTheOthersWaitForIt() throws Exception {
        Path socket = dir.resolve("i.sock");
        BlockingQueue<SingleInstance.Request> received = new ArrayBlockingQueue<>(4);
        SingleInstance first = new SingleInstance(socket);
        SingleInstance second = new SingleInstance(socket);
        // the first owns the lock but does not listen yet (still starting): the second waits, then gives up
        assertTrue(first.tryLock());
        long t = System.currentTimeMillis();
        assertFalse(second.handOff(files("p", "D:\\b.mkv"), received::add, 300));
        assertTrue(System.currentTimeMillis() - t >= 300);
        // once it listens, a waiting start hands its files over
        first.start(received::add);
        assertTrue(new SingleInstance(socket).handOff(files("p", "D:\\c.mkv"), r -> { }, 5000));
        assertEquals(files("p", "D:\\c.mkv"), received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void theOnlyStartOpensTheWindow() throws Exception {
        BlockingQueue<SingleInstance.Request> received = new ArrayBlockingQueue<>(4);
        Path socket = dir.resolve("i.sock");
        assertFalse(new SingleInstance(socket).handOff(files(null, "D:\\a.mkv"), received::add, 5000));
        assertTrue(new SingleInstance(socket).send(files(null, "D:\\b.mkv")));
        assertEquals(files(null, "D:\\b.mkv"), received.poll(5, TimeUnit.SECONDS));
    }
}
