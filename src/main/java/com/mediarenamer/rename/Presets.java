package com.mediarenamer.rename;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.AppFolders;
import com.mediarenamer.app.ContextMenu;
import com.mediarenamer.app.Logging;
import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Preset store in %APPDATA%/MediaRenamer/presets.json (JSON list in the Preset format). */
public final class Presets {

    private final Path file;
    private final List<Preset> presets = new ArrayList<>();

    private static Presets instance;

    public static synchronized Presets get() {
        if (instance == null) {
            instance = new Presets(AppFolders.data().resolve("presets.json"));
        }
        return instance;
    }

    public Presets(Path file) {
        this.file = file;
        load();
    }

    public synchronized List<Preset> list() {
        return List.copyOf(presets);
    }

    public synchronized Optional<Preset> find(String nameOrId) {
        return presets.stream().filter(p -> p.name().equalsIgnoreCase(nameOrId) || p.id().equals(nameOrId)).findFirst();
    }

    /** Adds or replaces (by id). */
    public synchronized void put(Preset preset) {
        presets.removeIf(p -> p.id().equals(preset.id()));
        presets.add(preset);
        save();
    }

    public synchronized void remove(Preset preset) {
        presets.removeIf(p -> p.id().equals(preset.id()));
        save();
    }

    private void load() {
        presets.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            Object json = new JsonSlurper().parse(Files.readAllBytes(file), "UTF-8");
            if (json instanceof List<?> l) {
                for (Object o : l) {
                    if (o instanceof Map<?, ?> m && m.get("name") != null && m.get("format") != null) {
                        presets.add(Preset.fromJson(m));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Logging.warn(tr("log.presetsRead", file, e.getMessage()));
        }
    }

    private void save() {
        try {
            List<Map<String, Object>> json = presets.stream().map(Preset::toJson).toList();
            com.mediarenamer.util.FileUtilities.writeAtomically(file, JsonOutput.prettyPrint(JsonOutput.toJson(json)));
            // one Explorer context menu entry per profile
            ContextMenu.sync(List.copyOf(presets));
        } catch (IOException e) {
            Logging.warn(tr("log.presetsSave", e.getMessage()));
        }
    }
}
