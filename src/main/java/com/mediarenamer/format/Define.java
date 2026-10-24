package com.mediarenamer.format;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a MediaBindingBean getter as a format variable. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Define {

    /** Variable names, e.g. {"n"} or {"vs", "source"}. */
    String[] value();
}
