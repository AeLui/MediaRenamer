package com.mediarenamer.cli;

import static com.mediarenamer.app.I18n.plural;
import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.mediarenamer.media.MediaInfo;
import com.mediarenamer.rename.ConflictAction;
import com.mediarenamer.rename.Destinations;
import com.mediarenamer.rename.NameValidator;
import com.mediarenamer.rename.Quality;
import com.mediarenamer.rename.History;
import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.Preset;
import com.mediarenamer.rename.Presets;
import com.mediarenamer.rename.RenameAction;
import com.mediarenamer.rename.RenameEngine;
import com.mediarenamer.rename.match.AutoCompleteMatcher;
import com.mediarenamer.rename.match.Selector;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.EpisodeListProvider;
import com.mediarenamer.web.SearchResult;
import com.mediarenamer.web.SortOrder;
import com.mediarenamer.web.WebServices;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Command line interface:
 *
 * <pre>
 * MediaRenamer -rename &lt;files|folders&gt; [--db TheMovieDB] [--format "{n} ({y})"] [--action move|copy|test|...]
 *             [--conflict skip|override|auto|index|fail] [--lang es-ES] [--order Airdate|DVD|Absolute]
 *             [--q "query"] [--output folder] [-r] [-non-strict]
 * MediaRenamer -list --db TheTVDB --q "Dark" [--format "{n} - {s00e00} - {t}"] [--order DVD] [--lang es]
 * MediaRenamer -mediainfo &lt;file&gt; [--format "{vf} {vc} {ac}"]
 * MediaRenamer -history
 * </pre>
 */
public final class Cli {

    private Cli() {
    }

