package com.mediarenamer.similarity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Text normalization to compare names. */
public final class Normalization {

    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Punct}\\p{Space}\\p{Cntrl}\\p{Cf}\\p{Sk}]+");
    private static final Pattern APOSTROPHE = Pattern.compile("['`´‘’ʻ]+");
    private static final Pattern CHECKSUM = Pattern.compile("[\\(\\[](\\p{XDigit}{8})[\\]\\)]");
    private static final Pattern TRAILING_PARENTHESIS = Pattern.compile("(?<!^)[(]([^)]*)[)]$");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private Normalization() {
    }

    /** "The.Show_(2010)" -> "The Show 2010" */
    public static String normalizePunctuation(String s) {
        s = APOSTROPHE.matcher(s).replaceAll("");
        return PUNCTUATION.matcher(s).replaceAll(" ").trim();
    }

    public static String removeEmbeddedChecksum(String s) {
        return CHECKSUM.matcher(s).replaceAll("");
    }

    public static String removeTrailingBrackets(String s) {
        return TRAILING_PARENTHESIS.matcher(s).replaceAll("").trim();
    }

    /** Removes accents: "Película" -> "Pelicula" */
    public static String stripDiacritics(String s) {
        return DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
    }

    /** Canonical form for comparison: no accents, no punctuation, lowercase. */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return normalizePunctuation(stripDiacritics(removeEmbeddedChecksum(s))).toLowerCase(Locale.ROOT);
    }

    public static String replaceSpace(String s, String replacement) {
        return s.replaceAll("[\\s]+", replacement);
    }
}
