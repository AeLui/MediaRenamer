package com.mediarenamer.similarity;

import java.util.HashMap;
import java.util.Map;

/**
 * Name similarity by q-grams (q=3) on normalized text. Like
 * QGramsDistance from simmetrics: similarity = (maxDist - dist) / maxDist, with
 * dist = sum of |differences| of trigram frequencies and maxDist = total trigrams.
 */
public class NameSimilarityMetric implements SimilarityMetric {

    @Override
    public float getSimilarity(Object a, Object b) {
        return similarity(Normalization.normalize(String.valueOf(a)), Normalization.normalize(String.valueOf(b)));
    }

    public static float similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Map<String, Integer> qa = qgrams(a);
        Map<String, Integer> qb = qgrams(b);
        int total = 0;
        int distance = 0;
        Map<String, Integer> all = new HashMap<>(qa);
        qb.forEach((k, v) -> all.merge(k, 0, Integer::sum));
        for (String k : all.keySet()) {
            int x = qa.getOrDefault(k, 0);
            int y = qb.getOrDefault(k, 0);
            distance += Math.abs(x - y);
            total += x + y;
        }
        return total == 0 ? 0 : (float) (total - distance) / total;
    }

    private static Map<String, Integer> qgrams(String s) {
        String padded = "##" + s + "##";
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i + 3 <= padded.length(); i++) {
            m.merge(padded.substring(i, i + 3), 1, Integer::sum);
        }
        return m;
    }
}
