// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when your Groovy code could not be compiled, e.g. due to syntax errors.
 */
public class CompilationException extends Exception {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    @SuppressWarnings("unused")
    public CompilationException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    public CompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
