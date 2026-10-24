package com.mediarenamer.web;

import java.io.Serializable;
import java.util.Objects;

/** Episode of a series. */
public class Episode implements Serializable {

    private final String seriesName;
    private final Integer season;
    private final Integer episode;
    private final String title;
    private final Integer absolute;
    private final Integer special;
    private final SimpleDate airdate;
    private final Integer id;
    private final SeriesInfo seriesInfo;

    public Episode(String seriesName, Integer season, Integer episode, String title, Integer absolute, Integer special,
            SimpleDate airdate, Integer id, SeriesInfo seriesInfo) {
        this.seriesName = seriesName;
        this.season = season;
        this.episode = episode;
        this.title = title;
        this.absolute = absolute;
        this.special = special;
        this.airdate = airdate;
        this.id = id;
        this.seriesInfo = seriesInfo;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public Integer getSeason() {
        return season;
    }

    public Integer getEpisode() {
        return episode;
    }

    public String getTitle() {
        return title;
    }

    public Integer getAbsolute() {
        return absolute;
    }

    public Integer getSpecial() {
        return special;
    }

    public SimpleDate getAirdate() {
        return airdate;
    }

    public Integer getId() {
        return id;
    }

    public SeriesInfo getSeriesInfo() {
        return seriesInfo;
    }

    public boolean isSpecial() {
        return special != null;
    }

    /** Copy of the episode with another season/episode number (AnimeLists/XEM mappings). */
    public Episode derive(String newSeriesName, Integer newSeason, Integer newEpisode, Integer newAbsolute, Integer newSpecial) {
        return new Episode(newSeriesName, newSeason, newEpisode, title, newAbsolute, newSpecial, airdate, id, seriesInfo);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Episode e && !(o instanceof MultiEpisode)) {
            return Objects.equals(seriesName, e.seriesName) && Objects.equals(season, e.season) && Objects.equals(episode, e.episode)
                    && Objects.equals(special, e.special) && Objects.equals(title, e.title);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seriesName, season, episode, special, title);
    }

    @Override
    public String toString() {
        return EpisodeFormat.SeasonEpisode.format(this);
    }
}
