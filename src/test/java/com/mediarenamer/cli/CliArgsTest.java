package com.mediarenamer.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Profiles were called presets up to 2.0: the old option names still work. */
class CliArgsTest {

    @Test
    void oldPresetOptionNamesAreAliases() {
        Cli.Args a = Cli.Args.parse(new String[] { "-rename", "D:/in", "--Preset", "Movies", "--db", "TMDb" });
        assertEquals("Movies", a.get("--profile"));
        assertEquals("TMDb", a.get("--db"));
        assertTrue(Cli.Args.parse(new String[] { "-presets" }).flags.contains("-profiles"));
    }
}
