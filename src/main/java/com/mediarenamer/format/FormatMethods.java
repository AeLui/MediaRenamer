package com.mediarenamer.format;

import com.mediarenamer.util.FileUtilities;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Groovy extension methods available inside formats: {n.upperInitial()}, {e.pad(2)}...
 * 
 * Registered in META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule.
 */
public final class FormatMethods {

    private FormatMethods() {
    }

    // ---------- padding and numbers ----------

    /** {e.pad(2)} -> "02" */
    public static String pad(Number self, int length) {
        return pad(self.toString(), length);
    }

    public static String pad(String self, int length) {
        return pad(self, length, "0");
    }

    public static String pad(CharSequence self, int length, CharSequence padding) {
        String s = self.toString();
        StringBuilder sb = new StringBuilder();
        while (sb.length() + s.length() < length) {
            sb.append(padding);
        }
        return sb.append(s).toString();
    }

    public static Number round(Number self, int precision) {
        return Math.round(self.doubleValue() * Math.pow(10, precision)) / Math.pow(10, precision);
    }

    // ---------- upper / lower case ----------

    public static String lower(String self) {
        return self.toLowerCase(Locale.ROOT);
    }

    public static String upper(String self) {
        return self.toUpperCase(Locale.ROOT);
    }

    private static final Pattern WORD = Pattern.compile("\\b((?<!['`´])\\p{Alnum}|['`´](?!m|s|re|ll)\\p{Alnum}(?=\\p{Alnum}))(\\p{Alnum}*)\\b");

    /** "the walking dead" -> "The Walking Dead" (only the first letter of each word). */
    public static String upperInitial(String self) {
        Matcher m = WORD.matcher(self);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1).toUpperCase() + m.group(2)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** "THE WALKING DEAD" -> "THe Walking Dead": lowercases everything except the initial of each word. */
    public static String lowerTrail(String self) {
        Matcher m = WORD.matcher(self);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + m.group(2).toLowerCase()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---------- spaces and separators ----------

    /** {n.space('.')} -> "The.Walking.Dead" */
    public static String space(String self, String replacement) {
        return self.replaceAll("[\\s\\p{Zs}]+", Matcher.quoteReplacement(replacement));
    }

    /** {t.colon(' - ')} replaces ':' (with its space) with the given text. */
    public static String colon(String self, String replacement) {
        return self.replaceAll("\\s*[:∶]\\s*", Matcher.quoteReplacement(replacement));
    }

    /** Variant with a ratio for "1:2" (numbers) and a separator for the rest. */
    public static String colon(String self, String ratio, String replacement) {
        return colon(self.replaceAll("(?<=\\d)[:∶](?=\\d)", Matcher.quoteReplacement(ratio)), replacement);
    }

    public static String slash(String self, String replacement) {
        return self.replaceAll("\\s*[\\\\/]+\\s*", Matcher.quoteReplacement(replacement));
    }

    // ---------- cleanup ----------

    public static String removeIllegalCharacters(String self) {
        return FileUtilities.validateFileName(self);
    }

    /** Replaces illegal characters with visually similar Unicode equivalents (":" -> "∶"). */
    public static String replaceIllegalCharacters(String self) {
        return self.replace(':', '∶').replace('/', '∕').replace('\\', '⧵').replace('?', '？').replace('*', '∗').replace('"', '\'')
                .replace('<', '‹').replace('>', '›').replace('|', '│');
    }

    /** Removes special characters and repeated spaces. */
    public static String clean(String self) {
        return self.replaceAll("[\\p{Punct}&&[^'&!,.()\\-]]+", " ").replaceAll("\\s+", " ").trim();
    }

    public static String ascii(String self) {
        return transliterate(self, null).replaceAll("[^\\p{ASCII}]+", "");
    }

    /** Removes diacritics: "Pokémon" -> "Pokemon" (the ICU transliteration id is not used). */
    public static String transliterate(String self, String id) {
        return Normalizer.normalize(self, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace("ß", "ss").replace("æ", "ae")
                .replace("Æ", "AE").replace("ø", "o").replace("Ø", "O").replace("ł", "l").replace("Ł", "L");
    }

    public static boolean isLatin(String self) {
        return transliterate(self, null).matches("[\\p{InBasicLatin}\\p{InLatin-1Supplement}\\p{InLatinExtended-A}\\p{InLatinExtended-B}]*");
    }

    public static String truncate(String self, int limit) {
        return self.length() <= limit ? self : self.substring(0, limit).trim();
    }

    public static String truncate(String self, int limit, String ellipsis) {
        return self.length() <= limit ? self : self.substring(0, Math.max(0, limit - ellipsis.length())).trim() + ellipsis;
    }

    // ---------- regex ----------

    /** First group (or the whole match) of the regex; throws if there is no match. */
    public static String match(String self, String pattern) {
        return match(self, pattern, -1);
    }

    public static String match(String self, String pattern, int group) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE).matcher(self);
        if (m.find()) {
            if (group >= 0) {
                return m.group(group);
            }
            return m.groupCount() > 0 ? m.group(1) : m.group();
        }
        throw new BindingException("match", "Pattern not found: " + pattern);
    }

