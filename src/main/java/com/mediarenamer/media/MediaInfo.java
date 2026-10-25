package com.mediarenamer.media;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import groovy.json.JsonSlurper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reading of technical properties with MediaInfo: MediaInfo.dll is loaded through JNA
 * requesting JSON output, and if the DLL
 * is not available the "mediainfo" executable (CLI) is used with --Output=JSON.
 *
 * Result: stream type (General, Video, Audio, Text, Image, Menu) -> list of field->value maps.
 */
public final class MediaInfo {

    public interface Lib extends com.sun.jna.Library {
        Pointer MediaInfo_New();

        int MediaInfo_Open(Pointer handle, WString file);

        WString MediaInfo_Option(Pointer handle, WString option, WString value);

        WString MediaInfo_Inform(Pointer handle, int reserved);

        void MediaInfo_Close(Pointer handle);

        void MediaInfo_Delete(Pointer handle);
    }

    private static final Map<String, Map<String, List<Map<String, String>>>> CACHE = new ConcurrentHashMap<>();
    /** A damaged or huge file must not block the analysis forever. */
    static final java.time.Duration CLI_TIMEOUT = java.time.Duration.ofMinutes(2);
    private static volatile Lib lib;
    private static volatile boolean libFailed;

    private MediaInfo() {
    }

    /** File properties; empty map if MediaInfo is not available or the file is not a media file. */
    public static Map<String, List<Map<String, String>>> read(File file) {
        String key = file.getAbsolutePath() + "|" + file.length() + "|" + file.lastModified();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                String json = readJson(file);
                return json == null ? Map.of() : parse(json);
            } catch (Exception e) {
                Logging.warn("MediaInfo: " + file.getName() + ": " + e.getMessage());
                return Map.of();
            }
        });
    }

    /** Value of a field of the first stream of the given type, or null. */
    public static String get(File file, String streamKind, String field) {
        List<Map<String, String>> streams = read(file).get(streamKind);
        if (streams == null || streams.isEmpty()) {
            return null;
        }
        return streams.get(0).get(field);
    }

    public static boolean isAvailable() {
        return library() != null || findCli() != null;
    }

    private static String readJson(File file) throws Exception {
        Lib l = library();
        if (l != null) {
            synchronized (MediaInfo.class) {
                Pointer h = l.MediaInfo_New();
                try {
                    l.MediaInfo_Option(h, new WString("Output"), new WString("JSON"));
                    l.MediaInfo_Option(h, new WString("Complete"), new WString("1"));
                    if (l.MediaInfo_Open(h, new WString(file.getAbsolutePath())) == 0) {
                        return null;
                    }
                    WString s = l.MediaInfo_Inform(h, 0);
                    l.MediaInfo_Close(h);
                    return s == null ? null : s.toString();
                } finally {
                    l.MediaInfo_Delete(h);
                }
            }
        }
        Path cli = findCli();
        if (cli != null) {
            return com.mediarenamer.util.Processes.run(List.of(cli.toString(), "--Output=JSON", "--Full", file.getAbsolutePath()), CLI_TIMEOUT);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<Map<String, String>>> parse(String json) {
        Object root = new JsonSlurper().parseText(json);
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        Object media = root instanceof Map<?, ?> m ? m.get("media") : null;
        Object tracks = media instanceof Map<?, ?> mm ? mm.get("track") : null;
        if (tracks instanceof List<?> list) {
            for (Object t : list) {
                if (t instanceof Map<?, ?> track) {
                    Map<String, String> fields = new LinkedHashMap<>();
                    ((Map<String, Object>) track).forEach((k, v) -> {
                        if (v != null && !(v instanceof Map) && !(v instanceof List)) {
                            fields.put(k, v.toString());
                        }
                    });
                    String type = fields.getOrDefault("@type", "General");
                    result.computeIfAbsent(type, x -> new ArrayList<>()).add(fields);
                }
            }
        }
        return result;
    }

    private static Lib library() {
        if (lib == null && !libFailed) {
            synchronized (MediaInfo.class) {
                if (lib == null && !libFailed) {
                    for (Path dll : candidates("MediaInfo.dll")) {
                        try {
                            lib = Native.load(dll.toString(), Lib.class);
                            Logging.debug("MediaInfo: " + dll);
                            break;
                        } catch (Throwable e) {
                            Logging.debug("MediaInfo not loaded from " + dll + ": " + e.getMessage());
                        }
                    }
                    // the system library search is only used outside Windows (libmediainfo.so/.dylib from the system
                    // folders): on Windows LoadLibrary would also look in the working directory
                    if (lib == null && !isWindows()) {
                        try {
                            lib = Native.load("mediainfo", Lib.class);
                        } catch (Throwable e) {
                            Logging.debug("MediaInfo library not found: " + e.getMessage());
                        }
                    }
                    libFailed = lib == null;
                }
            }
        }
        return lib;
    }

    private static Path findCli() {
        for (Path p : candidates(isWindows() ? "MediaInfo.exe" : "mediainfo")) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Where MediaInfo is found when no path is set (library first, then the command line), for the placeholder of
     * Preferences → Tools; null if it is not installed.
     */
    public static Path detectedPath() {
        // outside Windows the library comes from the system search (no path to show): only the command line is looked for
        List<Path> l = new ArrayList<>(isWindows() ? candidates("MediaInfo.dll", false) : List.of());
        l.addAll(candidates(isWindows() ? "MediaInfo.exe" : "mediainfo", false));
        return l.isEmpty() ? null : l.get(0);
    }

    /**
     * Forgets a failed library search and the cached readings, so that a path chosen in Preferences is used without
     * restarting (a library already loaded stays in use until the application is closed).
     */
    public static synchronized void reset() {
        if (lib == null) {
            libFailed = false;
        }
        CACHE.clear();
    }

    /** Candidate absolute paths: user preference, lib next to the application, MediaInfo installation, PATH. */
    static List<Path> candidates(String fileName) {
        return candidates(fileName, true);
    }

    private static List<Path> candidates(String fileName, boolean withConfigured) {
        List<Path> list = new ArrayList<>();
        String configured = withConfigured ? Settings.get().get(Settings.MEDIAINFO_PATH) : null;
        if (configured != null) {
            Path c = Paths.get(configured).toAbsolutePath();
            list.add(Files.isDirectory(c) ? c.resolve(fileName) : c);
        }
        // lib next to the application, never relative to the working directory (DLL planting)
        Path app = com.mediarenamer.app.AppFolders.installDir();
        if (app != null) {
            list.add(app.resolve("lib").resolve(fileName));
        }
        if (isWindows()) {
            for (String env : List.of("ProgramFiles", "ProgramFiles(x86)")) {
                String pf = System.getenv(env);
                if (pf != null) {
                    list.add(Paths.get(pf, "MediaInfo", fileName));
                }
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                // relative PATH entries (".", "lib") would depend on the working directory
                if (!dir.isBlank() && isAbsolutePath(dir)) {
                    list.add(Paths.get(dir, fileName));
                }
            }
        }
        return list.stream().filter(p -> p.isAbsolute() && Files.isRegularFile(p)).toList();
    }

    /** Absolute PATH entry; relative or malformed ones (quotes...) are ignored. */
    public static boolean isAbsolutePath(String dir) {
        try {
            return Paths.get(dir).isAbsolute();
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
