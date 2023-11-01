// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when a class could not be loaded, e.g. due to incorrect class path, or wrong settings.
 */
public class ClassLoadingException extends Exception {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    @SuppressWarnings("unused")
    public ClassLoadingException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    public ClassLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
