package com.mediarenamer.format;

import java.util.HashSet;
import java.util.Set;

import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

/**
 * Keeps format variables whose name is also the name of an imported class ({@code {XEM.scene}}, while
 * {@code com.mediarenamer.web.XEM} is star-imported) pointing to the variable: Groovy would otherwise resolve the name
 * to the class. Such names become {@code this.NAME}, which reads the binding, unless the block declares them itself.
 */
final class BindingNameCustomizer extends CompilationCustomizer {

    private final Set<String> names;

    /** @param names variable names that collide with imported class names */
    BindingNameCustomizer(Set<String> names) {
        super(CompilePhase.CONVERSION);
        this.names = Set.copyOf(names);
    }

    /** The given variable names that are also simple names of classes in the given packages. */
    static Set<String> collisions(Set<String> variables, String... packages) {
        Set<String> result = new HashSet<>();
        for (String v : variables) {
            if (v.isEmpty() || !Character.isUpperCase(v.charAt(0))) {
                continue;
            }
            for (String p : packages) {
                try {
                    Class.forName(p + "." + v, false, BindingNameCustomizer.class.getClassLoader());
                    result.add(v);
                } catch (ClassNotFoundException | LinkageError e) {
                    // not a class of that package
                }
            }
        }
        return result;
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (names.isEmpty()) {
            return;
        }
        Set<String> declared = declaredNames(source, classNode);
        ClassCodeExpressionTransformer transformer = new ClassCodeExpressionTransformer() {
            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }

            @Override
            public Expression transform(Expression e) {
                if (e instanceof VariableExpression v && names.contains(v.getName()) && !declared.contains(v.getName())) {
                    PropertyExpression p = new PropertyExpression(VariableExpression.THIS_EXPRESSION, v.getName());
                    p.setSourcePosition(v);
                    return p;
                }
                if (e instanceof ClosureExpression c) {
                    c.getCode().visit(this);
                    return c;
                }
                return e == null ? null : e.transformExpression(this);
            }
        };
        for (MethodNode m : classNode.getMethods()) {
            if (m.getCode() != null) {
                m.getCode().visit(transformer);
            }
        }
    }

    /** Names the block declares (local variables, closure and method parameters). */
    private static Set<String> declaredNames(SourceUnit source, ClassNode classNode) {
        Set<String> declared = new HashSet<>();
        ClassCodeVisitorSupport collector = new ClassCodeVisitorSupport() {
            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }

            @Override
            public void visitDeclarationExpression(DeclarationExpression d) {
                if (d.isMultipleAssignmentDeclaration()) {
                    d.getTupleExpression().getExpressions().forEach(x -> declared.add(((VariableExpression) x).getName()));
                } else {
                    declared.add(d.getVariableExpression().getName());
                }
                super.visitDeclarationExpression(d);
            }

            @Override
            public void visitClosureExpression(ClosureExpression c) {
                if (c.getParameters() != null) {
                    for (Parameter p : c.getParameters()) {
                        declared.add(p.getName());
                    }
                }
                super.visitClosureExpression(c);
            }
        };
        for (MethodNode m : classNode.getMethods()) {
            for (Parameter p : m.getParameters()) {
                declared.add(p.getName());
            }
            if (m.getCode() != null) {
                m.getCode().visit(collector);
            }
        }
        return declared;
    }
}
