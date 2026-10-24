package com.mediarenamer.format;

import groovy.lang.Binding;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Groovy binding that resolves format variables on demand by calling the bean getters
 * annotated with {@link Define}. Values are
 * computed once per evaluation and cached.
 */
public class ExpressionBinding extends Binding {

    private static final Map<Class<?>, Map<String, Method>> DEFINES = new ConcurrentHashMap<>();

    private final Object bean;
    private final Map<String, Method> methods;
    private final Map<String, Object> computed = new LinkedHashMap<>();
    private final Function<Object, Object> valueNormalizer;

    public ExpressionBinding(Object bean, Function<Object, Object> valueNormalizer) {
        this.bean = bean;
        this.methods = defines(bean.getClass());
        this.valueNormalizer = valueNormalizer == null ? Function.identity() : valueNormalizer;
    }

    public static Map<String, Method> defines(Class<?> type) {
        return DEFINES.computeIfAbsent(type, t -> {
            Map<String, Method> m = new LinkedHashMap<>();
            for (Method method : t.getMethods()) {
                Define d = method.getAnnotation(Define.class);
                if (d != null) {
                    for (String name : d.value()) {
                        m.put(name, method);
                    }
                }
            }
            return m;
        });
    }

    public Set<String> getDefinedNames() {
        return methods.keySet();
    }

    @Override
    public boolean hasVariable(String name) {
        return methods.containsKey(name) || super.hasVariable(name);
    }

    @Override
    public Object getVariable(String name) {
        if (super.hasVariable(name)) {
            return super.getVariable(name);
        }
        Method m = methods.get(name);
        if (m == null) {
            throw new BindingException(name, "undefined");
        }
        if (computed.containsKey(name)) {
            Object v = computed.get(name);
            if (v == null) {
                throw new BindingException(name, "undefined");
            }
            return v;
        }
        Object value;
        try {
            value = m.invoke(bean);
        } catch (InvocationTargetException e) {
            computed.put(name, null);
            Throwable cause = e.getCause();
            BindingException be = new BindingException(name, cause == null ? "error" : String.valueOf(cause.getMessage()));
            be.initCause(cause);
            throw be;
        } catch (IllegalAccessException e) {
            throw new BindingException(name, e.getMessage());
        }
        if (value instanceof CharSequence cs && cs.toString().isEmpty() || value instanceof Collection<?> c && c.isEmpty()) {
            value = null;
        }
        value = value == null ? null : valueNormalizer.apply(value);
        computed.put(name, value);
        if (value == null) {
            throw new BindingException(name, "undefined");
        }
        return value;
    }

    /** Value of the variable or null, without exceptions (used by the "View Bindings" dialog). */
    public Object getValueOrNull(String name) {
        try {
            return getVariable(name);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
