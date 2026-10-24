package com.mediarenamer.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Generic search result: numeric id + name + aliases. */
public class SearchResult implements Serializable {

    protected final int id;
    protected final String name;
    protected final List<String> aliasNames;

    public SearchResult(int id, String name, List<String> aliasNames) {
        this.id = id;
        this.name = name;
        this.aliasNames = aliasNames == null ? List.of() : List.copyOf(aliasNames);
    }

    /** Poster URL (thumbnail), if the service returns it in the search. */
    protected String poster;
    /** Short overview, if the service returns it in the search. */
    protected String overview;
    /** Original title, if it differs from the localized one. */
    protected String originalName;

    public String getPoster() {
        return poster;
    }

    public String getOverview() {
        return overview;
    }

    public String getOriginalName() {
        return originalName;
    }

    /** Adds presentation data (poster, overview, original title) and returns the object itself. */
    @SuppressWarnings("unchecked")
    public <T extends SearchResult> T withDetails(String poster, String overview, String originalName) {
        this.poster = poster;
        this.overview = overview;
        this.originalName = originalName != null && !originalName.equals(name) ? originalName : null;
        return (T) this;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<String> getAliasNames() {
        return aliasNames;
    }

    /** Main name followed by all aliases, without duplicates. */
    public List<String> getEffectiveNames() {
        List<String> names = new ArrayList<>();
        if (name != null) {
            names.add(name);
        }
        for (String a : aliasNames) {
            if (a != null && !names.contains(a)) {
                names.add(a);
            }
        }
        return names;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o.getClass() == getClass() && ((SearchResult) o).id == id && Objects.equals(((SearchResult) o).name, name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }

    @Override
    public String toString() {
        return name;
    }
}
