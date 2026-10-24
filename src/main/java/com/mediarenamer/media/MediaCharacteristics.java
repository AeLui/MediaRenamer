package com.mediarenamer.media;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Properties derived from MediaInfo for the bindings {vf}, {vc}, {ac}, {channels}, {hdr}...
 * Resolution classes as in the usual 480p/720p/1080p/2160p naming.
 */
public final class MediaCharacteristics {

    /** Resolution classes {lines, reference width} of the 16:9 frames, largest first. */
    private static final int[][] RESOLUTION_CLASSES = { { 4320, 7680 }, { 2160, 3840 }, { 1440, 2560 }, { 1080, 1920 }, { 720, 1280 },
            { 576, 1024 }, { 480, 854 }, { 360, 640 } };

    private final File file;
    private final Map<String, List<Map<String, String>>> info;

    public MediaCharacteristics(File file) {
        this.file = file;
        this.info = MediaInfo.read(file);
    }

    public boolean isEmpty() {
        return info.isEmpty();
    }

    public Map<String, String> general() {
        return first("General");
    }

    public Map<String, String> video() {
        return first("Video");
    }

    public Map<String, String> audio() {
        return first("Audio");
    }

    public List<Map<String, String>> streams(String kind) {
        return info.getOrDefault(kind, List.of());
    }

    private Map<String, String> first(String kind) {
        List<Map<String, String>> l = info.get(kind);
        return l == null || l.isEmpty() ? Map.of() : l.get(0);
    }

    public Integer width() {
        return integer(video().get("Width"));
    }

    public Integer height() {
        return integer(video().get("Height"));
    }

    /** {vf}: 2160p, 1080p, 720p, 576p, 480p... (resolution class of the frame). */
    public String videoFormat() {
        Integer w = width();
        Integer h = height();
        if (w == null || h == null || w <= 0 || h <= 0) {
            return null;
        }
        return videoFormat(w, h, "Interlaced".equalsIgnoreCase(video().get("ScanType")));
    }

    /**
     * Resolution class of a frame: the largest class whose 16:9 reference frame is reached (98 %) by the width or by
     * the height, so letterboxed (1920x800) and 4:3 or anamorphic (1440x1080) video get the class of their frame.
     * Frames below the smallest class report their own height.
     */
    public static String videoFormat(int width, int height, boolean interlaced) {
        int lines = height;
        for (int[] c : RESOLUTION_CLASSES) {
            if (width * 100 >= c[1] * 98 || height * 100 >= c[0] * 98) {
                lines = c[0];
                break;
            }
        }
        return lines + (interlaced ? "i" : "p");
    }

    /** {vk}: 8K, 4K, 2K or the {vf} for lower resolutions. */
    public String videoFormatK() {
        String vf = videoFormat();
        if (vf == null) {
            return null;
        }
        int h = Integer.parseInt(vf.replaceAll("\\D", ""));
        return h >= 4320 ? "8K" : h >= 2160 ? "4K" : h >= 1440 ? "2K" : vf;
    }

    /** {hd}: UHD, HD o SD. */
    public String definition() {
        Integer w = width();
        Integer h = height();
        if (w == null || h == null) {
            return null;
        }
        return w >= 3840 || h >= 2160 ? "UHD" : w >= 1280 || h >= 720 ? "HD" : "SD";
    }

    /** {vc}: x264/x265 if the encoding library is known; otherwise AVC, HEVC, AV1, Xvid, DivX... */
    public String videoCodec() {
        Map<String, String> v = video();
        String lib = Objects.toString(v.get("Encoded_Library_Name"), Objects.toString(v.get("Encoded_Library"), "")).toLowerCase(Locale.ROOT);
        if (lib.contains("x264")) {
            return "x264";
        }
        if (lib.contains("x265")) {
            return "x265";
        }
        String format = v.get("Format");
        if (format == null) {
            return null;
        }
        String codecId = Objects.toString(v.get("CodecID"), "").toUpperCase(Locale.ROOT);
        return switch (format) {
            case "MPEG-4 Visual" -> codecId.contains("XVID") ? "Xvid" : codecId.contains("DX50") || codecId.contains("DIVX") ? "DivX" : "MPEG-4";
            case "MPEG Video" -> "MPEG-" + Objects.toString(v.get("Format_Version"), "2").replaceAll("\\D", "");
            default -> format;
        };
    }

