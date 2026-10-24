package com.mediarenamer.media;

import com.mediarenamer.similarity.SeasonEpisodeMatcher;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Release tokens that are not part of a title: edition, 3D layout, repack, format, source, language, subtitle kind,
 * known groups, filler words and extras. The term lists are compiled from the public scene release rules and the
 * Plex, Jellyfin and Kodi naming guides.
 *
 * <p>Every pattern is case-insensitive and only matches whole tokens: a term is never matched inside a longer word of
 * letters and digits ({@code TS} does not match in {@code Tests}). Separators such as {@code .}, {@code _}, {@code -},
 * spaces and brackets delimit tokens.
 */
public final class ReleaseInfo {

    /** Optional separator between the words of a term. */
    private static final String SEP = "[\\W_]?";
    /** Any run of separators between the words of a term. */
    private static final String SEPS = "[\\W_]*";
    private static final String APOSTROPHE_S = "(?:['’`]?s)?";

    static final List<String> EDITION_TERMS = List.of("director" + APOSTROPHE_S + SEPS + "cut", "final" + SEPS + "cut",
            "(?:special|collector" + APOSTROPHE_S + "|anniversary|ultimate|deluxe|definitive|diamond|platinum)" + SEPS + "edition",
            "theatrical(?:" + SEPS + "(?:cut|edition|version))?", "extended(?:" + SEPS + "(?:cut|edition|version))?",
            "alternate" + SEPS + "(?:cut|ending|version)", "criterion(?:" + SEPS + "collection)?", "imax(?:" + SEPS + "edition)?",
            "open" + SEPS + "matte", "unrated", "uncut", "uncensored", "remastered", "restored", "redux");

    static final List<String> S3D_TERMS = List.of("(?:half|full)" + SEP + "(?:sbs|tab|ou)", "[hf]?(?:sbs|tab|ou)");

    static final List<String> REPACK_TERMS = List.of("real" + SEP + "proper", "repack\\d?", "proper", "rerip");

    static final List<String> FORMAT_TERMS = List.of(
            // video codecs
            "[xh]" + SEP + "26[456]", "hevc", "avc", "xvid", "divx", "av1", "vp9", "mpeg" + SEP + "[24]",
            // audio codecs, with an optional channel layout (5.1, 7.1, 2.0)
            "(?:dts(?:" + SEP + "(?:hd|x|es))?(?:" + SEP + "(?:ma|hra))?|(?:e" + SEP + ")?ac" + SEP + "3|ddp?\\+?|dolby" + SEP + "digital(?:" + SEP + "plus)?"
                    + "|truehd|atmos|aac(?:" + SEP + "lc)?|flac|mp3|opus|l?pcm)(?:" + SEP + "[1-9][.,_ ][0-2])?",
            // HDR
            "hdr10(?:\\+|plus)?", "hdr", "dv", "dovi", "dolby" + SEP + "vision", "hlg",
            // resolution, bit depth, frame rate
            "(?:240|360|480|540|576|720|1080|1440|2160|4320)[pi]", "(?:8|10|12)" + SEP + "bits?", "\\d{2,3}fps");

    /** Sources that are only release tokens when they follow the title (they are also ordinary words). */
    static final List<String> AMBIGUOUS_SOURCE_TERMS = List.of("web", "cam", "ts", "tc", "scr", "nf", "max", "hulu", "stan");

    static final List<String> SOURCE_TERMS = List.of(
            // physical media and rips
            "blu" + SEP + "ray(?:" + SEP + "rip)?", "bd" + SEP + "rip", "br" + SEP + "rip", "bd" + SEP + "remux", "remux", "bd(?:25|50|66|100)", "uhd",
            "dvd" + SEP + "rip", "dvd" + SEP + "scr", "dvd(?:r|5|9)", "dvd", "vhs" + SEP + "rip", "laserdisc",
            // TV and web
            "hdtv(?:" + SEP + "rip)?", "pdtv", "sdtv", "tv" + SEP + "rip", "dsr(?:ip)?", "web" + SEP + "dl", "web" + SEP + "rip", "web" + SEP + "hd", "hd" + SEP + "rip",
            // theatrical copies
            "hd" + SEP + "cam", "cam" + SEP + "rip", "hd" + SEP + "ts", "telesync", "hd" + SEP + "tc", "telecine", "screener", "workprint", "r5",
            // streaming services
            "amzn", "dsnp", "atvp", "hmax", "pcok", "pmtp", "crav",
            // Spanish market
            "microhd", "m1080p", "m720p");

    static final List<String> LANGUAGE_TERMS = List.of("multi(?:" + SEP + "(?:audio|subs?|lang))?", "dual(?:" + SEP + "audio)?", "castellano", "latino",
            "spanish", "english", "french", "truefrench", "german", "italian", "japanese", "vostfr", "vost", "vose", "vos", "subbed", "dubbed",
            "subtitulado", "subs?", "spa", "esp", "eng", "lat", "fre", "fra", "ger", "deu", "ita", "jpn",
            "korean", "chinese", "mandarin", "cantonese", "hindi", "russian", "portuguese", "polish", "dutch", "swedish", "danish", "norwegian",
            "finnish", "turkish", "arabic", "thai", "kor", "chi", "rus", "por", "pol", "hin", "dl", "hc", "hardsubs?", "softsubs?");

