package com.mediarenamer.web;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AniDB <-> TheTVDB / TheMovieDB numbering mapping using anime-list.xml from the project
 * github.com/Anime-Lists/anime-lists.
 *
 * Structure: {@code <anime anidbid tvdbid defaulttvdbseason episodeoffset tmdbid imdbid>}
 * with {@code <mapping-list><mapping anidbseason tvdbseason start end offset>;1-5;2-6;</mapping>}.
 */
public class AnimeLists {

    private static final String URL = "https://github.com/Anime-Lists/anime-lists/raw/master/anime-list.xml";
    private static volatile List<Entry> entries;

    public record Mapping(int anidbSeason, int tvdbSeason, Integer start, Integer end, Integer offset, String explicit) {
    }

    public record Entry(int anidbid, String tvdbid, String defaultTvdbSeason, int episodeOffset, String tmdbid, String imdbid, String name,
            List<Mapping> mappings) {
    }

    public static synchronized List<Entry> entries() throws Exception {
        if (entries == null) {
            Document doc = WebRequest.getXml(URL, null, Duration.ofDays(7));
            List<Entry> list = new ArrayList<>();
            NodeList animes = doc.getElementsByTagName("anime");
            for (int i = 0; i < animes.getLength(); i++) {
                Element a = (Element) animes.item(i);
                List<Mapping> mappings = new ArrayList<>();
                NodeList ms = a.getElementsByTagName("mapping");
                for (int j = 0; j < ms.getLength(); j++) {
                    Element m = (Element) ms.item(j);
                    mappings.add(new Mapping(intAttr(m, "anidbseason", 1), intAttr(m, "tvdbseason", 1), optInt(m, "start"), optInt(m, "end"),
                            optInt(m, "offset"), m.getTextContent()));
                }
                NodeList names = a.getElementsByTagName("name");
                list.add(new Entry(intAttr(a, "anidbid", 0), a.getAttribute("tvdbid"), a.getAttribute("defaulttvdbseason"), intAttr(a, "episodeoffset", 0),
                        a.getAttribute("tmdbid"), a.getAttribute("imdbid"), names.getLength() > 0 ? names.item(0).getTextContent() : null, mappings));
            }
            entries = list;
        }
        return entries;
    }

    public static Optional<Entry> forAniDB(int aid) throws Exception {
        return entries().stream().filter(e -> e.anidbid() == aid).findFirst();
    }

    /**
     * Converts an AniDB episode to TheTVDB numbering (season/episode).
     * Returns the derived episode or empty if there is no mapping.
     */
    public static Optional<Episode> toTheTVDB(Episode e) throws Exception {
        if (e.getSeriesInfo() == null || !"AniDB".equals(e.getSeriesInfo().getDatabase())) {
            return Optional.empty();
        }
        Optional<Entry> entry = forAniDB(e.getSeriesInfo().getId());
        if (entry.isEmpty() || !entry.get().tvdbid().matches("\\d+")) {
            return Optional.empty();
        }
        Entry en = entry.get();
        int anidbSeason = e.isSpecial() ? 0 : 1;
        int number = e.isSpecial() ? e.getSpecial() : e.getEpisode();

        for (Mapping m : en.mappings()) {
            if (m.anidbSeason() != anidbSeason) {
                continue;
            }
            // explicit mapping ";1-5;2-6;"
            if (m.explicit() != null) {
                for (String pair : m.explicit().split(";")) {
                    String[] ab = pair.split("-");
                    if (ab.length == 2 && ab[0].trim().equals(String.valueOf(number))) {
                        int tvdbEp = Integer.parseInt(ab[1].trim());
                        return Optional.of(derive(e, m.tvdbSeason(), tvdbEp));
                    }
                }
            }
            if (m.offset() != null && (m.start() == null || number >= m.start()) && (m.end() == null || number <= m.end())) {
                return Optional.of(derive(e, m.tvdbSeason(), number + m.offset()));
            }
        }
        String def = en.defaultTvdbSeason();
        if (def.equals("a")) {
            return Optional.empty(); // absolute numbering in TheTVDB: requires the episode list
        }
        if (def.matches("\\d+")) {
            int season = anidbSeason == 0 ? 0 : Integer.parseInt(def);
            return Optional.of(derive(e, season, number + (anidbSeason == 0 ? 0 : en.episodeOffset())));
        }
        return Optional.empty();
    }

    private static Episode derive(Episode e, int season, int episode) {
        if (season == 0) {
            return e.derive(e.getSeriesName(), null, null, null, episode);
        }
        return e.derive(e.getSeriesName(), season, episode, e.getAbsolute(), null);
    }

    private static int intAttr(Element e, String name, int def) {
        String v = e.getAttribute(name);
        return v.matches("\\d+") ? Integer.parseInt(v) : def;
    }

    private static Integer optInt(Element e, String name) {
        String v = e.getAttribute(name);
        return v.matches("-?\\d+") ? Integer.valueOf(v) : null;
    }
}
