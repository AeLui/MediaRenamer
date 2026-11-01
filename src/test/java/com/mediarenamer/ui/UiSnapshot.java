package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Settings;
import com.mediarenamer.rename.History;
import com.mediarenamer.rename.NameValidator;
import com.mediarenamer.rename.Preset;
import com.mediarenamer.rename.Presets;
import com.mediarenamer.rename.Quality;
import com.mediarenamer.rename.RenameEngine;
import com.mediarenamer.similarity.Match;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.Episode;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.SimpleDate;
import com.mediarenamer.web.WebServices;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Development tool (not a test): opens the UI with sample data and saves the PNG screenshots of the documentation
 * (main window light and dark, progress, search, details card, episode chooser, name check, conflict, format editor, history).
 * <p>
 * Every title, name, synopsis and poster is invented here (posters are drawn in code), so the screenshots carry no
 * third-party content. It works offline and without API keys, on an empty temporary data folder that is deleted at
 * the end: the user's preferences, keys and history are never read.
 *
 * Usage: java -cp target/MediaRenamer.jar;target/test-classes;&lt;dependencies&gt; com.mediarenamer.ui.UiSnapshot &lt;output&gt;
 */
public final class UiSnapshot {

    /** A fictional movie: source file name, size, title, year and poster colors. */
    private record Sample(String file, long size, String title, int year, int id, Color top, Color bottom) {
    }

    /** A sample file that does not exist on disk but reports a size, so the source list shows clean example paths. */
    private static final class SampleFile extends File {
        private final long size;

        SampleFile(Path path, long size) {
            super(path.toString());
            this.size = size;
        }

        @Override
        public boolean isFile() {
            return true;
        }

        @Override
        public long length() {
            return size;
        }

        @Override
        public boolean exists() {
            return true;
        }
    }

    private static final List<Sample> MOVIES = List.of(
            new Sample("Glass.Harbor.2024.2160p.WEB-DL.HDR.mkv", 18_734_221_312L, "Glass Harbor", 2024, 900101, new Color(0x1E3A5F), new Color(0x0B132B)),
            new Sample("the_quiet_orchard_1987_BDRip_h264_AC3_Eng+Sub.mp4", 2_147_483_648L, "The Quiet Orchard", 1987, 900102, new Color(0xC97B3A), new Color(0x3B1F0E)),
            new Sample("Copper.Tides.2019.1080p.BluRay.x264.mkv", 9_876_543_210L, "Copper Tides", 2019, 900103, new Color(0xB5651D), new Color(0x2A1A3A)),
            new Sample("La.Ciudad.Sin.Relojes.2008.BDRip.mkv", 1_468_006_400L, "The Clockless City", 2008, 900104, new Color(0x6A4C93), new Color(0x1B1B3A)),
            new Sample("Kestrel.Point.Signal.2016.2160p.UHD.mkv", 21_474_836_480L, "Kestrel Point Signal", 2016, 900105, new Color(0x2B7A78), new Color(0x0F2027)),
            new Sample("Kestrel.Point.Signal.2016.2160p.UHD.spa.srt", 86_528L, "Kestrel Point Signal", 2016, 900105, new Color(0x2B7A78), new Color(0x0F2027)),
            new Sample("Lantern Moth (2022) WEB-DL 4K SDR.mkv", 14_495_514_624L, "Lantern Moth", 2022, 900106, new Color(0x8E9AAF), new Color(0x22223B)));

