package com.mediarenamer.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/** Application data folders. */
public final class AppFolders {

    /** Name of the data folder before the application was renamed to MediaRenamer (1.x). */
    static final String LEGACY_NAME = "FileRenamer";

    private static volatile boolean migrationChecked;

    private AppFolders() {
    }

    /**
     * User data folder: %APPDATA%/MediaRenamer or ~/.mediarenamer. The first time, the data of the old
     * FileRenamer folder is copied into it (see {@link #migrateLegacy(Path, Path)}).
     */
    public static Path data() {
        String override = System.getProperty("mediarenamer.data");
        if (override != null) {
            return Paths.get(override);
        }
        String appdata = System.getenv("APPDATA");
        Path data = appdata != null
                ? Paths.get(appdata, Settings.APP_NAME)
                : Paths.get(System.getProperty("user.home"), ".mediarenamer");
        if (!migrationChecked) {
            synchronized (AppFolders.class) {
                if (!migrationChecked) {
                    Path legacy = appdata != null
                            ? Paths.get(appdata, LEGACY_NAME)
                            : Paths.get(System.getProperty("user.home"), ".filerenamer");
                    migrateLegacy(legacy, data);
                    migrationChecked = true;
                }
            }
        }
        return data;
    }

    /**
     * Folder of the application itself: the one of MediaRenamer.exe (jpackage) or of the jar; null if unknown (e.g.
     * running from the classes). External tools are looked for in its {@code lib} folder, never in the working
     * directory: a {@code lib\MediaInfo.dll} planted in a downloaded folder must not be loaded.
     */
    public static Path installDir() {
        String exe = System.getProperty("jpackage.app-path");
        if (exe != null) {
            return Paths.get(exe).toAbsolutePath().getParent();
        }
        try {
            Path jar = Paths.get(AppFolders.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(jar) ? jar.toAbsolutePath().getParent() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static Path cache() {
        return data().resolve("cache");
    }

    public static Path history() {
        return data().resolve("history.xml");
    }

    /**
     * Copies the data of the old folder (preferences, encrypted API keys, presets, history and cache) when the new
     * folder does not exist yet. The old folder is kept untouched as a backup. The copy goes to a temporary sibling
     * folder that is renamed at the end, so an interrupted copy is repeated on the next start. The DPAPI keys are
     * re-encrypted by {@link Settings} when it loads the file.
     *
     * @return true if the data was copied
     */
    static boolean migrateLegacy(Path legacy, Path target) {
        if (Files.exists(target) || !Files.isDirectory(legacy)) {
            return false;
        }
        Path tmp = target.resolveSibling(target.getFileName() + ".migrating");
        try {
            deleteTree(tmp);
            try (Stream<Path> all = Files.walk(legacy)) {
                for (Path p : (Iterable<Path>) all::iterator) {
                    Path dest = tmp.resolve(legacy.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            Logging.info(I18n.tr("log.dataMigrated", legacy, target));
            return true;
        } catch (IOException e) {
            Logging.warn(I18n.tr("log.dataMigrateFailed", legacy, target, e));
            return false;
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> all = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) all.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }
}
