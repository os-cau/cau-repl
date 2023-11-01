// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when an invocation of an external program failed, wither because it could not be started,
 * or because it returned a non-zero exit code.
 */
public class ExternalCommandException extends Exception {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    public ExternalCommandException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    @SuppressWarnings("unused")
    public ExternalCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
