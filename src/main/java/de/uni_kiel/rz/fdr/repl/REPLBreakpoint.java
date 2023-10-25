// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import org.codehaus.groovy.runtime.StackTraceUtils;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

/**
 * This class represents a single triggered Breakpoint instance in your code. It also provides static methods
 * to manage all current instances of triggered breakpoints.
 */
public class REPLBreakpoint {

    /**
     * More than this many simultaneously triggered breakpoint instances will be dropped.
     */
    public static int MAX_BREAKPOINTS = 20;

    /**
     * A request to eval a Groovy statement in the breakpoint's thread.
     * @param command The Groovy statement to eval
     * @param parameter An extra object that can be passed to the statement
     */
    public record Eval(String command, Object parameter){}

    /**
     * The result of an {@link Eval Eval} request.
     * @param result Your statement's result
     * @param exception Any exception that was thrown during eval
     */
    public record EvalResult(Object result, Throwable exception){}

    private static long count = 0;
    private static final LinkedHashMap<Long, REPLBreakpoint> breakpoints = new LinkedHashMap<>(MAX_BREAKPOINTS);
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private static final Lock r = lock.readLock();
    private static final Lock w = lock.writeLock();
    private static final LinkedHashMap<String, Pattern> disabledPatterns = new LinkedHashMap<>();
    private static boolean overflowWarning = false;

    /**
     * Pauses the current thread and triggers a breakpoint in the REPL.
     * @return An optional feedback value passed from the REPL
     */
    @SuppressWarnings("unused")
    public static Object replbreakpoint() {
        return replbreakpoint(null, null, null);
    }

    /**
     * Pauses the current thread and triggers a breakpoint in the REPL.
     * @param name An informative name for this instance
     * @return An optional feedback value passed from the REPL
     */
    @SuppressWarnings("unused")
    public static Object replbreakpoint(String name) {
        return replbreakpoint(name, null, null);
    }

    /**
     * Pauses the current thread and triggers a breakpoint in the REPL.
     * @param name An informative name for this instance
     * @param extra Any extra data you would like to make available in the REPL context
     * @return An optional feedback value passed from the REPL
     */
    @SuppressWarnings("unused")
    public static Object replbreakpoint(String name, Object extra) {
        return replbreakpoint(name, extra, null);
    }

