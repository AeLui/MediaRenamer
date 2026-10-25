package com.mediarenamer.web;

import java.util.ArrayList;
import java.util.List;

/** Registry of available data sources. */
public final class WebServices {

    public static final TheTVDBClient TheTVDB = new TheTVDBClient();
    public static final TMDbTVClient TheMovieDB_TV = new TMDbTVClient();
    public static final AniDBClient AniDB = new AniDBClient();

    public static final TMDbMovieClient TheMovieDB = new TMDbMovieClient();

    public static final AcoustIDClient AcoustID = new AcoustIDClient();
    public static final ID3Lookup ID3 = new ID3Lookup();

    private WebServices() {
    }

    /** Menu order: TheMovieDB::TV, TheTVDB, AniDB. */
    public static List<EpisodeListProvider> getEpisodeListProviders() {
        return List.of(TheMovieDB_TV, TheTVDB, AniDB);
    }

    public static List<MovieLookupService> getMovieLookupServices() {
        return List.of(TheMovieDB);
    }

    public static List<MusicLookupService> getMusicLookupServices() {
        return List.of(ID3, AcoustID);
    }

    /** Movies first, then series, then music. */
    public static List<Datasource> all() {
        List<Datasource> l = new ArrayList<>();
        l.addAll(getMovieLookupServices());
        l.addAll(getEpisodeListProviders());
        l.addAll(getMusicLookupServices());
        return l;
    }

    /** Searches by identifier, case-insensitively, accepting the CLI aliases. */
    public static Datasource get(String id) {
        if (id == null) {
            return null;
        }
        String n = id.trim().toLowerCase();
        switch (n) {
            // "omdb"/"imdb": source removed in 2.5; profiles and preferences that used it switch to TheMovieDB
            case "tmdb", "themoviedb", "movie", "omdb", "imdb" -> {
                return TheMovieDB;
            }
            // "tvmaze": source removed in 2.1; profiles that used it switch to TheMovieDB::TV
            case "tmdb::tv", "themoviedb::tv", "tmdbtv", "tv", "tvmaze" -> {
                return TheMovieDB_TV;
            }
            case "tvdb", "thetvdb" -> {
                return TheTVDB;
            }
            case "anidb", "anime" -> {
                return AniDB;
            }
            case "acoustid", "music" -> {
                return AcoustID;
            }
            case "id3", "tags" -> {
                return ID3;
            }
            default -> {
                return all().stream().filter(d -> d.getIdentifier().equalsIgnoreCase(id)).findFirst().orElse(null);
            }
        }
    }
}