    static final List<String> SUBTITLE_TERMS = List.of("forced", "sdh", "hi", "cc");

    static final List<String> GROUP_TERMS = List.of("yts(?:" + SEP + "(?:mx|ag|lt))?", "yify", "rarbg", "sparks", "killers", "dimension", "lol", "fgt",
            "flux", "ntb", "cmrg", "evo", "tgx", "etrg", "psa");

    /** Site and filler words that are never part of a title, wherever they are. */
    static final List<String> SITE_TERMS = List.of(
            // a whole site address first: "www.SiteName.com"
            "www[.\\s_][\\p{L}\\p{N}-]{1,40}[.\\s_](?:com|net|org|to|tv|me|io|cc|es|info|xyz|ws|mx|lt|se|ag|ch|nu|eu)",
            "www", "com", "org", "net", "torrents?", "download");

    /** Filler words that are only release tokens after the title. */
    static final List<String> FILLER_TERMS = List.of("complete", "4k", "sdr", "hdr", "internal", "limited", "readnfo");

    public static final Pattern VIDEO_TAGS = tokens(EDITION_TERMS);
    public static final Pattern VIDEO_S3D = tokens(S3D_TERMS);
    public static final Pattern VIDEO_REPACK = tokens(REPACK_TERMS);
    public static final Pattern VIDEO_FORMAT = tokens(FORMAT_TERMS);
    public static final Pattern VIDEO_SOURCE = tokens(concat(SOURCE_TERMS, AMBIGUOUS_SOURCE_TERMS));
    public static final Pattern VIDEO_LANGUAGE = tokens(LANGUAGE_TERMS);
    public static final Pattern SUBTITLE_TAGS = tokens(SUBTITLE_TERMS);
    public static final Pattern RELEASE_GROUP = tokens(GROUP_TERMS);
    public static final Pattern QUERY_EXCLUDES = tokens(concat(SITE_TERMS, FILLER_TERMS));

    /**
     * Extras and samples: a sample or trailer token at the end of the name (or as the whole name), an extras category
     * word ending the name after a dash, dot or underscore (Plex/Jellyfin suffixes such as {@code -featurette}), or
     * "Behind the Scenes", "Deleted Scenes" and anime creditless openings/endings anywhere.
     */
    public static final Pattern CLUTTER = Pattern.compile("(?:^|[-._\\s\\[(])(?:sample|trailer)s?\\d{0,2}[\\])]?$"
            + "|(?:^|[-._])(?:behind" + SEPS + "the" + SEPS + "scenes|deleted(?:" + SEPS + "scenes?)?|featurettes?|interviews?|scenes?|shorts?|others?|extras?|bts)$"
            + "|(?<![\\p{L}\\p{N}])(?:behind" + SEPS + "the" + SEPS + "scenes|deleted" + SEPS + "scenes|nc(?:op|ed)\\d{0,2})(?![\\p{L}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Tokens that mark the end of the title: everything from the first one on is release information. */
    static final Pattern STRONG = tokens(concat(FORMAT_TERMS, S3D_TERMS, REPACK_TERMS, SOURCE_TERMS));
    /** Tokens removed wherever they are. */
    static final Pattern ALWAYS = tokens(SITE_TERMS);
    /** Every release token. */
    static final Pattern ANY = tokens(concat(FORMAT_TERMS, S3D_TERMS, REPACK_TERMS, SOURCE_TERMS, AMBIGUOUS_SOURCE_TERMS, EDITION_TERMS,
            LANGUAGE_TERMS, GROUP_TERMS, SITE_TERMS, FILLER_TERMS));

    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern GROUP_AT_END = Pattern.compile("-([\\p{L}\\p{N}]+)$");
    private static final Pattern GROUP_AT_START = Pattern.compile("^\\s*\\[([^\\[\\]]+)\\]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern RELEASE_WORD = tokens(concat(FORMAT_TERMS, S3D_TERMS, REPACK_TERMS, SOURCE_TERMS, AMBIGUOUS_SOURCE_TERMS,
            EDITION_TERMS, LANGUAGE_TERMS, SUBTITLE_TERMS, List.of("sample", "trailer", "cd\\d{1,2}", "dis[ck]\\d{1,2}")));
    private static final Pattern AMBIGUOUS_SOURCE = tokens(AMBIGUOUS_SOURCE_TERMS);
    private static final Pattern YEAR = Pattern.compile("(?<![\\p{L}\\p{N}])(?:19|20)\\d{2}(?![\\p{L}\\p{N}])");
    private static final SeasonEpisodeMatcher STRICT_SXE = new SeasonEpisodeMatcher(true);

    private ReleaseInfo() {
    }

