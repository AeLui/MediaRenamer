package com.mediarenamer;

import com.mediarenamer.app.SingleInstance;
import com.mediarenamer.app.TrustStores;
import com.mediarenamer.cli.Cli;
import com.mediarenamer.ui.MainFrame;
import com.mediarenamer.web.WebRequest;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * Entry point: without arguments opens the UI; with only paths (files dropped on the exe or a shortcut, "Send to")
 * opens the UI with them in the list; {@code -open [--profile <id>] <paths...>} (Explorer context menu) also applies a
 * profile to them; with any other option runs the command line (see docs/CLI.md).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SingleInstance.Request open = GraphicsEnvironment.isHeadless() ? null : openRequest(args);
        // a window is already open (or being opened by another start): it gets the files, no second window
        if (open != null && SingleInstance.handOff(open, received -> SwingUtilities.invokeLater(() -> MainFrame.receive(received)))) {
            System.exit(0);
        }
        // HTTPS: the JDK roots plus, on Windows, the system store (antivirus/corporate proxies that inspect HTTPS)
        TrustStores.install();
        // expired responses are never read again: remove them in the background so the cache does not grow forever
        Thread purge = new Thread(WebRequest::purgeCache, "cache-purge");
        purge.setDaemon(true);
        purge.setPriority(Thread.MIN_PRIORITY);
        purge.start();
        if (open == null) {
            System.exit(Cli.run(args));
        }
        SwingUtilities.invokeLater(() -> MainFrame.open(open));
    }

    /**
     * What to open in the UI: no files without arguments, the arguments when all of them are paths, and
     * {@code -open [--profile <id>] <paths...>}; null when there is any other option (every command starts with one,
     * e.g. {@code -rename}), which goes to the command line.
     */
    static SingleInstance.Request openRequest(String[] args) {
        List<File> files = new ArrayList<>();
        boolean open = false;
        String profile = null;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equalsIgnoreCase("-open")) {
                open = true;
            } else if (a.equalsIgnoreCase("--profile") && i + 1 < args.length) {
                profile = args[++i];
            } else if (a.startsWith("-")) {
                return null;
            } else {
                files.add(new File(a));
            }
        }
        // a profile without -open belongs to a command line that forgot its command: the CLI explains it
        return profile != null && !open ? null : new SingleInstance.Request(files, profile);
    }
}
