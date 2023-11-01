// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when cau-repl does not proper access rights in the JVM.
 * You probably need to add {@code --add-opens 'java.base/java.lang=ALL-UNNAMED'} to your java parameters.
 */
public class InsufficientAccessRightsException extends Exception {
    /**
     * Contains the instructions for fixing this error condition.
     */
    public static final String explanation = ": please add \"--add-opens 'java.base/java.lang=ALL-UNNAMED'\" to your java parameters or unset CAU.Groovy.UseSystemClassLoader to use a private classloader.";

    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    @SuppressWarnings("unused")
    public InsufficientAccessRightsException(String message) {
        super(message + explanation);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    public InsufficientAccessRightsException(String message, Throwable cause) {
        super(message + explanation, cause);
    }
}
