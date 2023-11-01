// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.error;

/**
 * Thrown when there was invalid data in one of cau-repl's job state files.
 */
public class ObjectStoreInvalidException extends Exception {
    /**
     * Create a new instance of the exception
     * @param message The message.
     */
    public ObjectStoreInvalidException(String message) {
        super(message);
    }

    /**
     * Create a new instance of the exception
     * @param message The message.
     * @param cause The cause.
     */
    @SuppressWarnings("unused")
    public ObjectStoreInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
