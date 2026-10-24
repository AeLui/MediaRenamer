package com.mediarenamer.similarity;

/** Similarity metric in [0, 1] (some metrics use negative values to penalize). */
@FunctionalInterface
public interface SimilarityMetric {

    float getSimilarity(Object a, Object b);
}
