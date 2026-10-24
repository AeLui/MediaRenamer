package com.mediarenamer.ui;

import com.mediarenamer.app.Settings;
import com.mediarenamer.rename.Presets;
import com.mediarenamer.web.Datasource;
import com.mediarenamer.web.Movie;
import com.mediarenamer.web.SearchResult;
import com.mediarenamer.web.WebServices;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/**
 * Development tool (not a test): opens the UI with sample files and saves PNG screenshots
 * of the main window (light and dark), of the poster search and of the details card.
 * Needs the TMDb key configured and a preset "Películas para Plex" (or pass another name).
 *
 * Usage: java -cp target/classes;target/test-classes;&lt;dependencies&gt; com.mediarenamer.ui.UiSnapshot &lt;output&gt; &lt;samples-folder&gt; [preset]
 */
public final class UiSnapshot {

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
        File media = new File(args[1]);
        String preset = args.length > 2 ? args[2] : "Películas para Plex";
        Files.createDirectories(out);
        // work on a COPY of the user's data (keys and presets), so as not to touch their preferences
        Path real = com.mediarenamer.app.AppFolders.data();
        Path tmp = Files.createTempDirectory("mediarenamer-snapshot");
        // the copy includes the API keys: it is always deleted on exit (normal end, System.exit or error)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(tmp)));
        for (String name : List.of("settings.properties", "presets.json")) {
            if (Files.exists(real.resolve(name))) {
                Files.copy(real.resolve(name), tmp.resolve(name));
            }
        }
        System.setProperty("mediarenamer.data", tmp.toString());
        com.mediarenamer.app.TrustStores.install();
        Locale locale = Settings.get().getLocale();

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
        snap(frame.get().getRootPane(), out.resolve("screenshot-empty.png"));
        SwingUtilities.invokeAndWait(() -> panel.addFiles(List.of(media)));
        SwingUtilities.invokeAndWait(() -> panel.applyPreset(Presets.get().find(preset).orElseThrow()));
        Thread.sleep(9000);
        snap(frame.get().getRootPane(), out.resolve("screenshot-main.png"));

        SwingUtilities.invokeAndWait(() -> {
            Theme.toggle();
            frame.get().repaint();
        });
        Thread.sleep(800);
        snap(frame.get().getRootPane(), out.resolve("screenshot-main-dark.png"));
        SwingUtilities.invokeAndWait(Theme::toggle);

