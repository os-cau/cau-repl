// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static de.uni_kiel.rz.fdr.repl.REPLLog.*;

/**
 * A Callback that you can use for your jobs; it automatically adjusts its concurrency to tune it for maximum throughput.
 */
@SuppressWarnings("unused")
public class REPLJobCallbackAutoTune implements Consumer<REPLJob.JobEvent> {

    private record HeapEntry (int concurrency, double amortizedDuration) implements Comparable<HeapEntry> {
        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (this == obj) return true;
            if (obj instanceof HeapEntry other) {
                return this.concurrency == other.concurrency();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(concurrency);
        }

        @Override
        public int compareTo(HeapEntry o) {
            return Double.compare(this.amortizedDuration, o.amortizedDuration);
        }
    }

    private static class DurationHistory {
        private long sum = 0;
        private final LinkedList<Long> list = new LinkedList<>();

        public DurationHistory() {
            super();
        }

        public void addAndEvict (long l, int capacity) {
            evict(capacity - 1);
            list.addLast(l);
            sum += l;
        }

        public long getSumMicros() {
            return sum;
        }

        public Double getAvgMicros() {
            if (list.isEmpty()) return null;
            return (double) sum / list.size();
        }


        public Double getAvgSeconds() {
            if (list.isEmpty()) return null;
            //noinspection DataFlowIssue
            return getAvgMicros() / (1000 * 1000);
        }

        public int size() {
            return list.size();
        }

        public void clear() {
            list.clear();
            sum = 0;
        }

        public void evict(int keep) {
            if (keep >= 0) while (list.size() > keep) {
                sum -= list.removeFirst();
            }
        }

    }

    private int minConcurrency;
    private int maxConcurrency;
    private Integer awaitMeasure = null;
    private Integer toMeasure = null;
    private Integer measureSampleSize = null;
    private Integer previousConcurrency;
    private Integer knownOptimumConcurrency = null;
    private Double knownOptimumDuration = null;
    private Integer concurrency;
    private Long concurrencyStableSince;
    private boolean lowerLimitWarning = false;
    private boolean upperLimitWarning = false;
    private boolean forcePeriodicRescan = false;
    private final PriorityQueue<HeapEntry> durationHeap = new PriorityQueue<>();
    private final LinkedList<Integer> toMeasureQueue = new LinkedList<>();
    private final HashMap<Integer, DurationHistory> durationHistories = new HashMap<>();


    /**
     * Create a new AutoTune job callback. Each instance should only be used once, with a single job.
     * @param minConcurrency The minimum concurrency level this callback will ever set.
     * @param maxConcurrency The maximum concurrency level this callback will ever set.
     */
    public REPLJobCallbackAutoTune(int minConcurrency, int maxConcurrency) {
        if (minConcurrency < 1 || minConcurrency > maxConcurrency) throw new IllegalArgumentException("[" + minConcurrency + ", " + maxConcurrency + "] is not a valid concurrency interval");
        this.minConcurrency = minConcurrency;
        this.maxConcurrency = maxConcurrency;
    }

    /**
     * For internal use only.
     * @param ev A new job event.
     */
    @Override
    public void accept(REPLJob.JobEvent ev) {
        if (ev.eventType() != REPLJob.JobEventType.INPUT_SUCCESS && ev.eventType() != REPLJob.JobEventType.INPUT_ERROR) return;
        synchronized (this) {
            // initialize
            REPLJob job = ev.job();
            // one-shot jobs can't be tuned
            if (job.inputs == null || job.inputs.length < 2) return;

            if (concurrency == null || !(concurrency.equals(job.getConcurrency()))) {
                if (concurrency != null) {
                    job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "Job {}: the concurrency level was modified externally, this may lead to unpredictable AutoTune results", job.key), INTERNAL_LOG_TARGETS);
                    previousConcurrency = concurrency;
                    concurrency = job.getConcurrency();
                    awaitMeasure = previousConcurrency;
                }  else {
                    concurrency = job.getConcurrency();
                    previousConcurrency = concurrency;
                    awaitMeasure = 0;
                }
                toMeasure = concurrency;
                measureSampleSize = measureSampleSize(toMeasure, toMeasure, knownOptimumDuration);
                knownOptimumConcurrency = null;
                concurrencyStableSince = System.nanoTime();
                toMeasureQueue.addAll(concurrenciesToMeasure(toMeasure, 3, false, false));
            }

            // still waiting for pipeline flush before measuring
            if (awaitMeasure > 0) {
                awaitMeasure--;
                return;
            }

