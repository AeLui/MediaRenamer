package com.mediarenamer.cleanroom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mediarenamer.format.ExpressionFileFormat;
import com.mediarenamer.format.FormatException;
import com.mediarenamer.format.MediaBindingBean;
import com.mediarenamer.media.MediaCharacteristics;
import com.mediarenamer.media.MediaDetection;
import com.mediarenamer.media.ReleaseInfo;
import com.mediarenamer.similarity.EpisodeMetrics;
import com.mediarenamer.similarity.SeasonEpisodeMatcher;
import com.mediarenamer.similarity.SxE;
import com.mediarenamer.web.Movie;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs the reference corpus (files in src/test/resources/com/mediarenamer/cleanroom): the behavior described in
 * docs/MATCHING.md and docs/FORMAT.md, one case per line.
 */
class ReferenceCorpusTest {

    private static final String NONE = "none";

    private record Case(int line, String[] fields) {
        String name(String file) {
            return file + ":" + line + "  " + String.join(" | ", fields);
        }
    }

    /** Reads the non-comment lines of a corpus file, split on " | ". */
    private static List<Case> read(String file, int columns) throws IOException {
        List<Case> cases = new ArrayList<>();
        try (InputStream in = ReferenceCorpusTest.class.getResourceAsStream("/com/mediarenamer/cleanroom/" + file)) {
            if (in == null) {
                throw new IOException("missing corpus file " + file);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            int n = 0;
            while ((line = r.readLine()) != null) {
                n++;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(" \\| ", -1);
                if (f.length != columns) {
                    throw new IOException(file + ":" + n + " has " + f.length + " fields instead of " + columns);
                }
                cases.add(new Case(n, f));
            }
        }
        return cases;
    }

    private static Stream<DynamicTest> tests(String file, int columns, java.util.function.Consumer<String[]> check) throws IOException {
        return read(file, columns).stream().map(c -> DynamicTest.dynamicTest(c.name(file), () -> check.accept(c.fields())));
    }

    private static String sxe(List<SxE> list) {
        if (list == null || list.isEmpty()) {
            return NONE;
        }
        return list.stream().map(s -> (s.hasSeason() ? s.season().toString() : "?") + "x" + s.episode()).collect(Collectors.joining(","));
    }

    private static String orNone(Object o) {
        return o == null ? NONE : o.toString();
    }

    private static String firstMatch(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group() : NONE;
    }

    @TestFactory
    Stream<DynamicTest> seasonEpisode() throws IOException {
        SeasonEpisodeMatcher lenient = new SeasonEpisodeMatcher(false);
        SeasonEpisodeMatcher strict = new SeasonEpisodeMatcher(true);
        return tests("sxe.txt", 3, f -> {
            String mode = f[0];
            if (!mode.equals("strict")) {
                assertEquals(f[2], sxe(lenient.match(f[1])), "lenient");
            }
            if (!mode.equals("lenient")) {
                assertEquals(f[2], sxe(strict.match(f[1])), "strict");
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> releaseInfo() throws IOException {
        return tests("release.txt", 3, f -> {
            Function<String, String> fn = switch (f[0]) {
                case "strip" -> s -> MediaDetection.stripReleaseInfo(s, false);
                case "stripStrict" -> s -> MediaDetection.stripReleaseInfo(s, true);
                case "source" -> s -> orNone(ReleaseInfo.getVideoSource(s));
                case "tags" -> s -> orNone(ReleaseInfo.getVideoTags(s));
                case "group" -> s -> orNone(ReleaseInfo.getReleaseGroup(s));
                case "s3d" -> s -> firstMatch(ReleaseInfo.VIDEO_S3D, s);
                case "subtitle" -> s -> firstMatch(ReleaseInfo.SUBTITLE_TAGS, s);
                case "clutter" -> s -> String.valueOf(ReleaseInfo.isClutter(s));
                default -> throw new IllegalArgumentException("unknown function " + f[0]);
            };
            assertEquals(f[2], fn.apply(f[1]));
        });
    }

    @TestFactory
    Stream<DynamicTest> detection() throws IOException {
        EpisodeMetrics metrics = new EpisodeMetrics();
        return tests("detection.txt", 3, f -> {
            File file = new File(f[1]);
            String actual = switch (f[0]) {
                case "series" -> orNone(MediaDetection.detectSeriesName(file));
                case "movie" -> orNone(MediaDetection.detectMovie(file));
                case "folderSeason" -> orNone(MediaDetection.folderSeason(file));
                case "episode" -> sxe(metrics.sxe(file));
                default -> throw new IllegalArgumentException("unknown function " + f[0]);
            };
            assertEquals(f[2], actual);
        });
    }

    @TestFactory
    Stream<DynamicTest> expressionFormat() throws IOException {
        File file = new File("Avatar.2009.1080p.mkv");
        return tests("format.txt", 3, f -> {
            MediaBindingBean bean = new MediaBindingBean(new Movie(f[0], List.of(), 2009, 0, 19995, Locale.US), file);
            if (f[2].startsWith("error: ")) {
                String prefix = f[2].substring("error: ".length());
                FormatException e = assertThrows(FormatException.class, () -> new ExpressionFileFormat(f[1]).format(bean));
                assertTrue(e.getMessage().startsWith(prefix), "message: " + e.getMessage());
            } else {
                try {
                    assertEquals(f[2], new ExpressionFileFormat(f[1]).format(bean));
                } catch (FormatException e) {
                    fail("unexpected " + e.getMessage(), e);
                }
            }
        });
    }

    @TestFactory
    Stream<DynamicTest> resolution() throws IOException {
        return tests("resolution.txt", 4,
                f -> assertEquals(f[3], MediaCharacteristics.videoFormat(Integer.parseInt(f[0]), Integer.parseInt(f[1]), f[2].equals("i"))));
    }
}
