// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when a job-related function could not be performed, e.g. because the job was in the wrong state.
 */
public class JobException extends Exception {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    public JobException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    @SuppressWarnings("unused")
    public JobException(String message, Throwable cause) {
        super(message, cause);
    }
}
