package com.mediarenamer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.mediarenamer.app.I18n;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Third-party notices: the file covers every redistributed library, reproduces the NOTICE and bundled license files
 * of their jars, travels inside the jar, is shipped by the packaging scripts and is reachable from "About".
 */
class ThirdPartyNoticesTest {

    private static final Path ROOT_FILE = Path.of("THIRD-PARTY-NOTICES.txt");
    /** Jars whose META-INF notices are redistributed (runtime dependencies). */
    private static final Pattern RUNTIME_JAR = Pattern.compile("/(groovy|groovy-json|jna|flatlaf|rsyntaxtextarea)-[0-9.]+\\.jar!");

    @AfterEach
    void restore() {
        I18n.setLocale(Locale.ENGLISH);
    }

    private static String squash(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    @Test
    void bundledResourceIsTheRepositoryFile() throws Exception {
        String bundled = ThirdPartyLicensesDialog.load();
        assertNotNull(bundled, ThirdPartyLicensesDialog.RESOURCE + " must be in the jar");
        assertEquals(Files.readString(ROOT_FILE, StandardCharsets.UTF_8), bundled);
        assertTrue(bundled.length() < ThirdPartyLicensesDialog.MAX_BYTES);
    }

    @Test
    void everyRuntimeDependencyOfThePomIsListedWithItsVersion() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        String notices = Files.readString(ROOT_FILE, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        Matcher props = Pattern.compile("<groovy\\.version>([^<]+)</groovy\\.version>").matcher(pom);
        assertTrue(props.find());
        String groovyVersion = props.group(1);
        Matcher dep = Pattern.compile("<dependency>\\s*<groupId>[^<]+</groupId>\\s*<artifactId>([^<]+)</artifactId>\\s*"
                + "<version>([^<]+)</version>\\s*(<scope>([^<]+)</scope>)?", Pattern.DOTALL).matcher(pom);
        List<String> checked = new ArrayList<>();
        while (dep.find()) {
            if ("test".equals(dep.group(4))) {
                continue;
            }
            String artifact = dep.group(1).toLowerCase(Locale.ROOT);
            String version = dep.group(2).replace("${groovy.version}", groovyVersion);
            assertTrue(notices.contains(artifact), artifact + " is not in THIRD-PARTY-NOTICES.txt");
            assertTrue(notices.contains(version), artifact + " " + version + ": version not in THIRD-PARTY-NOTICES.txt");
            checked.add(artifact);
        }
        assertEquals(5, checked.size(), "runtime dependencies found: " + checked);
    }

    @Test
    void noticeAndBundledLicenseFilesOfTheJarsAreReproduced() throws Exception {
        String notices = squash(Files.readString(ROOT_FILE, StandardCharsets.UTF_8));
        ClassLoader cl = getClass().getClassLoader();
        int found = 0;
        for (String name : List.of("META-INF/NOTICE", "META-INF/licenses/antlr4-license.txt", "META-INF/licenses/asm-license.txt")) {
            for (URL url : Collections.list(cl.getResources(name))) {
                if (!RUNTIME_JAR.matcher(url.toString()).find()) {
                    continue;
                }
                try (InputStream in = url.openStream()) {
                    String text = squash(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    assertTrue(notices.contains(text), url + " is not reproduced in THIRD-PARTY-NOTICES.txt");
                    found++;
                }
            }
        }
        assertTrue(found >= 3, "Groovy NOTICE, ANTLR and ASM licenses: " + found);
    }

    @Test
    void javaRuntimeAndLicenseTextsArePresent() throws Exception {
        String notices = Files.readString(ROOT_FILE, StandardCharsets.UTF_8);
        for (String required : List.of("GPL-2.0-only WITH Classpath-exception-2.0", "\"CLASSPATH\" EXCEPTION TO THE GPL",
                "OPENJDK ASSEMBLY EXCEPTION", "runtime\\legal", "TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION",
                "Copyright (c) 2021, Robert Futrell", "libffi", "Apache-2.0 OR LGPL-2.1")) {
            assertTrue(notices.contains(required), required);
        }
    }

    @Test
    void packagingShipsTheNotices() throws Exception {
        String script = Files.readString(Path.of("scripts", "build-exe.ps1"), StandardCharsets.UTF_8);
        assertTrue(script.contains("THIRD-PARTY-NOTICES.txt"), "build-exe.ps1 copies the notices into the app-image");
        assertTrue(script.contains("runtime\\legal\\java.base\\LICENSE"), "build-exe.ps1 checks the runtime notices");
        assertTrue(script.contains("MediaRenamer/$notices"), "build-exe.ps1 checks the zip");
        String iss = Files.readString(Path.of("src", "main", "packaging", "MediaRenamer.iss"), StandardCharsets.UTF_8);
        assertTrue(iss.contains("#if !FileExists(AddBackslash(SourceDir) + \"THIRD-PARTY-NOTICES.txt\")"), "the installer refuses to build without them");
    }

    @Test
    void readingIsBounded() throws Exception {
        byte[] big = new byte[ThirdPartyLicensesDialog.MAX_BYTES + 1];
        assertNull(ThirdPartyLicensesDialog.read(new ByteArrayInputStream(big)));
        assertEquals("abc", ThirdPartyLicensesDialog.read(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void aboutHasAThirdPartyLicensesEntryThatShowsThePlainText() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            AboutDialog about = new AboutDialog(null);
            try {
                assertEquals("Third-party licenses", about.licenses.getText());
                assertEquals(1, about.licenses.getActionListeners().length);
                ThirdPartyLicensesDialog d = new ThirdPartyLicensesDialog(about);
                try {
                    assertEquals("Third-party licenses", d.getTitle());
                    assertTrue(d.text.getText().startsWith("MediaRenamer - Third-party software notices"));
                    assertFalse(d.text.isEditable());
                    assertEquals(0, d.text.getCaretPosition(), "opens at the top");
                } finally {
                    d.dispose();
                }
            } finally {
                about.dispose();
            }
        });
        I18n.setLocale(Locale.forLanguageTag("es"));
        SwingUtilities.invokeAndWait(() -> {
            AboutDialog about = new AboutDialog(null);
            try {
                assertEquals("Licencias de terceros", about.licenses.getText());
            } finally {
                about.dispose();
            }
        });
    }

    @Test
    void aboutHasNoDevelopmentToolCredit() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            AboutDialog about = new AboutDialog(null);
            try {
                List<String> texts = new ArrayList<>();
                collectLabels(about.getContentPane(), texts);
                assertTrue(texts.contains("AeLiu"), "labels found: " + texts);
                for (String t : texts) {
                    assertFalse(t.contains("Claude") || t.startsWith("Powered by"), t);
                }
            } finally {
                about.dispose();
            }
        });
    }

    private static void collectLabels(java.awt.Container c, List<String> out) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof javax.swing.JLabel l && l.getText() != null) {
                out.add(l.getText());
            }
            if (child instanceof java.awt.Container sub) {
                collectLabels(sub, out);
            }
        }
    }
}
