package com.mediarenamer.app;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Minimal logging: console + listeners (the UI shows the messages in its status bar). */
public final class Logging {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile boolean debug = Boolean.getBoolean("mediarenamer.debug");

    private Logging() {
    }

    public static void addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Consumer<String> listener) {
        LISTENERS.remove(listener);
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static void debug(String message) {
        if (debug) {
            emit("[DEBUG] " + message);
        }
    }

    public static void info(String message) {
        emit(message);
    }

    public static void warn(String message) {
        emit("[WARN] " + message);
    }

    private static void emit(String message) {
        String line = TIME.format(LocalTime.now()) + " " + message;
        System.err.println(line);
        for (Consumer<String> l : LISTENERS) {
            l.accept(message);
        }
    }
}
