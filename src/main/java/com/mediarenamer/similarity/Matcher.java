package com.mediarenamer.similarity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One-to-one matcher of values (data objects) with candidates (files) through a cascade of metrics.
 *
 * <p>Each pair gets a score vector, one similarity per metric, rounded to 2 decimals; vectors compare in metric order,
 * so the first metric decides and later ones only break its ties. Repeatedly, every pair that is the single best
 * option of both its value and its candidate is accepted. When no such pair is left, the best remaining pairs are
 * ties: lenient mode accepts them in candidate order (then value order), strict mode drops them. In strict mode a
 * pair whose first metric is 0 or less is never accepted.
 */
public class Matcher<V, C> {

    private final List<V> values;
    private final List<C> candidates;
    private final boolean strict;
    private final SimilarityMetric[] metrics;

    private final List<V> remainingValues = new ArrayList<>();
    private final List<C> remainingCandidates = new ArrayList<>();

    public Matcher(Collection<? extends V> values, Collection<? extends C> candidates, boolean strict, SimilarityMetric[] metrics) {
        this.values = new ArrayList<>(values);
        this.candidates = new ArrayList<>(candidates);
        this.strict = strict;
        this.metrics = metrics.clone();
        this.remainingValues.addAll(this.values);
        this.remainingCandidates.addAll(this.candidates);
    }

    /** Marks a level of a score vector that is not computed yet. */
    private static final int UNSET = Integer.MIN_VALUE;

    /**
     * A value/candidate pair still in play, with its rounded score vector. Only the first level is computed up front;
     * the others are computed when a comparison needs them (most pairs are told apart by the first metrics).
     */
    private record Pair(int value, int candidate, int[] score) {
    }

    public List<Match<V, C>> match() {
        int nv = values.size();
        int nc = candidates.size();
        List<Pair> pairs = new ArrayList<>();
        for (int c = 0; c < nc; c++) {
            for (int v = 0; v < nv; v++) {
                int[] score = new int[metrics.length];
                java.util.Arrays.fill(score, UNSET);
                if (metrics.length > 0) {
                    score[0] = level(0, values.get(v), candidates.get(c));
                }
                if (strict && metrics.length > 0 && score[0] <= 0) {
                    continue;
                }
                pairs.add(new Pair(v, c, score));
            }
        }

        int[] matchOfCandidate = new int[nc];
        java.util.Arrays.fill(matchOfCandidate, -1);
        boolean[] usedValue = new boolean[nv];
        boolean[] usedCandidate = new boolean[nc];

        while (!pairs.isEmpty()) {
            Pair[] bestOfValue = new Pair[nv];
            Pair[] bestOfCandidate = new Pair[nc];
            boolean[] valueTied = new boolean[nv];
            boolean[] candidateTied = new boolean[nc];
            Pair best = null;
            for (Pair p : pairs) {
                int cv = bestOfValue[p.value()] == null ? 1 : compare(p, bestOfValue[p.value()]);
                if (cv > 0) {
                    bestOfValue[p.value()] = p;
                    valueTied[p.value()] = false;
                } else if (cv == 0) {
                    valueTied[p.value()] = true;
                }
                int cc = bestOfCandidate[p.candidate()] == null ? 1 : compare(p, bestOfCandidate[p.candidate()]);
                if (cc > 0) {
                    bestOfCandidate[p.candidate()] = p;
                    candidateTied[p.candidate()] = false;
                } else if (cc == 0) {
                    candidateTied[p.candidate()] = true;
                }
                if (best == null || compare(p, best) > 0) {
                    best = p;
                }
            }

            boolean accepted = false;
            for (Pair p : pairs) {
                if (bestOfValue[p.value()] == p && !valueTied[p.value()] && bestOfCandidate[p.candidate()] == p && !candidateTied[p.candidate()]) {
                    accept(p, matchOfCandidate, usedValue, usedCandidate);
                    accepted = true;
                }
            }

            if (!accepted) {
                // every remaining pair competes with another one of the same score: resolve the best tie group
                Pair top = best;
                for (Pair p : pairs) {
                    if (compare(p, top) == 0 && !strict && !usedValue[p.value()] && !usedCandidate[p.candidate()]) {
                        accept(p, matchOfCandidate, usedValue, usedCandidate);
                    }
                }
                if (strict) {
                    pairs.removeIf(p -> compare(p, top) == 0);
                }
            }
            pairs.removeIf(p -> usedValue[p.value()] || usedCandidate[p.candidate()]);
        }

        List<Match<V, C>> result = new ArrayList<>();
        remainingValues.clear();
        remainingCandidates.clear();
        for (int c = 0; c < nc; c++) {
            if (matchOfCandidate[c] >= 0) {
                result.add(new Match<>(values.get(matchOfCandidate[c]), candidates.get(c)));
            } else {
                remainingCandidates.add(candidates.get(c));
            }
        }
        for (int v = 0; v < nv; v++) {
            if (!usedValue[v]) {
                remainingValues.add(values.get(v));
            }
        }
        return result;
    }

    /** Values left unmatched by {@link #match()}, in their original order. */
    public List<V> remainingValues() {
        return new ArrayList<>(remainingValues);
    }

    /** Candidates left unmatched by {@link #match()}, in their original order. */
    public List<C> remainingCandidates() {
        return new ArrayList<>(remainingCandidates);
    }

    private static void accept(Pair p, int[] matchOfCandidate, boolean[] usedValue, boolean[] usedCandidate) {
        matchOfCandidate[p.candidate()] = p.value();
        usedValue[p.value()] = true;
        usedCandidate[p.candidate()] = true;
    }

    /** Similarity of one metric, rounded to 2 decimals (as an integer percentage). */
    private int level(int i, V value, C candidate) {
        float f = metrics[i].getSimilarity(value, candidate);
        return Float.isNaN(f) ? 0 : Math.round(f * 100);
    }

    private int score(Pair p, int i) {
        if (p.score()[i] == UNSET) {
            p.score()[i] = level(i, values.get(p.value()), candidates.get(p.candidate()));
        }
        return p.score()[i];
    }

    /** Compares the score vectors metric by metric, computing the levels it needs. */
    private int compare(Pair a, Pair b) {
        if (a == b) {
            return 0;
        }
        for (int i = 0; i < metrics.length; i++) {
            int x = score(a, i);
            int y = score(b, i);
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
