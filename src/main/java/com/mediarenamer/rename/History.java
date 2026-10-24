package com.mediarenamer.rename;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.AppFolders;
import com.mediarenamer.app.Logging;
import com.mediarenamer.web.WebRequest;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Rename history (%APPDATA%/MediaRenamer/history.xml):
 *
 * <pre>
 * &lt;history&gt;
 *   &lt;sequence date="2025-02-19T20:47:49.133+01:00"&gt;
 *     &lt;rename dir="C:\Descargas" from="Mufasa.2024.mkv" to="Z:\Peliculas\Mufasa (2024).mkv"/&gt;
 *   &lt;/sequence&gt;
 * &lt;/history&gt;
 * </pre>
 *
 * "to" is relative to "dir" if the target is in the same folder, absolute otherwise.
 */
public final class History {

    public record Element(String dir, String from, String to) {
        public File source() {
            return new File(dir, from);
        }

        public File target() {
            File t = new File(to);
            return t.isAbsolute() ? t : new File(dir, to);
        }
    }

    public record Sequence(OffsetDateTime date, List<Element> elements) {
    }

    /** One renamed file with the date of its sequence: a row of the history window and of the CSV export. */
    public record Entry(OffsetDateTime date, Element element) {
    }

    /** Every renamed file, most recent first (within a sequence, in the order they were renamed). */
    public synchronized List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        for (int i = sequences.size() - 1; i >= 0; i--) {
            Sequence s = sequences.get(i);
            for (Element e : s.elements()) {
                list.add(new Entry(s.date(), e));
            }
        }
        return list;
    }

    /** Date format of the history window and the CSV export. */
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Writes the entries as CSV (RFC 4180: comma separator, CRLF, fields with comma, quote or line break quoted)
     * with a header row: date, source path, target path.
     */
    public static void writeCsv(List<Entry> entries, String[] header, Writer w) throws IOException {
        w.write(csvLine(header[0], header[1], header[2]));
        for (Entry e : entries) {
            w.write(csvLine(DISPLAY_DATE.format(e.date()), e.element().source().getPath(), e.element().target().getPath()));
        }
    }

    private static String csvLine(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String f = fields[i] == null ? "" : fields[i];
            if (f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
        }
        return sb.append("\r\n").toString();
    }

    private static History instance;

    public static synchronized History get() {
        if (instance == null) {
            instance = new History(AppFolders.history());
            instance.load();
        }
        return instance;
    }

    private final Path file;
    private final List<Sequence> sequences = new ArrayList<>();

    public History(Path file) {
        this.file = file;
    }

    public synchronized List<Sequence> getSequences() {
        return Collections.unmodifiableList(sequences);
    }

    public synchronized void load() {
        sequences.clear();
        sequences.addAll(read(file));
    }

    static List<Sequence> read(Path p) {
        List<Sequence> list = new ArrayList<>();
        if (!Files.isRegularFile(p)) {
            return list;
        }
        try {
            Document doc = WebRequest.parseXml(Files.readAllBytes(p));
            NodeList seqs = doc.getElementsByTagName("sequence");
            for (int i = 0; i < seqs.getLength(); i++) {
                org.w3c.dom.Element s = (org.w3c.dom.Element) seqs.item(i);
                List<Element> elements = new ArrayList<>();
                NodeList renames = s.getElementsByTagName("rename");
                for (int j = 0; j < renames.getLength(); j++) {
                    org.w3c.dom.Element r = (org.w3c.dom.Element) renames.item(j);
                    elements.add(new Element(r.getAttribute("dir"), r.getAttribute("from"), r.getAttribute("to")));
                }
                OffsetDateTime date;
                try {
                    date = OffsetDateTime.parse(s.getAttribute("date"));
                } catch (Exception e) {
                    date = OffsetDateTime.now();
                }
                list.add(new Sequence(date, elements));
            }
        } catch (IOException e) {
            Logging.warn(tr("log.historyRead", p, e.getMessage()));
        }
        return list;
    }

    /** Adds a sequence (one click on "Rename") and saves the file. */
    public synchronized void add(Map<File, File> renames) {
        if (renames.isEmpty()) {
            return;
        }
        List<Element> elements = new ArrayList<>();
        renames.forEach((from, to) -> {
            File dir = from.getAbsoluteFile().getParentFile();
            File parentOfTarget = to.getAbsoluteFile().getParentFile();
            String toValue = dir.equals(parentOfTarget) ? to.getName() : to.getAbsolutePath();
            elements.add(new Element(dir.getPath(), from.getName(), toValue));
        });
        sequences.add(new Sequence(OffsetDateTime.now(), elements));
        targetIndex = null;
        save();
    }

    private void save() {
        StringBuilder w = new StringBuilder();
        w.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<history>\n");
        DateTimeFormatter f = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        for (Sequence s : sequences) {
            w.append("    <sequence date=\"").append(f.format(s.date())).append("\">\n");
            for (Element e : s.elements()) {
                w.append("        <rename dir=\"").append(xml(e.dir())).append("\" from=\"").append(xml(e.from())).append("\" to=\"")
                        .append(xml(e.to())).append("\"/>\n");
            }
            w.append("    </sequence>\n");
        }
        w.append("</history>\n");
        try {
            // atomic: a crash while saving must not lose the whole history
            com.mediarenamer.util.FileUtilities.writeAtomically(file, w.toString());
        } catch (IOException e) {
            Logging.warn(tr("log.historySave", e.getMessage()));
        }
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Original name of a file following the rename chain backwards
     * in the history. null if it was never renamed.
     */
    public String originalName(File current) {
        Map<String, Element> byTarget = targetIndex();
        String original = null;
        File f = current;
        for (int guard = 0; guard < 100; guard++) {
            Element e = byTarget.get(key(f));
            if (e == null) {
                break;
            }
            original = e.from();
            f = e.source();
        }
        return original;
    }

    private Map<String, Element> targetIndex;

    /** Target -> element index, built once. */
    private synchronized Map<String, Element> targetIndex() {
        if (targetIndex == null) {
            Map<String, Element> index = new LinkedHashMap<>();
            for (Sequence s : sequences) {
                for (Element e : s.elements()) {
                    index.put(key(e.target()), e);
                }
            }
            targetIndex = index;
        }
        return targetIndex;
    }

    private static String key(File f) {
        return f.getAbsolutePath().replace('/', '\\').toLowerCase(java.util.Locale.ROOT);
    }
}
