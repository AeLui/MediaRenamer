package com.mediarenamer.format;

import groovy.lang.MissingPropertyException;

/**
 * Format variable without a value. It makes the {...} block that uses it fail, and the block is replaced
 * by an empty string.
 */
public class BindingException extends MissingPropertyException {

    public BindingException(String name, String reason) {
        super("Binding \"" + name + "\": " + reason, name, null);
    }
}
