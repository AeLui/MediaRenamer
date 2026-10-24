package com.mediarenamer.format;

import java.util.List;
import java.util.Set;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MethodPointerExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.syntax.Types;

/**
 * Blacklist of what a format block may not do: run processes, exit, load or reflect on classes, change files, open
 * network connections or evaluate more code. Formats are often copied from forums or shared profiles, so a block
 * like {@code {'cmd /c ...'.execute()}} must not run.
 *
 * <p>The check runs on the syntax tree right after parsing (before any AST transformation, so annotations such as
 * {@code @ASTTest} cannot run code at compile time) and works on names, because format code is dynamically typed:
 * the blocked method names are rejected on any receiver. Everything formats normally do (variables, the format
 * methods, String/date/collection/regex methods, closures, {@code text()}/{@code csv()}/{@code lines()}) is still
 * allowed. It is a blacklist, not a full sandbox: it raises the bar against hostile formats, it does not make any
 * expression safe.
 */
final class FormatSecurity extends CompilationCustomizer {

    /** Types that give access to processes, the JVM, class loading, reflection, file writing or the network. */
    static final Set<String> BLOCKED_TYPES = Set.of("Runtime", "ProcessBuilder", "Process", "ProcessHandle", "System", "Thread",
            "ThreadGroup", "Class", "ClassLoader", "URLClassLoader", "GroovyShell", "GroovyClassLoader", "Eval", "GroovySystem",
            "Files", "FileWriter", "FileOutputStream", "PrintWriter", "PrintStream", "RandomAccessFile", "URL", "Socket",
            "ServerSocket", "DatagramSocket", "HttpClient", "Unsafe", "MethodHandles", "Proxy", "ObjectInputStream",
            "ScriptEngineManager", "Executors", "ForkJoinPool", "CompletableFuture", "Timer");
    static final List<String> BLOCKED_PACKAGES = List.of("java.lang.reflect.", "java.lang.invoke.", "java.net.", "java.nio.channels.",
            "java.nio.file.", "javax.script.", "groovy.grape.", "groovy.lang.Groovy", "org.codehaus.groovy.", "sun.", "jdk.", "com.sun.");
    /** Rejected on any receiver (format code has no static types). */
    static final Set<String> BLOCKED_METHODS = Set.of("execute", "exec", "exit", "halt", "forName", "loadClass", "defineClass",
            "newInstance", "getDeclaredMethod", "getDeclaredMethods", "getDeclaredField", "getDeclaredFields", "getDeclaredConstructor",
            "getDeclaredConstructors", "getMethod", "getMethods", "getField", "getFields", "getConstructor", "getConstructors",
            "setAccessible", "invokeMethod", "getMetaClass", "setMetaClass", "getProperty", "setProperty", "evaluate", "parseClass",
            "getClassLoader", "getClass", "load", "loadLibrary", "delete", "deleteDir", "deleteOnExit", "renameTo", "write", "withWriter",
            "withWriterAppend", "withOutputStream", "withPrintWriter", "withDataOutputStream", "withObjectOutputStream", "newWriter",
            "newOutputStream", "newPrintWriter", "newObjectInputStream", "setText", "setBytes", "append", "createNewFile", "mkdir",
            "mkdirs", "setExecutable", "setWritable", "setReadable", "setLastModified", "toURL", "openConnection", "openStream",
            "getBinding", "setBinding", "run", "start", "interrupt", "sleep", "wait", "notify", "notifyAll");
    static final Set<String> BLOCKED_PROPERTIES = Set.of("class", "metaClass", "classLoader", "declaredMethods", "declaredFields",
            "methods", "fields", "binding", "runtime", "properties");

    FormatSecurity() {
        super(CompilePhase.CONVERSION);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        ModuleNode module = source.getAST();
        if (module.getClasses().size() > 1 || !classNode.isScript()) {
            throw denied("class declarations");
        }
        for (List<? extends ImportNode> imports : List.of(module.getImports(), module.getStarImports(), module.getStaticImports().values().stream().toList(),
                module.getStaticStarImports().values().stream().toList())) {
            for (ImportNode i : imports) {
                // the ImportCustomizer imports (java.time...) have no line number; the ones written in a block do
                if (i.getLineNumber() > 0) {
                    throw denied("import");
                }
            }
        }
        Checker checker = new Checker(source);
        checker.visitAnnotations(classNode);
        for (MethodNode m : classNode.getMethods()) {
            // the generated main() calls InvokerHelper.runScript: only the block code (run) and user methods are checked
            if (m.isStatic() && m.getName().equals("main")) {
                continue;
            }
            checker.visitAnnotations(m);
            for (Parameter p : m.getParameters()) {
                checker.visitAnnotations(p);
            }
            if (m.getCode() != null) {
                m.getCode().visit(checker);
            }
        }
        if (!classNode.getFields().isEmpty() || !classNode.getObjectInitializerStatements().isEmpty()) {
            throw denied("fields");
        }
    }

