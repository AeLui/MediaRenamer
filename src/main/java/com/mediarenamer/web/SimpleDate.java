package com.mediarenamer.web;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date without time zone (yyyy-MM-dd). */
public final class SimpleDate implements Serializable, Comparable<SimpleDate> {

    private static final Pattern ISO = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    private final int year;
    private final int month;
    private final int day;

    public SimpleDate(int year, int month, int day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public static SimpleDate of(LocalDate d) {
        return d == null ? null : new SimpleDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }

    /** Accepts "yyyy-MM-dd" (also with a time after it, e.g. "2020-01-02T00:00:00Z"). */
    public static SimpleDate parse(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        Matcher m = ISO.matcher(s);
        if (m.find()) {
            return new SimpleDate(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        return null;
    }

    public int getYear() {
        return year;
    }

    public int getMonth() {
        return month;
    }

    public int getDay() {
        return day;
    }

    public LocalDate toLocalDate() {
        return LocalDate.of(year, month, day);
    }

    /** Format with a java.time pattern, e.g. {@code {d.format('dd.MM.yyyy')}} */
    public String format(String pattern) {
        return toLocalDate().format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
    }

    public String format(String pattern, Locale locale) {
        return toLocalDate().format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    public long getTimeStamp() {
        return toLocalDate().toEpochDay() * 86400000L;
    }

    @Override
    public int compareTo(SimpleDate o) {
        return Integer.compare(year * 10000 + month * 100 + day, o.year * 10000 + o.month * 100 + o.day);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SimpleDate d && d.year == year && d.month == month && d.day == day;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day);
    }

    @Override
    public String toString() {
        return String.format("%04d-%02d-%02d", year, month, day);
    }
}