    /** {ac}: AAC, AC3, EAC3, DTS, TrueHD, FLAC, MP3, Opus... */
    public String audioCodec() {
        return audioCodec(audio());
    }

    static String audioCodec(Map<String, String> a) {
        String format = a.get("Format");
        if (format == null) {
            return null;
        }
        String profile = Objects.toString(a.get("Format_Commercial_IfAny"), "") + " " + Objects.toString(a.get("Format_Profile"), "");
        return switch (format) {
            case "AC-3" -> "AC3";
            case "E-AC-3" -> profile.contains("Atmos") ? "EAC3 Atmos" : "EAC3";
            case "MLP FBA" -> profile.contains("Atmos") ? "TrueHD Atmos" : "TrueHD";
            case "DTS" -> profile.contains("MA") ? "DTS-HD MA" : profile.contains("X") ? "DTS-X" : "DTS";
            case "MPEG Audio" -> "Layer 3".equals(a.get("Format_Profile")) ? "MP3" : "MP2";
            default -> format;
        };
    }

    /** {cf}: container format from the extension (mkv, mp4, avi...). */
    public String containerFormat() {
        String ext = MediaTypes.extension(file);
        return ext == null ? null : ext;
    }

    /** {channels}: 2.0, 5.1, 7.1 */
    public String channels() {
        Integer n = integer(audio().get("Channels"));
        if (n == null) {
            return null;
        }
        return switch (n) {
            case 1 -> "1.0";
            case 2 -> "2.0";
            case 3 -> "2.1";
            case 6 -> "5.1";
            case 7 -> "6.1";
            case 8 -> "7.1";
            default -> n + "ch";
        };
    }

    /** {af}: 6ch, 2ch */
    public String audioChannelFormat() {
        Integer n = integer(audio().get("Channels"));
        return n == null ? null : n + "ch";
    }

    /** {hdr}: HDR10, HDR10+, Dolby Vision, HLG or null. */
    public String hdr() {
        String f = Objects.toString(video().get("HDR_Format"), "") + " " + Objects.toString(video().get("HDR_Format_Compatibility"), "");
        if (f.isBlank()) {
            String transfer = Objects.toString(video().get("transfer_characteristics"), "");
            return transfer.contains("PQ") ? "HDR10" : transfer.contains("HLG") ? "HLG" : null;
        }
        if (f.contains("Dolby Vision")) {
            return "DV";
        }
        if (f.contains("HDR10+")) {
            return "HDR10+";
        }
        if (f.contains("HLG")) {
            return "HLG";
        }
        return "HDR10";
    }

    public boolean dolbyVision() {
        return Objects.toString(video().get("HDR_Format"), "").contains("Dolby Vision");
    }

    public Integer bitDepth() {
        return integer(video().get("BitDepth"));
    }

    public String aspectRatio() {
        String ar = video().get("DisplayAspectRatio");
        if (ar == null) {
            return null;
        }
        try {
            return new BigDecimal(ar).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + ":1";
        } catch (NumberFormatException e) {
            return ar;
        }
    }

    public Double aspectRatioValue() {
        String ar = video().get("DisplayAspectRatio");
        try {
            return ar == null ? null : Double.valueOf(ar);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String frameRate() {
        String fps = video().get("FrameRate");
        if (fps == null) {
            return null;
        }
        try {
            return new BigDecimal(fps).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return fps;
        }
    }

    /** Duration in milliseconds (MediaInfo JSON gives it in seconds with decimals). */
    public Long durationMillis() {
        String d = general().get("Duration");
        if (d == null) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(d) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long overallBitRate() {
        return longValue(general().get("OverallBitRate"));
    }

    public Long videoBitRate() {
        return longValue(video().get("BitRate"));
    }

    public Long audioBitRate() {
        return longValue(audio().get("BitRate"));
    }

    public Integer samplingRate() {
        return integer(audio().get("SamplingRate"));
    }

    public String mediaTitle() {
        return general().get("Title") != null ? general().get("Title") : general().get("Movie");
    }

    public List<String> languages(String kind) {
        List<String> out = new ArrayList<>();
        for (Map<String, String> s : streams(kind)) {
            String l = s.get("Language");
            if (l != null && !out.contains(l)) {
                out.add(l);
            }
        }
        return out;
    }

    static Integer integer(String s) {
        if (s == null) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(s.replaceAll("[^0-9.]", "")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Long longValue(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Math.round(Double.parseDouble(s.replaceAll("[^0-9.]", "")));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
