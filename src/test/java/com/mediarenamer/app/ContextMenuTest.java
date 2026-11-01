package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.rename.Preset;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The Explorer context menu: "Add to MediaRenamer" and one entry per profile, written as a .reg file. */
class ContextMenuTest {

    private static final Path EXE = Path.of("C:\\Program Files\\MediaRenamer\\MediaRenamer.exe");

    @Test
    void menuForFilesAndFoldersWithAddAndOneEntryPerProfile() {
        List<Preset> profiles = List.of(
                new Preset("id-1", "Series para Plex", "{n}", "TheMovieDB::TV", null, null, null),
                new Preset("id-2", "Pelis & \"4K\"", "{n}", "TheMovieDB", null, null, null));
        String reg = ContextMenu.regFile(EXE, "Añadir a MediaRenamer", profiles);

        assertTrue(reg.startsWith("Windows Registry Editor Version 5.00\r\n"));
        // the old profile entries are deleted before writing the current ones
        assertTrue(reg.contains("[-HKEY_CURRENT_USER\\Software\\Classes\\MediaRenamer.Menu]"));
        for (String type : List.of("*", "Directory")) {
            assertTrue(reg.contains("[HKEY_CURRENT_USER\\Software\\Classes\\" + type + "\\shell\\MediaRenamer]\r\n"
                    + "\"MUIVerb\"=\"MediaRenamer\"\r\n"
                    + "\"Icon\"=\"C:\\\\Program Files\\\\MediaRenamer\\\\MediaRenamer.exe,0\"\r\n"
                    + "\"ExtendedSubCommandsKey\"=\"MediaRenamer.Menu\"\r\n"), type);
        }
        String menu = "[HKEY_CURRENT_USER\\Software\\Classes\\MediaRenamer.Menu\\shell\\";
        assertTrue(reg.contains(menu + "00add]\r\n\"MUIVerb\"=\"Añadir a MediaRenamer\"\r\n"));
        assertTrue(reg.contains(menu + "00add\\command]\r\n"
                + "@=\"\\\"C:\\\\Program Files\\\\MediaRenamer\\\\MediaRenamer.exe\\\" \\\"%1\\\"\"\r\n"));
        // profiles in their order, the first one after a separator
        assertTrue(reg.contains(menu + "p001]\r\n\"MUIVerb\"=\"Series para Plex\"\r\n\"CommandFlags\"=dword:00000020\r\n"));
        assertTrue(reg.contains(menu + "p001\\command]\r\n"
                + "@=\"\\\"C:\\\\Program Files\\\\MediaRenamer\\\\MediaRenamer.exe\\\" -open --profile \\\"id-1\\\" \\\"%1\\\"\"\r\n"));
        // "&" would be an access key and quotes would end the string
        assertTrue(reg.contains(menu + "p002]\r\n\"MUIVerb\"=\"Pelis && \\\"4K\\\"\"\r\n\r\n"));
    }

    @Test
    void withoutProfilesOnlyAdd() {
        String reg = ContextMenu.regFile(EXE, "Add to MediaRenamer", List.of());
        assertTrue(reg.contains("00add"));
        assertTrue(!reg.contains("p001"));
    }

    @Test
    void quoteEscapesForRegFiles() {
        assertEquals("\"a\\\\b \\\"c\\\" d\"", ContextMenu.quote("a\\b \"c\"\nd"));
    }

    @Test
    void outsideAnInstalledCopyTheRegistryIsLeftAlone() {
        // tests run from the classes, without jpackage.app-path or an uninstaller
        assertNull(ContextMenu.installedExe());
    }
}
