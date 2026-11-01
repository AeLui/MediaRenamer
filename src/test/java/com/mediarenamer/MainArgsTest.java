package com.mediarenamer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.app.SingleInstance;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Files dropped on the exe arrive as bare paths and open the UI, as does {@code -open --profile} (Explorer context
 * menu); anything else with an option is the command line.
 */
class MainArgsTest {

    @Test
    void noArgumentsOpensTheEmptyWindow() {
        assertEquals(new SingleInstance.Request(List.of(), null), Main.openRequest(new String[0]));
    }

    @Test
    void onlyPathsOpenTheWindowWithThem() {
        assertEquals(new SingleInstance.Request(List.of(new File("D:\\Series\\a b.mkv"), new File("D:\\Películas")), null),
                Main.openRequest(new String[] { "D:\\Series\\a b.mkv", "D:\\Películas" }));
    }

    @Test
    void contextMenuProfileOpensTheWindowAndAppliesIt() {
        assertEquals(new SingleInstance.Request(List.of(new File("D:\\a.mkv")), "1b2c"),
                Main.openRequest(new String[] { "-open", "--profile", "1b2c", "D:\\a.mkv" }));
        assertTrue(Main.openRequest(new String[] { "-Open", "D:\\a.mkv" }).profile() == null);
    }

    @Test
    void anyOtherOptionGoesToTheCommandLine() {
        assertNull(Main.openRequest(new String[] { "-rename", "D:\\in" }));
        assertNull(Main.openRequest(new String[] { "D:\\in", "--action", "test" }));
        assertNull(Main.openRequest(new String[] { "-help" }));
        // a profile without -open: a command line without its command, the CLI shows the usage
        assertNull(Main.openRequest(new String[] { "--profile", "Movies", "D:\\in" }));
    }
}
