package com.mediarenamer.format;

import groovy.lang.Closure;
import groovy.lang.Script;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base class of each {...} block. Provides the global functions
 * any, allOf, none, list, concat, quote, csv, lines...
 */
public abstract class FormatScript extends Script {

    /** First non-empty value of the closures; failing ones are ignored: {any{audio.language}{'und'}} */
    public Object any(Closure<?>... closures) {
        for (Closure<?> c : closures) {
            Object v = call(c);
            if (!isEmptyValue(v)) {
                return v;
            }
        }
        throw new BindingException("any", "all options are empty");
    }

    /** All non-empty values: {allOf{vf}{vc}{ac}.join(' ')} */
    public List<Object> allOf(Closure<?>... closures) {
        List<Object> values = new ArrayList<>();
        for (Closure<?> c : closures) {
            Object v = call(c);
            if (!isEmptyValue(v)) {
                values.add(v);
            }
        }
        return values;
    }

    /** true if no closure yields a truthy value. */
    public boolean none(Closure<?>... closures) {
        for (Closure<?> c : closures) {
            Object v = call(c);
            if (!isEmptyValue(v) && !Boolean.FALSE.equals(v)) {
                return false;
            }
        }
        return true;
    }

    /** Flat list of the non-empty arguments. */
    public List<Object> list(Object... values) {
        List<Object> l = new ArrayList<>();
        for (Object v : values) {
            if (v instanceof Closure<?> c) {
                v = call(c);
            }
            if (v instanceof Collection<?> col) {
                col.stream().filter(x -> !isEmptyValue(x)).forEach(l::add);
            } else if (!isEmptyValue(v)) {
                l.add(v);
            }
        }
        return l;
    }

    /** Concatenates the non-empty values (closures included). */
    public String concat(Object... values) {
        return list(values).stream().map(String::valueOf).collect(Collectors.joining());
    }

    public long count(Object... values) {
        return list(values).size();
    }

    public String quote(Object... values) {
        return list(values).stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(" "));
    }

    /** Reads a two-column CSV/TSV as a map: {csv('/path/names.csv').get(n, n)} */
    public Map<String, String> csv(Object path) throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : lines(path)) {
            String[] cols = line.split("[\t;,]", 2);
            if (cols.length == 2) {
                m.put(cols[0].trim(), cols[1].trim());
            }
        }
        return m;
    }

    public List<String> lines(Object path) throws Exception {
        File f = path instanceof File file ? file : new File(path.toString());
        return Files.readAllLines(f.toPath(), StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
    }

    public String text(Object path) throws Exception {
        File f = path instanceof File file ? file : new File(path.toString());
        return Files.readString(f.toPath(), StandardCharsets.UTF_8);
    }

    private static Object call(Closure<?> c) {
        try {
            return c.call();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static boolean isEmptyValue(Object v) {
        return v == null || v instanceof CharSequence cs && cs.toString().isBlank() || v instanceof Collection<?> c && c.isEmpty()
                || v instanceof Map<?, ?> m && m.isEmpty();
    }
}
