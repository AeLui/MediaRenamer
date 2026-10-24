package com.mediarenamer.similarity;

import java.util.Objects;

/** Match between a value (data object) <-> candidate (file). */
public class Match<V, C> {

    private final V value;
    private final C candidate;

    public Match(V value, C candidate) {
        this.value = value;
        this.candidate = candidate;
    }

    public V getValue() {
        return value;
    }

    public C getCandidate() {
        return candidate;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Match<?, ?> m && Objects.equals(m.value, value) && Objects.equals(m.candidate, candidate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, candidate);
    }

    @Override
    public String toString() {
        return "[" + value + ", " + candidate + "]";
    }
}
