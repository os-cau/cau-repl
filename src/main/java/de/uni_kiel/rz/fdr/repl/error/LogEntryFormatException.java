// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when there was corrupted data in one of cau-repl's log files.
 */
public class LogEntryFormatException extends RuntimeException {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    public LogEntryFormatException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    @SuppressWarnings("unused")
    public LogEntryFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
