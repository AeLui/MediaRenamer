package com.mediarenamer.web;

import java.util.Iterator;
import java.util.List;

/** Several episodes contained in the same file (e.g. S01E01-E02). */
public class MultiEpisode extends Episode implements Iterable<Episode> {

    private final List<Episode> episodes;

    public MultiEpisode(List<Episode> episodes) {
        super(episodes.get(0).getSeriesName(), episodes.get(0).getSeason(), episodes.get(0).getEpisode(), episodes.get(0).getTitle(),
                episodes.get(0).getAbsolute(), episodes.get(0).getSpecial(), episodes.get(0).getAirdate(), episodes.get(0).getId(),
                episodes.get(0).getSeriesInfo());
        this.episodes = List.copyOf(episodes);
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public Episode getFirst() {
        return episodes.get(0);
    }

    @Override
    public Iterator<Episode> iterator() {
        return episodes.iterator();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MultiEpisode m && m.episodes.equals(episodes);
    }

    @Override
    public int hashCode() {
        return episodes.hashCode();
    }

    @Override
    public String toString() {
        return EpisodeFormat.SeasonEpisode.format(this);
    }
}
