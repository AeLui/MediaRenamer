package com.mediarenamer.web;

import java.io.Serializable;

/** Identified audio track (AcoustID / ID3 tags). */
public class AudioTrack implements Serializable {

    private final String artist;
    private final String title;
    private final String album;
    private final String albumArtist;
    private final String trackTitle;
    private final SimpleDate albumReleaseDate;
    private final Integer mediumIndex;
    private final Integer mediumCount;
    private final Integer trackIndex;
    private final Integer trackCount;
    private final String mbid;
    private final String genre;

    public AudioTrack(String artist, String title, String album, String albumArtist, String trackTitle, SimpleDate albumReleaseDate,
            Integer mediumIndex, Integer mediumCount, Integer trackIndex, Integer trackCount, String mbid, String genre) {
        this.artist = artist;
        this.title = title;
        this.album = album;
        this.albumArtist = albumArtist;
        this.trackTitle = trackTitle;
        this.albumReleaseDate = albumReleaseDate;
        this.mediumIndex = mediumIndex;
        this.mediumCount = mediumCount;
        this.trackIndex = trackIndex;
        this.trackCount = trackCount;
        this.mbid = mbid;
        this.genre = genre;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public String getAlbum() {
        return album;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public String getTrackTitle() {
        return trackTitle;
    }

    public SimpleDate getAlbumReleaseDate() {
        return albumReleaseDate;
    }

    public Integer getMediumIndex() {
        return mediumIndex;
    }

    public Integer getMediumCount() {
        return mediumCount;
    }

    public Integer getTrackIndex() {
        return trackIndex;
    }

    public Integer getTrackCount() {
        return trackCount;
    }

    public String getMBID() {
        return mbid;
    }

    public String getGenre() {
        return genre;
    }

    @Override
    public String toString() {
        return (artist == null ? "" : artist + " - ") + title;
    }
}
