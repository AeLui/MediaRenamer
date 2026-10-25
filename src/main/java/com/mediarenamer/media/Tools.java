package com.mediarenamer.media;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * Check of the path the user gives for an external tool (Preferences → Tools): it must be the tool's own file, or a
 * folder that holds it. Only names and existence are checked; the tool is never run here.
 */
public final class Tools {

    /** Result of {@link #check}. */
    public enum Status {
        /** Empty: the tool is searched automatically. */
        AUTO,
        OK,
        /** The file or folder does not exist. */
        MISSING,
        /** It exists, but it is not the tool (another file, or a folder without it). */
        WRONG
    }

    /** MediaInfo library or command line, Windows names first. */
    public static final List<String> MEDIAINFO = List.of("MediaInfo.dll", "MediaInfo.exe", "libmediainfo.so", "libmediainfo.so.0", "libmediainfo.dylib",
            "libmediainfo.0.dylib", "mediainfo");
    /** Chromaprint fingerprinter. */
    public static final List<String> FPCALC = List.of("fpcalc.exe", "fpcalc");

    private Tools() {
    }

    public static Status check(String configured, List<String> names) {
        if (configured == null || configured.isBlank()) {
            return Status.AUTO;
        }
        Path p;
        try {
            p = Paths.get(configured.trim());
        } catch (InvalidPathException e) {
            return Status.MISSING;
        }
        if (!p.isAbsolute()) {
            // a relative path would depend on the working directory (planted executables)
            return Status.WRONG;
        }
        if (Files.isDirectory(p)) {
            return inFolder(p, names) != null ? Status.OK : Status.WRONG;
        }
        if (!Files.isRegularFile(p)) {
            return Status.MISSING;
        }
        return hasName(p.getFileName().toString(), names) ? Status.OK : Status.WRONG;
    }

    /** The tool's file inside a folder, or null. */
    public static Path inFolder(Path folder, List<String> names) {
        for (String n : names) {
            Path f = folder.resolve(n);
            if (Files.isRegularFile(f)) {
                return f;
            }
        }
        return null;
    }

    public static boolean hasName(String fileName, List<String> names) {
        return names.stream().anyMatch(n -> n.equalsIgnoreCase(fileName));
    }

    /** Names that make sense on this system (for the file chooser and the messages). */
    public static List<String> platformNames(List<String> names) {
        boolean windows = MediaInfo.isWindows();
        List<String> l = names.stream().filter(n -> windows == n.toLowerCase(Locale.ROOT).matches(".*\\.(dll|exe)")).toList();
        return l.isEmpty() ? names : l;
    }
}
