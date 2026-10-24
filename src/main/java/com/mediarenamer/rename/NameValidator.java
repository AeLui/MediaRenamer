package com.mediarenamer.rename;

import static com.mediarenamer.app.I18n.tr;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pre-check of the new names before renaming: detects what would make the rename fail on
 * Windows (errors) and what is better avoided on media servers or SMB (warnings), and suggests a
 * clean name for each case.
 */
public final class NameValidator {

    public enum Severity {
        /** The rename would fail or the name would end up different than expected. */
        ERROR,
        /** It would work, but it is worth fixing. */
        WARNING
    }

    /** Problem detected in the target of an entry, with the suggested correction (or null). */
    public record Issue(RenameEngine.Entry entry, Severity severity, String problem, File suggestion) {
    }

    /** NTFS name limit. */
    static final int MAX_NAME = 255;
    /** Classic Windows path limit (MAX_PATH - 1); many programs do not support more. */
    static final int MAX_PATH = 259;

    private static final Pattern ILLEGAL = Pattern.compile("[<>:\"|?*\\p{Cntrl}]");
    private static final Pattern RESERVED = Pattern.compile("(?i)^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$");
    /** Invisible or problematic characters: odd spaces, zero width, direction marks. */
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00A0\\u2000-\\u200F\\u2028-\\u202F\\u205F\\u2060\\uFEFF]");
    /** Homoglyphs of forbidden characters that sometimes come from the services ("∶", "⁄", "？"...). */
    private static final Pattern LOOKALIKE = Pattern.compile("[\\u2236\\uA789\\uFF1A\\u2215\\u2044\\uFF0F\\u29F5\\uFF3C\\uFF1F\\u2217\\uFF0A\\u2039\\u203A\\u2502\\uFF5C]");

    private NameValidator() {
    }

    public static List<Issue> validate(List<RenameEngine.Entry> entries) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (RenameEngine.Entry e : entries) {
            if (!e.ok()) {
                continue;
            }
            File target = e.target();
            Set<String> problems = new LinkedHashSet<>();
            Severity severity = null;
            for (String seg : segments(target)) {
                if (ILLEGAL.matcher(seg).find()) {
                    problems.add(tr("problem.illegal", listChars(seg, ILLEGAL)));
                    severity = Severity.ERROR;
                }
                if (RESERVED.matcher(seg).matches()) {
                    problems.add(tr("problem.reserved", seg));
                    severity = Severity.ERROR;
                }
                if (seg.endsWith(" ") || seg.endsWith(".")) {
                    problems.add(tr("problem.trailing"));
                    severity = Severity.ERROR;
                }
                if (seg.length() > MAX_NAME) {
                    problems.add(tr("problem.nameTooLong", seg.length(), MAX_NAME));
                    severity = Severity.ERROR;
                }
                if (seg.startsWith(" ") || seg.contains("  ")) {
                    problems.add(tr("problem.spaces"));
                    severity = max(severity, Severity.WARNING);
                }
                if (INVISIBLE.matcher(seg).find() || LOOKALIKE.matcher(seg).find() || hasSymbols(seg)) {
                    problems.add(tr("problem.special"));
                    severity = max(severity, Severity.WARNING);
                }
            }
            if (target.getAbsolutePath().length() > MAX_PATH) {
                problems.add(tr("problem.pathTooLong", target.getAbsolutePath().length(), MAX_PATH));
                severity = max(severity, Severity.WARNING);
            }
            String key = target.getAbsolutePath().toLowerCase(Locale.ROOT);
            int count = seen.merge(key, 1, Integer::sum);
            File suggestion = problems.isEmpty() ? null : clean(target);
            if (count > 1) {
                problems.add(tr("problem.duplicate"));
                severity = max(severity, Severity.WARNING);
                suggestion = withIndex(suggestion != null ? suggestion : target, count - 1);
            }
            if (!problems.isEmpty()) {
                issues.add(new Issue(e, severity, String.join(" · ", problems), suggestion == null || suggestion.equals(target) ? null : suggestion));
            }
        }
        return issues;
    }

    /** Clean version of a path: fixes each segment below the root (drive, UNC). */
    public static File clean(File target) {
        String abs = target.getAbsolutePath();
        File root = target.getAbsoluteFile();
        while (root.getParentFile() != null) {
            root = root.getParentFile();
        }
        List<String> segs = segments(target);
        File result = root;
        for (int i = 0; i < segs.size(); i++) {
            boolean last = i == segs.size() - 1;
            result = new File(result, cleanSegment(segs.get(i), last));
        }
        // whole path: shorten the file name if needed (keeping the extension)
        if (result.getAbsolutePath().length() > MAX_PATH && abs.length() > MAX_PATH) {
            int excess = result.getAbsolutePath().length() - MAX_PATH;
            result = new File(result.getParentFile(), truncate(result.getName(), Math.max(20, result.getName().length() - excess)));
        }
        return result;
    }

    /** Cleans a name: homoglyphs and forbidden, invisible, symbols, leftover spaces and dots, reserved names and length. */
    public static String cleanSegment(String seg, boolean isFile) {
        String s = Normalizer.normalize(seg, Normalizer.Form.NFC);
        s = s.replaceAll("\\s*[:\\u2236\\uA789\\uFF1A]\\s*", " - ");
        s = s.replaceAll("[\\u2215\\u2044\\uFF0F\\u29F5\\uFF3C]", "-");
        s = s.replace('"', '\'').replace('\uFF1F', ' ').replace('\u2217', ' ').replace('\uFF0A', ' ');
        s = s.replaceAll("[\\u2039\\u203A<>|\\u2502\\uFF5C?*]", " ");
        s = INVISIBLE.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\p{Cntrl}", "");
        s = stripSymbols(s);
        s = s.replaceAll("\\s+", " ").replaceAll("\\s*-\\s*-\\s*", " - ").trim();
        s = s.replaceAll("[ .]+$", "");
        if (RESERVED.matcher(s).matches()) {
            int dot = s.indexOf('.');
            s = dot < 0 ? s + "_" : s.substring(0, dot) + "_" + s.substring(dot);
        }
        if (s.length() > MAX_NAME) {
            s = isFile ? truncate(s, MAX_NAME) : s.substring(0, MAX_NAME).trim();
        }
        return s.isEmpty() ? "_" : s;
    }

    /** Shortens the base name keeping the extension (including the subtitle language one ".spa.srt"). */
    static String truncate(String name, int max) {
        if (name.length() <= max) {
            return name;
        }
        java.util.regex.Matcher m = Pattern.compile("((?:\\.[a-z]{2,3})?\\.[A-Za-z0-9]{1,5})$").matcher(name);
        String ext = m.find() ? m.group(1) : "";
        String base = name.substring(0, name.length() - ext.length());
        int keep = Math.max(1, max - ext.length());
        return base.substring(0, Math.min(base.length(), keep)).trim() + ext;
    }

    private static File withIndex(File f, int n) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        return new File(f.getParentFile(), base + " (" + n + ")" + ext);
    }

    /** Path segments below the root ("Z:\Series\Dark (2017)\Dark - 1x01.mkv" -> 3 segments). */
    static List<String> segments(File f) {
        List<String> l = new ArrayList<>();
        for (File x = f.getAbsoluteFile(); x != null && x.getParentFile() != null; x = x.getParentFile()) {
            l.add(0, x.getName());
        }
        return l;
    }

    /** Emojis and graphic symbols (not letters, digits or normal punctuation). */
    static boolean hasSymbols(String s) {
        return s.codePoints().anyMatch(NameValidator::isSymbol);
    }

    private static String stripSymbols(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().filter(cp -> !isSymbol(cp)).forEach(sb::appendCodePoint);
        return sb.toString();
    }

    private static boolean isSymbol(int cp) {
        if (Character.isSupplementaryCodePoint(cp) && !Character.isLetterOrDigit(cp)) {
            return true; // emojis and the like
        }
        int t = Character.getType(cp);
        return t == Character.OTHER_SYMBOL || t == Character.PRIVATE_USE || t == Character.SURROGATE || t == Character.UNASSIGNED;
    }

    private static String listChars(String s, Pattern p) {
        Set<String> chars = new LinkedHashSet<>();
        java.util.regex.Matcher m = p.matcher(s);
        while (m.find()) {
            String c = m.group();
            chars.add(Character.isISOControl(c.charAt(0)) ? "control" : c);
        }
        return String.join(" ", chars);
    }

    private static Severity max(Severity a, Severity b) {
        return a == Severity.ERROR || b == Severity.ERROR ? Severity.ERROR : Severity.WARNING;
    }
}
