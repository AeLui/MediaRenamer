package com.mediarenamer.media;

import java.io.File;
import java.util.Locale;
import java.util.Set;

/** File categories by extension. */
public final class MediaTypes {

    public static final Set<String> VIDEO = Set.of("avi", "mkv", "mp4", "m4v", "mov", "wmv", "mpg", "mpeg", "m2ts", "ts", "mts", "vob", "ogm",
            "ogv", "webm", "flv", "divx", "xvid", "3gp", "rmvb", "rm", "asf", "iso", "img", "tp", "strm");
    public static final Set<String> SUBTITLE = Set.of("srt", "sub", "ssa", "ass", "smi", "idx", "sup", "vtt", "ttml", "txt");
    public static final Set<String> AUDIO = Set.of("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "alac", "aiff", "aif",
            "mka", "dts", "ac3", "wv", "mpc", "dsf");
    public static final Set<String> IMAGE = Set.of("jpg", "jpeg", "png", "gif", "bmp", "tif", "tiff", "webp", "heic", "nef", "cr2", "arw", "dng");
    public static final Set<String> NFO = Set.of("nfo");

    private MediaTypes() {
    }

    public static String extension(File f) {
        return extension(f.getName());
    }

    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? null : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Name without extension. */
    public static String nameWithoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    public static boolean isVideo(File f) {
        return VIDEO.contains(extension(f));
    }

    public static boolean isSubtitle(File f) {
        return SUBTITLE.contains(extension(f));
    }

    public static boolean isAudio(File f) {
        return AUDIO.contains(extension(f));
    }

    public static boolean isImage(File f) {
        return IMAGE.contains(extension(f));
    }

    /** Files that accompany a video (subtitles, nfo, images with the same base name). */
    public static boolean isCompanion(File f) {
        return isSubtitle(f) || NFO.contains(extension(f)) || isImage(f);
    }
}
