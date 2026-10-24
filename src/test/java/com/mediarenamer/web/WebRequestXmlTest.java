package com.mediarenamer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

/** parseXml never reads external entities or DTDs (XXE): AniDB answers over plain HTTP. */
class WebRequestXmlTest {

    @TempDir
    Path dir;

    private static Document parse(String xml) throws IOException {
        return WebRequest.parseXml(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void normalDocumentsStillParse() throws IOException {
        Document d = parse("<?xml version=\"1.0\"?><anime id=\"1\"><title xml:lang=\"en\">Tom &amp; Jerry</title></anime>");
        assertEquals("Tom & Jerry", d.getElementsByTagName("title").item(0).getTextContent());
        assertEquals("1", d.getDocumentElement().getAttribute("id"));
    }

    @Test
    void doctypeWithoutExternalReferencesIsAccepted() throws IOException {
        Document d = parse("<?xml version=\"1.0\"?><!DOCTYPE anime><anime><title>X</title></anime>");
        assertEquals("X", d.getElementsByTagName("title").item(0).getTextContent());
    }

    @Test
    void externalGeneralEntityIsNotRead() throws IOException {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "SECRET-123");
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]><r><t>&x;</t></r>";
        try {
            Document d = parse(xml);
            assertFalse(d.getDocumentElement().getTextContent().contains("SECRET"));
        } catch (IOException e) {
            assertFalse(String.valueOf(e.getMessage()).contains("SECRET"));
        }
    }

    @Test
    void externalParameterEntitiesCannotExfiltrate() throws IOException {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "SECRET-123");
        Path out = dir.resolve("out-SECRET-123");
        Path dtd = Files.writeString(dir.resolve("evil.dtd"), "<!ENTITY % f SYSTEM \"" + secret.toUri() + "\">"
                + "<!ENTITY % w \"<!ENTITY &#x25; o SYSTEM '" + dir.toUri() + "out-%f;'>\">%w;%o;");
        // if the parser followed the chain, it would open out-SECRET-123 (secret in the URL) and define &y;
        Files.writeString(out, "<!ENTITY y 'leaked'>");
        try {
            Document d = parse("<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY % d SYSTEM \"" + dtd.toUri() + "\"> %d;]><r>ok</r>");
            assertFalse(d.getDocumentElement().getTextContent().contains("leaked"));
        } catch (IOException e) {
            assertFalse(String.valueOf(e.getMessage()).contains("SECRET"), e.getMessage());
        }
    }

    @Test
    void entityExpansionIsLimited() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY a \"aaaaaaaaaa\">");
        String prev = "a";
        for (int i = 0; i < 9; i++) {
            String name = "e" + i;
            sb.append("<!ENTITY ").append(name).append(" \"").append(("&" + prev + ";").repeat(10)).append("\">");
            prev = name;
        }
        sb.append("]><r>&").append(prev).append(";</r>");
        boolean small;
        try {
            small = parse(sb.toString()).getDocumentElement().getTextContent().length() < 1_000_000;
        } catch (IOException e) {
            small = true; // limit reached
        }
        assertTrue(small);
    }
}
