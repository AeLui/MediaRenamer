package com.mediarenamer.format;

import com.mediarenamer.util.FileUtilities;
import com.mediarenamer.web.AudioTrack;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.EpisodeFormat;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.MultiEpisode;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard folder structures for media servers: {plex}, {kodi}, {emby}, {jellyfin}.
 * Scheme (strings "Movies", "TV Shows", "Anime", "Music",
 * "Season N", "Specials", id suffix {tmdb-123} / [tmdbid-123] / [tmdbid=123]).
 */
public enum NamingStandard {

    Plex("{%s-%s}"), Kodi(null), Emby("[%sid=%s]"), Jellyfin("[%sid-%s]");

    private final String idFormat;

    NamingStandard(String idFormat) {
        this.idFormat = idFormat;
    }

    /** Resulting path; {plex.name} is the last segment and {plex.id} the variant with ids. */
    public static final class Path {
        private final List<String> segments;
        private final List<String> idSegments;

        Path(List<String> segments, List<String> idSegments) {
            this.segments = segments;
            this.idSegments = idSegments;
        }

        public String getName() {
            return segments.get(segments.size() - 1);
        }

        public Path getId() {
            return new Path(idSegments, idSegments);
        }

        /** Without the root folder ("Movies", "TV Shows"...). */
        public Path getTail() {
            return new Path(segments.subList(1, segments.size()), idSegments.subList(1, idSegments.size()));
        }

        public List<String> getSegments() {
            return segments;
        }

        @Override
        public String toString() {
            return String.join("/", segments);
        }
    }

    public Path getPath(Object info, String extensionPart) {
        if (info instanceof Episode e) {
            return episode(e);
        }
        if (info instanceof Movie m) {
            return movie(m, extensionPart);
        }
        if (info instanceof AudioTrack t) {
            return music(t);
        }
        return null;
    }

    private Path movie(Movie m, String part) {
        String name = clean(m.getName()) + (m.getYear() > 0 ? " (" + m.getYear() + ")" : "");
        String id = idFormat == null ? null : m.getTmdbId() > 0 ? String.format(idFormat, "tmdb", m.getTmdbId())
                : m.getImdbId() > 0 ? String.format(idFormat, "imdb", String.format("tt%07d", m.getImdbId())) : null;
        String file = name + (part == null ? "" : part);
        List<String> s = List.of("Movies", name, file);
        List<String> ids = id == null ? s : List.of("Movies", name + " " + id, file);
        return new Path(s, ids);
    }

    private Path episode(Episode e) {
        boolean anime = e.getSeriesInfo() != null && "AniDB".equals(e.getSeriesInfo().getDatabase());
        String root = anime ? "Anime" : "TV Shows";
        String series = clean(e.getSeriesName());
        Integer year = e.getSeriesInfo() != null && e.getSeriesInfo().getStartDate() != null ? e.getSeriesInfo().getStartDate().getYear() : null;
        String seriesFolder = series;
        String season = e.isSpecial() ? "Specials" : e.getSeason() == null ? null : "Season " + pad(e.getSeason());
        String title = e instanceof MultiEpisode m ? m.getEpisodes().stream().map(Episode::getTitle).filter(t -> t != null).distinct()
                .reduce((a, b) -> a + " & " + b).orElse(null) : e.getTitle();
        String sxe = e.getSeason() == null && !e.isSpecial() ? pad(e.getEpisode()) : EpisodeFormat.S00E00.format(e);
        String file = series + " - " + sxe + (title == null ? "" : " - " + clean(title));

        List<String> s = new ArrayList<>(List.of(root, seriesFolder));
        if (season != null) {
            s.add(season);
        }
        s.add(file);

        List<String> ids = new ArrayList<>(s);
        if (idFormat != null && e.getSeriesInfo() != null) {
            String db = switch (String.valueOf(e.getSeriesInfo().getDatabase())) {
                case "TheTVDB" -> "tvdb";
                case "TheMovieDB::TV" -> "tmdb";
                case "AniDB" -> "anidb";
                default -> null;
            };
            if (db != null) {
                ids.set(1, seriesFolder + (year != null ? " (" + year + ")" : "") + " " + String.format(idFormat, db, e.getSeriesInfo().getId()));
            }
        }
        return new Path(s, ids);
    }

    private Path music(AudioTrack t) {
        String artist = clean(t.getAlbumArtist() != null ? t.getAlbumArtist() : t.getArtist() != null ? t.getArtist() : "Unknown Artist");
        String album = clean(t.getAlbum() != null ? t.getAlbum() : "Unknown Album");
        String file = (t.getTrackIndex() != null ? pad(t.getTrackIndex()) + " - " : "") + clean(t.getTitle());
        List<String> s = List.of("Music", artist, album, file);
        return new Path(s, s);
    }

    private static String pad(Integer n) {
        return n == null ? "" : String.format("%02d", n);
    }

    private static String clean(String s) {
        return s == null ? "" : FileUtilities.validateFileName(s.replaceAll("\\s*[:]\\s*", " - ").replace('/', ' '));
    }
}
