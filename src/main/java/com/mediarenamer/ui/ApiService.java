package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Settings;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingWorker;

/**
 * API key fields shared by Preferences and the setup wizard: settings key, label, "what is it for" text (info icon
 * tooltip) and the page where the key is obtained ("Get" button).
 */
record ApiService(String settingsKey, String labelKey, String infoKey, boolean secret, String url) {

    /** In the order shown in Preferences and in the wizard. */
    static final List<ApiService> ALL = List.of(
            new ApiService(Settings.TMDB_KEY, "prefs.tmdb", "service.info.tmdb", true, "https://www.themoviedb.org/settings/api"),
            new ApiService(Settings.TVDB_KEY, "prefs.tvdb", "service.info.tvdb", true, "https://thetvdb.com/api-information"),
            new ApiService(Settings.TVDB_PIN, "prefs.tvdbPin", "service.info.tvdbPin", true, null),
            new ApiService(Settings.ACOUSTID_KEY, "prefs.acoustid", "service.info.acoustid", true, "https://acoustid.org/new-application"),
            new ApiService(Settings.ANIDB_CLIENT, "prefs.anidb", "service.info.anidb", false, "https://anidb.net/software/add"),
            new ApiService(Settings.ANIDB_CLIENT_VERSION, "prefs.anidbVersion", "service.info.anidbVersion", false, null));

    /** Info icon whose tooltip explains what the service is used for. */
    static JLabel infoIcon(String infoKey) {
        JLabel l = new JLabel(Icons.info().withSize(16).withColor(Theme.secondaryText()));
        l.setToolTipText("<html><div style='width:300px'>" + tr(infoKey) + "</div></html>");
        return l;
    }

    /** "Get" button that opens the page where the key is requested (the URL is also the tooltip). */
    static JButton getKeyButton(String url) {
        JButton get = new JButton(tr("prefs.get"));
        get.setToolTipText(url);
        get.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            } catch (Exception ex) {
                // no browser available: the URL stays in the tooltip
            }
        });
        return get;
    }

    /**
     * "Test connection" button for a media server: check is called on the Swing thread with the typed values (not
     * saved) and returns the request to run in the background, which gives the server name and version; the result or
     * the error is shown in status. afterDone runs on the Swing thread.
     */
    static JButton serverTestButton(Supplier<Callable<String>> check, JLabel status, Runnable afterDone) {
        JButton test = new JButton(tr("prefs.jellyfin.test"));
        test.addActionListener(e -> {
            test.setEnabled(false);
            status.setText(tr("prefs.jellyfin.connecting"));
            Callable<String> request = check.get();
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    return request.call();
                }

                @Override
                protected void done() {
                    test.setEnabled(true);
                    try {
                        status.setText(tr("prefs.jellyfin.connected", get()));
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        status.setText(errorText(cause));
                    }
                    afterDone.run();
                }
            }.execute();
        });
        return test;
    }

    /** "Error: first line of the message" for a status label. */
    static String errorText(Throwable t) {
        return tr("common.error", firstLine(t.getMessage()));
    }

    private static String firstLine(String s) {
        if (s == null) {
            return tr("prefs.jellyfin.noResponse");
        }
        int nl = s.indexOf('\n');
        return nl < 0 ? s : s.substring(0, nl);
    }
}
