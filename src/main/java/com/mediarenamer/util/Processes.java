package com.mediarenamer.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs external tools (MediaInfo CLI, fpcalc) with a time limit. */
public final class Processes {

    private Processes() {
    }

    /**
     * Runs the command and returns its output (stdout + stderr, UTF-8). The output is read in another thread so a
     * tool that writes a lot cannot block; if it does not finish in time it is killed and an IOException is thrown.
     */
    public static String run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        p.getOutputStream().close();
        CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
            try (InputStream in = p.getInputStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                return new byte[0];
            }
        });
        try {
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                throw new IOException(command.get(0) + ": no answer after " + timeout.toSeconds() + " s");
            }
            return new String(output.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException(command.get(0) + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            p.destroyForcibly();
            throw e;
        }
    }
}
