package com.mediarenamer.web;

import com.mediarenamer.app.Logging;
import com.mediarenamer.app.Settings;
import com.mediarenamer.similarity.NameSimilarityMetric;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * AniDB:
 * <ul>
 * <li>Local search over the anime-titles.dat.gz title dump (lines "aid|type|lang|title").</li>
 * <li>Episode data via the HTTP API: api.anidb.net:9001/httpapi?request=anime&client=..&clientver=..&protover=1&aid=..</li>
 * </ul>
 * AniDB bans IPs for abuse: 1 request every 2.5 s and a long cache are applied (7 days for episodes,
 * 1 day for the title dump, which AniDB asks not to download more than once a day).
 * Needs an HTTP client registered at anidb.net (apikey.anidb.client / apikey.anidb.clientver).
 */
public class AniDBClient implements EpisodeListProvider {

    private static final String TITLES = "https://anidb.net/api/anime-titles.dat.gz";
    private static final String API = "http://api.anidb.net:9001/httpapi";
    private static final FloodLimit LIMIT = new FloodLimit(1, 2500);

    private volatile Map<Integer, List<String[]>> index;

    @Override
    public String getIdentifier() {
        return "AniDB";
    }

    @Override
    public boolean hasSeasonSupport() {
        return false;
    }

    @Override
    public List<SortOrder> getSortOrders() {
        return List.of(SortOrder.Absolute);
    }

    /** aid -> list of {type, lang, title}. type: 1 main, 2 synonym, 3 short, 4 official. */
    private Map<Integer, List<String[]>> index() throws Exception {
        if (index == null) {
            synchronized (this) {
                if (index == null) {
                    byte[] data = WebRequest.get(TITLES, null, Duration.ofDays(1));
                    if (data.length > 2 && (data[0] & 0xff) == 0x1f) {
                        data = WebRequest.gunzip(data);
                    }
                    Map<Integer, List<String[]>> map = new LinkedHashMap<>();
                    for (String line : new String(data, StandardCharsets.UTF_8).split("\\R")) {
                        String[] title = parseTitleLine(line);
                        if (title != null) {
                            map.computeIfAbsent(Integer.valueOf(title[0]), k -> new ArrayList<>()).add(new String[] { title[1], title[2], title[3] });
                        }
                    }
                    if (map.isEmpty()) {
                        throw new IllegalStateException("AniDB search index is empty");
                    }
                    index = map;
                }
            }
        }
        return index;
    }

