package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Data sources offered: movies first, no TVmaze or OMDb (profiles that used them fall back to TheMovieDB). */
class WebServicesTest {

    @Test
    void moviesFirstThenSeriesThenMusic() {
        List<Datasource> all = WebServices.all();
        assertEquals(WebServices.TheMovieDB, all.get(0));
        assertEquals(WebServices.TheMovieDB_TV, all.get(1));
    }

    @Test
    void omdbIsGone() {
        assertFalse(WebServices.all().stream().anyMatch(d -> d.getIdentifier().equalsIgnoreCase("OMDb")));
        assertEquals(List.of(WebServices.TheMovieDB), WebServices.getMovieLookupServices());
        // profiles and preferences that used it fall back to TheMovieDB
        assertEquals(WebServices.TheMovieDB, WebServices.get("OMDb"));
        assertEquals(WebServices.TheMovieDB, WebServices.get("imdb"));
    }

    @Test
    void tvmazeIsGone() {
        assertFalse(WebServices.all().stream().anyMatch(d -> d.getIdentifier().toLowerCase().contains("tvmaze")));
        assertEquals(WebServices.TheMovieDB_TV, WebServices.get("TVmaze"));
    }
}
