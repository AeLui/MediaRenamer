package com.mediarenamer.media;

import com.mediarenamer.similarity.Normalization;
import com.mediarenamer.similarity.SeasonEpisodeMatcher;
import com.mediarenamer.similarity.SxE;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Series name, movie name/year and season folders detected from file names and their folders. */
public final class MediaDetection {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final SeasonEpisodeMatcher LENIENT = new SeasonEpisodeMatcher(false);

    private static final Pattern YEAR = Pattern.compile("(?<![\\p{L}\\p{N}])(?:19|20)\\d{2}(?![\\p{L}\\p{N}])");
    private static final Pattern BRACKETED_YEAR = Pattern.compile("[(\\[]\\s*((?:19|20)\\d{2})\\s*[)\\]]");
    private static final Pattern SQUARE_OR_CURLY = Pattern.compile("\\[[^\\[\\]]*\\]|\\{[^{}]*\\}");
    private static final Pattern ROUND = Pattern.compile("\\([^()]*\\)");
    private static final Pattern EMPTY_BRACKETS = Pattern.compile("\\(\\s*\\)|\\[\\s*\\]|\\{\\s*\\}");
    private static final Pattern DOTS_AND_UNDERSCORES = Pattern.compile("[._]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** Characters trimmed from the end and the start of names (trimmed by hand: a regex anchored at $ is quadratic). */
    private static final String TRAILING_PUNCTUATION = "-–—_.,:;!?([{+&";
    private static final String LEADING_PUNCTUATION = "-–—_.,:;!?)]}+&";
    private static final Pattern YEAR_GAP = Pattern.compile("[\\s\\-()\\[\\]]*");

    private static final String SEASON_WORDS = "season|series|temporada|staffel|saison|stagione";
    private static final Pattern TRAILING_SEASON_WORD = Pattern.compile("(?<![\\s._-])[\\s._-]*(?:" + SEASON_WORDS + ")(?:[\\s._-]*\\d{1,2})?$", FLAGS);

    /** Anime absolute number after " - ": "Frieren - 05", "Show - 12v2". */
    private static final Pattern ANIME_DASH = Pattern.compile("\\s-\\s*\\d{1,4}(?:v\\d)?(?![\\p{L}\\p{N}])", FLAGS);
    /** Anime absolute number after an episode word that follows the title: "Show EP 05". */
    private static final Pattern ANIME_EPISODE_WORD = Pattern.compile("(?<=[\\p{L}\\p{N})\\]])\\s+(?:ep|episode)\\.?\\s*\\d{1,4}(?!\\d)", FLAGS);
    /** Air date written year, month, day: "2024.03.15". */
    /** Compact air date: "20240315". */
    private static final Pattern COMPACT_DATE = Pattern.compile("(?<![\\p{L}\\p{N}])(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])(?![\\p{L}\\p{N}])");
    private static final Pattern DATE = Pattern.compile("(?<![\\p{L}\\p{N}])(?:19|20)\\d{2}[\\s._-]\\d{1,2}[\\s._-]\\d{1,2}(?!\\d)");

    /** "Season 2", "Temporada 03", "Staffel 1", "S04", "Season 2 (2019)"; not "Seasons". */
    /** "CD1", "Disc 2", "DVD1", "Part 1". */
    private static final Pattern DISC_FOLDER = Pattern.compile("^\\s*(?:cd|dis[ck]|dvd|part)[\\s._-]*\\d{1,2}\\s*$", FLAGS);

    private static final Pattern SEASON_FOLDER =Pattern.compile("^\\s*(?:" + SEASON_WORDS + "|s)[\\s._-]*(\\d{1,2})(?![\\p{L}\\p{N}]).*$", FLAGS);

    /** Folder names that never name a series or a movie (checked on the name without accents). */
    private static final Pattern GENERIC_FOLDER = Pattern.compile("^\\s*(?:" + SEASON_WORDS + "|specials?|extras?|featurettes?|bonus|cd|dvd|disc|disk"
            + "|movies|peliculas|films?|tv shows|tv|shows|videos|downloads|descargas|media|torrents|telegram desktop|complete|new folder"
            + "|nueva carpeta|video_ts|bdmv)(?![\\p{L}])", FLAGS);

    /** First standalone number of a file inside a season folder, optionally after an episode word. */
    private static final Pattern EPISODE_NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])(?:(?:episode|episodio|ep|e|cap[ií]tulo|cap)[\\s._-]*)?(\\d{1,3})(?![\\p{L}\\p{N}])", FLAGS);

    private MediaDetection() {
    }

    /** Movie name and year; toString() gives "Name (year)", or "Name" when the year is 0. */
    public record MovieQuery(String name, int year) {

        @Override
        public String toString() {
            return year > 0 ? name + " (" + year + ")" : name;
        }
    }

    // ------------------------------------------------------------------ release name cleanup

    /**
     * Cleans a release name: removes the media extension, embedded checksums, [...] and {...} groups (and (...) in
     * strict mode) and the release tokens of {@link ReleaseInfo}; dots and underscores become spaces. Years are kept.
     *
     * <p>Site words are removed anywhere. Every other release token is removed from the end of the title on: the first
     * strong token (format, source, 3D, repack), the first year after some text or the first season/episode code. Words
     * before that point are part of the title, so "The English Patient" or "Uncut Gems" keep their words.
     */
    public static String stripReleaseInfo(String name, boolean strict) {
        if (name == null) {
            return null;
        }
        String s = withoutMediaExtension(name);
        s = Normalization.removeEmbeddedChecksum(s);
        s = removeAll(SQUARE_OR_CURLY, s);
        if (strict) {
            s = removeAll(ROUND, s);
        }
        s = removeReleaseTokens(s);
        s = DOTS_AND_UNDERSCORES.matcher(s).replaceAll(" ");
        s = removeAll(EMPTY_BRACKETS, s);
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return trimStart(trimEnd(s, "-"), "-");
    }

    /** Removes the pattern repeatedly, so nested groups go too. */
    private static String removeAll(Pattern p, String s) {
        String previous;
        do {
            previous = s;
            s = p.matcher(s).replaceAll(" ");
        } while (!s.equals(previous));
        return s;
    }

    private static String removeReleaseTokens(String s) {
        int end = ReleaseInfo.titleEnd(s);
        boolean[] remove = new boolean[s.length()];
        // a trailing "-GROUP" after the title is release information too
        int group = ReleaseInfo.trailingGroupStart(s.stripTrailing());
        if (group >= end) {
            java.util.Arrays.fill(remove, group, s.length(), true);
        }
        Matcher m = ReleaseInfo.ANY.matcher(s);
        while (m.find()) {
            if (m.start() >= end) {
                java.util.Arrays.fill(remove, m.start(), m.end(), true);
            }
        }
        m = ReleaseInfo.ALWAYS.matcher(s);
        while (m.find()) {
            java.util.Arrays.fill(remove, m.start(), m.end(), true);
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (!remove[i]) {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    /** The name without its extension when it is a video, subtitle or audio extension. */
    private static String withoutMediaExtension(String name) {
        String ext = MediaTypes.extension(name);
        if (ext != null && (MediaTypes.VIDEO.contains(ext) || MediaTypes.SUBTITLE.contains(ext) || MediaTypes.AUDIO.contains(ext))) {
            return MediaTypes.nameWithoutExtension(name);
        }
        return name;
    }

    // ------------------------------------------------------------------ series

    /** Series name from the file name or, when it has none, from the first meaningful parent folder; null if none. */
    public static String detectSeriesName(File file) {
        String stripped = stripReleaseInfo(file.getName(), false);
        int marker = episodeMarker(stripped);
        if (marker > 0) {
            String name = cleanSeriesName(stripped.substring(0, marker));
            if (!name.isEmpty()) {
                return name;
            }
        }
        for (File p = file.getParentFile(); p != null; p = p.getParentFile()) {
            String folder = p.getName();
            if (folder.isBlank() || isGenericFolder(folder) || SEASON_FOLDER.matcher(folder).matches()) {
                continue;
            }
            String s = stripReleaseInfo(folder, false);
            int m = episodeMarker(s);
            String name = cleanSeriesName(m > 0 ? s.substring(0, m) : m == 0 ? "" : s);
            if (!name.isEmpty()) {
                return name;
            }
        }
        if (marker < 0) {
            String name = cleanSeriesName(stripped);
            return name.isEmpty() ? null : name;
        }
        return null;
    }

    /** Start of the first episode marker: an SxE notation, an anime absolute number or an air date; -1 if none. */
    private static int episodeMarker(String s) {
        int i = LENIENT.find(s, 0);
        if (i >= 0) {
            return i;
        }
        for (Pattern p : new Pattern[] { ANIME_DASH, ANIME_EPISODE_WORD, DATE, COMPACT_DATE }) {
            Matcher m = p.matcher(s);
            if (m.find()) {
                return m.start();
            }
        }
        return -1;
    }

    private static String cleanSeriesName(String s) {
        s = trimEnd(stripReleaseInfo(s, false), TRAILING_PUNCTUATION);
        s = trimEnd(TRAILING_SEASON_WORD.matcher(s).replaceAll(""), TRAILING_PUNCTUATION);
        s = trimStart(s, LEADING_PUNCTUATION);
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** Removes whitespace and the given characters from the end. */
    private static String trimEnd(String s, String chars) {
        int end = s.length();
        while (end > 0 && (Character.isWhitespace(s.charAt(end - 1)) || chars.indexOf(s.charAt(end - 1)) >= 0)) {
            end--;
        }
        return s.substring(0, end);
    }

    /** Removes whitespace and the given characters from the start. */
    private static String trimStart(String s, String chars) {
        int start = 0;
        while (start < s.length() && (Character.isWhitespace(s.charAt(start)) || chars.indexOf(s.charAt(start)) >= 0)) {
            start++;
        }
        return s.substring(start);
    }

    /** True for folder names that never name a series or a movie (season, extras, disc folders and library roots). */
    public static boolean isGenericFolder(String name) {
        return name != null && GENERIC_FOLDER.matcher(Normalization.stripDiacritics(name)).find();
    }

    /** Season given by the parent folder ("Season 2", "Temporada 03", "S04"), or null. */
    public static Integer folderSeason(File file) {
        File parent = file.getParentFile();
        if (parent == null) {
            return null;
        }
        Matcher m = SEASON_FOLDER.matcher(parent.getName());
        return m.matches() ? Integer.valueOf(m.group(1)) : null;
    }

    /**
     * File name to use for SxE detection: inside a season folder, a name without its own season gets
     * "S{season}E{number} " in front, where number is its first standalone number ("Season 2/05 - Title.mkv" gives
     * "S02E05 05 - Title.mkv"). Otherwise the name unchanged.
     */
    public static String nameWithParentSeason(File file) {
        String name = file.getName();
        Integer season = folderSeason(file);
        if (season == null) {
            return name;
        }
        String stripped = stripReleaseInfo(name, false);
        List<SxE> own = LENIENT.match(stripped);
        if (own != null && own.stream().anyMatch(SxE::hasSeason)) {
            return name;
        }
        Matcher m = EPISODE_NUMBER.matcher(stripped);
        if (!m.find()) {
            return name;
        }
        return String.format(Locale.ROOT, "S%02dE%02d ", season, Integer.parseInt(m.group(1))) + name;
    }

    /** Files grouped by detected series name, in order of first appearance; names that normalize alike share a key. */
    public static Map<String, List<File>> groupBySeries(List<File> files) {
        Map<String, List<File>> groups = new LinkedHashMap<>();
        Map<String, String> keys = new LinkedHashMap<>();
        for (File f : files) {
            String name = detectSeriesName(f);
            String detected = name == null ? "" : name;
            String key = keys.computeIfAbsent(Normalization.normalize(detected), k -> detected);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }
        return groups;
    }

    // ------------------------------------------------------------------ movies

    /** Movie name and year from the file name, or from its folder when the folder says more; null if nothing. */
    public static MovieQuery detectMovie(File file) {
        MovieQuery fromFile = parseMovie(file.getName());
        File parent = file.getParentFile();
        if (parent != null && DISC_FOLDER.matcher(parent.getName()).matches()) {
            // "Movie (1999)/CD1/movie-cd1.avi": the movie folder is the one above the disc folder
            parent = parent.getParentFile();
        }
        if (parent != null && !parent.getName().isBlank() && !isGenericFolder(parent.getName())) {
            MovieQuery fromFolder = parseMovie(parent.getName());
            if (fromFolder != null && (fromFile == null || fromFile.year() == 0 && fromFolder.year() > 0 || fromFile.name().length() < 3)) {
                return fromFolder;
            }
        }
        return fromFile;
    }

    private static MovieQuery parseMovie(String fileName) {
        String s = DOTS_AND_UNDERSCORES.matcher(withoutMediaExtension(fileName)).replaceAll(" ");
        // 1. a year between brackets after some text: "Title (1982) [1080p]"
        Matcher m = BRACKETED_YEAR.matcher(s);
        while (m.find()) {
            String title = cleanMovieName(s.substring(0, m.start()));
            if (!title.isEmpty()) {
                return new MovieQuery(title, Integer.parseInt(m.group(1)));
            }
        }
        // 2. the first standalone year with text before it; a year right after it wins ("Blade Runner 2049 2017")
        List<int[]> years = new ArrayList<>();
        m = YEAR.matcher(s);
        while (m.find()) {
            years.add(new int[] { m.start(), m.end() });
        }
        for (int i = 0; i < years.size(); i++) {
            if (cleanMovieName(s.substring(0, years.get(i)[0])).isEmpty()) {
                continue;
            }
            while (i + 1 < years.size() && YEAR_GAP.matcher(s.substring(years.get(i)[1], years.get(i + 1)[0])).matches()) {
                i++;
            }
            int[] y = years.get(i);
            return new MovieQuery(cleanMovieName(s.substring(0, y[0])), Integer.parseInt(s.substring(y[0], y[1])));
        }
        // 3. no year
        String title = cleanMovieName(YEAR.matcher(s).replaceAll(" "));
        return title.isEmpty() ? null : new MovieQuery(title, 0);
    }

    private static String cleanMovieName(String s) {
        s = stripReleaseInfo(s, false);
        s = trimEnd(removeAll(EMPTY_BRACKETS, s), TRAILING_PUNCTUATION);
        s = trimStart(s, LEADING_PUNCTUATION);
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    /** The last standalone year 1900-2099 in the name, or 0. */
    public static int detectYear(String name) {
        int year = 0;
        Matcher m = YEAR.matcher(name);
        while (m.find()) {
            year = Integer.parseInt(m.group());
        }
        return year;
    }

    /** Lower case in Locale.ROOT. */
    public static String toLowerCase(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
