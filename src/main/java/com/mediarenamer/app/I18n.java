package com.mediarenamer.app;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Interface and CLI texts in several languages.
 *
 * The texts live in {@code com/mediarenamer/i18n/messages*.properties} (UTF-8): {@code messages.properties}
 * is English (base and fallback for any missing key) and there is one per language ({@code _es}, {@code _fr},
 * {@code _de}, {@code _pt}, {@code _it}). The language is chosen in Preferences ({@link Settings#UI_LANGUAGE});
 * empty = the system language if supported, otherwise English.
 *
 * Parameters are written {0}, {1}... and replaced literally: MessageFormat is not used, so
 * apostrophes (French, Italian) need not be doubled and format variables such as {n} or {s00e00}
 * can appear in the texts without escaping.
 */
public final class I18n {

    /** Interface languages, in the order of the Preferences selector. */
    public static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.forLanguageTag("es"), Locale.FRENCH, Locale.GERMAN,
            Locale.forLanguageTag("pt"), Locale.ITALIAN);

    private static final String BUNDLE = "com.mediarenamer.i18n.messages";
    /** No fallback to the system language: a key missing in a language shows in English, not in the machine's language. */
    private static final ResourceBundle.Control CONTROL = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    /** System language at startup (before anyone changes Locale.getDefault()). */
    private static final Locale SYSTEM = Locale.getDefault();

    private static volatile Locale locale;
    private static volatile ResourceBundle bundle;

    private I18n() {
    }

    /** Interface language in use. */
    public static Locale locale() {
        Locale l = locale;
        if (l == null) {
            Settings settings = Settings.get();
            if (settings == null) {
                // messages emitted while the preferences themselves are loading: system language, without fixing it
                l = resolve(null);
                bundle = ResourceBundle.getBundle(BUNDLE, l, I18n.class.getClassLoader(), CONTROL);
                return l;
            }
            l = resolve(settings.get(Settings.UI_LANGUAGE));
            setLocale(l);
        }
        return l;
    }

    /** Changes the language in memory (Preferences stores it separately; tests set it here). */
    public static synchronized void setLocale(Locale l) {
        locale = l;
        bundle = ResourceBundle.getBundle(BUNDLE, l, I18n.class.getClassLoader(), CONTROL);
    }

    /** Supported language for a tag ("es", "pt-BR"...); empty or unsupported = system or English. */
    public static Locale resolve(String tag) {
        Locale wanted = tag == null || tag.isBlank() ? SYSTEM : Locale.forLanguageTag(tag.trim());
        for (Locale s : SUPPORTED) {
            if (s.getLanguage().equals(wanted.getLanguage())) {
                return s;
            }
        }
        return Locale.ENGLISH;
    }

    /** System language that the "automatic" option would use. */
    public static Locale systemLocale() {
        return resolve(null);
    }

    /** Text of the key with the parameters {0}, {1}... replaced; the key itself if it does not exist. */
    public static String tr(String key, Object... args) {
        locale();
        String s;
        try {
            s = bundle.getString(key);
        } catch (MissingResourceException e) {
            Logging.debug("i18n: missing key " + key);
            s = key;
        }
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /** Singular or plural: key + ".one" if n == 1, otherwise key + ".many"; {0} is n. */
    public static String plural(String key, long n, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = n;
        System.arraycopy(args, 0, all, 1, args.length);
        return tr(key + (n == 1 ? ".one" : ".many"), all);
    }

    /** Language name in its own language, capitalized: "Español", "Deutsch". */
    public static String nativeName(Locale l) {
        String n = l.getDisplayLanguage(l);
        return n.isEmpty() ? l.toLanguageTag() : n.substring(0, 1).toUpperCase(l) + n.substring(1);
    }

    /**
     * Language of Swing's own texts (JOptionPane Yes/No buttons, JFileChooser). Swing ships
     * Brazilian Portuguese, not European Portuguese: pt-BR is used for those texts.
     */
    public static void applyToSwing() {
        Locale l = locale();
        Locale swing = l.getLanguage().equals("pt") ? Locale.forLanguageTag("pt-BR") : l;
        javax.swing.JComponent.setDefaultLocale(swing);
        javax.swing.UIManager.getDefaults().setDefaultLocale(swing);
        javax.swing.UIManager.getLookAndFeelDefaults().setDefaultLocale(swing);
    }
}
