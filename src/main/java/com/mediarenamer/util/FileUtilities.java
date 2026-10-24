package com.mediarenamer.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** File name utilities. */
public final class FileUtilities {

    /** Illegal characters in file names (pattern: [\\/:*?"<>|]). */
    public static final Pattern ILLEGAL_CHARACTERS = Pattern.compile("[\\\\/:*?\"<>|\\r\\n]|\\p{Cntrl}");
    private static final Pattern PATH_SEPARATORS = Pattern.compile("[\\\\/]+");

    private FileUtilities() {
    }

    public static String replacePathSeparators(String s, String replacement) {
        return PATH_SEPARATORS.matcher(s).replaceAll(replacement);
    }

    /** Unifies separators to '/' keeping the UNC prefix (\\server). */
    public static String normalizePathSeparators(String path) {
        if (path.startsWith("\\\\")) {
            return "\\\\" + String.join("/", PATH_SEPARATORS.split(path.substring(2)));
        }
        return String.join("/", PATH_SEPARATORS.split(path, -1));
    }

    /** Cleans a file name: removes illegal characters and leftover spaces and dots at the ends. */
    public static String validateFileName(String name) {
        String s = ILLEGAL_CHARACTERS.matcher(name).replaceAll("");
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("(?<=[^.])[.]+$", ""); // Windows does not allow trailing dots
        return s.trim();
    }

    /** Validates each segment of a path, respecting the drive letter (C:) and the UNC prefix. */
    public static String validateFilePath(String path) {
        String prefix = "";
        String rest = path;
        if (rest.startsWith("\\\\")) {
            prefix = "\\\\";
            rest = rest.substring(2);
        } else if (rest.matches("^[A-Za-z]:[\\\\/].*")) {
            prefix = rest.substring(0, 3).replace('\\', '/');
            rest = rest.substring(3);
        } else if (rest.startsWith("/")) {
            prefix = "/";
            rest = rest.substring(1);
        }
        List<String> parts = new ArrayList<>();
        for (String seg : rest.split("[\\\\/]")) {
            String v = validateFileName(seg);
            if (!v.isEmpty()) {
                parts.add(v);
            }
        }
        return prefix + String.join("/", parts);
    }

    /**
     * Writes a UTF-8 text file atomically: first a temporary sibling, then a rename over the target. A crash or a
     * full disk halfway leaves the previous file intact instead of a truncated one.
     */
    public static void writeAtomically(Path file, String content) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String getExtension(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot <= 0 ? null : n.substring(dot + 1);
    }

    public static String getNameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /** Human-readable size: 1.5 GB */
    public static String formatSize(long bytes) {
        if (bytes >= 1L << 30) {
            return String.format("%.1f GB", bytes / (double) (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format("%.1f MB", bytes / (double) (1L << 20));
        }
        if (bytes >= 1L << 10) {
            return String.format("%d KB", bytes >> 10);
        }
        return bytes + " bytes";
    }
}