    static SecurityException denied(String what) {
        return new SecurityException("not allowed in formats: " + what);
    }

    static void checkType(String name) {
        if (name == null) {
            return;
        }
        String simple = name.substring(name.lastIndexOf('.') + 1);
        if (BLOCKED_TYPES.contains(simple)) {
            throw denied(simple);
        }
        for (String p : BLOCKED_PACKAGES) {
            if (name.startsWith(p)) {
                throw denied(name);
            }
        }
    }

    /** "java.lang.Runtime" written as a chain of properties (types are not resolved yet in this phase). */
    static String dottedName(Expression e) {
        if (e instanceof VariableExpression v) {
            return v.getName();
        }
        if (e instanceof ClassExpression c) {
            return c.getType().getName();
        }
        if (e instanceof PropertyExpression p && p.getProperty() instanceof ConstantExpression c) {
            String left = dottedName(p.getObjectExpression());
            return left == null ? null : left + "." + c.getValue();
        }
        return null;
    }

    private static final class Checker extends ClassCodeVisitorSupport {
        private final SourceUnit source;

        Checker(SourceUnit source) {
            this.source = source;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return source;
        }

        @Override
        public void visitAnnotations(AnnotatedNode node) {
            // generated annotations (@Generated on the script methods) have no position; written ones do
            if (node.getAnnotations().stream().anyMatch(a -> a.getLineNumber() > 0)) {
                throw denied("annotations");
            }
            super.visitAnnotations(node);
        }

        private static void checkMethodName(Expression name) {
            if (!(name instanceof ConstantExpression c) || !(c.getValue() instanceof String s)) {
                throw denied("dynamic method names");
            }
            if (BLOCKED_METHODS.contains(s)) {
                throw denied(s + "()");
            }
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            checkMethodName(call.getMethod());
            checkType(dottedName(call.getObjectExpression()));
            super.visitMethodCallExpression(call);
        }

        @Override
        public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
            if (BLOCKED_METHODS.contains(call.getMethod())) {
                throw denied(call.getMethod() + "()");
            }
            checkType(call.getOwnerType().getName());
            super.visitStaticMethodCallExpression(call);
        }

        @Override
        public void visitMethodPointerExpression(MethodPointerExpression e) {
            checkMethodName(e.getMethodName());
            super.visitMethodPointerExpression(e);
        }

        @Override
        public void visitConstructorCallExpression(ConstructorCallExpression call) {
            checkType(call.getType().getName());
            super.visitConstructorCallExpression(call);
        }

        @Override
        public void visitPropertyExpression(PropertyExpression e) {
            if (!(e.getProperty() instanceof ConstantExpression c) || !(c.getValue() instanceof String s)) {
                throw denied("dynamic property names");
            }
            if (BLOCKED_PROPERTIES.contains(s)) {
                throw denied("." + s);
            }
            checkType(dottedName(e));
            super.visitPropertyExpression(e);
        }

        @Override
        public void visitAttributeExpression(AttributeExpression e) {
            throw denied(".@ field access");
        }

        @Override
        public void visitVariableExpression(VariableExpression e) {
            checkType(e.getName());
            super.visitVariableExpression(e);
        }

        @Override
        public void visitClassExpression(ClassExpression e) {
            checkType(e.getType().getName());
            super.visitClassExpression(e);
        }

        @Override
        public void visitCastExpression(CastExpression e) {
            checkType(e.getType().getName());
            super.visitCastExpression(e);
        }

        @Override
        public void visitDeclarationExpression(DeclarationExpression e) {
            checkType(e.getLeftExpression().getType().getName());
            super.visitDeclarationExpression(e);
        }

        @Override
        public void visitBinaryExpression(BinaryExpression e) {
            // f.text = '...' / f.bytes = ... would write files; formats only assign local variables
            if (Types.isAssignment(e.getOperation().getType()) && !(e instanceof DeclarationExpression)
                    && !(e.getLeftExpression() instanceof VariableExpression)) {
                throw denied("assignment to properties");
            }
            super.visitBinaryExpression(e);
        }
    }
}
