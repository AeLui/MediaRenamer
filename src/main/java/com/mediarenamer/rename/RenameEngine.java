package com.mediarenamer.rename;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.format.ExpressionFileFormat;
import com.mediarenamer.format.FormatException;
import com.mediarenamer.format.MediaBindingBean;
import com.mediarenamer.media.MediaTypes;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.web.AudioTrack;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.Movie;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes the new names (folder + format + extension) and runs the rename with handling of
 * conflicts and history.
 */
public final class RenameEngine {

    /** Formats per type of matched object. */
    public record Formats(String episode, String movie, String music, String file) {
        public static Formats fromSettings() {
            return new Formats(Mode.Episode.getFormat(), Mode.Movie.getFormat(), Mode.Music.getFormat(), Mode.File.getFormat());
        }

        public static Formats all(String format) {
            return new Formats(format, format, format, format);
        }

        String forObject(Object o) {
            if (o instanceof Episode) {
                return episode;
            }
            if (o instanceof Movie) {
                return movie;
            }
            if (o instanceof AudioTrack) {
                return music;
            }
            return file;
        }
    }

    /** Result of formatting a match: target or error. */
    public record Entry(File source, Object match, File target, String error) {
        public boolean ok() {
            return target != null;
        }
    }

    /** Decision for a target that already exists. */
    public enum Decision {
        /** The new one replaces the existing one (the existing one goes to the recycle bin). */
        REPLACE,
        /** The existing one is kept and the new one is not renamed. */
        KEEP_EXISTING,
        /** Both are kept: the new one gets an index " (1)". */
        KEEP_BOTH,
        /** Stop the process (nothing else is renamed). */
        CANCEL
    }

    /** Asks the user what to do with a conflict ("Ask each time" option). */
    @FunctionalInterface
    public interface ConflictResolver {
        Decision resolve(File source, File existing, Object match);
    }

    private static final Set<String> KNOWN_EXTENSIONS = known();

    private static Set<String> known() {
        Set<String> s = new java.util.HashSet<>();
        s.addAll(MediaTypes.VIDEO);
        s.addAll(MediaTypes.SUBTITLE);
        s.addAll(MediaTypes.AUDIO);
        s.addAll(MediaTypes.IMAGE);
        s.addAll(MediaTypes.NFO);
        return s;
    }

    private RenameEngine() {
    }

    /** Formats using the target folders from Preferences (without a preset). */
    public static List<Entry> format(List<Match<Object, File>> matches, Formats formats) {
        return format(matches, formats, Destinations::forType);
    }

    /**
     * Computes the targets. The format result can be a name ("Series - S01E01 - Title"),
     * a relative path ("{ny}/{n} - {sxe}") or an absolute path.
     * @param baseDir base folder for each matched object (preset/Preferences); null = the file's folder
     */
    public static List<Entry> format(List<Match<Object, File>> matches, Formats formats, Function<Object, File> baseDir) {
        Map<File, Object> context = new LinkedHashMap<>();
        for (Match<Object, File> m : matches) {
            context.put(m.getCandidate(), m.getValue());
        }
        Map<String, ExpressionFileFormat> compiled = new LinkedHashMap<>();
        List<Entry> result = new ArrayList<>();
        for (Match<Object, File> m : matches) {
            File source = m.getCandidate();
            String expr = formats.forObject(m.getValue());
            try {
                ExpressionFileFormat f = compiled.get(expr);
                if (f == null) {
                    f = new ExpressionFileFormat(expr);
                    compiled.put(expr, f);
                }
                String name = f.format(new MediaBindingBean(m.getValue(), source, context));
                File base = baseDir == null ? null : baseDir.apply(m.getValue());
                result.add(new Entry(source, m.getValue(), resolve(source, name, base), null));
            } catch (FormatException e) {
                result.add(new Entry(source, m.getValue(), null, e.getMessage()));
            }
        }
        return result;
    }

