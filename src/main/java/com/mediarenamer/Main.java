package com.mediarenamer;

import com.mediarenamer.app.TrustStores;
import com.mediarenamer.cli.Cli;
import com.mediarenamer.ui.MainFrame;
import com.mediarenamer.web.WebRequest;

import java.awt.GraphicsEnvironment;

import javax.swing.SwingUtilities;

/** Entry point: without arguments opens the UI; with arguments runs the command line (see docs/CLI.md). */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // HTTPS: the JDK roots plus, on Windows, the system store (antivirus/corporate proxies that inspect HTTPS)
        TrustStores.install();
        // expired responses are never read again: remove them in the background so the cache does not grow forever
        Thread purge = new Thread(WebRequest::purgeCache, "cache-purge");
        purge.setDaemon(true);
        purge.setPriority(Thread.MIN_PRIORITY);
        purge.start();
        if (args.length > 0 || GraphicsEnvironment.isHeadless()) {
            System.exit(Cli.run(args));
        }
        SwingUtilities.invokeLater(MainFrame::open);
    }
}
