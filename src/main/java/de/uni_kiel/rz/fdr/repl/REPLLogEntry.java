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

@SuppressWarnings("unused")
public class REPLLogEntry implements Serializable {

    public static String TRACE_PREFIX = "=====";

    @SuppressWarnings("unused")
    public enum LOG_LEVEL { TRACE, DEBUG, INFO, WARN, ERROR }

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Instant timestamp;
    private final LOG_LEVEL level;
    private final String message;

    public REPLLogEntry(Instant timestamp, LOG_LEVEL level, Object... messages) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = interpolateMessage(messages);
        validate();
    }

    public REPLLogEntry(LOG_LEVEL level, Object... messages) {
        this.timestamp = Instant.now();
        this.level = level;
        this.message = interpolateMessage(messages);
        validate();
    }

    public REPLLogEntry(String level, Object... messages) {
        this.timestamp = Instant.now();
        this.level = LOG_LEVEL.valueOf(level.toUpperCase(Locale.ROOT));
        this.message = interpolateMessage(messages);
        validate();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public LOG_LEVEL getLevel() {
        return level;
    }

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

    public String toTSV() {
        return timestamp + "\t" + level + ((level.equals(LOG_LEVEL.TRACE) && TRACE_PREFIX != null) ? " " + TRACE_PREFIX : "") + "\t" + StringEscapeUtils.escapeJavaScript(message) + System.lineSeparator();
    }

    private void validate() {
        if (timestamp == null) throw new RuntimeException("missing timestamp");
        if (level == null) throw new RuntimeException("missing level");
        if (message == null) throw new RuntimeException("missing message");
    }

    public static REPLLogEntry fromTSV(String tsv) {
        String[] s = tsv.split("\\t");
        if (s.length != 3) throw new RuntimeException("invalid log line");
        return new REPLLogEntry(Instant.parse(s[0]), LOG_LEVEL.valueOf(s[1].toUpperCase(Locale.ROOT).split(" ")[0]), StringEscapeUtils.unescapeJavaScript(s[2]));
    }

}
