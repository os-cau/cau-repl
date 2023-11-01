// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when your Groovy code could not be compiled, e.g. due to syntax errors.
 * This is an unchecked exception that you should only encounter wrapped in a checked exception.
 */
public class UncheckedCompilationException extends RuntimeException {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    public UncheckedCompilationException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    public UncheckedCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
