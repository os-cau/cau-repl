// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.groovy.GroovyShellCommand;
import groovy.lang.Binding;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * This class provides the means for logging messages to various targets. If Log4J is available in the current VM,
 * it can also be used. cau-repl uses this class internally for its log messages, and you are encouraged to use it
 * from your Groovy code as well.
 */
public class REPLLog {

    /**
     * Controls whether very fine-grained internal trace messages from cau-repl are logged. Do not enable in production.
     */
    public static boolean TRACE = false;
    /**
     * Controls whether very fine-grained internal trace messages from cau-repl related to compilation are logged.
     * Enabling {@link REPLLog#TRACE TRACE} overrides this setting. Do not enable in production.
     */
    public static boolean TRACE_COMPILE = false;
    /**
     * Controls whether very fine-grained internal trace messages from cau-repl related to dynamization are logged.
     * Enabling {@link REPLLog#TRACE TRACE} overrides this setting. Do not enable in production.
     */
    public static boolean TRACE_DYNAMIZE = false;
    /**
     * Controls whether very fine-grained internal trace messages from cau-repl related to jobs are logged.
     * Enabling {@link REPLLog#TRACE TRACE} overrides this setting. Do not enable in production.
     */
    public static boolean TRACE_JOBS = false;

    /**
     * The different targets that log messages can be written to.
     */
    public enum LOG_TARGETS {
        /**
         * The JVM's standard error output.
         */
        STDERR,
        /**
         * The JVM's standard output.
         */
        STDOUT,
        /**
         * Log4J, but only if it is available in the current JVM.
         */
        LOG4J,
        /**
         * Log4J, with a fallback the JVM's standard error output if the former is not available.
         */
        STDERR_OR_LOG4J,
        /**
         * The SSH consoles of all currently active shells.
         */
        REPL_ALL_SHELLS,
        /**
         * The repl.log file in cau-repl's work directory.
         */
        REPL_FILE
    }

    /**
     * The default log targets for your messages: Log4J with a fallback to the standard error output and additionally
     * cau-repl's own log file.
     */
    public static final Set<LOG_TARGETS> DEFAULT_LOG_TARGETS = Set.of(LOG_TARGETS.STDERR_OR_LOG4J, LOG_TARGETS.REPL_FILE);
    /**
     * The log targets for internal log messages originating from cau-repl itself.
     */
    public static final Set<LOG_TARGETS> INTERNAL_LOG_TARGETS = System.getProperty("CAU.REPL.Log.Internal", "").equalsIgnoreCase("file") ? Set.of(LOG_TARGETS.REPL_FILE) : (System.getProperty("CAU.REPL.Log.Internal", "").equalsIgnoreCase("stderr") ? Set.of(LOG_TARGETS.STDERR) : Set.of(LOG_TARGETS.STDERR_OR_LOG4J));
    protected static final Set<LOG_TARGETS> TRACE_LOG_TARGETS = System.getProperty("CAU.REPL.Log.Internal", "").equalsIgnoreCase("stderr") ? Set.of(LOG_TARGETS.STDERR, LOG_TARGETS.REPL_FILE) : Set.of(LOG_TARGETS.REPL_FILE);
    protected static final Set<LOG_TARGETS> EPHEMERAL_LOG_TARGETS = Set.of(LOG_TARGETS.STDERR, LOG_TARGETS.REPL_ALL_SHELLS);

    @SuppressWarnings("FieldCanBeLocal")
    private static Class<?> L4J_LOGMANAGER_CLASS = null;
    private static Class<?> L4J_SIMPLEMESSAGE_CLASS = null;
    private static Object L4J_LOGGER = null;
    private static Method L4J_DEBUG = null;
    private static Method L4J_INFO = null;
    private static Method L4J_WARN = null;
    private static Method L4J_ERROR = null;

    private static int KEEP_LOGS = 10;
    private static final String DEFAULT_LOG_PREFIX = "repl";
    private static final String DEFAULT_LOG_SUFFIX = ".log";

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSSSSSSSS").withZone(ZoneId.systemDefault());

    static {
        initializeLibs();
    }

