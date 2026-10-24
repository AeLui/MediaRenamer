package com.mediarenamer.rename;

import com.mediarenamer.app.Settings;
import com.mediarenamer.web.AudioTrack;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.Movie;

import java.io.File;

/**
 * Base folder where the format result is placed: target = &lt;folder&gt;/&lt;format&gt;.
 *
 * Prioridad:
 * <ol>
 * <li>Folder of the active preset (or --output in the CLI).</li>
 * <li>Default folder from Preferences by type (Movies, Series, Anime, Music, Other).</li>
 * <li>None: the same folder as the original file.</li>
 * </ol>
 * If the format produces an absolute path (e.g. "Z:\Movies\{n} ({y})"), it is used as is.
 */
public final class Destinations {

    private Destinations() {
    }

    /** Folder configured in Preferences for the type of the matched object, or null. */
    public static File forType(Object match) {
        String key;
        if (match instanceof Episode e) {
            boolean anime = e.getSeriesInfo() != null && "AniDB".equals(e.getSeriesInfo().getDatabase());
            if (anime && dir(Settings.DEST_ANIME) != null) {
                return dir(Settings.DEST_ANIME);
            }
            key = Settings.DEST_SERIES;
        } else if (match instanceof Movie) {
            key = Settings.DEST_MOVIES;
        } else if (match instanceof AudioTrack) {
            key = Settings.DEST_MUSIC;
        } else {
            key = Settings.DEST_OTHER;
        }
        return dir(key);
    }

    /** Effective base folder: the preset's (or --output) if any; otherwise the Preferences one; otherwise null. */
    public static File resolve(Object match, String presetOrOutputFolder) {
        if (presetOrOutputFolder != null && !presetOrOutputFolder.isBlank()) {
            return new File(presetOrOutputFolder.trim());
        }
        return forType(match);
    }

    private static File dir(String key) {
        String v = Settings.get().get(key);
        return v == null || v.isBlank() ? null : new File(v.trim());
    }
}
