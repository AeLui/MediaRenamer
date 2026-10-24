package com.mediarenamer.rename.match;

import com.mediarenamer.web.Datasource;

import java.util.List;
import java.util.Locale;

/**
 * Interaction with the user during matching: choosing among several search results
 * or entering a name. The UI shows dialogs; the CLI decides on its own.
 */
public interface Selector {

    /** Repeats the search with another text (from the selection dialog itself). */
    @FunctionalInterface
    interface Search<T> {
        List<T> search(String query) throws Exception;
    }

    /**
     * Selection request with context: service and language (to load the details of each result)
     * and a function to search again with another text.
     */
    record Request<T>(String query, List<String> files, List<T> options, Datasource datasource, Locale locale, Search<T> research) {
    }

    /**
     * @param query    searched text (detected name)
     * @param files    affected files (to show context)
     * @param options  results sorted by relevance
     * @return the chosen option, or null to skip these files
     */
    <T> T select(String query, List<String> files, List<T> options);

    /** Variant with context; by default it ignores the additional context. */
    default <T> T select(Request<T> request) {
        return select(request.query(), request.files(), request.options());
    }

    /** Asks for a search text when the name could not be detected. null = skip. */
    String input(String message, String initialValue);
}
