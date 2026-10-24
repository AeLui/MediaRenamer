package com.mediarenamer.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Extended details of a movie (bindings {info}, {genres}, {rating}, {collection}...). */
public class MovieInfo implements Serializable {

    public String name;
    public String originalName;
    public String originalLanguage;
    public List<String> spokenLanguages = new ArrayList<>();
    public SimpleDate released;
    public Integer runtime;
    public String certification;
    public List<String> genres = new ArrayList<>();
    public Double rating;
    public Integer votes;
    public String collection;
    public String overview;
    public String tagline;
    public List<String> directors = new ArrayList<>();
    public List<String> writers = new ArrayList<>();
    public List<String> actors = new ArrayList<>();
    public List<String> countries = new ArrayList<>();
    public List<String> productionCompanies = new ArrayList<>();
    public String poster;
    public Map<String, String> externalIds = new LinkedHashMap<>();
    /** Raw JSON response of the service, accessible from formats such as {info.raw...}. */
    public Map<String, Object> raw = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public String getOriginalName() {
        return originalName;
    }

    public List<String> getGenres() {
        return genres;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getVotes() {
        return votes;
    }

    public String getCollection() {
        return collection;
    }

    public String getCertification() {
        return certification;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public String getOverview() {
        return overview;
    }

    public String getDirector() {
        return directors.isEmpty() ? null : directors.get(0);
    }

    public List<String> getActors() {
        return actors;
    }

    public SimpleDate getReleased() {
        return released;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    @Override
    public String toString() {
        return name;
    }
}