    /**
     * Joins the format result with the base folder and the original extension.
     * <ul>
     * <li>Extension: the original file's one is always added (with the language for subtitles, ".spa.srt"),
     * unless the format already ends with a known extension (".mkv", ".srt"...): then the
     * format rules and may change it.</li>
     * <li>Folder: absolute path in the format => as is; otherwise baseDir/format; without baseDir,
     * the original file's folder.</li>
     * </ul>
     */
    public static File resolve(File source, String formatted, File baseDir) {
        String name = withExtension(source, formatted);
        File f = new File(name);
        if (isAbsolute(name)) {
            return f;
        }
        File dir = baseDir != null ? baseDir : source.getAbsoluteFile().getParentFile();
        return new File(dir, name);
    }

    static boolean isAbsolute(String path) {
        return new File(path).isAbsolute() || path.matches("^[A-Za-z]:[\\\\/].*") || path.startsWith("\\\\") || path.startsWith("/");
    }

    /** Does the format already set the extension? (ends with a known .ext or the original file's one) */
    static boolean hasExplicitExtension(File source, String formatted) {
        String last = formatted.replace('\\', '/');
        last = last.substring(last.lastIndexOf('/') + 1);
        String ext = MediaTypes.extension(last);
        if (ext == null) {
            return false;
        }
        String original = MediaTypes.extension(source);
        return KNOWN_EXTENSIONS.contains(ext) || ext.equalsIgnoreCase(original);
    }

    static String withExtension(File source, String formatted) {
        if (hasExplicitExtension(source, formatted)) {
            return formatted;
        }
        String ext = extensionWithLanguage(source);
        if (ext == null) {
            return formatted;
        }
        // if the format already includes part of the extension (e.g. {subt} -> ".spa"), only the rest is added
        String[] parts = ext.split("\\.");
        String lower = formatted.toLowerCase(Locale.ROOT);
        for (int i = 1; i < parts.length; i++) {
            String head = "." + String.join(".", Arrays.copyOfRange(parts, 0, parts.length - i)).toLowerCase(Locale.ROOT);
            if (lower.endsWith(head)) {
                return formatted + "." + String.join(".", Arrays.copyOfRange(parts, parts.length - i, parts.length));
            }
        }
        return formatted + "." + ext;
    }

    /** Extension to keep. For subtitles the language suffix ".spa.srt" is kept too. */
    static String extensionWithLanguage(File source) {
        String ext = MediaTypes.extension(source);
        if (ext == null) {
            return null;
        }
        if (MediaTypes.isSubtitle(source)) {
            String base = com.mediarenamer.util.FileUtilities.getNameWithoutExtension(source.getName());
            Matcher m = Pattern.compile("[.]((?:[a-z]{2,3})(?:[-_][A-Za-z]{2})?(?:[.](?:forced|sdh|hi|cc))?)$", Pattern.CASE_INSENSITIVE).matcher(base);
            if (m.find() && !m.group(1).matches("\\d+")) {
                return m.group(1) + "." + ext;
            }
        }
        return source.getName().substring(source.getName().lastIndexOf('.') + 1);
    }

    /** Result of a run. cancelled = the user stopped the process (or chose "Cancel all"). */
    public record Result(Map<File, File> renamed, Map<File, String> failed, List<File> skipped, boolean cancelled) {
    }

    /** Progress: file about to be processed (index 1..total). */
    @FunctionalInterface
    public interface Progress {
        void update(int index, int total, File current);
    }

    public static Result execute(List<Entry> entries, RenameAction action, ConflictAction conflict, BiConsumer<Integer, Integer> progress) {
        return execute(entries, action, conflict, null, progress);
    }

    public static Result execute(List<Entry> entries, RenameAction action, ConflictAction conflict, ConflictResolver resolver,
            BiConsumer<Integer, Integer> progress) {
        return execute(entries, action, conflict, resolver, progress == null ? null : (i, n, f) -> progress.accept(i, n), null);
    }

