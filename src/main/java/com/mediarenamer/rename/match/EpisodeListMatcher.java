package com.mediarenamer.rename.match;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.media.MediaDetection;
import com.mediarenamer.similarity.EpisodeMetrics;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.similarity.Matcher;
import com.mediarenamer.similarity.NameSimilarityMetric;
import com.mediarenamer.similarity.Normalization;
import com.mediarenamer.similarity.SxE;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.EpisodeListProvider;
import com.mediarenamer.web.MultiEpisode;
import com.mediarenamer.web.SearchResult;
import com.mediarenamer.web.SortOrder;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Episode mode:
 * <ol>
 * <li>Groups the files by detected series name.</li>
 * <li>Searches each name in the provider and picks the series (automatically if unambiguous; otherwise
 * it asks the user: "Select best match for ...").</li>
 * <li>Downloads the episode list with the chosen order and language.</li>
 * <li>Matches files and episodes with the EpisodeMetrics cascade and creates a MultiEpisode for
 * files with several episodes (S01E01E02).</li>
 * </ol>
 */
public class EpisodeListMatcher implements AutoCompleteMatcher {

    private final EpisodeListProvider provider;

    public EpisodeListMatcher(EpisodeListProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<Match<Object, File>> match(List<File> files, Options o) throws Exception {
        List<File> primary = AutoCompleteMatcher.primary(files);
        SortOrder order = provider.getSortOrders().contains(o.order()) ? o.order() : provider.getSortOrders().get(0);
        if (!provider.hasSeasonSupport()) {
            order = SortOrder.Absolute;
        }

        List<Match<Object, File>> result = new ArrayList<>();
        Map<SearchResult, List<File>> bySeries = new LinkedHashMap<>();
        Map<String, List<File>> groups = o.hasQuery() ? Map.of(o.query(), primary) : MediaDetection.groupBySeries(primary);
        for (Map.Entry<String, List<File>> group : groups.entrySet()) {
            if (o.cancelled().getAsBoolean()) {
                return result;
            }
            String query = group.getKey();
            List<String> names = group.getValue().stream().map(File::getName).toList();
            if (query.isEmpty()) {
                query = o.selector().input(tr("prompt.seriesName"), group.getValue().get(0).getName());
                if (query == null || query.isBlank()) {
                    continue;
                }
            }
            SearchResult series = selectSeries(query, names, o);
            if (series == null) {
                Logging.info(tr("log.noMatch", query));
                continue;
            }
            Logging.info(tr("log.seriesFound", query, series) + " [" + provider.getIdentifier() + "::" + series.getId() + "]");
            bySeries.computeIfAbsent(series, k -> new ArrayList<>()).addAll(group.getValue());
        }

        for (Map.Entry<SearchResult, List<File>> e : bySeries.entrySet()) {
            if (o.cancelled().getAsBoolean()) {
                break;
            }
            List<Episode> episodes = provider.getEpisodeList(e.getKey(), order, o.locale());
            Logging.info(tr("log.episodes", e.getKey(), episodes.size(), order));
            result.addAll(matchEpisodes(e.getValue(), episodes, o.strict()));
        }
        return AutoCompleteMatcher.addCompanions(files, result);
    }

    private SearchResult selectSeries(String query, List<String> files, Options o) throws Exception {
        List<SearchResult> results = new ArrayList<>(provider.search(query, o.locale()));
        if (results.isEmpty()) {
            // retry without year / without parentheses
            String simpler = Normalization.removeTrailingBrackets(query).replaceAll("\\b(19|20)\\d{2}\\b", "").trim();
            if (!simpler.equals(query) && !simpler.isEmpty()) {
                results = new ArrayList<>(provider.search(simpler, o.locale()));
            }
        }
        if (results.isEmpty()) {
            return null;
        }
        String q = Normalization.normalize(query);
        results.sort(Comparator.comparingDouble((SearchResult r) -> -score(q, r)));
        double best = score(q, results.get(0));
        double second = results.size() > 1 ? score(q, results.get(1)) : 0;
        // auto-selection: name (or alias) equal to the query and no tie with the second one
        if (best >= 0.99 && second < 0.99 || results.size() == 1 && best >= 0.6) {
            return results.get(0);
        }
        if (o.strict() && best >= 0.9 && best - second > 0.1) {
            return results.get(0);
        }
        return o.selector().select(new Selector.Request<>(query, files, results, provider, o.locale(), text -> provider.search(text, o.locale())));
    }

    private static double score(String normalizedQuery, SearchResult r) {
        double s = 0;
        for (String n : r.getEffectiveNames()) {
            String norm = Normalization.normalize(n);
            if (norm.equals(normalizedQuery)) {
                return 1;
            }
            s = Math.max(s, NameSimilarityMetric.similarity(normalizedQuery, norm));
        }
        return s;
    }

    /** Matches files with episodes and expands multi-episodes. */
    public static List<Match<Object, File>> matchEpisodes(List<File> files, List<Episode> episodes, boolean strict) {
        EpisodeMetrics metrics = new EpisodeMetrics();
        Matcher<Episode, File> matcher = new Matcher<>(episodes, files, strict, metrics.matchSequence());
        List<Match<Object, File>> result = new ArrayList<>();
        for (Match<Episode, File> m : matcher.match()) {
            Episode e = m.getValue();
            File f = m.getCandidate();
            if (strict && metrics.verify(e, f) <= 0) {
                continue;
            }
            List<SxE> sxe = metrics.sxe(f);
            if (sxe.size() > 1) {
                List<Episode> multi = new ArrayList<>();
                for (SxE s : sxe) {
                    episodes.stream().filter(x -> s.hasSeason() ? Objects.equals(x.getSeason(), s.season()) && Objects.equals(x.getEpisode(), s.episode())
                            : Objects.equals(x.getEpisode(), s.episode())).findFirst().ifPresent(multi::add);
                }
                if (multi.size() > 1 && multi.contains(e)) {
                    result.add(new Match<>(new MultiEpisode(multi), f));
                    continue;
                }
            }
            result.add(new Match<>(e, f));
        }
        return result;
    }
}