            // process result
            REPLJob.InputResult result = job.results[ev.inputIndex()];
            if (result == null) {
                job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "Job {}: REPLJobCallbackAutoTune got null result #{}", job.key, ev.inputIndex()), INTERNAL_LOG_TARGETS);
                return;
            }
            long durationMicros = result.epochMicrosTo() - result.epochMicrosFrom();
            if (durationMicros == 0) durationMicros = 1;
            if (!durationHistories.containsKey(concurrency)) durationHistories.put(concurrency, new DurationHistory());
            DurationHistory durationHistory = durationHistories.get(concurrency);
            durationHistory.addAndEvict(durationMicros, durationHistoryLimit(concurrency, job.inputs.length, durationHistory.getAvgMicros()));

            if (toMeasure > 0) {
                // a running measurement
                if (--toMeasure > 0) return;
                durationHistory.evict(measureSampleSize);
                if (TRACE || TRACE_JOBS) {
                    //noinspection DataFlowIssue
                    job.trace("auto tune measurement completed at #{}: concurrency {} -> {}us / {}us amortized", ev.inputIndex(), concurrency, Math.round(durationHistory.getAvgMicros()), Math.round((durationHistory.getAvgMicros() / concurrency)));
                }
                //noinspection DataFlowIssue
                updateDurationHeap(concurrency, durationHistory.getAvgMicros() / concurrency);
            } else if (!toMeasureQueue.isEmpty()) {
                // set up a new measurement
                toMeasure = toMeasureQueue.pop();
                toMeasureQueue.removeAll(Set.of(toMeasure));
                measureSampleSize = measureSampleSize(toMeasure, job.inputs.length, knownOptimumDuration);
                if (TRACE || TRACE_JOBS) job.trace("starting new auto tune measurement of concurrency {} at #{}", toMeasure, ev.inputIndex());
                previousConcurrency = concurrency;
                awaitMeasure = previousConcurrency;
                job.setConcurrency(toMeasure);
                concurrency = toMeasure;
                concurrencyStableSince = System.nanoTime();
            } else {
                // normal operation
                if (isHeapUpdateDue(concurrencyStableSince, durationHistory.getAvgMicros())) {
                    //noinspection DataFlowIssue
                    updateDurationHeap(concurrency, durationHistory.getAvgMicros() / concurrency);
                }
                HeapEntry best;
                // housekeeping
                for (best = durationHeap.peek(); best != null && (best.concurrency < minConcurrency || best.concurrency > maxConcurrency); best = durationHeap.peek()) {
                    // remove concurrencies outside the limit
                    if (TRACE || TRACE_JOBS) job.trace("auto tune optimum concurrency would be outside the limit: {}", best.concurrency);
                    durationHeap.remove();
                }
                if (best == null) {
                    // no optimum within limits: we need to measure
                    if (TRACE || TRACE_JOBS) job.trace("no auto tune optimum within the limit, starting new measurement");
                    toMeasureQueue.addAll(concurrenciesToMeasure((minConcurrency + maxConcurrency) / 2, scanInterval((minConcurrency + maxConcurrency) / 2, job.inputs.length, knownOptimumDuration), true, false));
                    return;
                }
                if (knownOptimumConcurrency == null || best.concurrency != knownOptimumConcurrency) {
                    //noinspection DataFlowIssue
                    job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.DEBUG, "Job {}: Auto Tune optimum concurrency: {} -> {}, {} inputs/minute", job.key, knownOptimumConcurrency != null ? knownOptimumConcurrency : "initial", best.concurrency, String.format("%.2f", (concurrency * 1000L * 1000L * 60L) / durationHistory.getAvgMicros())), INTERNAL_LOG_TARGETS);
                    knownOptimumConcurrency = best.concurrency();
                    knownOptimumDuration = durationHistories.get(knownOptimumConcurrency).getAvgMicros();
                    if (!lowerLimitWarning && minConcurrency != maxConcurrency && best.concurrency == minConcurrency) {
                        lowerLimitWarning = true;
                        job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "Job {}: Auto Tune optimum concurrency {} hit your lower limit. You might want to decrease it further.", job.key, best.concurrency), INTERNAL_LOG_TARGETS);
                    }
                    if (!upperLimitWarning && minConcurrency != maxConcurrency && best.concurrency == maxConcurrency) {
                        upperLimitWarning = true;
                        job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "Job {}: Auto Tune optimum concurrency {} hit your upper limit. You might want to increase it further.", job.key, best.concurrency), INTERNAL_LOG_TARGETS);
                    }
                }
                // no change
                if (best.concurrency == concurrency) {
                    //noinspection DataFlowIssue
                    if (isPeriodicRescanDue(concurrencyStableSince, job.inputs.length, durationHistory.getAvgMicros() / concurrency)) {
                        if (TRACE || TRACE_JOBS) job.trace("starting a periodic auto tune rescan");
                        toMeasureQueue.addAll(concurrenciesToMeasure(concurrency, 1, false, true));
                    }
                    return;
                }
                // we have a new best concurrency
                List<Integer> neighborsToMeasure = concurrenciesToMeasure(best.concurrency, scanInterval(best.concurrency, job.inputs.length, durationHistories.get(best.concurrency).getAvgMicros()), false, false);
                if (neighborsToMeasure.isEmpty()) {
                    // no neighbors to measure -> switch to new optimum
                    //noinspection DataFlowIssue
                    updateDurationHeap(concurrency, durationHistory.getAvgMicros() / concurrency);
                    job.setConcurrency(best.concurrency);
                    previousConcurrency = concurrency;
                    awaitMeasure = previousConcurrency;
                    concurrency = best.concurrency;
                    concurrencyStableSince = System.nanoTime();
                } else {
                    // let's measure some neighbors first
                    toMeasureQueue.addAll(neighborsToMeasure);
                }
            }
        }
    }

    private synchronized void updateDurationHeap(int concurrency, double amortizedDuration) {
        HeapEntry x = new HeapEntry(concurrency, amortizedDuration);
        durationHeap.remove(x);
        durationHeap.add(x);
    }

    private synchronized int measureSampleSize(int concurrency, int totalInputs, Double duration) {
        if (duration == null || duration > 10 * 1000L * 1000L) return concurrency * 4; // 10s per generation -> short sample
        return concurrency * 8;
    }

    private synchronized int durationHistoryLimit(int concurrency, int totalInputs, Double duration) {
        return concurrency * 50; // 50 generations
    }

    private synchronized int scanInterval(int concurrency, int totalInputs, Double duration) {
        if (duration == null || duration > 10 * 1000L * 1000L) return 3; // 10s per generation -> short sample
        return 5;
    }

    private synchronized boolean isPeriodicRescanDue(long stableSince, int totalInputs, Double amortizedDuration) {
        if (amortizedDuration == null) return false;
        if (forcePeriodicRescan) {
            forcePeriodicRescan = false;
            return true;
        }
        long totalNanos = TimeUnit.MICROSECONDS.toNanos(Math.round(amortizedDuration * totalInputs));
        return System.nanoTime() - stableSince > Math.max(totalNanos / 100, 60L * 1000L * 1000L * 1000L); // every 1%, but never more than once a minute
    }

    private synchronized boolean isHeapUpdateDue(long stableSince, Double duration) {
        return System.nanoTime() - stableSince > Math.min(Math.max(duration * 1000L * 2L, 10L * 1000L * 1000L * 1000L), 300L * 1000L * 1000L * 1000L); // every 2 generations, but never more than once every 10 seconds and never less than once every 5 minutes
    }

    private synchronized List<Integer> concurrenciesToMeasure(int center, int interval, boolean centerIncluded, boolean force) {
        List<Integer> result = new ArrayList<>();
        for (int i = Math.min(center + interval, maxConcurrency); i >= Math.max(center - interval, minConcurrency); i--) {
            if (i == center && !centerIncluded) continue;
            if (!force && durationHistories.containsKey(i)) continue;
            result.add(i);
        }
        return result;
    }

    /**
     * Get the current minimum concurrency limit.
     * @return The current minimum concurrency limit.
     */
    public synchronized int getMinConcurrency() {
        return minConcurrency;
    }

    /**
     * Set a new minimum concurrency limit. Must be bigger than 0 and can not be greater then the maximum concurrency limit.
     * You can safely invoke this method while the associated job is active.
     * @param minConcurrency The new minimum concurrency limit.
     */
    public synchronized void setMinConcurrency(int minConcurrency) {
        if (minConcurrency < 1 || minConcurrency > maxConcurrency) throw new IllegalArgumentException("[" + minConcurrency + ", " + maxConcurrency + "] is not a valid concurrency interval");
        this.minConcurrency = minConcurrency;
        lowerLimitWarning = false;
        forcePeriodicRescan = true;
    }

    /**
     * Get the current maximum concurrency limit.
     * @return The current maximum concurrency limit.
     */
    public synchronized int getMaxConcurrency() {
        return maxConcurrency;
    }

    /**
     * Set a new maximum concurrency limit. Must not be smaller than the minimum concurrency limit.
     * You can safely invoke this method while the associated job is active.
     * @param maxConcurrency The new maximum concurrency limit.
     */
    public synchronized void setMaxConcurrency(int maxConcurrency) {
        if (minConcurrency > maxConcurrency) throw new IllegalArgumentException("[" + minConcurrency + ", " + maxConcurrency + "] is not a valid concurrency interval");
        this.maxConcurrency = maxConcurrency;
        upperLimitWarning = false;
        forcePeriodicRescan = true;
    }
}

