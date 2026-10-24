package com.mediarenamer.similarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects season/episode numbers in file names.
 *
 * <p>The notations are tried in three families, from the most explicit to the loosest:
 * <ol>
 * <li>explicit: "Season 2 Episode 3", {@code S01E02} (with optional separators), {@code 1x02}, multi-episode forms
 * ({@code S01E01E02}, {@code S01E01-03}, {@code 2x05x06}) and "S01 - 02";</li>
 * <li>episode only: "Ep 12", {@code E05}, "Episode 7", "3of6", and specials {@code SP01};</li>
 * <li>lenient mode only: a bare 3 or 4 digit number read as season + two-digit episode ({@code 102} = 1x02).</li>
 * </ol>
 * A looser family is only used when the more explicit ones find nothing in the name.
 */
public class SeasonEpisodeMatcher {

    /** Ranges longer than this ("E01-E99") are not expanded: only their first episode is kept. */
    static final int MAX_RANGE = 50;

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** "Season 2 Episode 3", "Series 1 Part 2", "Temporada 1 Capitulo 3", "Staffel 1 Folge 2"... */
    private static final Pattern WORDS = Pattern.compile("(?<![\\p{L}\\p{N}])(?:season|series|temporada|staffel|saison|stagione)"
            + "[\\s._-]*(\\d{1,4})[\\s._,-]*(?:episode|episodio|ep|part|parte|cap[ií]tulo|cap|folge|[ée]pisode)[\\s._-]*(\\d{1,4})(?!\\d)", FLAGS);

    /** S01E02, s01.e02, S01 E05, [s01]_[e02], S01EP05. */
    private static final Pattern SE = Pattern.compile("(?<![\\p{L}\\p{N}])s(\\d{1,4})[\\s._\\[\\]-]*ep?(\\d{1,4})(?!\\d)", FLAGS);

    /** Spanish "T1 Cap 05", "T2E07" (temporada + capítulo). */
    private static final Pattern TEMPORADA = Pattern.compile("(?<![\\p{L}\\p{N}])t(\\d{1,2})[\\s._-]*(?:cap[ií]tulo|cap|ep|e)[\\s._-]*(\\d{1,3})(?!\\d)", FLAGS);

    /** 1x02, 01x05, S1x05. */
    private static final Pattern NXM = Pattern.compile("(?<![\\p{L}\\p{N}])s?(\\d{1,2})x(\\d{1,3})(?!\\d)", FLAGS);

    /** "S01 - 02", "Season 1 - 02". */
    private static final Pattern SEASON_DASH = Pattern.compile("(?<![\\p{L}\\p{N}])(?:season|s)[\\s._]*(\\d{1,2})[\\s._]+-[\\s._]+(\\d{1,3})(?![\\p{L}\\p{N}])", FLAGS);

    /** "Ep 12", "E05", "Episode 7". */
    private static final Pattern EPISODE = Pattern.compile("(?<![\\p{L}\\p{N}])(?:(?:episode|episodio|ep|cap[ií]tulo)[\\s._]*|e)(\\d{1,4})(?![\\p{L}\\p{N}])", FLAGS);

    /** "3of6", "3 of 6": episode 3 of a 6-part series. */
    private static final Pattern PART_OF = Pattern.compile("(?<![\\p{L}\\p{N}])(\\d{1,3})[\\s._]*of[\\s._]*(\\d{1,3})(?![\\p{L}\\p{N}])", FLAGS);

    /** SP01, SP01-02. */
    private static final Pattern SPECIAL = Pattern.compile("(?<![\\p{L}\\p{N}])sp[\\s._]?(\\d{1,3})(?:-(\\d{1,3}))?(?![\\p{L}\\p{N}])", FLAGS);

    /** 102, 1012 (lenient only); not after "H." (H.264). */
    private static final Pattern BARE = Pattern.compile("(?<![\\p{L}\\p{N}])(?<!h\\.)(\\d{1,2})(\\d{2})(?![\\p{L}\\p{N}])", FLAGS);