    public static int run(String[] args) {
        Args a = Args.parse(args);
        if (a.flags.contains("-debug")) {
            Logging.setDebug(true);
        }
        try {
            if (a.flags.contains("-rename")) {
                return rename(a);
            }
            if (a.flags.contains("-list")) {
                return list(a);
            }
            if (a.flags.contains("-mediainfo")) {
                return mediainfo(a);
            }
            if (a.flags.contains("-history")) {
                return history();
            }
            if (a.flags.contains("-profiles")) {
                return presets();
            }
            usage();
            return 1;
        } catch (Settings.MissingApiKeyException e) {
            System.err.println(e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println(tr("common.error", e.getMessage()));
            if (a.flags.contains("-debug")) {
                e.printStackTrace();
            }
            return 3;
        }
    }

    /** Usage help, in the interface language (Preferences; otherwise the system's). */
    static void usage() {
        System.out.println(tr("cli.usage"));
    }

    private static int rename(Args a) throws Exception {
        List<File> files = collect(a.values, a.flags.contains("-r"));
        if (files.isEmpty()) {
            System.err.println(tr("cli.noInput"));
            return 1;
        }
        if (a.get("--profile") != null && !applyPreset(a)) {
            return 1;
        }
        Datasource db = a.get("--db") == null ? null : WebServices.get(a.get("--db"));
        if (a.get("--db") != null && db == null && !a.get("--db").equalsIgnoreCase("file")) {
            System.err.println(tr("cli.unknownDb", a.get("--db")));
            return 1;
        }
        if (db == null) {
            db = guessDatasource(files);
        }
        Mode mode = a.get("--db") != null && a.get("--db").equalsIgnoreCase("file") ? Mode.File : Mode.forDatasource(db);
        boolean strict = !a.flags.contains("-non-strict");
        Locale locale = a.get("--lang") == null ? Settings.get().getLocale() : Locale.forLanguageTag(a.get("--lang"));
        SortOrder order = SortOrder.forName(a.get("--order"));

        System.out.println(tr("cli.renameUsing", mode, mode == Mode.File ? "file" : db.getIdentifier()));
        AutoCompleteMatcher matcher = mode == Mode.File ? Mode.matcher(Mode.LOCAL) : Mode.matcher(db);
        List<Match<Object, File>> matches = matcher.match(files,
                new AutoCompleteMatcher.Options(strict, order, locale, new CliSelector(strict), () -> false, a.get("--q")));

        String format = a.get("--format");
        RenameEngine.Formats formats = format == null ? RenameEngine.Formats.fromSettings() : RenameEngine.Formats.all(format);
        // target = folder / format; folder: --output (or the preset's) > Preferences > the file's own
        String output = a.get("--output");
        List<RenameEngine.Entry> entries = RenameEngine.format(matches, formats, m -> Destinations.resolve(m, output));
        for (File f : files) {
            if (matches.stream().noneMatch(m -> m.getCandidate().equals(f))) {
                System.out.println("[" + tr("cli.noMatch") + "] " + f.getName());
            }
        }
        // pre-check of the names; with -clean the suggested corrections are applied
        List<NameValidator.Issue> issues = NameValidator.validate(entries);
        if (!issues.isEmpty()) {
            boolean clean = a.flags.contains("-clean");
            java.util.Map<File, File> fixes = new java.util.HashMap<>();
            for (NameValidator.Issue i : issues) {
                System.out.println("[" + (i.severity() == NameValidator.Severity.ERROR ? "ERROR" : tr("cli.warning")) + "] " + i.entry().target().getName() + ": "
                        + i.problem() + (i.suggestion() == null ? "" : clean ? "  => " + i.suggestion().getName() : "  " + tr("cli.proposal", i.suggestion().getName())));
                if (clean && i.suggestion() != null) {
                    fixes.put(i.entry().source(), i.suggestion());
                }
            }
            if (!clean) {
                System.out.println(tr("cli.useClean"));
            }
            entries = entries.stream().map(e -> fixes.containsKey(e.source()) ? new RenameEngine.Entry(e.source(), e.match(), fixes.get(e.source()), null) : e)
                    .toList();
        }
        RenameAction action = RenameAction.forName(a.get("--action"));
        ConflictAction conflict = ConflictAction.forName(a.get("--conflict"));
        // in TEST conflicts are evaluated too, but without touching the disk
        RenameEngine.Result r = RenameEngine.execute(entries, action, conflict, new ConsoleResolver(), null);
        if (action == RenameAction.TEST) {
            r.renamed().forEach((from, to) -> System.out.println("[TEST] " + from + " -> " + to));
            r.skipped().forEach(f -> System.out.println("[TEST] " + tr("cli.testKeep", f)));
            r.failed().forEach((f, e) -> System.out.println("[ERROR] " + f.getName() + ": " + e));
            return 0;
        }
        System.out.println(tr("cli.processed", plural("count.files", r.renamed().size())) + (r.failed().isEmpty() ? "" : ", " + plural("count.errors", r.failed().size()))
                + (r.skipped().isEmpty() ? "" : ", " + plural("count.skipped", r.skipped().size())));
        return r.failed().isEmpty() ? 0 : 4;
    }

    /** --conflict ask on the console: shows quality and size of both and asks. Without a console, keeps the existing one. */
    static final class ConsoleResolver implements RenameEngine.ConflictResolver {
        private RenameEngine.Decision remembered;

        @Override
        public RenameEngine.Decision resolve(File source, File existing, Object match) {
            if (remembered != null) {
                return remembered;
            }
            java.io.Console console = System.console();
            if (console == null) {
                System.out.println("[ASK] " + tr("cli.ask.noConsole", existing));
                return RenameEngine.Decision.KEEP_EXISTING;
            }
            Quality n = Quality.of(source);
            Quality e = Quality.of(existing);
            console.printf("%n%s%n", tr("cli.ask.exists", existing));
            console.printf("  %-12s %-34s %s%n", "", tr("cli.ask.existing"), tr("cli.ask.new"));
            for (String k : e.describe().keySet()) {
                console.printf("  %-12s %-34s %s%n", Quality.label(k), e.describe().get(k), n.describe().get(k));
            }
            int cmp = Quality.QUALITY.compare(n, e);
            console.printf("  -> %s%n", cmp > 0 ? tr("cli.ask.newBetter") : cmp < 0 ? tr("cli.ask.existingBetter") : tr("cli.ask.equal"));
            // answer letters in the interface language: keep, replace, both, cancel
            String keys = tr("cli.ask.keys");
            while (true) {
                String answer = console.readLine("%s ", tr("cli.ask.prompt"));
                if (answer == null || answer.isBlank()) {
                    continue;
                }
                int choice = keys.indexOf(Character.toLowerCase(answer.trim().charAt(0)));
                RenameEngine.Decision d = switch (choice) {
                    case 0 -> RenameEngine.Decision.KEEP_EXISTING;
                    case 1 -> RenameEngine.Decision.REPLACE;
                    case 2 -> RenameEngine.Decision.KEEP_BOTH;
                    case 3 -> RenameEngine.Decision.CANCEL;
                    default -> null;
                };
                if (d != null) {
                    if (Character.isUpperCase(answer.trim().charAt(0)) && d != RenameEngine.Decision.CANCEL) {
                        remembered = d;
                    }
                    return d;
                }
            }
        }
    }

    /**
     * --profile: fills the options not given explicitly (--db, --format, --order,
     * --lang and the matching mode) with the preset's values.
     */
    static boolean applyPreset(Args a) {
        Preset p = Presets.get().find(a.get("--profile")).orElse(null);
        if (p == null) {
            System.err.println(tr("cli.unknownPreset", a.get("--profile")));
            return false;
        }
        System.out.println(tr("profile.log", p.name()));
        a.options.putIfAbsent("--db", p.database() == null ? "file" : p.database());
        a.options.putIfAbsent("--format", p.format());
        if (p.outputFolder() != null && !p.outputFolder().isBlank()) {
            a.options.putIfAbsent("--output", p.outputFolder());
        }
        if (p.sortOrder() != null) {
            a.options.putIfAbsent("--order", p.sortOrder());
        }
        if (p.language() != null) {
            a.options.putIfAbsent("--lang", p.language());
        }
        if (Boolean.FALSE.equals(p.isStrict()) && !a.flags.contains("-strict")) {
            a.flags.add("-non-strict");
        }
        return true;
    }

    private static int presets() {
        List<Preset> list = Presets.get().list();
        if (list.isEmpty()) {
            System.out.println(tr("cli.noPresets"));
        }
        for (Preset p : list) {
            System.out.println(p.name());
            System.out.println("  " + tr("cli.preset.format", p.format()));
            System.out.println("  " + tr("cli.preset.details", p.database() == null ? "file" : p.database(), p.sortOrder(), p.matchMode(), p.language()));
        }
        return 0;
    }

    /** Without --db: music if they are audio files, episodes if SxE is detected, otherwise movies. */
    static Datasource guessDatasource(List<File> files) {
        long audio = files.stream().filter(com.mediarenamer.media.MediaTypes::isAudio).count();
        if (audio > files.size() / 2) {
            return WebServices.ID3;
        }
        com.mediarenamer.similarity.SeasonEpisodeMatcher m = new com.mediarenamer.similarity.SeasonEpisodeMatcher(true);
        long episodes = files.stream().filter(f -> m.match(f.getName()) != null).count();
        return episodes > files.size() / 2 ? WebServices.TheMovieDB_TV : WebServices.TheMovieDB;
    }

    private static int list(Args a) throws Exception {
        Datasource db = WebServices.get(a.get("--db", "TheMovieDB::TV"));
        if (!(db instanceof EpisodeListProvider p)) {
            System.err.println(tr("cli.list.needsEpisodeDb"));
            return 1;
        }
        Locale locale = a.get("--lang") == null ? Settings.get().getLocale() : Locale.forLanguageTag(a.get("--lang"));
        String q = a.get("--q");
        if (q == null) {
            System.err.println(tr("cli.list.missingQ"));
            return 1;
        }
        List<SearchResult> results = q.matches("\\d+") ? List.of(p.getSeries(Integer.parseInt(q), locale)) : p.search(q, locale);
        if (results.isEmpty()) {
            System.err.println(tr("cli.list.noResults", q));
            return 1;
        }
        SearchResult s = results.get(0);
        String format = a.get("--format", "{n} - {s00e00} - {t}");
        com.mediarenamer.format.ExpressionFormat f = new com.mediarenamer.format.ExpressionFormat(format);
        for (Episode e : p.getEpisodeList(s, SortOrder.forName(a.get("--order")), locale)) {
            System.out.println(f.format(new com.mediarenamer.format.MediaBindingBean(e, null)));
        }
        return 0;
    }

    private static int mediainfo(Args a) throws Exception {
        for (String path : a.values) {
            File f = new File(path);
            if (a.get("--format") != null) {
                System.out.println(new com.mediarenamer.format.ExpressionFormat(a.get("--format")).format(new com.mediarenamer.format.MediaBindingBean(f, f)));
                continue;
            }
            if (!MediaInfo.isAvailable()) {
                System.err.println(tr("cli.mediainfo.missing", Settings.MEDIAINFO_PATH));
                return 2;
            }
            for (Map.Entry<String, List<Map<String, String>>> kind : MediaInfo.read(f).entrySet()) {
                for (Map<String, String> stream : kind.getValue()) {
                    System.out.println("[" + kind.getKey() + "]");
                    stream.forEach((k, v) -> System.out.println("  " + k + " = " + v));
                }
            }
        }
        return 0;
    }

    private static int history() {
        for (History.Sequence s : History.get().getSequences()) {
            System.out.println("# " + s.date());
            for (History.Element e : s.elements()) {
                System.out.println("  " + e.source() + " -> " + e.target());
            }
        }
        return 0;
    }

    static List<File> collect(List<String> paths, boolean recursive) throws IOException {
        List<File> files = new ArrayList<>();
        for (String p : paths) {
            File f = new File(p);
            if (f.isDirectory()) {
                try (Stream<java.nio.file.Path> s = recursive ? Files.walk(f.toPath()) : Files.list(f.toPath())) {
                    s.filter(Files::isRegularFile).map(java.nio.file.Path::toFile).sorted().forEach(files::add);
                }
            } else if (f.isFile()) {
                files.add(f);
            } else {
                System.err.println(tr("log.notFound", p));
            }
        }
        return files;
    }

    /** Non-interactive selection: in strict mode it does not guess, in non-strict mode it takes the first one. */
    static final class CliSelector implements Selector {
        private final boolean strict;

        CliSelector(boolean strict) {
            this.strict = strict;
        }

        @Override
        public <T> T select(String query, List<String> files, List<T> options) {
            if (strict) {
                System.out.println(tr("cli.ambiguous", query, options.subList(0, Math.min(5, options.size()))));
                return null;
            }
            System.out.println(tr("cli.autoSelect", query, options.get(0)));
            return options.get(0);
        }

        @Override
        public String input(String message, String initialValue) {
            return null;
        }
    }

    /** Minimal parser: flags "-x", options "--x value", the rest positional. */
    static final class Args {
        final List<String> flags = new ArrayList<>();
        final java.util.Map<String, String> options = new java.util.LinkedHashMap<>();
        final List<String> values = new ArrayList<>();

        static Args parse(String[] args) {
            Args a = new Args();
            for (int i = 0; i < args.length; i++) {
                String s = args[i];
                if (s.startsWith("--") && i + 1 < args.length) {
                    a.options.put(alias(s.toLowerCase()), args[++i]);
                } else if (s.startsWith("-") && s.length() > 1) {
                    a.flags.add(alias(s.toLowerCase()));
                } else {
                    a.values.add(s);
                }
            }
            return a;
        }

        /** Profiles were called presets up to 2.0: the old option names still work. */
        static String alias(String option) {
            return switch (option) {
                case "-presets" -> "-profiles";
                case "--preset" -> "--profile";
                default -> option;
            };
        }

        String get(String key) {
            return options.get(key);
        }

        String get(String key, String def) {
            return options.getOrDefault(key, def);
        }
    }
}
