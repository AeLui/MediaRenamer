package com.mediarenamer.format;

import com.mediarenamer.util.FileUtilities;

import java.io.File;
import java.util.regex.Pattern;

/**
 * Format whose result is a file path:
 * <ul>
 * <li>text values of the bindings cannot add folders: {@code /} and {@code \} become a space ({@code AC/DC} gives
 * {@code AC DC}); {@link File} values such as {@code {plex}} keep their separators;</li>
 * <li>a path segment made only of dots that comes from a block value is dropped, so a value cannot climb folders
 * ({@code ..} written in the literal text is kept);</li>
 * <li>when every block before a literal came out empty, the separators at the start of that literal are dropped, so
 * {@code {x}/{n}} does not become the absolute {@code /Avatar} (an absolute literal at the very start is kept);</li>
 * <li>the result loses line breaks and control characters, has its spaces collapsed, its separators unified to
 * {@code /} and every segment validated ({@link FileUtilities#validateFilePath}).</li>
 * </ul>
 */
public class ExpressionFileFormat extends ExpressionFormat {

    private static final Pattern SEPARATOR_SPLIT = Pattern.compile("(?<=[/\\\\])|(?=[/\\\\])");
    private static final Pattern DOTS_ONLY = Pattern.compile("[\\s.]*\\.[\\s.]*");
    private static final Pattern LEADING_SEPARATORS = Pattern.compile("^[/\\\\]+");
    private static final Pattern CONTROL = Pattern.compile("[\\r\\n\\x00-\\x08\\x0B-\\x1F\\x7F\\x85\\u2028\\u2029]");
    private static final Pattern BLANKS = Pattern.compile("[ \\t]+");

    public ExpressionFileFormat(String expression) throws FormatException {
        super(expression);
    }

    public File formatFile(Object bean) throws FormatException {
        return new File(format(bean));
    }

    @Override
    protected Object normalizeBindingValue(Object value) {
        if (value instanceof CharSequence cs) {
            return FileUtilities.replacePathSeparators(cs.toString(), " ");
        }
        return value;
    }

    @Override
    protected String blockText(Object value, CharSequence resultSoFar) {
        String text = String.valueOf(value);
        StringBuilder out = new StringBuilder(text.length());
        boolean skipSeparators = false;
        for (String piece : SEPARATOR_SPLIT.split(text)) {
            boolean separator = piece.equals("/") || piece.equals("\\");
            if (separator) {
                if (!skipSeparators) {
                    out.append(piece);
                }
                continue;
            }
            skipSeparators = false;
            if (DOTS_ONLY.matcher(piece).matches() && atSegmentStart(resultSoFar, out)) {
                // the separator after a dropped segment would otherwise turn the path absolute
                skipSeparators = true;
                continue;
            }
            out.append(piece);
        }
        return out.toString();
    }

    private static boolean atSegmentStart(CharSequence before, StringBuilder out) {
        CharSequence last = out.isEmpty() ? before : out;
        if (last.isEmpty()) {
            return true;
        }
        char c = last.charAt(last.length() - 1);
        return c == '/' || c == '\\';
    }

    @Override
    protected String literalText(String literal, CharSequence resultSoFar, boolean first) {
        if (!first && resultSoFar.isEmpty()) {
            return LEADING_SEPARATORS.matcher(literal).replaceFirst("");
        }
        return literal;
    }

    @Override
    protected String normalizeResult(String result) {
        String s = CONTROL.matcher(result).replaceAll("");
        s = BLANKS.matcher(s).replaceAll(" ").trim();
        s = FileUtilities.normalizePathSeparators(s);
        return FileUtilities.validateFilePath(s);
    }
}
