package com.mediarenamer.web;

import java.util.List;

/** Series found in a search (includes the source database). */
public class Series extends SearchResult {

    private final Integer year;
    private final String database;

    public Series(int id, String name, List<String> aliasNames, Integer year, String database) {
        super(id, name, aliasNames);
        this.year = year;
        this.database = database;
    }

    public Integer getYear() {
        return year;
    }

    public String getDatabase() {
        return database;
    }

    @Override
    public String toString() {
        return year == null ? name : name + " (" + year + ")";
    }
}