    /**
     * One line of the title dump, "aid|type|language|title" (the title may itself contain '|'), as
     * {aid, type, language, title}; null for comments ('#'), empty and malformed lines.
     */
    static String[] parseTitleLine(String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) {
            return null;
        }
        String[] parts = line.split("\\|", 4);
        if (parts.length < 4) {
            return null;
        }
        String aid = parts[0].trim();
        String type = parts[1].trim();
        String language = parts[2].trim();
        String title = parts[3].trim();
        if (!aid.matches("\\d{1,9}") || !type.matches("\\d{1,2}") || language.isEmpty() || title.isEmpty()) {
            return null;
        }
        return new String[] { aid, type, language, title };
    }

    @Override
    public List<SearchResult> search(String query, Locale locale) throws Exception {
        NameSimilarityMetric metric = new NameSimilarityMetric();
        List<Object[]> scored = new ArrayList<>();
        for (Map.Entry<Integer, List<String[]>> e : index().entrySet()) {
            float best = 0;
            for (String[] t : e.getValue()) {
                best = Math.max(best, metric.getSimilarity(query, t[2]));
            }
            if (best >= 0.5f) {
                scored.add(new Object[] { best, e.getKey() });
            }
        }
        scored.sort(Comparator.comparing((Object[] o) -> (Float) o[0]).reversed());
        List<SearchResult> result = new ArrayList<>();
        for (Object[] s : scored.subList(0, Math.min(20, scored.size()))) {
            int aid = (Integer) s[1];
            result.add(toSearchResult(aid, locale));
        }
        return result;
    }

    private SearchResult toSearchResult(int aid, Locale locale) throws Exception {
        List<String[]> titles = index().getOrDefault(aid, List.of());
        String main = null;
        String localized = null;
        List<String> aliases = new ArrayList<>();
        for (String[] t : titles) {
            if (t[0].equals("1")) {
                main = t[2];
            } else if (t[0].equals("4") && t[1].equals(locale.getLanguage())) {
                localized = t[2];
            }
            aliases.add(t[2]);
        }
        String name = localized != null ? localized : main;
        aliases.remove(name);
        return new Series(aid, name, aliases, null, getIdentifier());
    }

    @Override
    public SearchResult getSeries(int id, Locale locale) throws Exception {
        return toSearchResult(id, locale);
    }

    private Document anime(int aid) throws Exception {
        String client = Settings.get().requireKey(Settings.ANIDB_CLIENT, "AniDB");
        String clientver = Settings.get().get(Settings.ANIDB_CLIENT_VERSION, "1");
        String url = API + "?" + WebRequest.query(WebRequest.params("request", "anime", "client", client, "clientver", clientver, "protover", 1, "aid", aid));
        LIMIT.acquire();
        Document doc = WebRequest.getXml(url, null, Duration.ofDays(7));
        if (doc.getDocumentElement().getTagName().equals("error")) {
            String msg = doc.getDocumentElement().getTextContent();
            Logging.warn("AniDB: " + msg);
            throw new IllegalStateException("AniDB: " + msg);
        }
        return doc;
    }

    @Override
    public SeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception {
        return seriesInfo(anime(series.getId()), series.getId(), locale);
    }

    private SeriesInfo seriesInfo(Document doc, int aid, Locale locale) {
        Element root = doc.getDocumentElement();
        SeriesInfo info = new SeriesInfo(getIdentifier(), SortOrder.Absolute, locale, aid);
        String main = null;
        String official = null;
        List<String> aliases = new ArrayList<>();
        NodeList titles = root.getElementsByTagName("title");
        for (int i = 0; i < titles.getLength(); i++) {
            Element t = (Element) titles.item(i);
            if (!(t.getParentNode() instanceof Element p) || !p.getTagName().equals("titles")) {
                continue;
            }
            String lang = t.getAttribute("xml:lang");
            String type = t.getAttribute("type");
            if (type.equals("main")) {
                main = t.getTextContent();
            } else if (type.equals("official") && lang.equals(locale.getLanguage())) {
                official = t.getTextContent();
            }
            aliases.add(t.getTextContent());
        }
        info.setName(official != null ? official : main);
        aliases.remove(info.getName());
        info.setAliasNames(aliases.stream().distinct().toList());
        info.setStartDate(SimpleDate.parse(text(root, "startdate")));
        info.setType(text(root, "type"));
        List<String> genres = new ArrayList<>();
        NodeList tags = root.getElementsByTagName("tag");
        for (int i = 0; i < tags.getLength() && genres.size() < 10; i++) {
            Element t = (Element) tags.item(i);
            if (Integer.parseInt(t.getAttribute("weight").isEmpty() ? "0" : t.getAttribute("weight")) >= 400) {
                genres.add(text(t, "name"));
            }
        }
        info.setGenres(genres);
        NodeList permanent = root.getElementsByTagName("permanent");
        if (permanent.getLength() > 0) {
            Element p = (Element) permanent.item(0);
            info.setRating(Double.valueOf(p.getTextContent()));
            info.setRatingCount(Integer.valueOf(p.getAttribute("count")));
        }
        info.getExternalIds().put("anidb", String.valueOf(aid));
        return info;
    }

    @Override
    public List<Episode> getEpisodeList(SearchResult series, SortOrder order, Locale locale) throws Exception {
        Document doc = anime(series.getId());
        SeriesInfo info = seriesInfo(doc, series.getId(), locale);
        List<Episode> episodes = new ArrayList<>();
        List<Episode> specials = new ArrayList<>();
        NodeList list = doc.getDocumentElement().getElementsByTagName("episode");
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            Element epno = first(e, "epno");
            if (epno == null) {
                continue;
            }
            String type = epno.getAttribute("type");
            String number = epno.getTextContent().replaceAll("\\D", "");
            if (number.isEmpty()) {
                continue;
            }
            int n = Integer.parseInt(number);
            String title = episodeTitle(e, locale);
            SimpleDate airdate = SimpleDate.parse(text(e, "airdate"));
            Integer id = Integer.valueOf(e.getAttribute("id"));
            if (type.equals("1")) {
                episodes.add(new Episode(info.getName(), null, n, title, n, null, airdate, id, info));
            } else if (type.equals("2")) {
                specials.add(new Episode(info.getName(), null, null, title, null, n, airdate, id, info));
            }
        }
        episodes.sort(Comparator.comparing(Episode::getEpisode));
        specials.sort(Comparator.comparing(Episode::getSpecial));
        episodes.addAll(specials);
        return episodes;
    }

    private static String episodeTitle(Element e, Locale locale) {
        String en = null;
        String jat = null;
        NodeList titles = e.getElementsByTagName("title");
        for (int i = 0; i < titles.getLength(); i++) {
            Element t = (Element) titles.item(i);
            String lang = t.getAttribute("xml:lang");
            if (lang.equals(locale.getLanguage())) {
                return t.getTextContent();
            }
            if (lang.equals("en")) {
                en = t.getTextContent();
            } else if (lang.equals("x-jat")) {
                jat = t.getTextContent();
            }
        }
        return en != null ? en : jat;
    }

    private static Element first(Element parent, String tag) {
        NodeList l = parent.getElementsByTagName(tag);
        return l.getLength() == 0 ? null : (Element) l.item(0);
    }

    private static String text(Element parent, String tag) {
        Element e = first(parent, tag);
        return e == null ? null : e.getTextContent().trim();
    }
}
