package com.mediarenamer.web;

import java.util.List;
import java.util.stream.Collectors;

/** Standard episode formats used by {s00e00}, {sxe} and by toString(). */
public enum EpisodeFormat {

    /** "Series - 1x02 - Title" */
    SeasonEpisode,
    /** Only "S01E02" */
    S00E00,
    /** Only "1x02" */
    SxE;

    public String format(Episode e) {
        return switch (this) {
            case SeasonEpisode -> {
                StringBuilder sb = new StringBuilder();
                if (e.getSeriesName() != null) {
                    sb.append(e.getSeriesName()).append(" - ");
                }
                sb.append(SxE.format(e));
                String title = e instanceof MultiEpisode m
                        ? m.getEpisodes().stream().map(Episode::getTitle).filter(t -> t != null).distinct().collect(Collectors.joining(" & "))
                        : e.getTitle();
                if (title != null && !title.isEmpty()) {
                    sb.append(" - ").append(title);
                }
                yield sb.toString();
            }
            case S00E00 -> e instanceof MultiEpisode m ? joinMulti(m.getEpisodes(), true) : s00e00(e);
            case SxE -> e instanceof MultiEpisode m ? joinMulti(m.getEpisodes(), false) : sxe(e);
        };
    }

    private static String s00e00(Episode e) {
        if (e.isSpecial()) {
            return String.format("S00E%02d", e.getSpecial());
        }
        if (e.getSeason() == null) {
            return e.getEpisode() == null ? "" : String.format("E%02d", e.getEpisode());
        }
        return String.format("S%02dE%02d", e.getSeason(), e.getEpisode());
    }

    private static String sxe(Episode e) {
        if (e.isSpecial()) {
            return String.format("0x%02d", e.getSpecial());
        }
        if (e.getSeason() == null) {
            return e.getEpisode() == null ? "" : String.format("%02d", e.getEpisode());
        }
        return String.format("%dx%02d", e.getSeason(), e.getEpisode());
    }

    /** S01E01-E02 / 1x01-02 (consecutive multi-episodes). */
    private static String joinMulti(List<Episode> eps, boolean s00e00) {
        Episode first = eps.get(0);
        StringBuilder sb = new StringBuilder(s00e00 ? s00e00(first) : sxe(first));
        for (Episode e : eps.subList(1, eps.size())) {
            Integer n = e.isSpecial() ? e.getSpecial() : e.getEpisode();
            sb.append(s00e00 ? String.format("-E%02d", n) : String.format("-%02d", n));
        }
        return sb.toString();
    }
}
