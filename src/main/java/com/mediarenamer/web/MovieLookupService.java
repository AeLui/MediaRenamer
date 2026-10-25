package com.mediarenamer.web;

import java.util.List;
import java.util.Locale;

/** Movie data provider (TheMovieDB). */
public interface MovieLookupService extends Datasource {

    List<Movie> searchMovie(String query, int year, Locale locale) throws Exception;

    /** Searches by the service's own id (tmdb) or by imdb (tt0000000 -> 0000000). */
    Movie getMovieDescriptor(Movie id, Locale locale) throws Exception;

    MovieInfo getMovieInfo(Movie movie, Locale locale) throws Exception;
}
