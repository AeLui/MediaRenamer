package com.mediarenamer.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.mediarenamer.web.Movie;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Format blocks cannot run processes, reflect, write files or reach the network; the documented formats still work. */
class FormatSecurityTest {

    private static void assertDenied(String expr) {
        try {
            new ExpressionFormat(expr);
            fail("allowed: " + expr);
        } catch (FormatException e) {
            assertTrue(e.getMessage().startsWith("SecurityError: not allowed in formats"), expr + " -> " + e.getMessage());
        }
    }

    @Test
    void dangerousCodeIsRejected() {
        for (String expr : List.of("{'calc'.execute()}", "{['cmd', '/c', 'calc'].execute()}", "{n.execute()}", "{Runtime.getRuntime().exec('calc')}",
                "{java.lang.Runtime.runtime.exec('calc')}", "{new ProcessBuilder('calc').start()}", "{System.exit(0)}", "{System.getenv()}",
                "{Class.forName('java.lang.Runtime')}", "{n.getClass().forName('x')}", "{n.class.classLoader}", "{n.metaClass}",
                "{n.invokeMethod('execute', null)}", "{def m = 'exe' + 'cute'; n.\"$m\"()}", "{n.&execute}", "{evaluate('1')}",
                "{new GroovyShell().evaluate('1')}", "{Eval.me('1')}", "{new File('x').delete()}", "{new File('x').text = 'y'}",
                "{new File('x') .write('y')}", "{new File('x').withWriter { it << 'y' }}", "{Files.delete(null)}",
                "{java.nio.file.Files.write(null, null)}", "{new URL('http://x').text}", "{'http://x'.toURL().text}",
                "{new java.net.Socket('x', 1)}", "{@groovy.transform.ASTTest(value={ 1 }) def x = 1; x}", "{import java.lang.Runtime; 1}",
                "{class X {}; 1}", "{Thread.start { }}", "{n.@value}", "{this.binding}", "{(Runtime) null}", "{Runtime r = null}",
                "{n.properties}")) {
            assertDenied(expr);
        }
    }

    @Test
    void ordinaryFormatCodeIsAllowed(@TempDir Path dir) throws Exception {
        Movie m = new Movie("Avatar", List.of("Avatar: The Way"), 2009, 0, 19995, Locale.US);
        File f = new File("Avatar.2009.1080p.mkv");
        Path csv = Files.writeString(dir.resolve("names.csv"), "Avatar;Avatar (Remastered)\n");
        String csvPath = csv.toString().replace("\\", "/");
        assertEquals("AVATAR", new ExpressionFormat("{n.upper()}").format(new MediaBindingBean(m, f)));
        assertEquals("Avatar (Remastered)", new ExpressionFormat("{csv('" + csvPath + "').get(n, n)}").format(new MediaBindingBean(m, f)));
        assertEquals("Avatar (Remastered)", new ExpressionFormat("{lines('" + csvPath + "')[0].split(';')[1]}").format(new MediaBindingBean(m, f)));
        assertEquals("2009", new ExpressionFormat("{def x = y; x.toString()}").format(new MediaBindingBean(m, f)));
        assertEquals("a-b", new ExpressionFormat("{['a', 'b'].collect { it }.join('-')}").format(new MediaBindingBean(m, f)));
        assertEquals("Ava", new ExpressionFormat("{n[0..2]}").format(new MediaBindingBean(m, f)));
        assertEquals("AVA", new ExpressionFormat("{n.replaceAll(/tar/, '').upper()}").format(new MediaBindingBean(m, f)));
        assertEquals("2009-01-01", new ExpressionFormat("{LocalDate.of(y, 1, 1).toString()}").format(new MediaBindingBean(m, f)));
        assertEquals("3", new ExpressionFormat("{max(1, 3)}").format(new MediaBindingBean(m, f)));
        assertEquals("fallback", new ExpressionFormat("{any{vf}{'fallback'}}").format(new MediaBindingBean(m, f)));
        assertEquals("Avatar.2009.1080p", new ExpressionFormat("{f.name.replace('.mkv', '')}").format(new MediaBindingBean(m, f)));
    }

    /** Every {...} example of the user documentation compiles (none is rejected by the blacklist). */
    @Test
    void documentedExamplesAreAllowed() throws IOException {
        List<String> examples = new ArrayList<>();
        Matcher code = Pattern.compile("`([^`]*\\{[^`]*\\}[^`]*)`").matcher(String.join("\n", Files.readString(Path.of("docs/FORMAT.md"), StandardCharsets.UTF_8),
                Files.readString(Path.of("docs/USAGE.md")), Files.readString(Path.of("docs/CLI.md")), Files.readString(Path.of("README.md"))));
        while (code.find()) {
            examples.add(code.group(1));
        }
        assertTrue(examples.size() > 20, "examples found: " + examples.size());
        List<String> denied = new ArrayList<>();
        for (String e : examples) {
            try {
                new ExpressionFormat(e);
            } catch (FormatException ex) {
                if (ex.getMessage().startsWith("SecurityError")) {
                    denied.add(e + " -> " + ex.getMessage());
                }
            }
        }
        assertEquals(List.of(), denied);
    }
}
