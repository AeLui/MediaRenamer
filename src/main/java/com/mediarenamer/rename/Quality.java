package com.mediarenamer.rename;

import com.mediarenamer.app.I18n;
import com.mediarenamer.media.MediaCharacteristics;
import com.mediarenamer.util.FileUtilities;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Quality and size data of a file (via MediaInfo) to resolve conflicts and show them
 * in "Ask each time".
 *
 * Quality order (from most to least decisive):
 * 1. resolution (height in pixels), 2. HDR (Dolby Vision > HDR10+ > HDR10 > HLG > SDR),
 * 3. video bitrate (or overall), 4. audio channels, 5. size.
 * Without MediaInfo data (or for non-media files) overall bitrate and size are compared.
 */
public record Quality(File file, long size, Integer width, Integer height, String videoFormat, String videoCodec, String hdr, Long videoBitRate,
        Long overallBitRate, String audioCodec, String channels, Long durationMillis, long lastModified) {

    public static Quality of(File f) {
        MediaCharacteristics m = new MediaCharacteristics(f);
        boolean has = !m.isEmpty();
        return new Quality(f, f.length(), has ? m.width() : null, has ? m.height() : null, has ? m.videoFormat() : null, has ? m.videoCodec() : null,
                has ? m.hdr() : null, has ? m.videoBitRate() : null, has ? m.overallBitRate() : null, has ? m.audioCodec() : null,
                has ? m.channels() : null, has ? m.durationMillis() : null, f.lastModified());
    }

    /** Quality order: positive if a is better than b, negative if worse, 0 if equivalent. */
    public static final Comparator<Quality> QUALITY = Comparator
            .comparing((Quality q) -> q.height == null ? 0 : q.height)
            .thenComparingInt(q -> hdrRank(q.hdr))
            .thenComparingLong(q -> q.videoBitRate != null ? q.videoBitRate : q.overallBitRate != null ? q.overallBitRate : 0)
            .thenComparingInt(q -> channelCount(q.channels))
            .thenComparingLong(Quality::size);

    public static int compare(File a, File b) {
        return QUALITY.compare(of(a), of(b));
    }

    static int hdrRank(String hdr) {
        if (hdr == null) {
            return 0;
        }
        return switch (hdr) {
            case "DV" -> 4;
            case "HDR10+" -> 3;
            case "HDR10" -> 2;
            case "HLG" -> 1;
            default -> 0;
        };
    }

    static int channelCount(String channels) {
        if (channels == null) {
            return 0;
        }
        try {
            String[] p = channels.replace("ch", "").split("\\.");
            return Integer.parseInt(p[0]) + (p.length > 1 ? Integer.parseInt(p[1]) : 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Rows for the conflict dialog (key -> value). The keys are fixed (size, resolution, video,
     * bitrate, audio, duration, modified); {@link #label(String)} gives their name in the interface language.
     */
    public Map<String, String> describe() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("size", FileUtilities.formatSize(size));
        m.put("resolution", width == null ? "—" : width + "×" + height + (videoFormat == null ? "" : "  (" + videoFormat + ")"));
        m.put("video", videoCodec == null ? "—" : videoCodec + (hdr == null ? "" : "  · " + hdr));
        m.put("bitrate", bitrate(videoBitRate != null ? videoBitRate : overallBitRate));
        m.put("audio", audioCodec == null ? "—" : audioCodec + (channels == null ? "" : " " + channels));
        m.put("duration", durationMillis == null ? "—" : duration(durationMillis));
        m.put("modified", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault())));
        return m;
    }

    /** Name of a {@link #describe()} row: "Size", "Resolution"... */
    public static String label(String key) {
        return I18n.tr("quality." + key);
    }

    private static String bitrate(Long bps) {
        if (bps == null) {
            return "—";
        }
        return bps >= 1_000_000 ? String.format("%.1f Mbps", bps / 1e6) : Math.round(bps / 1000.0) + " kbps";
    }

    private static String duration(long ms) {
        Duration d = Duration.ofMillis(ms);
        return d.toHours() > 0 ? String.format("%d h %02d min", d.toHours(), d.toMinutesPart()) : String.format("%d min %02d s", d.toMinutes(), d.toSecondsPart());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Quality q && Objects.equals(q.file, file);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(file);
    }
}