    /**
     * Log a new message with log level {@code TRACE} to the trace log targets.
     * @param args The log messages. You an use the "{@code {}}" placeholder in the first argument to format the succeeding arguments.
     * @return The log entry that was sent to the targets.
     */
    @SuppressWarnings("unused")
    public static REPLLogEntry trace(Object... args) {
        REPLLogEntry e = new REPLLogEntry(REPLLogEntry.LOG_LEVEL.TRACE, args);
        log(e, TRACE_LOG_TARGETS);
        return e;
    }

    /**
     * Log a new message with log level {@code DEBUG} to the default log targets.
     * @param args The log messages. You an use the "{@code {}}" placeholder in the first argument to format the succeeding arguments.
     * @return The log entry that was sent to the targets.
     */
    @SuppressWarnings("unused")
    public static REPLLogEntry debug(Object... args) {
        REPLLogEntry e = new REPLLogEntry(REPLLogEntry.LOG_LEVEL.DEBUG, args);
        log(e, DEFAULT_LOG_TARGETS);
        return e;
    }

    /**
     * Log a new message with log level {@code INFO} to the default log targets.
     * @param args The log messages. You an use the "{@code {}}" placeholder in the first argument to format the succeeding arguments.
     * @return The log entry that was sent to the targets.
     */
    @SuppressWarnings("unused")
    public static REPLLogEntry info(Object... args) {
        REPLLogEntry e = new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, args);
        log(e, DEFAULT_LOG_TARGETS);
        return e;
    }

    /**
     * Log a new message with log level {@code WARN} to the default log targets.
     * @param args The log messages. You an use the "{@code {}}" placeholder in the first argument to format the succeeding arguments.
     * @return The log entry that was sent to the targets.
     */
    @SuppressWarnings("unused")
    public static REPLLogEntry warn(Object... args) {
        REPLLogEntry e = new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, args);
        log(e, DEFAULT_LOG_TARGETS);
        return e;
    }

    /**
     * Log a new message with log level {@code ERROR} to the default log targets.
     * @param args The log messages. You an use the "{@code {}}" placeholder in the first argument to format the succeeding arguments.
     * @return The log entry that was sent to the targets.
     */
    public static REPLLogEntry error(Object... args) {
        REPLLogEntry e = new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, args);
        log(e, DEFAULT_LOG_TARGETS);
        return e;
    }

    /**
     * Log a new message with log level {@code INFO} to the default log targets.
     * @param args The log messages. You an use the "{@code {}}" placeholder in the first argument to format the succeeding arguments.
     * @return The log entry that was sent to the targets.
     */
    @SuppressWarnings("unused")
    public static REPLLogEntry log(Object... args) {
        return info(args);
    }

    /**
     * Log a given message to the default log targets.
     * @param entry The message to log.
     */
    @SuppressWarnings("unused")
    public static void log(REPLLogEntry entry) {
        log(entry, DEFAULT_LOG_TARGETS);
    }

    /**
     * Log a given message.
     * @param entry The message to log.
     * @param targets The log targets to write to.
     * @param streams Additional Print Streams to write to.
     */
    public static void log(REPLLogEntry entry, Set<LOG_TARGETS> targets, PrintStream... streams) {
        boolean l4jSuccess = false;
        if ((targets.contains(LOG_TARGETS.LOG4J) || targets.contains(LOG_TARGETS.STDERR_OR_LOG4J)) && L4J_LOGGER != null) {
            try {
                Object l4jmsg = L4J_SIMPLEMESSAGE_CLASS.getDeclaredConstructor(String.class).newInstance(entry.getMessage());
                switch (entry.getLevel()) {
                    case TRACE, DEBUG -> L4J_DEBUG.invoke(L4J_LOGGER, l4jmsg);
                    case INFO -> L4J_INFO.invoke(L4J_LOGGER, l4jmsg);
                    case WARN -> L4J_WARN.invoke(L4J_LOGGER, l4jmsg);
                    case ERROR -> L4J_ERROR.invoke(L4J_LOGGER, l4jmsg);
                    default -> throw new RuntimeException("Unknown log level");
                }
                l4jSuccess = true;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | InstantiationException ignore) {}
        }

        // PrintStreams are thread-safe...

        if (targets.contains(LOG_TARGETS.STDERR) || (targets.contains(LOG_TARGETS.STDERR_OR_LOG4J) && !l4jSuccess)) System.err.println(entry);

        if (targets.contains(LOG_TARGETS.STDOUT)) System.out.println(entry);

        if (targets.contains(LOG_TARGETS.REPL_ALL_SHELLS)) {
            for (GroovyShellCommand shell : GroovyShellCommand.activeShells.keySet()) {
                try {
                    Binding binding = GroovyShellCommand.activeShells.get(shell);
                    if (binding == null) continue;
                    PrintStream shellPrint = (PrintStream) binding.getVariable("err");
                    if (shellPrint == null) continue;
                    shellPrint.println(entry);
                    if (shellPrint.checkError()) {
                        GroovyShellCommand.activeShells.remove(shell);
                    }
                } catch (Exception ignore) {}
            }
        }

        if (streams != null) {
            for (PrintStream pr : streams) {
                pr.println(entry);
            }
        }

        // XXX keep this as the final try -> it can throw
        if (targets.contains(LOG_TARGETS.REPL_FILE)) {
            // Files.writeString is not thread-safe
            synchronized (REPLLog.class) {
                try {
                    Files.writeString(logFile(null).toPath(), entry.toTSV(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Gets all log entries from the repl's internal log file (which is rotated when the JVM starts). They will be read
     * from disk, so this call might be slow if the log file is big.
     * @return A list of all log entries from the repl's internal log file.
     */
    public static List<REPLLogEntry> getLog() {
        synchronized (REPLLog.class) {
            try (Stream<String> lines = Files.lines(logFile(null).toPath())) {
                return lines
                        .map(REPLLogEntry::fromTSV)
                        .toList();
            } catch (IOException e) {
                return null;
            }
        }
    }

    protected static Instant getFirstTimestamp() {
        synchronized (REPLLog.class) {
            try (Stream<String> lines = Files.lines(logFile(null).toPath())) {
                return lines
                        .limit(1)
                        .map(tsv -> REPLLogEntry.fromTSV(tsv).getTimestamp())
                        .findFirst()
                        .orElse(null);
            } catch (IOException e) {
                return null;
            }
        }
    }

    protected static void rollOver() throws IOException {
        synchronized (REPLLog.class) {
            Instant timestamp = getFirstTimestamp();
            if (timestamp != null) Files.move(logFile(null).toPath(), logFile(formatter.format(timestamp)).toPath());
            Pattern pattern = Pattern.compile("^" + Pattern.quote(DEFAULT_LOG_PREFIX) + "\\..*" + Pattern.quote(DEFAULT_LOG_SUFFIX));
            int keep = KEEP_LOGS;
            for (File f : Arrays.stream(Objects.requireNonNull(REPL.getWorkDir().listFiles((File x) -> x.isFile() && pattern.matcher(x.getName()).matches()))).sorted(Comparator.comparing(File::getAbsolutePath).reversed()).toList()) {
                if (keep-- <= 0) f.delete();
            }
        }
    }

    private static File logFile(String tag) {
        return new File(REPL.getWorkDir(), DEFAULT_LOG_PREFIX + (tag == null ? "" : ("." + tag)) + DEFAULT_LOG_SUFFIX);
    }

    /**
     * Try to (re-)initialize optional external libraries. At the moment, this pertains only to Log4J. Invoking this
     * method will check whether it is installed and make it available as a logging target.
     */
    public static synchronized void initializeLibs() {
        try {
            if (L4J_LOGGER != null) return;
            L4J_LOGMANAGER_CLASS = Class.forName("org.apache.logging.log4j.LogManager");
            L4J_SIMPLEMESSAGE_CLASS = Class.forName("org.apache.logging.log4j.message.SimpleMessage");
            L4J_LOGGER = L4J_LOGMANAGER_CLASS.getMethod("getLogger", Class.class).invoke(null, REPLLog.class);
            L4J_DEBUG = L4J_LOGGER.getClass().getMethod("debug", Class.forName("org.apache.logging.log4j.message.Message"));
            L4J_INFO = L4J_LOGGER.getClass().getMethod("info", Class.forName("org.apache.logging.log4j.message.Message"));
            L4J_WARN = L4J_LOGGER.getClass().getMethod("warn", Class.forName("org.apache.logging.log4j.message.Message"));
            L4J_ERROR = L4J_LOGGER.getClass().getMethod("error", Class.forName("org.apache.logging.log4j.message.Message"));
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            L4J_LOGGER = null;
        }
    }
}
