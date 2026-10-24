package com.mediarenamer.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.web.Movie;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/** Endless blocks stop by themselves and the compiled-class cache does not grow forever. */
class ExpressionLimitsTest {

    private static final MediaBindingBean BEAN = new MediaBindingBean(new Movie("Avatar", List.of(), 2009, 0, 19995, Locale.US), new File("a.mkv"));

    @Test
    void endlessLoopStopsAfterTheTimeLimit() throws Exception {
        long start = System.nanoTime();
        ExpressionFormat f = new ExpressionFormat("{n} {while (true) { }}");
        assertEquals("Avatar ", f.format(BEAN));
        long seconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
        assertTrue(seconds < ExpressionFormat.BLOCK_TIMEOUT_SECONDS + 5, "took " + seconds + " s");
        assertEquals(1, f.getSuppressed().size());
    }

    @Test
    void interruptedEvaluationStops() throws Exception {
        ExpressionFormat f = new ExpressionFormat("{n} {def i = 0; while (true) { i++ }}");
        Thread t = new Thread(() -> {
            try {
                f.format(BEAN);
            } catch (FormatException e) {
                // not expected: n is never empty
            }
        });
        t.start();
        Thread.sleep(300);
        t.interrupt();
        t.join(3000);
        assertTrue(!t.isAlive(), "the block ignores interrupt()");
    }

    @Test
    void endlessRecursionOnlyEmptiesItsBlock() throws Exception {
        ExpressionFormat f = new ExpressionFormat("{n}{def r; r = { x -> r(x + 1) }; r(0)}");
        assertEquals("Avatar", f.format(BEAN));
        assertEquals(1, f.getSuppressed().size());
        assertTrue(f.getSuppressed().get(0) instanceof StackOverflowError, f.getSuppressed().get(0).toString());
    }

    @Test
    void errorMessagesNameTheBlockWithoutCompilerNoise() {
        try {
            new ExpressionFormat("{n} {n.}");
        } catch (FormatException e) {
            assertTrue(e.getMessage().startsWith("SyntaxError: ") && e.getMessage().endsWith(" in {n.}"), e.getMessage());
            assertTrue(!e.getMessage().contains("startup failed") && !e.getMessage().contains(".groovy"), e.getMessage());
            return;
        }
        throw new AssertionError("no syntax error");
    }

    @Test
    void undefinedVariablesAreReportedByName() throws Exception {
        ExpressionFormat f = new ExpressionFormat("{n}{vs}{nosuchvariable}");
        assertEquals("Avatar", f.format(BEAN));
        assertEquals(2, f.getSuppressed().size());
        for (Throwable t : f.getSuppressed()) {
            assertTrue(t.getMessage().startsWith("Binding \"") && !t.getMessage().contains("FormatBlock"), t.getMessage());
        }
    }

    @Test
    void securityMessagesNameTheBlock() {
        try {
            new ExpressionFormat("{n} {'calc'.execute()}");
        } catch (FormatException e) {
            assertTrue(e.getMessage().startsWith("SecurityError: not allowed in formats") && e.getMessage().endsWith(" in {'calc'.execute()}"), e.getMessage());
            assertTrue(!e.getMessage().contains("@ line"), e.getMessage());
            return;
        }
        throw new AssertionError("not rejected");
    }

    @Test
    void grabIsDisabled() {
        try {
            new ExpressionFormat("{@Grab('commons-lang:commons-lang:2.6') import org.apache.commons.lang.WordUtils; 1}");
        } catch (FormatException e) {
            return;
        }
        throw new AssertionError("@Grab compiled");
    }

    @Test
    void cacheIsResetWhenFull() throws Exception {
        for (int i = 0; i < ExpressionFormat.MAX_COMPILED + 5; i++) {
            new ExpressionFormat("{n + '" + i + "'}");
        }
        assertEquals("Avatar7", new ExpressionFormat("{n + '7'}").format(BEAN));
    }
}
