package com.mediarenamer.format;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;
import groovy.transform.ThreadInterrupt;
import groovy.transform.TimedInterrupt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * Format expression: literal text with Groovy blocks in braces, e.g. {@code {n} - {s00e00} - {t}}.
 *
 * <p>Each top-level {@code {...}} is compiled once as a Groovy script (base class {@link FormatScript}, checked by
 * {@link FormatSecurity}, with a time limit and thread interruption checks). Braces nest and are counted without
 * regard to quotes. When evaluated, a block that yields null or fails adds nothing (its error is kept for
 * {@link #getSuppressed()}); an expression whose whole result is empty is an error.
 *
 * <p>Subclasses adapt the result through four hooks: {@link #normalizeBindingValue}, {@link #blockText},
 * {@link #literalText} and {@link #normalizeResult}.
 */
public class ExpressionFormat {

    /** Compiled blocks kept before the cache and its class loader are replaced. */
    static final int MAX_COMPILED = 1000;
    /** Time limit of each block evaluation. */
    static final int BLOCK_TIMEOUT_SECONDS = 10;

    private static final String SECURITY_MARK = "not allowed in formats";
    private static final AtomicLong SCRIPT_COUNTER = new AtomicLong();

    private static final Object CACHE_LOCK = new Object();
    private static GroovyClassLoader loader;
    private static Map<String, Class<?>> compiled = new HashMap<>();

    /** A part of the expression: literal text, or a block (compiled script, null for an empty block). */
    private record Part(String text, boolean block, Class<?> script) {
    }

    private final String expression;
    private final List<Part> parts;
    private volatile List<Throwable> suppressed = List.of();

    public ExpressionFormat(String expression) throws FormatException {
        this.expression = expression;
        this.parts = compile(expression);
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }

    /** True when the expression has no {...} blocks. */
    public boolean isConstant() {
        return parts.stream().noneMatch(Part::block);
    }

    /** Errors of the blocks that came out empty in the last evaluation. */
    public List<Throwable> getSuppressed() {
        return suppressed;
    }

    public String format(Object bean) throws FormatException {
        return format(new ExpressionBinding(bean, this::normalizeBindingValue));
    }

    public String format(ExpressionBinding binding) throws FormatException {
        StringBuilder result = new StringBuilder();
        List<Throwable> errors = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (!p.block()) {
                result.append(literalText(p.text(), result, i == 0));
                continue;
            }
            if (p.script() == null) {
                continue;
            }
            try {
                Object value = evaluate(p.script(), binding);
                if (value != null) {
                    result.append(blockText(value, result));
                }
            } catch (Throwable t) {
                if (t instanceof VirtualMachineError && !(t instanceof StackOverflowError)) {
                    throw (VirtualMachineError) t;
                }
                errors.add(readable(t, p.script()));
            }
        }
        suppressed = Collections.unmodifiableList(errors);
        String text = normalizeResult(result.toString());
        if (text.isEmpty()) {
            throw new FormatException("Expression yields empty value", errors);
        }
        return text;
    }

    /**
     * A variable missing from the binding reaches us as Groovy's "No such property: x for class: FormatBlock12", which
     * names an internal class; it is reported as the undefined variable instead.
     */
    private static Throwable readable(Throwable t, Class<?> script) {
        if (t instanceof groovy.lang.MissingPropertyException mpe && !(t instanceof BindingException) && mpe.getType() == script) {
            BindingException b = new BindingException(mpe.getProperty(), "undefined");
            b.initCause(t);
            return b;
        }
        return t;
    }

    private static Object evaluate(Class<?> script, ExpressionBinding binding) {
        Script s = InvokerHelper.createScript(script, binding);
        return s.run();
    }

    // ------------------------------------------------------------------ hooks

    /** How a binding value is normalized before the block code sees it. */
    protected Object normalizeBindingValue(Object value) {
        return value;
    }

    /** The text a non-null block value adds, given the result so far. */
    protected String blockText(Object value, CharSequence resultSoFar) {
        return String.valueOf(value);
    }

    /** The text a literal part adds, given the result so far and whether it is the first part of the expression. */
    protected String literalText(String literal, CharSequence resultSoFar, boolean first) {
        return literal;
    }

    /** How the final result is normalized. */
    protected String normalizeResult(String result) {
        return result;
    }

    // ------------------------------------------------------------------ compilation

    private static List<Part> compile(String expression) throws FormatException {
        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int depth = 0;
        int blockStart = -1;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    if (!literal.isEmpty()) {
                        parts.add(new Part(literal.toString(), false, null));
                        literal.setLength(0);
                    }
                    blockStart = i + 1;
                }
                depth++;
            } else if (c == '}') {
                if (depth == 0) {
                    throw new FormatException("SyntaxError: unexpected token: }");
                }
                depth--;
                if (depth == 0) {
                    String code = expression.substring(blockStart, i);
                    parts.add(new Part(code, true, code.isBlank() ? null : compileBlock(code)));
                }
            } else if (depth == 0) {
                literal.append(c);
            }
        }
        if (depth > 0) {
            throw new FormatException("SyntaxError: missing token: }");
        }
        if (!literal.isEmpty()) {
            parts.add(new Part(literal.toString(), false, null));
        }
        return parts;
    }

    private static Class<?> compileBlock(String code) throws FormatException {
        synchronized (CACHE_LOCK) {
            Class<?> c = compiled.get(code);
            if (c != null) {
                return c;
            }
            if (loader == null || compiled.size() >= MAX_COMPILED) {
                // a new loader lets the classes of the old one be unloaded once no format uses them (not closed:
                // live formats may still need it)
                loader = new GroovyClassLoader(ExpressionFormat.class.getClassLoader(), configuration());
                compiled = new HashMap<>();
            }
            try {
                GroovyCodeSource source = new GroovyCodeSource(code, "FormatBlock" + SCRIPT_COUNTER.incrementAndGet() + ".groovy", "/groovy/format");
                source.setCachable(false);
                c = loader.parseClass(source);
            } catch (CompilationFailedException e) {
                throw compileError(code, e);
            } catch (SecurityException e) {
                throw securityError(code, e.getMessage());
            }
            compiled.put(code, c);
            return c;
        }
    }

    private static CompilerConfiguration configuration() {
        CompilerConfiguration config = new CompilerConfiguration();
        config.setScriptBaseClass(FormatScript.class.getName());
        config.setDisabledGlobalASTTransformations(Set.of("groovy.grape.GrabAnnotationTransformation"));
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("java.time", "java.util.regex", "com.mediarenamer.web");
        imports.addStaticStars("java.lang.Math");
        Set<String> collisions = BindingNameCustomizer.collisions(ExpressionBinding.defines(MediaBindingBean.class).keySet(), "com.mediarenamer.web",
                "java.time", "java.util.regex");
        config.addCompilationCustomizers(new FormatSecurity(), imports, new BindingNameCustomizer(collisions),
                new ASTTransformationCustomizer(Map.of("value", (long) BLOCK_TIMEOUT_SECONDS), TimedInterrupt.class),
                new ASTTransformationCustomizer(ThreadInterrupt.class));
        return config;
    }

    private static FormatException compileError(String code, CompilationFailedException e) {
        if (e instanceof MultipleCompilationErrorsException me) {
            for (Message m : me.getErrorCollector().getErrors()) {
                if (m instanceof ExceptionMessage em && em.getCause() instanceof SecurityException se) {
                    return securityError(code, se.getMessage());
                }
            }
        }
        String message = String.valueOf(e.getMessage());
        if (message.contains(SECURITY_MARK)) {
            return securityError(code, message);
        }
        return new FormatException("SyntaxError: " + firstMeaningfulLine(message) + " in {" + code + "}");
    }

    private static FormatException securityError(String code, String message) {
        String m = String.valueOf(message);
        int i = m.indexOf(SECURITY_MARK);
        if (i >= 0) {
            m = m.substring(i);
        }
        m = m.split("\\R", 2)[0];
        int at = m.indexOf(" @ line");
        if (at >= 0) {
            m = m.substring(0, at);
        }
        return new FormatException("SecurityError: " + m.trim() + " in {" + code + "}");
    }

    /** First line of a compiler message without the "startup failed" header and the generated script name. */
    private static String firstMeaningfulLine(String message) {
        for (String line : message.split("\\R")) {
            String l = line.trim();
            if (l.isEmpty() || l.equalsIgnoreCase("startup failed:")) {
                continue;
            }
            return l.replaceFirst("^\\S+\\.groovy: (?:\\d+: )?", "");
        }
        return message.trim();
    }
}
