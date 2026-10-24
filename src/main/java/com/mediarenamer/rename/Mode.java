package com.mediarenamer.rename;

import com.mediarenamer.app.Settings;
import com.mediarenamer.rename.match.AutoCompleteMatcher;
import com.mediarenamer.rename.match.EpisodeListMatcher;
import com.mediarenamer.rename.match.FileMatcher;
import com.mediarenamer.rename.match.MovieMatcher;
import com.mediarenamer.rename.match.MusicMatcher;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.EpisodeListProvider;
import com.mediarenamer.web.MovieLookupService;
import com.mediarenamer.web.MusicLookupService;
import com.mediarenamer.web.WebServices;

import java.util.ArrayList;
import java.util.List;

/** Modes of the Rename tab: Episode, Movie, Music, File. */
public enum Mode {

    Episode("{n} - {s00e00} - {t}", Settings.FORMAT_EPISODE, Settings.DB_EPISODE),
    Movie("{n} ({y})", Settings.FORMAT_MOVIE, Settings.DB_MOVIE),
    Music("{n} - {t}", Settings.FORMAT_MUSIC, Settings.DB_MUSIC),
    File("{fn}", Settings.FORMAT_FILE, null);

    private final String defaultFormat;
    private final String formatKey;
    private final String dbKey;

    Mode(String defaultFormat, String formatKey, String dbKey) {
        this.defaultFormat = defaultFormat;
        this.formatKey = formatKey;
        this.dbKey = dbKey;
    }

    public String getDefaultFormat() {
        return defaultFormat;
    }

    public String getFormat() {
        return Settings.get().get(formatKey, defaultFormat);
    }

    public void setFormat(String format) {
        Settings.get().put(formatKey, format);
    }

    public List<Datasource> getDatasources() {
        return switch (this) {
            case Episode -> new ArrayList<>(WebServices.getEpisodeListProviders());
            case Movie -> new ArrayList<>(WebServices.getMovieLookupServices());
            case Music -> new ArrayList<>(WebServices.getMusicLookupServices());
            case File -> List.of(LOCAL);
        };
    }

    public Datasource getDefaultDatasource() {
        String id = dbKey == null ? null : Settings.get().get(dbKey);
        Datasource d = id == null ? null : WebServices.get(id);
        return d != null && getDatasources().contains(d) ? d : getDatasources().get(0);
    }

    public void setDefaultDatasource(Datasource d) {
        if (dbKey != null) {
            Settings.get().put(dbKey, d.getIdentifier());
        }
    }

    public static Mode forDatasource(Datasource d) {
        if (d instanceof EpisodeListProvider) {
            return Episode;
        }
        if (d instanceof MovieLookupService) {
            return Movie;
        }
        if (d instanceof MusicLookupService) {
            return Music;
        }
        return File;
    }

    public static AutoCompleteMatcher matcher(Datasource d) {
        if (d instanceof EpisodeListProvider p) {
            return new EpisodeListMatcher(p);
        }
        if (d instanceof MovieLookupService m) {
            return new MovieMatcher(m);
        }
        if (d instanceof MusicLookupService m) {
            return new MusicMatcher(m);
        }
        return new FileMatcher();
    }

    /** "Local" source of the File mode. */
    public static final Datasource LOCAL = new Datasource() {
        @Override
        public String getIdentifier() {
            return "file";
        }

        @Override
        public String getName() {
            return "Generic File";
        }

        @Override
        public String toString() {
            return getName();
        }
    };
}
