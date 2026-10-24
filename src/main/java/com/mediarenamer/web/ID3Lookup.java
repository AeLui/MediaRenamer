package com.mediarenamer.web;

import com.mediarenamer.media.MediaInfo;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Music identification by embedded tags (ID3, Vorbis, MP4) read with MediaInfo.
 * Fields used: Performer, Title, Album, Album/Performer,
 * Track/Position(_Total), Part/Position(_Total), Recorded_Date, Genre.
 */
public class ID3Lookup implements MusicLookupService {

    @Override
    public String getIdentifier() {
        return "ID3";
    }

    @Override
    public String getName() {
        return "ID3 Tags";
    }

    @Override
    public Map<File, AudioTrack> lookup(Collection<File> files) {
        Map<File, AudioTrack> result = new LinkedHashMap<>();
        for (File f : files) {
            Map<String, String> g = MediaInfo.read(f).getOrDefault("General", java.util.List.of()).stream().findFirst().orElse(Map.of());
            String title = first(g, "Title", "Track");
            if (title == null) {
                continue;
            }
            String artist = first(g, "Performer", "Artist");
            String album = g.get("Album");
            String albumArtist = first(g, "Album_Performer", "Album/Performer", "AlbumArtist");
            SimpleDate date = SimpleDate.parse(first(g, "Recorded_Date", "Released_Date", "Original_Released_Date"));
            if (date == null) {
                String y = first(g, "Recorded_Date", "Year");
                if (y != null && y.matches("\\d{4}.*")) {
                    date = new SimpleDate(Integer.parseInt(y.substring(0, 4)), 1, 1);
                }
            }
            result.put(f, new AudioTrack(artist, title, album, albumArtist, title, date, integer(g, "Part_Position", "Part/Position"),
                    integer(g, "Part_Position_Total", "Part/Position_Total"), integer(g, "Track_Position", "Track/Position"),
                    integer(g, "Track_Position_Total", "Track/Position_Total"), null, g.get("Genre")));
        }
        return result;
    }

    private static String first(Map<String, String> g, String... keys) {
        for (String k : keys) {
            String v = g.get(k);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static Integer integer(Map<String, String> g, String... keys) {
        String v = first(g, keys);
        if (v == null) {
            return null;
        }
        String digits = v.replaceAll("^\\D*(\\d+).*$", "$1");
        return digits.matches("\\d+") ? Integer.valueOf(digits) : null;
    }
}