    /** Continuations of an SxxEyy code: "E02", "-E03", "-03". */
    private static final Pattern TAIL_E = Pattern.compile("(?:[\\s._]*(-)[\\s._]*|[\\s._]*)ep?(\\d{1,4})(?!\\d)", FLAGS);
    /** Continuations of an NxM code: "x06". */
    private static final Pattern TAIL_X = Pattern.compile("x(\\d{1,3})(?!\\d)", FLAGS);
    /** Range or list continuation: "-03" (not "-1080p", not "-1x02"). */
    private static final Pattern TAIL_DASH = Pattern.compile("-(\\d{1,4})(?![\\p{L}\\p{N}])", FLAGS);

    private final boolean strict;

    public SeasonEpisodeMatcher(boolean strict) {
        this.strict = strict;
    }

    /** A notation found in the name: where it is and the numbers it gives. */
    private record Hit(int start, int end, List<SxE> sxe) {
    }

    /** Season/episode numbers in order of appearance without duplicates, or null if there are none. */
    public List<SxE> match(CharSequence name) {
        List<Hit> hits = hits(name);
        if (hits.isEmpty()) {
            return null;
        }
        LinkedHashSet<SxE> result = new LinkedHashSet<>();
        for (Hit h : hits) {
            result.addAll(h.sxe());
        }
        return new ArrayList<>(result);
    }

    /** Index where the first notation used by {@link #match} starts, searching from fromIndex, or -1. */
    public int find(CharSequence name, int fromIndex) {
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (fromIndex > name.length()) {
            return -1;
        }
        List<Hit> hits = hits(name.subSequence(fromIndex, name.length()));
        return hits.isEmpty() ? -1 : fromIndex + hits.get(0).start();
    }

    private List<Hit> hits(CharSequence name) {
        List<Hit> explicit = new ArrayList<>();
        words(name, explicit);
        seasonEpisode(name, explicit);
        nxm(name, explicit);
        seasonDash(name, explicit);
        List<Hit> result = arrange(explicit);
        if (!result.isEmpty()) {
            return result;
        }
        List<Hit> loose = new ArrayList<>();
        episodeOnly(name, loose);
        specials(name, loose);
        result = arrange(loose);
        if (!result.isEmpty() || strict) {
            return result;
        }
        List<Hit> bare = new ArrayList<>();
        bare(name, bare);
        return arrange(bare);
    }

    /** Sorts by position and drops the hits that overlap an earlier (or, at the same start, a longer) one. */
    private static List<Hit> arrange(List<Hit> hits) {
        hits.sort(Comparator.comparingInt(Hit::start).thenComparing(Comparator.comparingInt(Hit::end).reversed()));
        List<Hit> result = new ArrayList<>();
        int end = -1;
        for (Hit h : hits) {
            if (h.start() >= end && !h.sxe().isEmpty()) {
                result.add(h);
                end = h.end();
            }
        }
        return result;
    }

    private static void words(CharSequence name, List<Hit> out) {
        Matcher m = WORDS.matcher(name);
        while (m.find()) {
            add(out, m.start(), m.end(), season(m.group(1)), number(m.group(2)));
        }
    }

    private static void seasonEpisode(CharSequence name, List<Hit> out) {
        Matcher m = SE.matcher(name);
        while (m.find()) {
            Integer season = season(m.group(1));
            List<SxE> list = new ArrayList<>();
            int last = number(m.group(2));
            list.add(new SxE(season, last));
            int end = m.end();
            Matcher tailE = TAIL_E.matcher(name);
            Matcher tailDash = TAIL_DASH.matcher(name);
            while (end < name.length()) {
                tailE.region(end, name.length());
                tailDash.region(end, name.length());
                if (tailE.lookingAt()) {
                    int next = number(tailE.group(2));
                    boolean range = tailE.group(1) != null;
                    last = continueWith(list, season, last, next, range);
                    end = tailE.end();
                } else if (tailDash.lookingAt()) {
                    last = continueWith(list, season, last, number(tailDash.group(1)), true);
                    end = tailDash.end();
                } else {
                    break;
                }
            }
            add(out, m.start(), end, list);
        }
    }

