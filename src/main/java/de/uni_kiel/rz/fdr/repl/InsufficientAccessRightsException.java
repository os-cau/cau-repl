// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

public class InsufficientAccessRightsException extends Exception {
    public static final String explanation = ": please add \"--add-opens 'java.base/java.lang=ALL-UNNAMED'\" to your java parameters or unset CAU.Groovy.UseSystemClassLoader to use a private classloader.";

    @SuppressWarnings("unused")
    public InsufficientAccessRightsException(String message) {
        super(message + explanation);
    }

    public InsufficientAccessRightsException(String message, Throwable cause) {
        super(message + explanation, cause);
    }
}
