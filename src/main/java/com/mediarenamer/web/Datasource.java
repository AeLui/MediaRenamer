package com.mediarenamer.web;

/** Data source selectable in the UI and in the CLI (--db). */
public interface Datasource {

    /** Stable identifier used by --db and by the preferences (e.g. "TheMovieDB::TV"). */
    String getIdentifier();

    /** Display name. */
    default String getName() {
        return getIdentifier();
    }
}
