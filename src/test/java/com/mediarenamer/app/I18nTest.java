package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediarenamer.rename.ConflictAction;
import com.mediarenamer.rename.Mode;
import com.mediarenamer.rename.RenameAction;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class I18nTest {

    private static final ResourceBundle.Control NO_FALLBACK = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final Pattern PARAM = Pattern.compile("\\{\\d}");

    @AfterEach
    void restore() {
        I18n.setLocale(Locale.ENGLISH);
    }

    private static ResourceBundle bundle(Locale l) {
        return ResourceBundle.getBundle("com.mediarenamer.i18n.messages", l, I18n.class.getClassLoader(), NO_FALLBACK);
    }

    private static Set<String> params(String s) {
        Set<String> p = new TreeSet<>();
        Matcher m = PARAM.matcher(s);
        while (m.find()) {
            p.add(m.group());
        }
        return p;
    }

    /** Only the keys of a language's own file (without inheriting the English ones). */
    private static Properties own(Locale l) throws IOException {
        String name = "/com/mediarenamer/i18n/messages" + (l.equals(Locale.ENGLISH) ? "" : "_" + l.getLanguage()) + ".properties";
        Properties p = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @Test
    void everyLanguageHasEveryKeyWithTheSameParameters() throws IOException {
        Properties base = own(Locale.ENGLISH);
        assertTrue(base.size() > 300, "keys: " + base.size());
        for (Locale l : I18n.SUPPORTED) {
            Properties p = own(l);
            assertEquals(new TreeSet<>(base.stringPropertyNames()), new TreeSet<>(p.stringPropertyNames()), "keys of " + l);
            for (String k : base.stringPropertyNames()) {
                assertEquals(params(base.getProperty(k)), params(p.getProperty(k)), l + ": parameters of " + k);
                assertFalse(p.getProperty(k).isBlank(), l + ": " + k + " vacia");
            }
        }
    }

    @Test
    void languagesAreReallyTranslated() {
        I18n.setLocale(Locale.ENGLISH);
        String en = I18n.tr("rename.rename");
        assertEquals("Apply", en);
        for (Locale l : I18n.SUPPORTED) {
            I18n.setLocale(l);
            if (!l.equals(Locale.ENGLISH)) {
                assertNotEquals(en, I18n.tr("rename.rename"), l.toString());
            }
        }
        I18n.setLocale(Locale.forLanguageTag("es"));
        assertEquals("Aplicar", I18n.tr("rename.rename"));
        I18n.setLocale(Locale.GERMAN);
        assertEquals("Anwenden", I18n.tr("rename.rename"));
    }

    @Test
    void parametersAreReplacedLiterally() {
        I18n.setLocale(Locale.FRENCH);
        // apostrophes are not lost (it is not MessageFormat) and format variables stay as they are
        assertEquals("Langue de l'interface", I18n.tr("prefs.uiLanguage"));
        assertTrue(I18n.tr("rename.format.tooltip").contains("{n} - {s00e00} - {t}"));
        assertEquals("Erreur : l'hôte {0}", I18n.tr("common.error", "l'hôte {0}"));
        I18n.setLocale(Locale.forLanguageTag("es"));
        assertEquals("1 fichero", I18n.plural("count.files", 1));
        assertEquals("3 ficheros", I18n.plural("count.files", 3));
        assertEquals("2 / 5 listos", I18n.tr("rename.count.ready", 2, 5));
        assertEquals("missing.key.here", I18n.tr("missing.key.here"));
    }

    @Test
    void resolvesSupportedLanguagesOrEnglish() {
        assertEquals(Locale.forLanguageTag("pt"), I18n.resolve("pt-BR"));
        assertEquals(Locale.forLanguageTag("es"), I18n.resolve("es-MX"));
        assertEquals(Locale.GERMAN, I18n.resolve("de"));
        assertEquals(Locale.ENGLISH, I18n.resolve("ja"));
        assertTrue(I18n.SUPPORTED.contains(I18n.resolve(null)));
        assertEquals("Español", I18n.nativeName(Locale.forLanguageTag("es")));
        assertEquals("Deutsch", I18n.nativeName(Locale.GERMAN));
        assertEquals("Português", I18n.nativeName(Locale.forLanguageTag("pt")));
    }

    @Test
    void consoleAnswerLettersMatchThePrompt() {
        for (Locale l : I18n.SUPPORTED) {
            I18n.setLocale(l);
            String keys = I18n.tr("cli.ask.keys");
            String prompt = I18n.tr("cli.ask.prompt");
            assertEquals(4, keys.chars().distinct().count(), l + ": " + keys);
            for (char c : keys.toCharArray()) {
                assertTrue(prompt.contains("[" + c + "]"), l + ": missing [" + c + "] in " + prompt);
            }
        }
    }

    @Test
    void enumLabelsExist() {
        ResourceBundle base = bundle(Locale.ENGLISH);
        for (RenameAction a : RenameAction.values()) {
            assertTrue(base.containsKey("action." + a.name()), a.name());
        }
        for (ConflictAction c : ConflictAction.values()) {
            assertTrue(base.containsKey("conflict." + c.name()), c.name());
        }
        for (Mode m : Mode.values()) {
            assertTrue(base.containsKey("mode." + m.name()), m.name());
        }
        for (String q : List.of("size", "resolution", "video", "bitrate", "audio", "duration", "modified")) {
            assertTrue(base.containsKey("quality." + q), q);
        }
        I18n.setLocale(Locale.ITALIAN);
        assertEquals("Chiedi caso per caso", ConflictAction.ASK.getLabel());
    }

    /** Every tr("key") / plural("key") in the code exists in the base language. */
    @Test
    void keysUsedInTheCodeExist() throws IOException {
        ResourceBundle base = bundle(Locale.ENGLISH);
        Pattern call = Pattern.compile("\\b(tr|plural)\\(\"([a-zA-Z0-9_.]+)\"\\s*[,)]");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = call.matcher(Files.readString(p));
                while (m.find()) {
                    String key = m.group(2);
                    boolean ok = m.group(1).equals("tr") ? base.containsKey(key) : base.containsKey(key + ".one") && base.containsKey(key + ".many");
                    if (!ok) {
                        missing.add(p.getFileName() + ": " + key);
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }
}
