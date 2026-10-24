package com.mediarenamer.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Null-tolerant access to the Map/List tree produced by JsonSlurper. */
public final class Json {

    private Json() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object node, String key) {
        Object v = get(node, key);
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object node, String key) {
        Object v = key == null ? node : get(node, key);
        return v instanceof List ? (List<Object>) v : List.of();
    }

    public static List<Map<String, Object>> maps(Object node, String key) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list(node, key)) {
            if (o instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) o;
                out.add(m);
            }
        }
        return out;
    }

    public static Object get(Object node, String key) {
        if (node instanceof Map<?, ?> m) {
            return m.get(key);
        }
        return null;
    }

    public static String string(Object node, String key) {
        Object v = get(node, key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() || s.equals("N/A") ? null : s;
    }

    public static Integer integer(Object node, String key) {
        Object v = get(node, key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.valueOf(v.toString().trim().replaceAll("[^0-9-].*$", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public static Double decimal(Object node, String key) {
        Object v = get(node, key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v != null) {
            try {
                return Double.valueOf(v.toString().replace(",", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Extracts a field from each element of a list of objects, e.g. genres[].name */
    public static List<String> strings(Object node, String key, String field) {
        return maps(node, key).stream().map(m -> string(m, field)).filter(Objects::nonNull).distinct().collect(Collectors.toList());
    }

    public static List<String> strings(Collection<?> values) {
        return values.stream().filter(Objects::nonNull).map(Object::toString).filter(s -> !s.isBlank()).collect(Collectors.toList());
    }

    public static <T> List<T> mapAll(Object node, String key, Function<Map<String, Object>, T> f) {
        return maps(node, key).stream().map(f).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
