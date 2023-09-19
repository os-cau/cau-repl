// (C) Copyright 2015-2022 Denis Bazhenov
// SPDX-License-Identifier: MIT
// Source: https://github.com/bazhenov/groovy-shell-server/blob/c6e9781498be108529e2eeaa173cbbb19b805a47/groovy-shell-server/src/main/java/me/bazhenov/groovysh/SshTerminal.java

package de.uni_kiel.rz.fdr.repl;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;

import jline.UnixTerminal;
import org.apache.sshd.server.Environment;

/**
 * Overriding class for reading terminal width from SSH Environment
 */
public class SshTerminal extends UnixTerminal {

    private static final ThreadLocal<Environment> env = new ThreadLocal<>();

    public SshTerminal() throws Exception {
        super();
    }

    @Override
    public int getWidth() {
        String columnsAsString = retrieveEnvironment().getEnv().get("COLUMNS");
        try {
            if (isNullOrEmpty(columnsAsString)) {
                return DEFAULT_WIDTH;
            }

            int columns = parseInt(columnsAsString);
            return columns > 0
                    ? columns
                    : DEFAULT_WIDTH;
        } catch (NumberFormatException e) {
            return DEFAULT_WIDTH;
        }
    }

    @Override
    public int getHeight() {
        String linesAsString = retrieveEnvironment().getEnv().get("LINES");
        try {
            if (isNullOrEmpty(linesAsString)) {
                return DEFAULT_HEIGHT;
            }

            int lines = parseInt(linesAsString);
            return lines > 0
                    ? lines
                    : DEFAULT_HEIGHT;
        } catch (NumberFormatException e) {
            return DEFAULT_HEIGHT;
        }
    }

    private Environment retrieveEnvironment() {
        return requireNonNull(env.get(), "Environment is not registered");
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static void registerEnvironment(Environment environment) {
        env.set(environment);
    }
}
