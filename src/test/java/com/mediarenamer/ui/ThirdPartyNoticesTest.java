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
 * The same for MediaRenamer's own license (LICENSE, GNU GPL v3).
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
    void ownLicenseIsTheGplV3AndTravelsInsideTheJar() throws Exception {
        String license = Files.readString(Path.of("LICENSE"), StandardCharsets.UTF_8);
        assertTrue(license.contains("GNU GENERAL PUBLIC LICENSE") && license.contains("Version 3, 29 June 2007"));
        assertEquals(license, ThirdPartyLicensesDialog.load(ThirdPartyLicensesDialog.LICENSE_RESOURCE));
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        assertTrue(pom.contains("<name>GPL-3.0-only</name>"), "the pom declares the license");
        assertNull(ThirdPartyLicensesDialog.load("/META-INF/mediarenamer/missing"));
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
        assertTrue(script.contains("Copy-Item (Join-Path $root \"LICENSE\") (Join-Path $appDir $license)"), "build-exe.ps1 ships LICENSE.txt");
        assertTrue(script.contains("MediaRenamer/$license"), "build-exe.ps1 checks LICENSE.txt in the zip");
        assertFalse(script.contains("Personal use"), "the exe copyright names the GPL");
        String iss = Files.readString(Path.of("src", "main", "packaging", "MediaRenamer.iss"), StandardCharsets.UTF_8);
        assertTrue(iss.contains("#if !FileExists(AddBackslash(SourceDir) + \"THIRD-PARTY-NOTICES.txt\")"), "the installer refuses to build without them");
        assertTrue(iss.contains("#if !FileExists(AddBackslash(SourceDir) + \"LICENSE.txt\")"), "nor without the GPL");
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
    void aboutShowsTheGplAndOpensItsText() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            AboutDialog about = new AboutDialog(null);
            try {
                assertEquals("GNU GPL v3", about.license.getText());
                assertEquals(1, about.license.getActionListeners().length);
                List<String> texts = new ArrayList<>();
                collectLabels(about.getContentPane(), texts);
                assertTrue(texts.contains("License"), "labels found: " + texts);
                assertTrue(texts.stream().anyMatch(t -> t.contains("Free software under the GNU GPL v3")), "notice: " + texts);
                assertFalse(texts.stream().anyMatch(t -> t.contains("personal use")), "no personal-use restriction: " + texts);
                ThirdPartyLicensesDialog d = ThirdPartyLicensesDialog.ownLicense(about);
                try {
                    assertEquals("MediaRenamer license", d.getTitle());
                    assertTrue(d.text.getText().contains("GNU GENERAL PUBLIC LICENSE"));
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
                List<String> texts = new ArrayList<>();
                collectLabels(about.getContentPane(), texts);
                assertTrue(texts.contains("Licencia"), "labels found: " + texts);
            } finally {
                about.dispose();
            }
        });
    }

    @Test
    void aboutHasASponsorLinkToTheFixedGitHubSponsorsPage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        assertEquals("https://github.com/sponsors/AFBenot", AboutDialog.SPONSOR_URL);
        String funding = Files.readString(Path.of(".github", "FUNDING.yml"), StandardCharsets.UTF_8);
        assertTrue(funding.contains("github: AFBenot"), "the repository Sponsor button points to the same account");
        I18n.setLocale(Locale.ENGLISH);
        SwingUtilities.invokeAndWait(() -> {
            AboutDialog about = new AboutDialog(null);
            try {
                assertEquals("\u2665 Sponsor", about.sponsor.getText());
                assertEquals(AboutDialog.SPONSOR_URL, about.sponsor.getToolTipText());
                assertEquals(1, about.sponsor.getActionListeners().length);
            } finally {
                about.dispose();
            }
        });
        I18n.setLocale(Locale.forLanguageTag("es"));
        SwingUtilities.invokeAndWait(() -> {
            AboutDialog about = new AboutDialog(null);
            try {
                assertEquals("\u2665 Patrocinar", about.sponsor.getText());
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
