package com.mediarenamer.rename;

import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.SortOrder;
import com.mediarenamer.web.WebServices;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rename preset: format + data source + order +
 * matching mode + language + action. JSON shape:
 * {"@type":"Preset","id":"..","name":"..","format":"..","database":"TheMovieDB","sortOrder":"Airdate",
 * "matchMode":"Opportunistic","language":"es","action":"MOVE"}
 * plus two own fields: "outputFolder", the preset's target folder (takes precedence over the Preferences one),
 * and "jellyfinScan", scan Jellyfin after renaming with this preset (true/false; absent = use the general preference).
 */
public record Preset(String id, String name, String format, String database, String sortOrder, String matchMode, String language, String action,
        String outputFolder, Boolean jellyfinScan) {

    public Preset {
        if (id == null || id.isBlank()) {
            // unique even for presets created in the same second (the old ids, seconds since 1970, stay valid)
            id = java.util.UUID.randomUUID().toString();
        }
    }

    /** Preset without its own target folder. */
    public Preset(String id, String name, String format, String database, String sortOrder, String matchMode, String language, String action) {
        this(id, name, format, database, sortOrder, matchMode, language, action, null, null);
    }

    /** Preset that uses the general Jellyfin preference. */
    public Preset(String id, String name, String format, String database, String sortOrder, String matchMode, String language, String action,
            String outputFolder) {
        this(id, name, format, database, sortOrder, matchMode, language, action, outputFolder, null);
    }

    /** null if the source does not exist or it is the file mode. */
    public Datasource getDatasource() {
        if (database == null || database.isBlank() || database.equalsIgnoreCase("file")) {
            return null;
        }
        return WebServices.get(database);
    }

    public Mode getMode() {
        Datasource d = getDatasource();
        return d == null ? Mode.File : Mode.forDatasource(d);
    }

    /** null = do not change the current order. */
    public SortOrder getSortOrder() {
        return sortOrder == null || sortOrder.isBlank() ? null : SortOrder.forName(sortOrder);
    }

    /** null = do not change the current mode. */
    public Boolean isStrict() {
        return matchMode == null || matchMode.isBlank() ? null : !matchMode.equalsIgnoreCase("Opportunistic");
    }

    /** null = do not change the current language. */
    public Locale getLocale() {
        return language == null || language.isBlank() ? null : Locale.forLanguageTag(language);
    }

    /** null = do not change the current action. */
    public RenameAction getAction() {
        return action == null || action.isBlank() ? null : RenameAction.forName(action);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("@type", "Preset");
        m.put("id", id);
        m.put("name", name);
        m.put("format", format);
        putIfPresent(m, "database", database);
        putIfPresent(m, "sortOrder", sortOrder);
        putIfPresent(m, "matchMode", matchMode);
        putIfPresent(m, "language", language);
        putIfPresent(m, "action", action);
        putIfPresent(m, "outputFolder", outputFolder);
        if (jellyfinScan != null) {
            m.put("jellyfinScan", jellyfinScan);
        }
        return m;
    }

    public static Preset fromJson(Map<?, ?> m) {
        return new Preset(str(m, "id"), str(m, "name"), str(m, "format"), str(m, "database"), str(m, "sortOrder"), str(m, "matchMode"), str(m, "language"),
                str(m, "action"), str(m, "outputFolder"), m.get("jellyfinScan") == null ? null : Boolean.valueOf(m.get("jellyfinScan").toString()));
    }

    private static void putIfPresent(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isBlank()) {
            m.put(k, v);
        }
    }

    private static String str(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString();
    }

    @Override
    public String toString() {
        return name;
    }
}
