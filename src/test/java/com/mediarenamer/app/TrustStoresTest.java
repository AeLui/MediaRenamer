package com.mediarenamer.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.junit.jupiter.api.Test;

/** HTTPS trust: a chain is accepted if the JDK roots or the Windows store trust it. */
class TrustStoresTest {

    /** Trust manager stub that accepts or rejects every chain. */
    private static X509ExtendedTrustManager stub(boolean accept, String name) {
        return new X509ExtendedTrustManager() {
            private void check() throws CertificateException {
                if (!accept) {
                    throw new CertificateException("rejected by " + name);
                }
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                check();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                check();
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                check();
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                check();
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
                check();
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
                check();
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    @Test
    void acceptsIfAnyStoreTrustsTheChain() {
        X509Certificate[] chain = new X509Certificate[0];
        // Windows store almost empty (clean Windows) but cacerts trusts it, and the other way round (antivirus root)
        assertDoesNotThrow(() -> TrustStores.combine(List.of(stub(true, "cacerts"), stub(false, "windows"))).checkServerTrusted(chain, "RSA", (SSLEngine) null));
        assertDoesNotThrow(() -> TrustStores.combine(List.of(stub(false, "cacerts"), stub(true, "windows"))).checkServerTrusted(chain, "RSA", (SSLEngine) null));
    }

    @Test
    void rejectsIfNoStoreTrustsTheChain() {
        CertificateException e = assertThrows(CertificateException.class,
                () -> TrustStores.combine(List.of(stub(false, "cacerts"), stub(false, "windows"))).checkServerTrusted(new X509Certificate[0], "RSA"));
        assertEquals("rejected by windows", e.getMessage());
    }

    @Test
    void acceptedIssuersJoinBothStores() throws Exception {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init((KeyStore) null);
        X509ExtendedTrustManager cacerts = null;
        for (TrustManager tm : f.getTrustManagers()) {
            if (tm instanceof X509ExtendedTrustManager x) {
                cacerts = x;
            }
        }
        int jdkRoots = cacerts.getAcceptedIssuers().length;
        assertTrue(jdkRoots > 50, "cacerts roots: " + jdkRoots);
        assertEquals(jdkRoots, TrustStores.combine(List.of(cacerts, stub(false, "empty"))).getAcceptedIssuers().length);
    }
}