    /**
     * Pauses the current thread and triggers a breakpoint in the REPL.
     * @param name An informative name for this instance
     * @param extra Any extra data you would like to make available in the REPL context
     * @param timeoutMillis Auto-continue the breakpoint after this many milliseonds
     * @return An optional feedback value passed from the REPL
     */
    @SuppressWarnings("unused")
    public static Object replbreakpoint(String name, Object extra, Long timeoutMillis) {
        REPLBreakpoint bp = new REPLBreakpoint(-1, name, extra, timeoutMillis);

        String signature = bp.getSignature();
        r.lock();
        try {
            for (Pattern p : disabledPatterns.values()) {
                if (p.matcher(signature).find()) return null;
            }
        } finally {
            r.unlock();
        }

        w.lock();
        try {
            if (breakpoints.size() >= MAX_BREAKPOINTS) {
                // overflow
                if (!overflowWarning) {
                    overflowWarning = true;
                    REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "REPL: More than {} concurrent breakpoints, silently dropping the rest from now on", MAX_BREAKPOINTS), REPLLog.EPHEMERAL_LOG_TARGETS);
                }
                return null;
            }
            bp.setKey(count++);
            breakpoints.put(bp.getKey(), bp);
        } finally {
            w.unlock();
        }

        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.DEBUG, "Breakpoint triggered: {}", bp), REPLLog.EPHEMERAL_LOG_TARGETS);
        Object r = bp.doWait();

        w.lock();
        try {
            breakpoints.remove(bp.getKey());
        } finally {
            w.unlock();
        }
        return r;
    }

    /**
     * Disables all future breakpoint instances matching this pattern.
     * @param filter The filter pattern in Java regular expression syntax.
     */
    @SuppressWarnings("unused")
    public static void disable(String filter) {
        if (filter == null || filter.isEmpty()) return;
        Pattern p = Pattern.compile(filter);

        w.lock();
        try {
            disabledPatterns.put(filter, p);
        } finally {
            w.unlock();
        }
    }

    /**
     * Re-enables a previously disabled breakpoint pattern.
     * @param filter The filter pattern in Java regular expression syntax.
     */
    @SuppressWarnings("unused")
    public static void enable(String filter) {
        if (filter == null || filter.isEmpty()) return;
        w.lock();
        try {
            disabledPatterns.remove(filter);
        } finally {
            w.unlock();
        }
    }

    /**
     * Resumes a breakpoint instance.
     * @param key The instance's key
     * @return Whether the breakpoint was resumed
     */
    @SuppressWarnings("unused")
    public static boolean resume(long key) {
        return resume(key, null);
    }

    /**
     * Resumes a breakpoint instance.
     * @param key The instance's key
     * @param feedback The feedback value that the associated {@code replbreakpoint()} call will return
     * @return Whether the breakpoint was resumed
     */
    @SuppressWarnings("unused")
    public static boolean resume(long key, Object feedback) {
        REPLBreakpoint bp;
        w.lock();
        try {
            bp = breakpoints.remove(key);
        } finally {
            w.unlock();
        }
        if (bp == null) return false;
        bp.resume(feedback);
        return true;
    }

    /**
     * Queues a Groovy statement for evaluation in the Thread of a triggered breakpoint
     * @param key The breakpoint instance's key
     * @param eval The Groovy statement to evaluate
     * @return Whether the statement was enqueued
     */
    @SuppressWarnings("unused")
    public static boolean eval(long key, Eval eval) {
        REPLBreakpoint bp;
        r.lock();
        try {
            bp = breakpoints.get(key);
        } finally {
            r.unlock();
        }
        if (bp == null) return false;
        bp.eval(eval);
        return true;
    }

    /**
     * Get all waiting breakpoints.
     * @return A list of all currently triggered breakpoint instances
     */
    @SuppressWarnings("unused")
    public static List<REPLBreakpoint> list() {
        r.lock();
        try {
            return new ArrayList<>(breakpoints.values());
        } finally {
            r.unlock();
        }
    }

    /**
     * Retrieve a single breakpoint instance by key.
     * @param key The instance's key
     * @return The breakpoint instance with the given key
     */
    @SuppressWarnings("unused")
    public static REPLBreakpoint get(long key) {
        r.lock();
        try {
            return breakpoints.get(key);
        } finally {
            r.unlock();
        }
    }

    /**
     * Get a list of all currently disabled breakpoint patterns
     * @return The list of all currently disabled breakpoint patterns
     */
    @SuppressWarnings("unused")
    public static List<String> getDisabledPatterns() {
        r.lock();
        try {
            return new ArrayList<>(disabledPatterns.keySet());
        } finally {
            r.unlock();
        }
    }

    private long key;
    private Object feedback = null;
    private StackTraceElement owner = null;
    private Eval eval = null;
    private EvalResult evalResult = null;
    private final String name;
    private final Object extra;
    private final Long timeoutMillis;
    private boolean triggered = false;


    private REPLBreakpoint(long key, String name, Object extra, Long timeoutMillis) {
        this.key = key;
        this.name = name;
        this.extra = extra;
        this.timeoutMillis = timeoutMillis;

        StackTraceElement[] stack = StackTraceUtils.sanitize(new Throwable()).getStackTrace();
        stack = Arrays.stream(stack).filter(x -> !x.getClassName().equals(this.getClass().getName())).toArray(StackTraceElement[]::new);
        if (stack.length >= 1) owner = stack[0];
    }

    private synchronized Object doWait() {
        if (triggered) throw new RuntimeException("REPLBreakpoint may only trigger once");
        triggered = true;

        do {
            if (eval != null) {
                evalResult = null;
                Object er = null;
                try {
                    er = groovy.util.Eval.xy(this, eval.parameter, eval.command);
                    evalResult = new EvalResult(er, null);
                } catch (Throwable e) {
                    evalResult = new EvalResult(er, e);
                } finally {
                    eval = null;
                }
            }

            try {
                if (timeoutMillis == null) wait();
                else wait(timeoutMillis);
            } catch (InterruptedException e) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "REPL: Breakpoint {} was interrupted", key));
                return null;
            }
        } while (eval != null);

        return feedback;
    }

    /**
     * Resumes this breakpoint.
     */
    @SuppressWarnings("unused")
    public synchronized void resume() {
        resume(null);
    }

    /**
     * Resumes this breakpoint, returning a result.
     * @param feedback The return value of the corresponding {@code replbreakpoint()} call.
     */
    @SuppressWarnings("unused")
    public synchronized void resume(Object feedback) {
        this.feedback = feedback;
        notify();
    }

    /**
     * Enqueues a request to evaluate a Groovy statement while the breakpoint is waiting
     * @param eval The Groovy statement to eval.
     */
    @SuppressWarnings("unused")
    public synchronized void eval(Eval eval) {
        this.eval = eval;
        notify();
    }

    /**
     * Get the last eval request's result.
     * @return The las eval request's result.
     */
    public EvalResult getEvalResult() {
        return evalResult;
    }

    /**
     * Get this breakpoint instance's key.
     * @return This breakpoint instance's key.
     */
    public long getKey() {
        return key;
    }

    private void setKey(long key) {
        this.key = key;
    }

    /**
     * Gets the last feedback value thaqt was passed from the REPL.
     * @return The last feedback value thaqt was passed from the REPL.
     */
    @SuppressWarnings("unused")
    public Object getFeedback() {
        return feedback;
    }

    /**
     * Get's the context in which this breakpoint was triggered.
     * @return The context in which this breakpoint was triggered
     */
    @SuppressWarnings("unused")
    public StackTraceElement getOwner() {
        return owner;
    }

    /**
     * Gets the name of this breakpoint.
     * @return The name of this breakpoint
     */
    public String getName() {
        return name;
    }

    /**
     * Gets any extra data that was passed to the REPL.
     * @return The extra data that was passed to the REPL.
     */
    @SuppressWarnings("unused")
    public Object getExtra() {
        return extra;
    }

    /**
     * Gets this breakpoint's timeout in milliseconds.
     * @return This breakpoint's timeout in milliseconds.
     */
    @SuppressWarnings("unused")
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * Get this instance's signature, against which the disable-patterns will be matched.
     * @return This instance's signature
     */
    public String getSignature() {
        return (owner != null ? owner.getClassName() + "::" + owner.getMethodName() : "[unknown]::[unknown]") + " - " + (name != null ? name : (owner != null ? owner.getLineNumber() : "[unknown]"));
    }

    @Override
    public String toString() {
        return String.format("%1$4s", key) + " " + getSignature() + (evalResult != null ? " *" : "");
    }

}