    private static void nxm(CharSequence name, List<Hit> out) {
        Matcher m = NXM.matcher(name);
        while (m.find()) {
            Integer season = season(m.group(1));
            List<SxE> list = new ArrayList<>();
            int last = number(m.group(2));
            list.add(new SxE(season, last));
            int end = m.end();
            Matcher tailX = TAIL_X.matcher(name);
            Matcher tailDash = TAIL_DASH.matcher(name);
            while (end < name.length()) {
                tailX.region(end, name.length());
                tailDash.region(end, name.length());
                if (tailX.lookingAt()) {
                    last = continueWith(list, season, last, number(tailX.group(1)), false);
                    end = tailX.end();
                } else if (tailDash.lookingAt()) {
                    last = continueWith(list, season, last, number(tailDash.group(1)), true);
                    end = tailDash.end();
                } else {
                    break;
                }
            }
            add(out, m.start(), end, list);
        }
    }

    private static void seasonDash(CharSequence name, List<Hit> out) {
        for (Pattern p : new Pattern[] { SEASON_DASH, TEMPORADA }) {
            Matcher m = p.matcher(name);
            while (m.find()) {
                add(out, m.start(), m.end(), season(m.group(1)), number(m.group(2)));
            }
        }
    }

    private static void episodeOnly(CharSequence name, List<Hit> out) {
        Matcher m = EPISODE.matcher(name);
        while (m.find()) {
            add(out, m.start(), m.end(), null, number(m.group(1)));
        }
        m = PART_OF.matcher(name);
        while (m.find()) {
            int part = number(m.group(1));
            int total = number(m.group(2));
            if (part >= 1 && part <= total) {
                add(out, m.start(), m.end(), null, part);
            }
        }
    }

    private static void specials(CharSequence name, List<Hit> out) {
        Matcher m = SPECIAL.matcher(name);
        while (m.find()) {
            List<SxE> list = new ArrayList<>();
            int first = number(m.group(1));
            list.add(new SxE(0, first));
            if (m.group(2) != null) {
                continueWith(list, 0, first, number(m.group(2)), true);
            }
            add(out, m.start(), m.end(), list);
        }
    }

    private static void bare(CharSequence name, List<Hit> out) {
        Matcher m = BARE.matcher(name);
        while (m.find()) {
            int season = number(m.group(1));
            int episode = number(m.group(2));
            int whole = season * 100 + episode;
            boolean year = m.group().length() == 4 && whole >= 1900 && whole <= 2099;
            if (season > 0 && episode > 0 && !year) {
                add(out, m.start(), m.end(), season, episode);
            }
        }
    }

    /**
     * Adds the next episode of a multi-episode code: a range adds every episode up to it (only when it goes forward
     * and spans at most {@link #MAX_RANGE}), a list adds just that episode. Returns the new last episode.
     */
    private static int continueWith(List<SxE> list, Integer season, int last, int next, boolean range) {
        if (!range) {
            list.add(new SxE(season, next));
            return next;
        }
        if (next <= last || next - last > MAX_RANGE) {
            return last;
        }
        for (int e = last + 1; e <= next; e++) {
            list.add(new SxE(season, e));
        }
        return next;
    }

    private static void add(List<Hit> out, int start, int end, Integer season, int episode) {
        add(out, start, end, List.of(new SxE(season, episode)));
    }

    /** Adds a hit, without the pairs where the "season" is really a year (season 1900-2099 with episode above 99). */
    private static void add(List<Hit> out, int start, int end, List<SxE> sxe) {
        List<SxE> valid = sxe.stream().filter(s -> !(s.hasSeason() && s.season() >= 1900 && s.season() <= 2099 && s.episode() > 99)).toList();
        out.add(new Hit(start, end, valid));
    }

    private static Integer season(String digits) {
        return Integer.valueOf(digits);
    }

    private static int number(String digits) {
        return Integer.parseInt(digits);
    }
}
