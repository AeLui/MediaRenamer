package com.mediarenamer.format;

import java.util.List;

/** Syntax or evaluation error of a format. */
public class FormatException extends Exception {

    public FormatException(String message) {
        super(message);
    }

    public FormatException(String message, List<Throwable> suppressed) {
        super(message + (suppressed.isEmpty() ? "" : ": " + describe(suppressed.get(0))));
        suppressed.forEach(this::addSuppressed);
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : m.split("\\R")[0];
    }
}