    private static final Path DOWNLOADS = Path.of("D:\\Downloads");

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            // the open window would keep the process alive: exit so that the cleanup runs
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        // an empty data folder: no keys, presets or history of the user are read or written
        Path tmp = Files.createTempDirectory("mediarenamer-snapshot");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(tmp)));
        Files.writeString(tmp.resolve("settings.properties"), Settings.UI_LANGUAGE + "=en\n" + Settings.LANGUAGE + "=en-US\n");
        System.setProperty("mediarenamer.data", tmp.toString());
        Locale locale = Locale.US;

        SwingUtilities.invokeAndWait(() -> {
            Settings.get().put("ui.dark", "false");
            Theme.install();
        });
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainFrame f = new MainFrame();
            f.setSize(1280, 780);
            f.setVisible(true);
            frame.set(f);
        });
        RenamePanel panel = find(frame.get(), RenamePanel.class);

        // main window: sample rows matched with the fictional movies, as a profile would do
        Preset preset = new Preset("snapshot-movies", "Movies for Jellyfin", "{n.colon(' - ')} ({y}){subt}", "TheMovieDB", "Airdate",
                "Opportunistic", "en-US", "Z:\\Movies");
        Presets.get().put(preset);
        List<Match<Object, File>> matches = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                showMatches(panel, preset, matches);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(3000);
        snap(frame.get().getRootPane(), out.resolve("screenshot-main.png"));

        SwingUtilities.invokeAndWait(() -> {
            Theme.toggle();
            frame.get().repaint();
        });
        Thread.sleep(800);
        snap(frame.get().getRootPane(), out.resolve("screenshot-main-dark.png"));
        SwingUtilities.invokeAndWait(Theme::toggle);
        Thread.sleep(500);

        // modal progress layer (over the main window)
        SwingUtilities.invokeAndWait(() -> {
            ProgressOverlay o = new ProgressOverlay(frame.get());
            o.start(tr("action.MOVE") + "…", true);
            o.progress(3, 7, tr("rename.rename.progress", 4, 7, MOVIES.get(3).file()));
        });
        Thread.sleep(800);
        snap(frame.get().getRootPane(), out.resolve("screenshot-progress.png"));
        SwingUtilities.invokeAndWait(() -> ((ProgressOverlay) frame.get().getGlassPane()).finish());

        // poster search
        List<Movie> results = List.of(
                movie("Glass Harbor", 2024, 900101, "A harbor pilot returns to the fogbound town where she grew up and finds the lighthouse "
                        + "keeping a log of ships that have not arrived yet."),
                movie("Glass Harbor", 1962, 900201, "A black-and-white mystery about a glassblower who signs every bottle he sends out to sea, "
                        + "and the letter that one day comes back."),
                movie("Return to Glass Harbor", 1971, 900202, "Ten years later the town holds its regatta again, and an old rivalry "
                        + "between two boat builders flares up on the last lap."),
                movie("Glass Harbor: Low Tide", 2027, 900203, "When the sea withdraws further than ever before, the townsfolk walk out "
                        + "onto the seabed to see what it has been keeping."));
        List<Color[]> colors = List.of(new Color[] { new Color(0x1E3A5F), new Color(0x0B132B) },
                new Color[] { new Color(0x5C5C5C), new Color(0x161616) },
                new Color[] { new Color(0xD1495B), new Color(0x2E1F27) },
                new Color[] { new Color(0x3D9970), new Color(0x0B2E24) });
        for (int i = 0; i < results.size(); i++) {
            Movie m = results.get(i);
            putPoster(m.getPoster(), poster(m.getName(), m.getYear(), colors.get(i)[0], colors.get(i)[1], m.getId()));
        }
        List<Datasource> sources = new ArrayList<>(WebServices.getMovieLookupServices());
        sources.addAll(WebServices.getEpisodeListProviders());
        AtomicReference<SearchDialog> dialog = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            SearchDialog d = new SearchDialog(frame.get(), tr("search.chooseFor", "Glass Harbor"), "Glass Harbor",
                    List.of("Glass.Harbor.2024.2160p.WEB-DL.HDR.mkv"), results, sources, WebServices.TheMovieDB, locale, null);
            d.setModal(false);
            d.setVisible(true);
            dialog.set(d);
        });
        Thread.sleep(1500);
        snap(dialog.get().getRootPane(), out.resolve("screenshot-search.png"));
        SwingUtilities.invokeAndWait(() -> dialog.get().dispose());

        // details card
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put(tr("details.director"), "Ilse Varga");
        facts.put(tr("details.genre"), "Mystery, Drama");
        facts.put(tr("details.runtime"), "1 h 48 min");
        facts.put(tr("details.rating"), "★ 7.6 (1.2k votes)");
        facts.put(tr("details.certification"), "PG-13");
        facts.put(tr("details.cast"), "Noor Hallam, Tomas Rieger, Ada Okafor, Lucien Marchetti");
        facts.put(tr("details.country"), "IE, NO");
        facts.put(tr("details.released"), "14 Mar 2024");
        facts.put(tr("details.source"), "TheMovieDB #900101");
        MediaDetails details = new MediaDetails("Glass Harbor", null, "2024", results.get(0).getPoster(), facts,
                "A harbor pilot returns to the fogbound town where she grew up, after her father's boat is found drifting "
                        + "with nobody on board. In the old lighthouse she discovers a logbook that records ships days before "
                        + "they arrive, and the next entry is written in her own handwriting.");
        AtomicReference<JWindow> window = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JWindow w = new JWindow(frame.get());
            w.setContentPane(new SearchDialog.DetailCard(details, false, false, null));
            w.pack();
            w.setLocation(-2000, -2000);
            w.setVisible(true);
            window.set(w);
        });
        Thread.sleep(800);
        snap((JComponent) window.get().getContentPane(), out.resolve("screenshot-card.png"));
        SwingUtilities.invokeAndWait(() -> window.get().dispose());

        // name pre-check with typical cases
        List<RenameEngine.Entry> bad = List.of(
                new RenameEngine.Entry(new File("a.mkv"), null, new File("Z:/Movies/Codename: Heron? (1996).mkv"), null),
                new RenameEngine.Entry(new File("b.mkv"), null, new File("Z:/Movies/Midnight Bakery ✨ (2001).mkv"), null),
                new RenameEngine.Entry(new File("c.mkv"), null, new File("Z:/Movies/CON.mkv"), null),
                new RenameEngine.Entry(new File("d.mkv"), null, new File("Z:/Series/Maybe Tomorrow... /Maybe Tomorrow - 1x01.mkv"), null));
        List<NameValidator.Issue> issues = NameValidator.validate(bad);
        snapDialog(() -> new ValidateDialog(frame.get(), issues, 1), out.resolve("screenshot-validate.png"));

        // episode chooser with a fictional series
        List<Episode> eps = episodes();
        snapDialog(() -> new EpisodeChooserDialog(frame.get(), "Lanterns.of.Vell.S01E02.1080p.WEB-DL.mkv", "Lanterns of Vell", eps, eps.get(1)),
                out.resolve("screenshot-episode.png"));

        // conflict between two sample files, with the quality data MediaInfo would read
        File existing = new SampleFile(Path.of("Z:\\Movies\\Kestrel Point Signal (2016)\\Kestrel Point Signal (2016).mkv"), 4_831_838_208L);
        File source = new SampleFile(DOWNLOADS.resolve("Kestrel.Point.Signal.2016.2160p.UHD.mkv"), 21_474_836_480L);
        long modified = java.time.LocalDateTime.of(2026, 10, 30, 21, 14).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        Quality oq = new Quality(existing, existing.length(), 1920, 800, "AVC", "H.264", null, 4_200_000L, 4_850_000L, "AC-3", "5.1",
                7_968_000L, modified - 86_400_000L * 400);
        Quality nq = new Quality(source, source.length(), 3840, 1600, "HEVC", "H.265", "HDR10", 19_800_000L, 21_400_000L, "E-AC-3", "7.1",
                7_968_000L, modified);
        snapDialog(() -> new ConflictDialog(frame.get(), source, existing, nq, oq, 1), out.resolve("screenshot-conflict.png"));

        // format editor opened from a profile, previewing its invented sample
        snapDialog(() -> FormatDialog.forProfile(frame.get(), preset.getMode(), "{n.colon(' - ')} ({y})/{n.colon(' - ')} ({y}){subt}"),
                out.resolve("screenshot-format.png"));

        // history with fictional renames: the sample movies and some episodes of the sample series
        Map<File, File> movies = new LinkedHashMap<>();
        for (Sample s : MOVIES) {
            String name = s.title() + " (" + s.year() + ")";
            String ext = s.file().endsWith(".srt") ? ".spa.srt" : s.file().substring(s.file().lastIndexOf('.'));
            movies.put(DOWNLOADS.resolve(s.file()).toFile(), Path.of("Z:\\Movies", name, name + ext).toFile());
        }
        History.get().add(movies);
        Map<File, File> shows = new LinkedHashMap<>();
        for (Episode e : eps.subList(0, 6)) {
            String code = String.format("S%02dE%02d", e.getSeason(), e.getEpisode());
            shows.put(DOWNLOADS.resolve("Lanterns.of.Vell." + code + ".1080p.WEB-DL.mkv").toFile(),
                    Path.of("Z:\\Series\\Lanterns of Vell", String.format("Season %02d", e.getSeason()),
                            "Lanterns of Vell - " + code + " - " + e.getTitle() + ".mkv").toFile());
        }
        History.get().add(shows);
        snapDialog(() -> new HistoryDialog(frame.get()), out.resolve("screenshot-history.png"));

        System.out.println("Screenshots in " + out.toAbsolutePath());
        System.exit(0);
    }

    /** Fills the main window with the sample rows and their matches, as applying the profile would (without network). */
    private static void showMatches(RenamePanel panel, Preset preset, List<Match<Object, File>> matches) throws ReflectiveOperationException {
        preset.getMode().setFormat(preset.format());
        Settings.get().put(Settings.MATCH_MODE, "Opportunistic");
        Settings.get().put(Settings.EPISODE_ORDER, "Airdate");
        Class<?> rowClass = Class.forName(RenamePanel.class.getName() + "$Row");
        Constructor<?> newRow = rowClass.getDeclaredConstructor(File.class);
        newRow.setAccessible(true);
        Field rowsField = RenamePanel.class.getDeclaredField("rows");
        rowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        DefaultListModel<Object> rows = (DefaultListModel<Object>) rowsField.get(panel);
        for (Sample s : MOVIES) {
            File f = new SampleFile(DOWNLOADS.resolve(s.file()), s.size());
            rows.addElement(newRow.newInstance(f));
            matches.add(new Match<>(new Movie(s.title(), List.of(), s.year(), 0, s.id(), Locale.US), f));
        }
        Field strict = RenamePanel.class.getDeclaredField("strict");
        strict.setAccessible(true);
        strict.set(panel, false);
        Field last = RenamePanel.class.getDeclaredField("lastDatasource");
        last.setAccessible(true);
        last.set(panel, WebServices.TheMovieDB);
        Method active = RenamePanel.class.getDeclaredMethod("setActivePreset", Preset.class);
        active.setAccessible(true);
        active.invoke(panel, preset);
        Method label = RenamePanel.class.getDeclaredMethod("updateModeLabel");
        label.setAccessible(true);
        label.invoke(panel);
        Method apply = RenamePanel.class.getDeclaredMethod("apply", List.class, boolean.class);
        apply.setAccessible(true);
        apply.invoke(panel, matches, true);
    }

    private static Movie movie(String title, int year, int id, String overview) {
        return new Movie(title, List.of(), year, 0, id, Locale.US).withDetails("snapshot:poster/" + id, overview, null);
    }

    private static List<Episode> episodes() {
        String[][] seasons = {
                { "The Ferryman's Toll", "Embers", "A Map Without North", "The Bellfounder", "Low Water", "Salt and Iron", "The Night Market",
                        "Hollow Crowns", "What the River Keeps", "Lanterns Out" },
                { "First Light", "The Glass Orchard" } };
        List<Episode> list = new ArrayList<>();
        for (int s = 0; s < seasons.length; s++) {
            for (int e = 0; e < seasons[s].length; e++) {
                java.time.LocalDate day = (s == 0 ? java.time.LocalDate.of(2021, 9, 3) : java.time.LocalDate.of(2023, 4, 7)).plusWeeks(e);
                SimpleDate date = new SimpleDate(day.getYear(), day.getMonthValue(), day.getDayOfMonth());
                list.add(new Episode("Lanterns of Vell", s + 1, e + 1, seasons[s][e], null, null, date, 990000 + s * 100 + e, null));
            }
        }
        return list;
    }

    /** Draws an invented poster: gradient sky, a moon, layered hills or waves and the title. */
    private static BufferedImage poster(String title, int year, Color top, Color bottom, long seed) {
        int w = 300;
        int h = 450;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
        g.fillRect(0, 0, w, h);
        Random r = new Random(seed);
        g.setColor(new Color(255, 244, 214, 230));
        int moon = 70 + r.nextInt(40);
        g.fill(new Ellipse2D.Double(40 + r.nextInt(w - moon - 80), 50 + r.nextInt(60), moon, moon));
        for (int layer = 0; layer < 3; layer++) {
            Path2D.Double hill = new Path2D.Double();
            double base = h * (0.55 + layer * 0.12);
            hill.moveTo(0, h);
            hill.lineTo(0, base);
            for (int x = 0; x <= w; x += 30) {
                hill.lineTo(x, base - 10 - r.nextInt(35));
            }
            hill.lineTo(w, h);
            hill.closePath();
            float k = 0.25f + layer * 0.22f;
            g.setColor(new Color((int) (bottom.getRed() * (1 - k)), (int) (bottom.getGreen() * (1 - k)), (int) (bottom.getBlue() * (1 - k))));
            g.fill(hill);
        }
        g.setColor(new Color(255, 255, 255, 60));
        g.setStroke(new BasicStroke(1.5f));
        g.drawRect(12, 12, w - 25, h - 25);
        g.setColor(Color.WHITE);
        Font font = new Font(Font.SERIF, Font.BOLD, 34);
        g.setFont(font);
        List<String> lines = wrap(title.toUpperCase(Locale.ROOT), g.getFontMetrics(), w - 50);
        FontMetrics fm = g.getFontMetrics();
        int y = h - 70 - (lines.size() - 1) * fm.getHeight();
        for (String line : lines) {
            g.drawString(line, (w - fm.stringWidth(line)) / 2, y);
            y += fm.getHeight();
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        String ys = String.valueOf(year);
        g.setColor(new Color(255, 255, 255, 190));
        g.drawString(ys, (w - g.getFontMetrics().stringWidth(ys)) / 2, h - 36);
        g.dispose();
        return img;
    }

    private static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && fm.stringWidth(next) > width) {
                lines.add(line);
                line = word;
            } else {
                line = next;
            }
        }
        lines.add(line);
        return lines;
    }

    /** Puts an image in the poster cache as if it had been downloaded. */
    @SuppressWarnings("unchecked")
    private static void putPoster(String url, BufferedImage img) throws ReflectiveOperationException {
        Field originals = PosterCache.class.getDeclaredField("ORIGINALS");
        originals.setAccessible(true);
        ((Map<String, BufferedImage>) originals.get(null)).put(url, img);
    }

    /** Shows a dialog without blocking (non-modal), captures it and closes it. */
    private static void snapDialog(java.util.function.Supplier<javax.swing.JDialog> factory, Path file) throws Exception {
        AtomicReference<javax.swing.JDialog> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            javax.swing.JDialog d = factory.get();
            d.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
            d.setVisible(true);
            ref.set(d);
        });
        Thread.sleep(1000);
        snap(ref.get().getRootPane(), file);
        SwingUtilities.invokeAndWait(() -> ref.get().dispose());
    }

    private static void deleteRecursively(Path dir) {
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (java.io.IOException e) {
            System.err.println("Could not delete " + dir + ": " + e.getMessage());
        }
    }

    private static void snap(JComponent c, Path file) throws Exception {
        AtomicReference<BufferedImage> img = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = im.createGraphics();
            c.printAll(g);
            g.dispose();
            img.set(im);
        });
        ImageIO.write(img.get(), "png", file.toFile());
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return (T) c;
            }
            if (c instanceof Container ct) {
                T found = find(ct, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
