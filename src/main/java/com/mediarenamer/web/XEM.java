package com.mediarenamer.web;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * TheXEM (thexem.info): mapping between TheTVDB, "scene" and AniDB numbering.
 * Endpoint: thexem.info/map/all?id=..&origin=tvdb
 */
public final class XEM {

    private XEM() {
    }

    /**
     * Converts an episode to another numbering. origin/destination: "tvdb", "scene", "anidb".
     */
    public static Optional<Episode> map(Episode e, int seriesId, String origin, String destination) throws Exception {
        if (e.getSeason() == null || e.getEpisode() == null) {
            return Optional.empty();
        }
        Object json = WebRequest.getJson("https://thexem.info/map/all?" + WebRequest.query(WebRequest.params("id", seriesId, "origin", origin)), null,
                Duration.ofDays(1));
        if (!"success".equals(Json.string(json, "result"))) {
            return Optional.empty();
        }
        for (Map<String, Object> m : Json.maps(json, "data")) {
            Map<String, Object> from = Json.map(m, origin);
            if (e.getSeason().equals(Json.integer(from, "season")) && e.getEpisode().equals(Json.integer(from, "episode"))) {
                Map<String, Object> to = Json.map(m, destination);
                if (to.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(e.derive(e.getSeriesName(), Json.integer(to, "season"), Json.integer(to, "episode"), Json.integer(to, "absolute"), null));
            }
        }
        return Optional.empty();
    }
}