    /**
     * Runs the renames in order. Saves a sequence in the history with what was done (except in TEST).
     * In TEST the disk is not touched: conflicts are evaluated and only what would be done is reported
     * (with "Ask each time" nobody is asked; a would-be conflict is reported).
     * @param resolver  for ConflictAction.ASK; null = keep the existing one
     * @param progress  optional callback before each file
     * @param cancelled checked between files (a file is never interrupted halfway); null = not cancellable
     */
    public static Result execute(List<Entry> entries, RenameAction action, ConflictAction conflict, ConflictResolver resolver, Progress progress,
            java.util.function.BooleanSupplier cancelled) {
        Map<File, File> renamed = new LinkedHashMap<>();
        Map<File, String> failed = new LinkedHashMap<>();
        List<File> skipped = new ArrayList<>();
        boolean test = action == RenameAction.TEST;
        // targets this run has already produced: a later entry never replaces (or is indexed onto) one of them
        Set<String> produced = new java.util.HashSet<>();
        // sources not processed yet: replacing one of them would throw away a file this run still has to rename
        Set<String> pending = new java.util.HashSet<>();
        entries.forEach(x -> pending.add(targetKey(x.source())));
        int i = 0;
        for (Entry e : entries) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                Logging.info(tr("log.cancelledAfter", renamed.size()));
                return finish(action, renamed, failed, skipped, true);
            }
            i++;
            if (progress != null) {
                progress.update(i, entries.size(), e.source());
            }
            if (!e.ok()) {
                failed.put(e.source(), e.error());
                continue;
            }
            File from = e.source();
            File to = e.target();
            pending.remove(targetKey(from));
            if (from.getAbsolutePath().equals(to.getAbsolutePath())) {
                skipped.add(from);
                continue;
            }
            if (produced.contains(targetKey(to))) {
                String msg = tr("log.duplicateTarget", to);
                Logging.warn("[" + action + "] " + tr("log.errorIn", from, msg));
                failed.put(from, msg);
                if (conflict == ConflictAction.FAIL) {
                    break;
                }
                continue;
            }
            try {
                boolean sameFileOtherCase = CASE_INSENSITIVE_FS && from.getAbsolutePath().equalsIgnoreCase(to.getAbsolutePath());
                if (to.exists() && !sameFileOtherCase) {
                    Decision d = decide(from, to, e.match(), conflict, resolver, test);
                    String tag = "[" + (test ? "TEST " : "") + conflict.name() + "] ";
                    switch (d) {
                        case CANCEL -> {
                            Logging.info(tag + tr("log.cancelled"));
                            return finish(action, renamed, failed, skipped, true);
                        }
                        case KEEP_EXISTING -> {
                            Logging.info(tag + tr("log.keepExisting", to));
                            skipped.add(from);
                            continue;
                        }
                        case KEEP_BOTH -> to = indexed(to, f -> produced.contains(targetKey(f)));
                        case REPLACE -> {
                            if (pending.contains(targetKey(to))) {
                                throw new IOException(tr("log.targetPending", to));
                            }
                            if (test) {
                                Logging.info(tag + tr("log.wouldReplace", to));
                            } else {
                                trash(to);
                            }
                        }
                    }
                }
                File result = action.rename(from, to);
                Logging.info("[" + action + "] " + from + " -> " + result);
                renamed.put(from, result);
                produced.add(targetKey(result));
            } catch (IOException | RuntimeException ex) {
                Logging.warn("[" + action + "] " + tr("log.errorIn", from, ex.getMessage()));
                failed.put(from, ex.getMessage());
                if (conflict == ConflictAction.FAIL) {
                    break;
                }
            }
        }
        return finish(action, renamed, failed, skipped, false);
    }

    private static Result finish(RenameAction action, Map<File, File> renamed, Map<File, String> failed, List<File> skipped, boolean cancelled) {
        if (action != RenameAction.TEST && !renamed.isEmpty()) {
            History.get().add(renamed);
        }
        return new Result(renamed, failed, skipped, cancelled);
    }

    /** Applies the conflict policy. "New" wins => REPLACE; tie => the existing one is kept. */
    static Decision decide(File from, File to, Object match, ConflictAction conflict, ConflictResolver resolver, boolean test) throws IOException {
        return switch (conflict) {
            case SKIP -> Decision.KEEP_EXISTING;
            case OVERRIDE -> Decision.REPLACE;
            case INDEX -> Decision.KEEP_BOTH;
            case FAIL -> throw new IOException(tr("log.targetExists", to));
            case BEST_QUALITY -> Quality.compare(from, to) > 0 ? Decision.REPLACE : Decision.KEEP_EXISTING;
            case LOWEST_QUALITY -> Quality.compare(from, to) < 0 ? Decision.REPLACE : Decision.KEEP_EXISTING;
            case LARGEST -> from.length() > to.length() ? Decision.REPLACE : Decision.KEEP_EXISTING;
            case SMALLEST -> from.length() < to.length() ? Decision.REPLACE : Decision.KEEP_EXISTING;
            case ASK -> {
                if (test || resolver == null) {
                    Logging.info("[" + (test ? "TEST " : "") + "ASK] " + tr("log.wouldAsk", to));
                    yield Decision.KEEP_EXISTING;
                }
                Decision d = resolver.resolve(from, to, match);
                yield d == null ? Decision.KEEP_EXISTING : d;
            }
        };
    }

    /** Windows and macOS file systems ignore the case: "a.mkv" and "A.mkv" are the same file. */
    static final boolean CASE_INSENSITIVE_FS = !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");

    /** Key that identifies a target path within one run. */
    static String targetKey(File f) {
        String p = f.getAbsoluteFile().toPath().normalize().toString();
        return CASE_INSENSITIVE_FS ? p.toLowerCase(Locale.ROOT) : p;
    }

    static File indexed(File to) throws IOException {
        return indexed(to, f -> false);
    }

    /** First free "name (n).ext"; taken = targets reserved by this run that do not exist on disk yet (TEST). */
    static File indexed(File to, java.util.function.Predicate<File> taken) throws IOException {
        String name = to.getName();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        String base = name.substring(0, name.length() - ext.length());
        for (int n = 1; n < 10000; n++) {
            File f = new File(to.getParentFile(), base + " (" + n + ")" + ext);
            if (!f.exists() && !taken.test(f)) {
                return f;
            }
        }
        // never fall back to the original target: that would overwrite the existing file
        throw new IOException(tr("log.targetExists", to));
    }

    /**
     * Recycle bin if the system supports it; otherwise (no desktop, network drives...) the file is deleted for good,
     * which is logged as a warning so it never happens silently.
     */
    static void trash(File f) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            if (Desktop.getDesktop().moveToTrash(f)) {
                return;
            }
        }
        Logging.warn(tr("log.deletedForGood", f));
        Files.delete(f.toPath());
    }

    /** Undoes a history sequence (target -> source). Returns the restored files. */
    public static List<File> revert(List<History.Element> elements) {
        List<File> restored = new ArrayList<>();
        for (History.Element e : elements) {
            File target = e.target();
            File source = e.source();
            try {
                if (!target.exists()) {
                    Logging.warn(tr("log.notFound", target));
                    continue;
                }
                if (source.exists()) {
                    Logging.warn(tr("log.originalExists", source));
                    continue;
                }
                RenameAction.MOVE.rename(target, source);
                restored.add(source);
                Logging.info("[REVERT] " + target + " -> " + source);
            } catch (IOException ex) {
                Logging.warn("[REVERT] " + target + ": " + ex.getMessage());
            }
        }
        return restored;
    }
}
