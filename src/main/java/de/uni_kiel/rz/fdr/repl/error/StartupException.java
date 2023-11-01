// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when the REPL failed to start, e.g. due to wrong settings.
 */
public class StartupException extends Exception {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    public StartupException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    public StartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
