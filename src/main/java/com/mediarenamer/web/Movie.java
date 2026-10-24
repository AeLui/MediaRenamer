package com.mediarenamer.web;

import java.util.List;
import java.util.Locale;

/** Identified movie (TMDb and/or IMDb id). */
public class Movie extends SearchResult {

    private final int year;
    private final int imdbId;
    private final int tmdbId;
    private final Locale language;

    public Movie(String name, List<String> aliasNames, int year, int imdbId, int tmdbId, Locale language) {
        super(tmdbId > 0 ? tmdbId : imdbId, name, aliasNames);
        this.year = year;
        this.imdbId = imdbId;
        this.tmdbId = tmdbId;
        this.language = language;
    }

    public int getYear() {
        return year;
    }

    public int getImdbId() {
        return imdbId;
    }

    public int getTmdbId() {
        return tmdbId;
    }

    public Locale getLanguage() {
        return language;
    }

    /** Copy with a new name and aliases (translation), keeping the ids. */
    public Movie withNames(String newName, List<String> aliases, Locale locale) {
        return new Movie(newName, aliases, year, imdbId, tmdbId, locale);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Movie m) {
            if (tmdbId > 0 && m.tmdbId > 0) {
                return tmdbId == m.tmdbId;
            }
            if (imdbId > 0 && m.imdbId > 0) {
                return imdbId == m.imdbId;
            }
            return year == m.year && name.equalsIgnoreCase(m.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return tmdbId > 0 ? tmdbId : imdbId > 0 ? imdbId : name.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return year > 0 ? name + " (" + year + ")" : name;
    }
}
