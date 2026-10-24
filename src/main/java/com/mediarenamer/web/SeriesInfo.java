package com.mediarenamer.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Information about a series as returned by the episode provider. */
public class SeriesInfo implements Serializable {

    private String database;
    private SortOrder order;
    private Locale language;
    private int id;
    private String name;
    private List<String> aliasNames = new ArrayList<>();
    private String certification;
    private SimpleDate startDate;
    private List<String> genres = new ArrayList<>();
    private String network;
    private Double rating;
    private Integer ratingCount;
    private Integer runtime;
    private String status;
    private String type;
    private String originalLanguage;
    private String country;
    private String overview;
    private Map<String, String> externalIds = new LinkedHashMap<>();
    private List<String> creators = new ArrayList<>();
    private String originalName;

    /** Title in the original language (null if unknown). */
    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    /** Creators of the series (TMDb "created_by"); shown as "director" in the details card. */
    public List<String> getCreators() {
        return creators;
    }

    public void setCreators(List<String> creators) {
        this.creators = creators == null ? new ArrayList<>() : new ArrayList<>(creators);
    }

    public SeriesInfo() {
    }

    public SeriesInfo(String database, SortOrder order, Locale language, int id) {
        this.database = database;
        this.order = order;
        this.language = language;
        this.id = id;
    }

    public String getDatabase() {
        return database;
    }

    public SortOrder getOrder() {
        return order;
    }

    public Locale getLanguage() {
        return language;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAliasNames() {
        return aliasNames;
    }

    public void setAliasNames(List<String> aliasNames) {
        this.aliasNames = aliasNames == null ? new ArrayList<>() : new ArrayList<>(aliasNames);
    }

    public String getCertification() {
        return certification;
    }

    public void setCertification(String certification) {
        this.certification = certification;
    }

    public SimpleDate getStartDate() {
        return startDate;
    }

    public void setStartDate(SimpleDate startDate) {
        this.startDate = startDate;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres == null ? new ArrayList<>() : new ArrayList<>(genres);
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount) {
        this.ratingCount = ratingCount;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOriginalLanguage() {
        return originalLanguage;
    }

    public void setOriginalLanguage(String originalLanguage) {
        this.originalLanguage = originalLanguage;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    /** Ids in other databases: keys "imdb", "tvdb", "tmdb", "tvmaze", "anidb". */
    public Map<String, String> getExternalIds() {
        return externalIds;
    }

    @Override
    public String toString() {
        return database + "::" + id + " " + name;
    }
}
