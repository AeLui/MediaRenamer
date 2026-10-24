package com.mediarenamer.web;

import java.util.List;
import java.util.Locale;

/** Episode list provider (TheTVDB, TheMovieDB::TV, AniDB). */
public interface EpisodeListProvider extends Datasource {

    /** false for providers with absolute numbering (AniDB). */
    boolean hasSeasonSupport();

    List<SearchResult> search(String query, Locale locale) throws Exception;

    SearchResult getSeries(int id, Locale locale) throws Exception;

    List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) throws Exception;

    SeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception;

    /** Orders supported by this provider. */
    default List<SortOrder> getSortOrders() {
        return List.of(SortOrder.Airdate);
    }
}
