package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Plain http is only flagged when the server does not look local (the API key would cross the internet unencrypted). */
class JellyfinInsecureUrlTest {

    @Test
    void localServersAreNotFlagged() {
        for (String url : new String[] { "192.168.1.10:8096", "http://10.0.0.5:8096", "http://172.20.1.1", "http://127.0.0.1:8096",
                "http://localhost:8096", "http://nas:8096", "http://jellyfin.local", "http://media.lan:8096", "http://box.home.arpa",
                "http://[::1]:8096", "http://[fd00::1]:8096", "http://100.64.1.2", "https://jellyfin.example.com", "", null }) {
            assertFalse(JellyfinClient.isInsecureRemote(url), url);
        }
    }

    @Test
    void remoteHttpIsFlagged() {
        for (String url : new String[] { "http://jellyfin.example.com", "jellyfin.example.com:8096", "http://8.8.8.8:8096", "http://172.32.0.1",
                "http://[2001:db8::1]:8096" }) {
            assertTrue(JellyfinClient.isInsecureRemote(url), url);
        }
    }
}