    /** Case-insensitive whole-token alternation of the terms. */
    static Pattern tokens(List<String> terms) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + String.join("|", terms) + ")(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    @SafeVarargs
    private static List<String> concat(List<String>... lists) {
        return java.util.Arrays.stream(lists).flatMap(List::stream).toList();
    }

    /**
     * Where the title ends: the first strong release token (format, source, 3D, repack), the first year that follows
     * some text, or the first season/episode code; the length of the name if there is none.
     */
    static int titleEnd(String s) {
        int end = s.length();
        Matcher m = STRONG.matcher(s);
        if (m.find()) {
            end = m.start();
        }
        m = YEAR.matcher(s);
        while (m.find() && m.start() < end) {
            if (s.substring(0, m.start()).codePoints().anyMatch(Character::isLetterOrDigit)) {
                end = m.start();
                break;
            }
        }
        int sxe = STRICT_SXE.find(s, 0);
        if (sxe >= 0 && sxe < end) {
            end = sxe;
        }
        return end;
    }

    /**
     * First source tag in the name, as written ("BluRay", "WEB-DL"), or null. Sources that are also ordinary words
     * ("Web", "Max") only count after the title ("Mad.Max.2015" has no source).
     */
    public static String getVideoSource(String name) {
        if (name == null) {
            return null;
        }
        String base = withoutMediaExtension(name);
        int titleEnd = -1;
        Matcher m = VIDEO_SOURCE.matcher(base);
        while (m.find()) {
            if (AMBIGUOUS_SOURCE.matcher(m.group()).matches()) {
                if (titleEnd < 0) {
                    titleEnd = titleEnd(base);
                }
                if (m.start() < titleEnd) {
                    continue;
                }
            }
            return m.group();
        }
        return null;
    }

    /** First edition tag with its separators turned into single spaces ("Special.Edition" -> "Special Edition"), or null. */
    public static String getVideoTags(String name) {
        if (name == null) {
            return null;
        }
        Matcher m = VIDEO_TAGS.matcher(withoutMediaExtension(name));
        return m.find() ? NON_ALNUM.matcher(m.group()).replaceAll(" ").trim() : null;
    }

    /**
     * Release group: the word after the last dash at the end of the name ({@code -SPARKS}) or the bracketed text at
     * the start ({@code [SubsPlease] ...}), unless it is only digits, a release token or the end of one
     * ({@code WEB-DL}); otherwise a known group that stands as its own token between brackets, parentheses or dashes;
     * otherwise null.
     */
    public static String getReleaseGroup(String name) {
        if (name == null) {
            return null;
        }
        String base = withoutMediaExtension(name).trim();
        int dash = trailingGroupStart(base);
        if (dash >= 0) {
            return base.substring(dash + 1).trim();
        }
        Matcher m = GROUP_AT_START.matcher(base);
        if (m.find() && acceptableGroup(m.group(1).trim())) {
            return m.group(1).trim();
        }
        m = RELEASE_GROUP.matcher(base);
        while (m.find()) {
            String before = base.substring(0, m.start()).stripTrailing();
            String after = base.substring(m.end()).stripLeading();
            char b = before.isEmpty() ? ' ' : before.charAt(before.length() - 1);
            char a = after.isEmpty() ? ' ' : after.charAt(0);
            if ("[(-".indexOf(b) >= 0 || "])-".indexOf(a) >= 0) {
                return m.group();
            }
        }
        return null;
    }

    /** Index of the dash of a trailing "-GROUP" (generic rule of {@link #getReleaseGroup}), or -1. */
    static int trailingGroupStart(String s) {
        Matcher m = GROUP_AT_END.matcher(s);
        if (!m.find() || !acceptableGroup(m.group(1))) {
            return -1;
        }
        int dash = m.start();
        Matcher token = ANY.matcher(s);
        while (token.find()) {
            if (token.start() < dash && token.end() > dash) {
                return -1;
            }
        }
        return dash;
    }

    private static boolean acceptableGroup(String word) {
        return !word.isEmpty() && !DIGITS.matcher(word).matches() && !RELEASE_WORD.matcher(word).matches();
    }


    /** True for samples, trailers and extras (checked on the name without extension). */
    public static boolean isClutter(String name) {
        if (name == null) {
            return false;
        }
        return CLUTTER.matcher(MediaTypes.nameWithoutExtension(name).trim()).find();
    }

    /** The name without its extension when it is a known media, subtitle, image or nfo extension. */
    static String withoutMediaExtension(String name) {
        String ext = MediaTypes.extension(name);
        if (ext == null) {
            return name;
        }
        ext = ext.toLowerCase(Locale.ROOT);
        boolean known = MediaTypes.VIDEO.contains(ext) || MediaTypes.SUBTITLE.contains(ext) || MediaTypes.AUDIO.contains(ext)
                || MediaTypes.IMAGE.contains(ext) || MediaTypes.NFO.contains(ext);
        return known ? MediaTypes.nameWithoutExtension(name) : name;
    }
}
