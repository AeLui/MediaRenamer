package com.mediarenamer.similarity;

import java.util.Objects;

/** Season/episode pair detected in a file name. season == null => numbering without season. */
public record SxE(Integer season, int episode) {

    public boolean hasSeason() {
        return season != null;
    }

    @Override
    public String toString() {
        return season == null ? String.format("E%02d", episode) : String.format("S%02dE%02d", season, episode);
    }

    public boolean matches(Integer s, Integer e) {
        return e != null && e == episode && (season == null || Objects.equals(season, s));
    }
}
