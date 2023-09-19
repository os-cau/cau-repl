// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import org.codehaus.groovy.runtime.StackTraceUtils;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

public class REPLBreakpoint {

    public static int MAX_BREAKPOINTS = 20;

    public record Eval(String command, Object parameter){}
    public record EvalResult(Object result, Throwable exception){}

    private static long count = 0;
    private static final LinkedHashMap<Long, REPLBreakpoint> breakpoints = new LinkedHashMap<>(MAX_BREAKPOINTS);
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private static final Lock r = lock.readLock();
    private static final Lock w = lock.writeLock();
    private static final LinkedHashMap<String, Pattern> disabledPatterns = new LinkedHashMap<>();
    private static boolean overflowWarning = false;

    @SuppressWarnings("unused")
    public static Object replbreakpoint() {
        return replbreakpoint(null, null, null);
    }

    @SuppressWarnings("unused")
    public static Object replbreakpoint(String name) {
        return replbreakpoint(name, null, null);
    }

    @SuppressWarnings("unused")
    public static Object replbreakpoint(String name, Object extra) {
        return replbreakpoint(name, extra, null);
    }

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

    @SuppressWarnings("unused")
    public static void disable(String filter) {
        Pattern p = Pattern.compile(filter);

        w.lock();
        try {
            disabledPatterns.put(filter, p);
        } finally {
            w.unlock();
        }
    }

    @SuppressWarnings("unused")
    public static void enable(String filter) {
        w.lock();
        try {
            disabledPatterns.remove(filter);
        } finally {
            w.unlock();
        }
    }

    @SuppressWarnings("unused")
    public static boolean resume(long key) {
        return resume(key, null);
    }

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

    @SuppressWarnings("unused")
    public static List<REPLBreakpoint> list() {
        r.lock();
        try {
            return new ArrayList<>(breakpoints.values());
        } finally {
            r.unlock();
        }
    }

    @SuppressWarnings("unused")
    public static REPLBreakpoint get(long key) {
        r.lock();
        try {
            return breakpoints.get(key);
        } finally {
            r.unlock();
        }
    }

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

    @SuppressWarnings("unused")
    public synchronized void resume() {
        resume(null);
    }

    @SuppressWarnings("unused")
    public synchronized void resume(Object feedback) {
        this.feedback = feedback;
        notify();
    }

    @SuppressWarnings("unused")
    public synchronized void eval(Eval eval) {
        this.eval = eval;
        notify();
    }

    public EvalResult getEvalResult() {
        return evalResult;
    }

    public long getKey() {
        return key;
    }

    private void setKey(long key) {
        this.key = key;
    }

    @SuppressWarnings("unused")
    public Object getFeedback() {
        return feedback;
    }

    @SuppressWarnings("unused")
    public StackTraceElement getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    @SuppressWarnings("unused")
    public Object getExtra() {
        return extra;
    }

    @SuppressWarnings("unused")
    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    public String getSignature() {
        return (owner != null ? owner.getClassName() + "::" + owner.getMethodName() : "[unknown]::[unknown]") + " - " + (name != null ? name : (owner != null ? owner.getLineNumber() : "[unknown]"));
    }
    @Override
    public String toString() {
        return String.format("%1$4s", key) + " " + getSignature() + (evalResult != null ? " *" : "");
    }

}