        // poster search
        List<Movie> results = WebServices.TheMovieDB.searchMovie("Alien", 0, locale);
        for (SearchResult r : results) {
            PosterCache.get(r.getPoster(), 60, 90, null);
        }
        Thread.sleep(5000);
        List<Datasource> sources = new ArrayList<>(WebServices.getMovieLookupServices());
        sources.addAll(WebServices.getEpisodeListProviders());
        AtomicReference<SearchDialog> dialog = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            SearchDialog d = new SearchDialog(frame.get(), "Choose the match for \"Alien\"", "Alien", List.of("movie.mkv"), results, sources,
                    WebServices.TheMovieDB, locale, null);
            d.setModal(false);
            d.setVisible(true);
            dialog.set(d);
        });
        Thread.sleep(1500);
        snap(dialog.get().getRootPane(), out.resolve("screenshot-search.png"));
        SwingUtilities.invokeAndWait(() -> dialog.get().dispose());

        // details card
        MediaDetails details = MediaDetails.load(results.get(0), WebServices.TheMovieDB, locale);
        PosterCache.get(details.poster(), 154, 231, null);
        Thread.sleep(3000);
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

        // series: search and details card
        List<SearchResult> shows = WebServices.TheMovieDB_TV.search("La casa de papel", locale);
        for (SearchResult r : shows) {
            PosterCache.get(r.getPoster(), 60, 90, null);
        }
        MediaDetails showDetails = MediaDetails.load(shows.get(0), WebServices.TheMovieDB_TV, locale);
        PosterCache.get(showDetails.poster(), 154, 231, null);
        Thread.sleep(5000);
        SwingUtilities.invokeAndWait(() -> {
            SearchDialog d = new SearchDialog(frame.get(), "Choose the match for \"La casa de papel\"", "La casa de papel",
                    List.of("La.Casa.de.Papel.1x03.HDTV.Castellano.avi"), shows, sources, WebServices.TheMovieDB_TV, locale, null);
            d.setModal(false);
            d.setVisible(true);
            dialog.set(d);
        });
        Thread.sleep(1500);
        snap(dialog.get().getRootPane(), out.resolve("screenshot-search-series.png"));
        SwingUtilities.invokeAndWait(() -> {
            dialog.get().dispose();
            window.get().setContentPane(new SearchDialog.DetailCard(showDetails, false, true, null));
            window.get().pack();
        });
        Thread.sleep(800);
        snap((JComponent) window.get().getContentPane(), out.resolve("screenshot-card-series.png"));

        // modal progress layer (over the main window)
        SwingUtilities.invokeAndWait(() -> {
            ProgressOverlay o = new ProgressOverlay(frame.get());
            o.start("Mover / renombrar…", true);
            o.progress(3, 7, "4 of 7  ·  Interstellar.2014.2160p.UHD.mkv");
        });
        Thread.sleep(800);
        snap(frame.get().getRootPane(), out.resolve("screenshot-progress.png"));
        SwingUtilities.invokeAndWait(() -> ((ProgressOverlay) frame.get().getGlassPane()).finish());

        // reorder: move the first match to the third position (same as dragging)
        SwingUtilities.invokeAndWait(() -> panel.moveMatch(0, 3));
        Thread.sleep(4000);
        snap(frame.get().getRootPane(), out.resolve("screenshot-reordered.png"));

        // name pre-check with typical cases
        List<com.mediarenamer.rename.RenameEngine.Entry> bad = List.of(
                new com.mediarenamer.rename.RenameEngine.Entry(new File("a.mkv"), null, new File("Z:/Películas/Mission: Impossible? (1996).mkv"), null),
                new com.mediarenamer.rename.RenameEngine.Entry(new File("b.mkv"), null, new File("Z:/Películas/Amélie ✨ (2001).mkv"), null),
                new com.mediarenamer.rename.RenameEngine.Entry(new File("c.mkv"), null, new File("Z:/Películas/CON.mkv"), null),
                new com.mediarenamer.rename.RenameEngine.Entry(new File("d.mkv"), null, new File("Z:/Series/What If... /What If - 1x01.mkv"), null));
        List<com.mediarenamer.rename.NameValidator.Issue> issues = com.mediarenamer.rename.NameValidator.validate(bad);
        snapDialog(() -> new ValidateDialog(frame.get(), issues, 1), out.resolve("screenshot-validate.png"));

        // episode chooser with the real list of a series
        List<SearchResult> dark = WebServices.TheMovieDB_TV.search("Dark", locale);
        SearchResult dark2017 = dark.stream().filter(s -> s instanceof com.mediarenamer.web.Series se && Integer.valueOf(2017).equals(se.getYear())).findFirst()
                .orElse(dark.get(0));
        List<com.mediarenamer.web.Episode> eps = WebServices.TheMovieDB_TV.getEpisodeList(dark2017, com.mediarenamer.web.SortOrder.Airdate, locale);
        snapDialog(() -> new EpisodeChooserDialog(frame.get(), "Dark.S01E02.1080p.NF.WEB-DL.mkv", "Dark", eps, eps.get(1)), out.resolve("screenshot-episode.png"));

        // dialogs: preferences, presets and (if two files are given) conflict
        snapDialog(() -> new PreferencesDialog(frame.get()), out.resolve("screenshot-preferences.png"));
        snapDialog(() -> new PresetsDialog(frame.get()), out.resolve("screenshot-presets.png"));
        if (args.length > 4) {
            File source = new File(args[3]);
            File existing = new File(args[4]);
            com.mediarenamer.rename.Quality nq = com.mediarenamer.rename.Quality.of(source);
            com.mediarenamer.rename.Quality oq = com.mediarenamer.rename.Quality.of(existing);
            snapDialog(() -> new ConflictDialog(frame.get(), source, existing, nq, oq, 1), out.resolve("screenshot-conflict.png"));
        }

        System.out.println("Screenshots in " + out.toAbsolutePath());
        System.exit(0);
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
