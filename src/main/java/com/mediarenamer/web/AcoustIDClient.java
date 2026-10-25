package com.mediarenamer.web;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import groovy.json.JsonSlurper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AcoustID: acoustic fingerprint with Chromaprint (fpcalc) + api.acoustid.org/v2/lookup
 * (meta=recordings+releases+releasegroups+tracks+compress).
 */
public class AcoustIDClient implements MusicLookupService {

    private static final FloodLimit LIMIT = new FloodLimit(3, 1000);

    @Override
    public String getIdentifier() {
        return "AcoustID";
    }

    @Override
    public Map<File, AudioTrack> lookup(Collection<File> files) throws Exception {
        String key = Settings.get().requireKey(Settings.ACOUSTID_KEY, "AcoustID");
        Map<File, AudioTrack> result = new LinkedHashMap<>();
        for (File f : files) {
            try {
                Map<String, Object> fp = fingerprint(f);
                if (fp == null) {
                    continue;
                }
                String url = "https://api.acoustid.org/v2/lookup?client=" + WebRequest.encode(key)
                        + "&meta=recordings+releases+releasegroups+tracks+compress"
                        + "&duration=" + Json.integer(fp, "duration")
                        + "&fingerprint=" + WebRequest.encode(Json.string(fp, "fingerprint"));
                LIMIT.acquire();
                Object json = WebRequest.getJson(url, null, Duration.ofDays(30));
                AudioTrack t = parse(json);
                if (t != null) {
                    result.put(f, t);
                }
            } catch (Exception e) {
                Logging.warn("AcoustID: " + f.getName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    /** Picks the best recording and, within it, the original release (studio album, oldest date). */
    static AudioTrack parse(Object json) {
        List<Map<String, Object>> results = new ArrayList<>(Json.maps(json, "results"));
        results.sort((a, b) -> Double.compare(Json.decimal(b, "score") == null ? 0 : Json.decimal(b, "score"), Json.decimal(a, "score") == null ? 0 : Json.decimal(a, "score")));
        for (Map<String, Object> r : results) {
            for (Map<String, Object> rec : Json.maps(r, "recordings")) {
                String title = Json.string(rec, "title");
                String artist = String.join(", ", Json.strings(rec, "artists", "name"));
                if (title == null) {
                    continue;
                }
                Map<String, Object> bestGroup = null;
                Map<String, Object> bestRelease = null;
                SimpleDate bestDate = null;
                for (Map<String, Object> g : Json.maps(rec, "releasegroups")) {
                    boolean album = "Album".equals(Json.string(g, "type"));
                    for (Map<String, Object> rel : Json.maps(g, "releases")) {
                        Map<String, Object> d = Json.map(rel, "date");
                        SimpleDate date = Json.integer(d, "year") == null ? null
                                : new SimpleDate(Json.integer(d, "year"), Json.integer(d, "month") == null ? 1 : Json.integer(d, "month"), Json.integer(d, "day") == null ? 1 : Json.integer(d, "day"));
                        boolean better = bestRelease == null || (album && !"Album".equals(Json.string(bestGroup, "type")))
                                || (date != null && (bestDate == null || date.compareTo(bestDate) < 0));
                        if (better) {
                            bestGroup = g;
                            bestRelease = rel;
                            bestDate = date;
                        }
                    }
                }
                String album = bestGroup == null ? null : Json.string(bestGroup, "title");
                String albumArtist = bestGroup == null ? null : String.join(", ", Json.strings(bestGroup, "artists", "name"));
                Integer mediumIndex = null;
                Integer mediumCount = null;
                Integer trackIndex = null;
                Integer trackCount = null;
                if (bestRelease != null) {
                    mediumCount = Json.integer(bestRelease, "medium_count");
                    for (Map<String, Object> medium : Json.maps(bestRelease, "mediums")) {
                        for (Map<String, Object> track : Json.maps(medium, "tracks")) {
                            mediumIndex = Json.integer(medium, "position");
                            trackCount = Json.integer(medium, "track_count");
                            trackIndex = Json.integer(track, "position");
                        }
                    }
                }
                return new AudioTrack(artist.isEmpty() ? null : artist, title, album, albumArtist == null || albumArtist.isEmpty() ? null : albumArtist, title,
                        bestDate, mediumIndex, mediumCount, trackIndex, trackCount, Json.string(rec, "id"), null);
            }
        }
        return null;
    }

    /** fpcalc reads at most two minutes of audio: much longer means it is stuck. */
    static final java.time.Duration FPCALC_TIMEOUT = java.time.Duration.ofMinutes(1);

    private static Map<String, Object> fingerprint(File f) throws Exception {
        Path fpcalc = findFpcalc();
        if (fpcalc == null) {
            throw new IllegalStateException(com.mediarenamer.app.I18n.tr("error.fpcalc", Settings.FPCALC_PATH));
        }
        String out = com.mediarenamer.util.Processes.run(List.of(fpcalc.toString(), "-json", f.getAbsolutePath()), FPCALC_TIMEOUT);
        int start = out.indexOf('{');
        if (start < 0) {
            Logging.warn("fpcalc: " + out.trim());
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) new JsonSlurper().parseText(out.substring(start));
        return m;
    }

    static Path findFpcalc() {
        return findFpcalc(true);
    }

    /** Where fpcalc is found when no path is set (for the placeholder of Preferences → Tools); null if it is not installed. */
    public static Path detectedFpcalc() {
        return findFpcalc(false);
    }

    private static Path findFpcalc(boolean withConfigured) {
        List<Path> candidates = new ArrayList<>();
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "fpcalc.exe" : "fpcalc";
        String configured = withConfigured ? Settings.get().get(Settings.FPCALC_PATH) : null;
        if (configured != null) {
            Path c = Paths.get(configured).toAbsolutePath();
            // the path may be fpcalc itself or the folder that holds it (like the MediaInfo one)
            candidates.add(java.nio.file.Files.isDirectory(c) ? c.resolve(exe) : c);
        }
        // lib next to the application, never relative to the working directory (planted executables)
        Path app = com.mediarenamer.app.AppFolders.installDir();
        if (app != null) {
            candidates.add(app.resolve("lib").resolve(exe));
        }
        for (String env : List.of("ProgramFiles", "ProgramFiles(x86)")) {
            String pf = System.getenv(env);
            if (pf != null) {
                candidates.add(Paths.get(pf, "Chromaprint", exe));
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                if (!dir.isBlank() && com.mediarenamer.media.MediaInfo.isAbsolutePath(dir)) {
                    candidates.add(Paths.get(dir, exe));
                }
            }
        }
        return candidates.stream().filter(p -> p.isAbsolute() && Files.isRegularFile(p)).findFirst().orElse(null);
    }
}