    public static List<String> matchAll(String self, String pattern) {
        return matchAll(self, pattern, -1);
    }

    public static List<String> matchAll(String self, String pattern, int group) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE).matcher(self);
        List<String> l = new java.util.ArrayList<>();
        while (m.find()) {
            l.add(group >= 0 ? m.group(group) : m.groupCount() > 0 ? m.group(1) : m.group());
        }
        if (l.isEmpty()) {
            throw new BindingException("matchAll", "Pattern not found: " + pattern);
        }
        return l;
    }

    /** Removes the matches: {t.replaceAll(/[!?.]+$/)} */
    public static String replaceAll(String self, String pattern) {
        return self.replaceAll(pattern, "").trim();
    }

    public static String removeAll(String self, String pattern) {
        return self.replaceAll("(?i)" + pattern, "").replaceAll("\\s+", " ").trim();
    }

    /** Elimina palabras completas: {n.remove('The', 'A')} */
    public static String remove(String self, String word, String... more) {
        String s = self;
        for (String w : concat(word, more)) {
            s = s.replaceAll("(?i)(?<!\\p{Alnum})" + Pattern.quote(w) + "(?!\\p{Alnum})", "");
        }
        return s.replaceAll("\\s+", " ").trim();
    }

    public static String before(String self, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(self);
        return m.find() ? self.substring(0, m.start()) : self;
    }

    public static String after(String self, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(self);
        return m.find() ? self.substring(m.end()) : self;
    }

    public static String findMatch(String self, String pattern) {
        Matcher m = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(self);
        return m.find() ? m.group() : null;
    }

    public static String findWordMatch(String self, String pattern) {
        return findMatch(self, "(?<!\\p{Alnum})(?:" + pattern + ")(?!\\p{Alnum})");
    }

    // ---------- brackets and parts ----------

    private static final Pattern BRACKETS = Pattern.compile("[\\(\\[\\{]([^\\(\\[\\{\\)\\]\\}]*)[\\)\\]\\}]");

    /** Content of all brackets/parentheses. */
    public static List<String> matchBrackets(String self) {
        Matcher m = BRACKETS.matcher(self);
        List<String> l = new java.util.ArrayList<>();
        while (m.find()) {
            l.add(m.group(1));
        }
        return l;
    }

    public static String removeBrackets(String self) {
        return BRACKETS.matcher(self).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    /** "Title (1)" -> "Title, Part 1" (or "Title" if no replacement is given). */
    public static String replaceTrailingBrackets(String self) {
        return replaceTrailingBrackets(self, "");
    }

    public static String replaceTrailingBrackets(String self, String replacement) {
        Matcher m = Pattern.compile("\\s*[(]([^)]*)[)]$").matcher(self);
        if (m.find()) {
            return self.substring(0, m.start()) + replacement.replace("$1", m.group(1));
        }
        return self;
    }

    /** "Episode Title (1)" or "Episode Title, Part 1" -> "Episode Title, Part 1" with the given format. */
    public static String replacePart(String self) {
        return replacePart(self, "");
    }

    public static String replacePart(String self, String replacement) {
        Matcher m = Pattern.compile("(?:\\s*[(](\\d{1,2})[)]$|\\W+Part (\\w+)\\W*$)").matcher(self);
        if (m.find()) {
            String n = m.group(1) != null ? m.group(1) : m.group(2);
            return self.substring(0, m.start()) + replacement.replace("$1", n);
        }
        return self;
    }

    // ---------- names ----------

    /** "The Walking Dead" -> "Walking Dead, The" */
    public static String sortName(String self) {
        return sortName(self, "$2, $1");
    }

    public static String sortName(String self, String format) {
        Matcher m = Pattern.compile("^(The|A|An)\\s+(.+)", Pattern.CASE_INSENSITIVE).matcher(self);
        return m.matches() ? format.replace("$1", m.group(1)).replace("$2", m.group(2)) : self;
    }

    /** Sort initial: "The Walking Dead" -> "W", "2012" -> "0-9" */
    public static String sortInitial(String self) {
        String s = ascii(sortName(self, "$2")).trim();
        if (s.isEmpty()) {
            return "#";
        }
        char c = Character.toUpperCase(s.charAt(0));
        return Character.isDigit(c) ? "0-9" : Character.isLetter(c) ? String.valueOf(c) : "#";
    }

    /** "John Doe" -> "J. Doe" */
    public static String initialName(String self) {
        String[] words = self.trim().split("\\s+");
        if (words.length < 2) {
            return self;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length - 1; i++) {
            sb.append(words[i].charAt(0)).append(". ");
        }
        return sb.append(words[words.length - 1]).toString();
    }

    /** "Law and Order Special Victims Unit" -> "LOSVU" */
    public static String acronym(String self) {
        return Arrays.stream(self.split("[\\s\\p{Punct}]+")).filter(w -> !w.isEmpty() && !w.equalsIgnoreCase("and") && !w.equalsIgnoreCase("of"))
                .map(w -> w.substring(0, 1).toUpperCase()).collect(Collectors.joining());
    }

    private static final int[] ROMAN_VALUES = { 1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] ROMAN_SYMBOLS = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };

    /** Convierte numeros sueltos a romanos: "Rocky 2" -> "Rocky II" */
    public static String roman(String self) {
        Matcher m = Pattern.compile("\\b\\d+\\b").matcher(self);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group());
            m.appendReplacement(sb, n > 0 && n < 4000 ? toRoman(n) : m.group());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String toRoman(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (n >= ROMAN_VALUES[i]) {
                sb.append(ROMAN_SYMBOLS[i]);
                n -= ROMAN_VALUES[i];
            }
        }
        return sb.toString();
    }

    // ---------- collections and maps ----------

    /** {genres.joining(', ')} joins without nulls or empty values. */
    public static String joining(Collection<?> self, String delimiter) {
        return self.stream().filter(Objects::nonNull).map(Object::toString).filter(s -> !s.isBlank()).collect(Collectors.joining(delimiter));
    }

    public static String joining(Collection<?> self, String delimiter, String prefix, String suffix) {
        String s = joining(self, delimiter);
        if (s.isEmpty()) {
            throw new BindingException("joining", "Collection did not yield any values");
        }
        return prefix + s + suffix;
    }

    /** Replacement by map: {n.replace('Marvel\'s ': '')} */
    public static String replace(String self, Map<?, ?> replacements) {
        String s = self;
        for (Map.Entry<?, ?> e : replacements.entrySet()) {
            String k = String.valueOf(e.getKey());
            String v = String.valueOf(e.getValue());
            s = s.replace(k, v);
        }
        return s;
    }

    /** First map key (regex) matching the text -> its value: {vf.match('2160p':'4K', '1080p':'FHD')} */
    public static Object match(String self, Map<?, ?> cases) {
        for (Map.Entry<?, ?> e : cases.entrySet()) {
            if (Pattern.compile(String.valueOf(e.getKey()), Pattern.CASE_INSENSITIVE).matcher(self).find()) {
                return e.getValue();
            }
        }
        throw new BindingException("match", "no case matches " + self);
    }

    private static List<String> concat(String first, String... more) {
        List<String> l = new java.util.ArrayList<>();
        l.add(first);
        l.addAll(Arrays.asList(more));
        return l;
    }
}
