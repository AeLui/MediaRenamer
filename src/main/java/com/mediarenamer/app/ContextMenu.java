package com.mediarenamer.app;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.rename.Preset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * "MediaRenamer" submenu of the Explorer context menu for files and folders (Windows 11: under "Show more options"):
 * <i>Add to MediaRenamer</i> and one entry per profile, which opens the window with the selection and applies the
 * profile. It lives in the user's registry ({@code HKCU\Software\Classes}): {@code *\shell\MediaRenamer} and
 * {@code Directory\shell\MediaRenamer} point to the entries under {@code MediaRenamer.Menu}. It is rewritten when the
 * window opens and when the profiles change, only for an installed copy: the uninstaller removes it.
 * <p>
 * Explorer starts one process per selected file ({@link SingleInstance} gathers them) and hides the entries for more
 * than 15 files; "Send to" passes any number at once.
 */
public final class ContextMenu {

    static final String ROOT = "HKEY_CURRENT_USER\\Software\\Classes\\";
    static final String MENU = "MediaRenamer.Menu";
    /** ECF_SEPARATORBEFORE: a line between "Add to MediaRenamer" and the profiles. */
    private static final int SEPARATOR_BEFORE = 0x20;

    private ContextMenu() {
    }

    /** Rewrites the menu in the background with these profiles; nothing outside an installed copy on Windows. */
    public static void sync(List<Preset> profiles) {
        Path exe = installedExe();
        if (exe == null) {
            return;
        }
        String reg = regFile(exe, tr("contextMenu.add"), profiles);
        Thread t = new Thread(() -> write(reg), "context-menu");
        t.setDaemon(true);
        t.start();
    }

    /** Rewrites it now (CLI {@code -contextmenu}, run by the installer); false outside an installed copy. */
    public static boolean syncNow(List<Preset> profiles) {
        Path exe = installedExe();
        if (exe == null) {
            return false;
        }
        return write(regFile(exe, tr("contextMenu.add"), profiles));
    }

    /**
     * MediaRenamer.exe of an installed copy: next to the running launcher (the window or the console one) and to the
     * uninstaller. A portable copy or the development build leaves the registry alone.
     */
    static Path installedExe() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return null;
        }
        String app = System.getProperty("jpackage.app-path");
        if (app == null) {
            return null;
        }
        Path dir = Paths.get(app).toAbsolutePath().getParent();
        Path exe = dir.resolve("MediaRenamer.exe");
        try (Stream<Path> files = Files.list(dir)) {
            boolean installed = files.anyMatch(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).matches("unins\\d+\\.exe"));
            return installed && Files.isRegularFile(exe) ? exe : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Contents of the .reg file that replaces the menu (the old profile entries are deleted first). */
    static String regFile(Path exe, String addLabel, List<Preset> profiles) {
        String exePath = exe.toAbsolutePath().toString();
        String icon = exePath + ",0";
        StringBuilder r = new StringBuilder("Windows Registry Editor Version 5.00\r\n\r\n");
        r.append("[-").append(ROOT).append(MENU).append("]\r\n\r\n");
        for (String type : List.of("*", "Directory")) {
            key(r, type + "\\shell\\MediaRenamer");
            value(r, "MUIVerb", Settings.APP_NAME);
            value(r, "Icon", icon);
            value(r, "ExtendedSubCommandsKey", MENU);
            r.append("\r\n");
        }
        entry(r, "00add", addLabel, icon, 0, "\"" + exePath + "\" \"%1\"");
        for (int i = 0; i < profiles.size(); i++) {
            Preset p = profiles.get(i);
            entry(r, String.format("p%03d", i + 1), p.name(), null, i == 0 ? SEPARATOR_BEFORE : 0,
                    "\"" + exePath + "\" -open --profile \"" + p.id() + "\" \"%1\"");
        }
        return r.toString();
    }

    private static void entry(StringBuilder r, String name, String label, String icon, int flags, String command) {
        String key = MENU + "\\shell\\" + name;
        key(r, key);
        // "&" marks the access key in a menu text: doubled, it is shown as is
        value(r, "MUIVerb", label.replace("&", "&&"));
        if (icon != null) {
            value(r, "Icon", icon);
        }
        if (flags != 0) {
            r.append("\"CommandFlags\"=dword:").append(String.format("%08x", flags)).append("\r\n");
        }
        r.append("\r\n");
        key(r, key + "\\command");
        r.append("@=").append(quote(command)).append("\r\n\r\n");
    }

    private static void key(StringBuilder r, String key) {
        r.append('[').append(ROOT).append(key).append("]\r\n");
    }

    private static void value(StringBuilder r, String name, String data) {
        r.append(quote(name)).append('=').append(quote(data)).append("\r\n");
    }

    /** A .reg string: backslashes and quotes escaped; line breaks would end it, so they become spaces. */
    static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("[\\r\\n]", " ") + "\"";
    }

    /** Imports the .reg file with reg.exe (UTF-16 with BOM, the format regedit writes). */
    private static synchronized boolean write(String reg) {
        Path file = null;
        try {
            file = Files.createTempFile("MediaRenamer-menu", ".reg");
            Files.write(file, ("﻿" + reg).getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("reg", "import", file.toString()).redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                Logging.warn("Context menu: reg import did not finish");
                return false;
            }
            if (p.exitValue() != 0) {
                Logging.warn("Context menu: reg import exit code " + p.exitValue());
                return false;
            }
            return true;
        } catch (IOException e) {
            Logging.warn("Context menu: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // a temporary file
                }
            }
        }
    }
}
