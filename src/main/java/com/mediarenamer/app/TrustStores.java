package com.mediarenamer.app;

import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * HTTPS trust: the JDK's own root certificates ({@code cacerts}) <b>plus</b>, on Windows, the system store
 * ({@code Windows-ROOT}).
 *
 * Neither store alone is enough: {@code cacerts} does not contain the roots that an antivirus or a corporate proxy
 * installs to inspect HTTPS, and on a freshly installed Windows {@code Windows-ROOT} is almost empty, because Windows
 * downloads the roots on demand and Java's view of the store never triggers that download (found by the installer
 * smoke test in Windows Sandbox: every HTTPS request failed with "PKIX path building failed"). A server certificate
 * is accepted if any of the stores trusts it.
 *
 * If {@code javax.net.ssl.trustStore} or {@code javax.net.ssl.trustStoreType} is set explicitly, nothing is changed.
 */
public final class TrustStores {

    private TrustStores() {
    }

    /** Installs the combined trust as the default SSLContext (call before the first HTTPS request). */
    public static void install() {
        if (System.getProperty("javax.net.ssl.trustStore") != null || System.getProperty("javax.net.ssl.trustStoreType") != null) {
            return;
        }
        try {
            List<X509ExtendedTrustManager> managers = new ArrayList<>();
            managers.add(managerFor(null)); // cacerts of the bundled runtime
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                try {
                    KeyStore windows = KeyStore.getInstance("Windows-ROOT");
                    windows.load(null, null);
                    managers.add(managerFor(windows));
                } catch (Exception e) {
                    Logging.debug("Windows-ROOT not available: " + e);
                }
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] { combine(managers) }, null);
            SSLContext.setDefault(context);
        } catch (Exception e) {
            Logging.warn("TLS: " + e.getMessage());
        }
    }

    private static X509ExtendedTrustManager managerFor(KeyStore store) throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(store);
        for (TrustManager tm : f.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager x) {
                return x;
            }
        }
        throw new IllegalStateException("no X509 trust manager for " + (store == null ? "cacerts" : store.getType()));
    }

    /** Trust manager that accepts a chain if any of the given managers accepts it. */
    static X509ExtendedTrustManager combine(List<X509ExtendedTrustManager> managers) {
        return new Combined(List.copyOf(managers));
    }

    @FunctionalInterface
    private interface Check {
        void run(X509ExtendedTrustManager tm) throws CertificateException;
    }

    private static final class Combined extends X509ExtendedTrustManager {
        private final List<X509ExtendedTrustManager> managers;

        Combined(List<X509ExtendedTrustManager> managers) {
            this.managers = managers;
        }

        private void anyOf(Check check) throws CertificateException {
            CertificateException last = null;
            for (X509ExtendedTrustManager tm : managers) {
                try {
                    check.run(tm);
                    return;
                } catch (CertificateException e) {
                    last = e;
                }
            }
            throw last != null ? last : new CertificateException("no trust managers");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            anyOf(tm -> tm.checkServerTrusted(chain, authType));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            anyOf(tm -> tm.checkServerTrusted(chain, authType, socket));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            anyOf(tm -> tm.checkServerTrusted(chain, authType, engine));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            anyOf(tm -> tm.checkClientTrusted(chain, authType));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            anyOf(tm -> tm.checkClientTrusted(chain, authType, socket));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            anyOf(tm -> tm.checkClientTrusted(chain, authType, engine));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return managers.stream().map(X509TrustManager::getAcceptedIssuers).flatMap(Arrays::stream).distinct().toArray(X509Certificate[]::new);
        }
    }
}
