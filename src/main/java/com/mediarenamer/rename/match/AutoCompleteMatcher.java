package com.mediarenamer.rename.match;

import com.mediarenamer.media.MediaTypes;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.util.FileUtilities;
import com.mediarenamer.web.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/** Matching strategy of a mode (Episode, Movie, Music, File). */
public interface AutoCompleteMatcher {

    /** Options of a "Fetch Data" run. */
    record Options(boolean strict, SortOrder order, Locale locale, Selector selector, BooleanSupplier cancelled, String query) {

        public Options(boolean strict, SortOrder order, Locale locale, Selector selector, BooleanSupplier cancelled) {
            this(strict, order, locale, selector, cancelled, null);
        }

        /** Query forced by the user (--q in the CLI / "Fetch Data" with a manual name). */
        public boolean hasQuery() {
            return query != null && !query.isBlank();
        }
    }

    List<Match<Object, File>> match(List<File> files, Options options) throws Exception;

    /** Primary files (video/audio) as opposed to companions (subtitles, nfo, images). */
    static List<File> primary(List<File> files) {
        List<File> l = files.stream().filter(f -> !MediaTypes.isCompanion(f)).toList();
        return l.isEmpty() ? files : l;
    }

    /**
     * Gives each companion file the same object as its video ("Movie.mkv" ->
     * "Movie.spa.srt").
     */
    static List<Match<Object, File>> addCompanions(List<File> all, List<Match<Object, File>> matches) {
        List<Match<Object, File>> result = new ArrayList<>(matches);
        for (File f : all) {
            if (!MediaTypes.isCompanion(f) || matches.stream().anyMatch(m -> m.getCandidate().equals(f))) {
                continue;
            }
            String base = FileUtilities.getNameWithoutExtension(f.getName()).toLowerCase(Locale.ROOT);
            Match<Object, File> best = null;
            for (Match<Object, File> m : matches) {
                File v = m.getCandidate();
                if (MediaTypes.isCompanion(v)) {
                    continue;
                }
                String vb = FileUtilities.getNameWithoutExtension(v.getName()).toLowerCase(Locale.ROOT);
                if (base.startsWith(vb) && (best == null || vb.length() > FileUtilities.getNameWithoutExtension(best.getCandidate().getName()).length())) {
                    best = m;
                }
            }
            if (best != null) {
                result.add(new Match<>(best.getValue(), f));
            }
        }
        // keep the original order of the files
        result.sort((a, b) -> Integer.compare(all.indexOf(a.getCandidate()), all.indexOf(b.getCandidate())));
        return result;
    }
}
