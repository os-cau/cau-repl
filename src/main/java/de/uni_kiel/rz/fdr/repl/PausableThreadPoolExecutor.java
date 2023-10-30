// Original Author: Doug Lea, released to the Public Domain
// Further additions: Copyright (C) 2023 Ove Sörensen, licensed under the MIT license
// SPDX-License-Identifier: (CC-PDDC AND MIT)
// Source: ThreadPoolExecutor.java from the JDK 1.5, e.g. https://android.googlesource.com/platform/libcore/+/082d39a20d3844e02822d8c96a26230f6f4a0590/luni/src/main/java/java/util/concurrent/ThreadPoolExecutor.java

package de.uni_kiel.rz.fdr.repl;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static de.uni_kiel.rz.fdr.repl.REPLLog.TRACE;
import static de.uni_kiel.rz.fdr.repl.REPLLog.TRACE_JOBS;

class PausableThreadPoolExecutor extends ThreadPoolExecutor {
    private final AtomicInteger pausedThreadCount = new AtomicInteger();
    private boolean isPaused;
    private ReentrantLock pauseLock = new ReentrantLock();
    private Condition unpaused = pauseLock.newCondition();

    public PausableThreadPoolExecutor(int poolSize, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, String prefix) {
        super(poolSize, poolSize, 0, TimeUnit.SECONDS, workQueue, threadFactory != null ? threadFactory : new NamedThreadFactory(prefix));
    }

    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        pauseLock.lock();
        try {
            pausedThreadCount.incrementAndGet();
            while (isPaused) unpaused.await();
        } catch (InterruptedException ie) {
            t.interrupt();
        } finally {
            pausedThreadCount.decrementAndGet();
            pauseLock.unlock();
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        // don't invoke getPoolSize() during shutdown to not interfere
        if (!isShutdown() && getPoolSize() > getMaximumPoolSize()) {
            if (Thread.currentThread().getUncaughtExceptionHandler() == null || ! (Thread.currentThread().getUncaughtExceptionHandler() instanceof ReducePoolSizeExceptionHandler)) {
                Thread.currentThread().setUncaughtExceptionHandler(new ReducePoolSizeExceptionHandler(Thread.currentThread().getUncaughtExceptionHandler()));
            }
            throw new ReducePoolSizeException();
        }
    }

    public void pause() {
        pauseLock.lock();
        try {
            isPaused = true;
        } finally {
            pauseLock.unlock();
        }
    }

    public void resume() {
        pauseLock.lock();
        try {
            isPaused = false;
            unpaused.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    public int getWorkingThreadCount() { return Math.max(getActiveCount() - pausedThreadCount.get(), 0); }

    public void emptyQueue() {
        for (Runnable r : new ArrayList<>(getQueue())) remove(r);
    }

    private static class ReducePoolSizeExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Thread.UncaughtExceptionHandler delegate;
        private ReducePoolSizeExceptionHandler(Thread.UncaughtExceptionHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (e instanceof ReducePoolSizeException) {
                if (TRACE || TRACE_JOBS) REPLLog.trace("Terminated a worker thread to accommodate lowered pool size.");
                return;
            }
            if (delegate != null) delegate.uncaughtException(t, e);
        }
    }

    private static class ReducePoolSizeException extends RuntimeException {
        private ReducePoolSizeException () {
            super();
        }
    }

    private static class NamedThreadFactory implements ThreadFactory {
        private String prefix;
        private ThreadFactory proxy;

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix != null ? prefix : "";
            this.proxy = Executors.defaultThreadFactory();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = proxy.newThread(r);
            t.setName(prefix + t.getName());
            return t;
        }
    }

}
