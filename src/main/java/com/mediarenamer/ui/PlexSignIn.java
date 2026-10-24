package com.mediarenamer.ui;

import static com.mediarenamer.app.I18n.tr;

import com.mediarenamer.app.Logging;
import com.mediarenamer.web.PlexClient;

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;

/**
 * "Sign in" button of the Plex section: gets the token with the plex.tv PIN sign-in, so that the user does not have to
 * look for it. The browser opens plex.tv (the password is only typed there); when the user allows MediaRenamer, the
 * token of their server is written in the token field and, if the URL is empty, the first address of the server that
 * answers from this PC. Nothing is saved until Preferences is saved. While waiting the button cancels the sign-in,
 * and closing the window cancels it too. Every request runs outside the Swing thread.
 */
final class PlexSignIn {

    private final Window owner;
    private final JTextComponent url;
    private final JTextComponent token;
    private final JLabel status;
    private final JButton button = new JButton(tr("prefs.plex.login"));
    private SwingWorker<?, ?> running;

    private PlexSignIn(Window owner, JTextComponent url, JTextComponent token, JLabel status) {
        this.owner = owner;
        this.url = url;
        this.token = token;
        this.status = status;
        button.setToolTipText(tr("prefs.plex.login.tooltip"));
        button.addActionListener(e -> {
            if (running != null) {
                running.cancel(true);
            } else {
                signIn();
            }
        });
        if (owner != null) {
            owner.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    if (running != null) {
                        running.cancel(true);
                    }
                }
            });
        }
    }

    /** The button, linked to the URL and token fields of the page and to its status line. */
    static JButton button(Window owner, JTextComponent url, JTextComponent token, JLabel status) {
        return new PlexSignIn(owner, url, token, status).button;
    }

    private void busy(SwingWorker<?, ?> worker) {
        running = worker;
        button.setText(tr(worker == null ? "prefs.plex.login" : "common.cancel"));
    }

    /** Steps 1 and 2 (PIN, browser, wait) and 3 (servers of the user). */
    private void signIn() {
        status.setText(tr("prefs.plex.login.starting"));
        SwingWorker<Result, String> worker = new SwingWorker<>() {
            @Override
            protected Result doInBackground() throws Exception {
                PlexClient.Pin pin = PlexClient.requestPin();
                publish(pin.authUrl());
                String account = PlexClient.waitForToken(pin, this::isCancelled, PlexClient.POLL_INTERVAL);
                if (account == null) {
                    return null;
                }
                publish((String) null);
                List<PlexClient.Server> servers;
                try {
                    servers = PlexClient.servers(account);
                } catch (Exception e) {
                    // signed in anyway: the account token also works with the user's own servers
                    Logging.warn("Plex: " + e.getMessage());
                    servers = List.of();
                }
                return new Result(account, servers);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String authUrl : chunks) {
                    if (authUrl == null) {
                        status.setText(tr("prefs.plex.login.searching"));
                    } else {
                        status.setText(tr("prefs.plex.login.waiting"));
                        openBrowser(authUrl);
                    }
                }
            }

            @Override
            protected void done() {
                busy(null);
                if (isCancelled()) {
                    status.setText(tr("prefs.plex.login.cancelled"));
                    return;
                }
                try {
                    Result r = get();
                    if (r == null) {
                        status.setText(tr("prefs.plex.login.cancelled"));
                    } else {
                        signedIn(r);
                    }
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    Logging.warn("Plex: " + cause.getMessage());
                    status.setText(ApiService.errorText(cause));
                }
            }
        };
        busy(worker);
        worker.execute();
    }

    private void openBrowser(String authUrl) {
        try {
            java.awt.Desktop.getDesktop().browse(URI.create(authUrl));
        } catch (Exception e) {
            // no browser available: the user opens the address by hand
            JTextField field = new JTextField(authUrl, 40);
            field.setEditable(false);
            JOptionPane.showMessageDialog(owner, new Object[] { tr("prefs.plex.login.browser"), field }, "Plex", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Fills the token of the chosen server and, if the URL is empty, looks for an address that answers. */
    private void signedIn(Result r) {
        PlexClient.Server server = choose(r.servers());
        if (server == null) {
            token.setText(r.account());
            status.setText(tr("prefs.plex.login.noServer"));
            return;
        }
        token.setText(server.token());
        if (!url.getText().isBlank() || server.urls().isEmpty()) {
            status.setText(tr(url.getText().isBlank() ? "prefs.plex.login.unreachable" : "prefs.plex.login.done", server.name()));
            return;
        }
        status.setText(tr("prefs.plex.login.probing", server.name()));
        SwingWorker<String, Void> probe = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return PlexClient.firstReachable(server.urls());
            }

            @Override
            protected void done() {
                busy(null);
                if (isCancelled()) {
                    status.setText(tr("prefs.plex.login.cancelled"));
                    return;
                }
                String found = null;
                try {
                    found = get();
                } catch (Exception ex) {
                    Logging.debug("Plex probe: " + ex);
                }
                if (url.getText().isBlank()) {
                    // nothing answered: the first address is still a good start to edit by hand
                    url.setText(found != null ? found : server.urls().get(0));
                }
                status.setText(tr(found != null ? "prefs.plex.login.done" : "prefs.plex.login.unreachable", server.name()));
            }
        };
        busy(probe);
        probe.execute();
    }

    /**
     * The server to use: the one whose address is already typed, the only one, or the one the user picks (null if the
     * user has none of their own or closes the question).
     */
    private PlexClient.Server choose(List<PlexClient.Server> servers) {
        if (servers.isEmpty()) {
            return null;
        }
        String typed = PlexClient.normalizeUrl(url.getText());
        if (typed != null) {
            for (PlexClient.Server s : servers) {
                if (s.urls().stream().anyMatch(u -> sameHost(u, typed))) {
                    return s;
                }
            }
        }
        if (servers.size() == 1) {
            return servers.get(0);
        }
        Object picked = JOptionPane.showInputDialog(owner, tr("prefs.plex.login.choose"), "Plex", JOptionPane.QUESTION_MESSAGE, null,
                servers.toArray(), servers.get(0));
        return (PlexClient.Server) picked;
    }

    static boolean sameHost(String a, String b) {
        try {
            String ha = URI.create(a).getHost();
            return ha != null && ha.equalsIgnoreCase(URI.create(b).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Account token and the user's own servers. */
    private record Result(String account, List<PlexClient.Server> servers) {
    }
}
