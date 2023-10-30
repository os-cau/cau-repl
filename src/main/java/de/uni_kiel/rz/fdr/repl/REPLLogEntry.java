// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import groovy.json.StringEscapeUtils;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;

/**
 * Represents a single log message to be used by {@link REPLLog}.
 */
@SuppressWarnings("unused")
public class REPLLogEntry implements Serializable {

    private static final String TRACE_PREFIX = "=====";

    /**
     * The different levels of criticality for a log message.
     */
    @SuppressWarnings("unused")
    public enum LOG_LEVEL {
        /**
         * Internal trace messages, not logged by default.
         */
        TRACE,
        /**
         * Debug level messages.
         */
        DEBUG,
        /**
         * General information messages.
         */
        INFO,
        /**
         * Warning messages indicating potential problems.
         */
        WARN,
        /**
         * Error messages reporting a failure condition.
         */
        ERROR
    }

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Internal use only.
     */
    private final Instant timestamp;
    /**
     * Internal use only.
     */
    private final LOG_LEVEL level;
    /**
     * Internal use only.
     */
    private final String message;

    /**
     * Create a new log entry.
     * @param timestamp The timestamp of the event that triggered this message.
     * @param level The log level of the message.
     * @param messages The messages to log. The first argument can use the "{@code {}}" placeholder to format the succeeding arguments.
     */
    public REPLLogEntry(Instant timestamp, LOG_LEVEL level, Object... messages) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = interpolateMessage(messages);
        validate();
    }

    /**
     * Create a new log entry with the timestamp set to now.
     * @param level The log level of the message.
     * @param messages The messages to log. The first argument can use the "{@code {}}" placeholder to format the succeeding arguments.
     */
    public REPLLogEntry(LOG_LEVEL level, Object... messages) {
        this.timestamp = Instant.now();
        this.level = level;
        this.message = interpolateMessage(messages);
        validate();
    }

    /**
     * Create a new log entry with the timestamp set to now.
     * @param level The textual representation of the message's log level.
     * @param messages The messages to log. The first argument can use the "{@code {}}" placeholder to format the succeeding arguments.
     */
    public REPLLogEntry(String level, Object... messages) {
        this.timestamp = Instant.now();
        this.level = LOG_LEVEL.valueOf(level.toUpperCase(Locale.ROOT));
        this.message = interpolateMessage(messages);
        validate();
    }

    /**
     * Get the message's event timestamp.
     * @return The message's event timestamp.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Get the log level of the message.
     * @return The message's log level.
     */
    public LOG_LEVEL getLevel() {
        return level;
    }

    /**
     * Get the textual representation of the message's payload.
     * @return The message's payload.
     */
    public String getMessage() {
        return ((level.equals(LOG_LEVEL.TRACE) && TRACE_PREFIX != null) ? TRACE_PREFIX + " " : "") + message;
    }

    private static String interpolateMessage(Object[] messages) {
        if (messages == null) return "<NULL>";
        String s = messages.length == 0 ? "" : String.valueOf(messages[0]);

        int overflow = 1;
        for (int i = 1; i < messages.length; i++) {
            if (!s.contains("{}")) break;
            s = s.replaceFirst("\\{\\}", Matcher.quoteReplacement(String.valueOf(messages[i])));
            overflow++;
        }

        for (int i = overflow; i < messages.length; i++) {
            s = s + System.lineSeparator() + messages[i];
        }

        return s;
    }

    @Override
    public String toString() {
        return "[" + formatter.format(timestamp) + "] " + level + " " + getMessage();
    }

    protected String toTSV() {
        return timestamp + "\t" + level + ((level.equals(LOG_LEVEL.TRACE) && TRACE_PREFIX != null) ? " " + TRACE_PREFIX : "") + "\t" + StringEscapeUtils.escapeJavaScript(message) + System.lineSeparator();
    }

    private void validate() {
        if (timestamp == null) throw new LogEntryFormatException("missing timestamp");
        if (level == null) throw new LogEntryFormatException("missing level");
        if (message == null) throw new LogEntryFormatException("missing message");
    }

    protected static REPLLogEntry fromTSV(String tsv) {
        String[] s = tsv.split("\\t");
        if (s.length != 3) throw new LogEntryFormatException("invalid log line");
        return new REPLLogEntry(Instant.parse(s[0]), LOG_LEVEL.valueOf(s[1].toUpperCase(Locale.ROOT).split(" ")[0]), StringEscapeUtils.unescapeJavaScript(s[2]));
    }

    public static class LogEntryFormatException extends RuntimeException {
        public LogEntryFormatException(String message) {
            super(message);
        }

        public LogEntryFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
