package com.mediarenamer.similarity;

import com.mediarenamer.web.SimpleDate;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date detection in file names (daily shows: "Show.2024.03.15"). */
public class DateMatcher {

    private static final Pattern YMD = Pattern.compile("(?<!\\p{Alnum})((?:19|20)\\d{2})[\\P{Alnum}]{1,2}(\\d{1,2})[\\P{Alnum}]{1,2}(\\d{1,2})(?!\\p{Digit})");
    private static final Pattern DMY = Pattern.compile("(?<!\\p{Alnum})(\\d{1,2})[\\P{Alnum}]{1,2}(\\d{1,2})[\\P{Alnum}]{1,2}((?:19|20)\\d{2})(?!\\p{Digit})");
    private static final Pattern COMPACT = Pattern.compile("(?<!\\p{Alnum})((?:19|20)\\d{2})(\\d{2})(\\d{2})(?!\\p{Alnum})");

    public SimpleDate match(CharSequence s) {
        for (Pattern p : new Pattern[] { YMD, COMPACT }) {
            Matcher m = p.matcher(s);
            while (m.find()) {
                SimpleDate d = valid(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
                if (d != null) {
                    return d;
                }
            }
        }
        Matcher m = DMY.matcher(s);
        while (m.find()) {
            SimpleDate d = valid(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static SimpleDate valid(int y, int mo, int d) {
        if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
            return new SimpleDate(y, mo, d);
        }
        return null;
    }
}
