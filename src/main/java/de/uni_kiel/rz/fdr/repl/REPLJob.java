// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.mycore.REPLJobProcessableProxy;
import groovy.lang.Closure;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static de.uni_kiel.rz.fdr.repl.REPLLog.*;


@SuppressWarnings("unused")
public class REPLJob implements Serializable {
    public record InputResult(String key, int index, long epochMicrosFrom, long epochMicrosTo, Serializable result, Exception error) implements Serializable {}

    public enum JobState {
        INTERNAL_ERROR, PAUSING, PAUSED, CANCELLING, CANCELLED, RUNNING, COMPLETED_SUCCESSFULLY, NOT_YET_STARTED, COMPLETED_WITH_ERRORS;
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replaceAll("_", " ");
        }
    }

    public record JobProgress(JobState state, Integer nextInput, int totalInputs, int remainingInputs, int success, int skippedSuccess, int errors, int skippedErrors, int percentDone, ZonedDateTime pausedSince, ZonedDateTime cancelledSince, ZonedDateTime startTimestamp, ZonedDateTime doneTimestamp, ZonedDateTime eta, Long etaSeconds, int activeThreads, Future<JobProgress> future) {
        private static boolean isActive(ZonedDateTime startTimestamp, ZonedDateTime doneTimestamp) {
            return JobProgress.isActive(startTimestamp != null ? startTimestamp.toInstant() : null, doneTimestamp != null ? doneTimestamp.toInstant(): null);
        }

        private static boolean isActive(Instant startTimestamp, Instant doneTimestamp) {
            return doneTimestamp == null && startTimestamp != null;
        }

        private static boolean isComplete(int remainingInputs) {
            return remainingInputs == 0;
        }

        private static boolean isSuccess(int remainingInputs, int errors, int skippedErrors, int success, int skippedSuccess, int totalInputs) {
            return remainingInputs == 0 && errors == 0 && skippedErrors == 0 && (success + skippedSuccess) == totalInputs;
        }

        public boolean isActive() {
            return JobProgress.isActive(startTimestamp, doneTimestamp);
        }

        public boolean isComplete() {
            return JobProgress.isComplete(remainingInputs);
        }

        public boolean isSuccess() {
            return JobProgress.isSuccess(remainingInputs, errors, skippedErrors, success, skippedSuccess, totalInputs);
        }

        public String etaText() {
            if (etaSeconds == null) return null;
            if (etaSeconds <= 60) return etaSeconds + "s";
            String m = (new BigDecimal(etaSeconds % (60 * 60)).divide(new BigDecimal(60), RoundingMode.UP)) + "m";
            if (etaSeconds < (60 * 60) - 59) return m;
            int carry;
            if (m.equals("60m")) {
                carry = 1;
                m = "0m";
            } else {
                carry = 0;
            }
            String h = ((etaSeconds / (60 * 60)) + carry) + "h";
            return h + " " + m;
        }
    }

    public enum JobEventType { JOB_START, JOB_DONE_SUCCESS, JOB_DONE_CANCELLED, JOB_DONE_INTERNALERROR, JOB_PAUSED, JOB_UNPAUSED, JOB_PAUSE_REQUESTED, JOB_CANCEL_REQUESTED, INPUT_SUCCESS, INPUT_SKIPPED, INPUT_ERROR }
    public record JobEvent(REPLJob job, Instant timestamp, JobEventType eventType, Integer inputIndex) {}

    public static final String STATE_FILE_PREFIX = "job";
    public static final String STATE_FILE_SUFFIX = "state";
    
    public static final Consumer<JobEvent> CALLBACK_LOG_TO_SHELL = evt -> {
        switch (evt.eventType) {
            case INPUT_SKIPPED, INPUT_SUCCESS -> {
                if (evt.job.inputs == null) break;
                JobProgress p = evt.job.getProgress();
                Integer per = null;
                if (evt.job.inputs != null && p.etaSeconds != null) {
                    if (evt.job.inputs.length <= 100) per = 1;
                    else if (evt.job.inputs.length <= 1000) per = 10;
                    else per = evt.job.inputs.length / 100; // "/ 100" -> one output per 1%
                }
                if (per != null && evt.job.remainingInputs % per == 0) {
                    evt.job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "Job {}: {}% done, eta {}{}", evt.job.key, p.percentDone, p.etaText() != null ? p.etaText() : "?", p.errors > 0 ? ", " + p.errors + " errors" : ""), Set.of(LOG_TARGETS.REPL_ALL_SHELLS));
                }
            }
            case INPUT_ERROR, JOB_DONE_CANCELLED, JOB_DONE_INTERNALERROR -> evt.job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "Job {}: {}", evt.job.key, evt), Set.of(LOG_TARGETS.REPL_ALL_SHELLS));
            default -> evt.job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "Job {}: {}", evt.job.key, evt), Set.of(LOG_TARGETS.REPL_ALL_SHELLS));
        }
    };
    
    public static final Consumer<JobEvent> CALLBACK_PAUSE_ON_ERROR = evt -> {
        if (evt.eventType != JobEventType.INPUT_ERROR) return;
        evt.job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "Job {}: Pausing due to input #{} error: {}", evt.job.key, evt.inputIndex, evt.job.results[evt.inputIndex].error), Set.of(LOG_TARGETS.REPL_ALL_SHELLS));
        if (evt.job.inputs != null) evt.job.pause();
    };

    @Serial
    private static final long serialVersionUID = 1L;
    private static final int MAX_PARAMS_QUEUED = 100;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyMMdd-HHmmss-nnnnnnnnn");

    private static final Map<String, REPLJob> jobs = Collections.synchronizedMap(new LinkedHashMap<>());

    
    public static List<REPLJob> list() {
        return new ArrayList<>(jobs.values());
    }

    public static List<String> listArchived() {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(STATE_FILE_PREFIX) + "-(.*)\\." + Pattern.quote(STATE_FILE_SUFFIX));
        ArrayList<String> result = new ArrayList<>();
        for (File f : Objects.requireNonNull(REPL.getWorkDir().listFiles(File::isFile))) {
             Matcher m = pattern.matcher(f.getName());
             if (!m.matches()) continue;
             result.add(m.group(1));
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static Stream<REPLJob> streamArchived() {
        return listArchived().stream().map(k -> {
            try {
                return load(k);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static List<String> pruneArchived() {
        return streamArchived()
                        .filter(j -> j.getProgress().isSuccess())
                        .filter(j -> j.getStateFile().delete())
                        .map(j -> j.key)
                        .sorted()
                        .toList();
    }

    public static REPLJob get(String key) {
        return jobs.get(key);
    }

    public static boolean archive(REPLJob job) {
        return archive(job.key);
    }

    public static boolean archive(String key) {
        REPLJob j = jobs.get(key);
        if (j != null && j.getProgress().isActive()) throw new RuntimeException("can't archive a job that is still active");
        return jobs.remove(key) != null;
    }

    public static REPLJob repljob(Supplier<Serializable> supplier) {
        return repljob((x, y) -> supplier.get(), null, 1, null);
    }
    
    public static REPLJob repljob(Supplier<Serializable> supplier, int concurrency) {
        return repljob((x, y) -> supplier.get(), null, concurrency, null);
    }

    
    public static REPLJob repljob(Supplier<Serializable> supplier, String name) {
        return repljob((x, y) -> supplier.get(), null, 1, name);
    }

    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs) {
        return repljob(function, inputs, 1, null);
    }

    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs) {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, 1, null);
        closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, int concurrency) {
        return repljob(function, inputs, concurrency, null);
    }

    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, int concurrency) {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, concurrency, null);
        closure.setDelegate(j);
        return j;
    }

    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, String name) {
        return repljob(function, inputs, 1, name);
    }

    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, String name) {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, name);
        closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, int concurrency, String name) {
        REPLJob job = new REPLJob(TIMESTAMP_FORMAT.format(LocalDateTime.now()), function, inputs, concurrency, name);
        if (jobs.putIfAbsent(job.getKey(), job) != null) throw new RuntimeException("key collision: " + job.getKey());
        if (REPL.HAVE_MYCORE) job.setInternalCallback(new REPLJobProcessableProxy(job));
        return job;
    }

    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, int concurrency, String name) {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, concurrency, name);
        closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, int concurrency, String name, boolean becomeDelegate) {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, concurrency, name);
        if (becomeDelegate) closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob resume(String key, Closure<Serializable> closure) throws IOException {
        REPLJob j = resume(key, (x, y) -> closure.call(x, y), false, true);
        closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob resume(String key, BiFunction<Serializable, REPLJob, Serializable> function) throws IOException {
        return resume(key, function, false, true);
    }

    public static REPLJob resume(String key, Supplier<Serializable> supplier) throws IOException {
        return resume(key, supplier, false, true);
    }

    public static REPLJob resume(String key, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors) throws IOException {
        REPLJob j = resume(key, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        closure.setDelegate(j);
        return j;
    }

    public static REPLJob resume(String key, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors, boolean becomeDelegate) throws IOException {
        REPLJob j = resume(key, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        if (becomeDelegate) closure.setDelegate(j);
        return j;
    }

    public static REPLJob resume(String key, BiFunction<Serializable, REPLJob, Serializable> function, boolean retrySuccess, boolean retryErrors) throws IOException {
        REPLJob oldJob = jobs.get(key);
        if (oldJob != null && oldJob.getProgress().isActive()) throw new RuntimeException("Can't resume a job that is still active");
        return resume(Path.of(REPL.getWorkDir().getAbsolutePath(), STATE_FILE_PREFIX + "-" + key + "." + STATE_FILE_SUFFIX).toFile(), function, retrySuccess, retryErrors);
    }
    
    public static REPLJob resume(String key, Supplier<Serializable> supplier, boolean retrySuccess, boolean retryErrors) throws IOException {
        return resume(key, (x, y) -> supplier.get(), retrySuccess, retryErrors);
    }

    public static REPLJob resume(File path, Closure<Serializable> closure) throws IOException {
        REPLJob j = resume(path, (x, y) -> closure.call(x, y), false, true);
        closure.setDelegate(j);
        return j;
    }

    public static REPLJob resume(File path, BiFunction<Serializable, REPLJob, Serializable> function) throws IOException {
        return resume(path, function, false, true);
    }
    
    public static REPLJob resume(File path, Supplier<Serializable> supplier) throws IOException {
        return resume(path, supplier, false, true);
    }
    
    public static REPLJob resume(File path, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors) throws IOException {
        REPLJob j = resume(path, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob resume(File path, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors, boolean becomeDelegate) throws IOException {
        REPLJob j = resume(path, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        if (becomeDelegate) closure.setDelegate(j);
        return j;
    }
    
    public static REPLJob resume(File path, BiFunction<Serializable, REPLJob, Serializable> function, boolean retrySuccess, boolean retryErrors) throws IOException {
        REPLJob job = new REPLJob(path, TIMESTAMP_FORMAT.format(LocalDateTime.now()), function, retrySuccess, retryErrors);
        if (job.inputs == null || job.inputs.length == 0) throw new RuntimeException("can't resume a job that had no inputs");
        if (jobs.putIfAbsent(job.getKey(), job) != null) throw new RuntimeException("key collision: " + job.getKey());
        if (REPL.HAVE_MYCORE) job.setInternalCallback(new REPLJobProcessableProxy(job));
        return job;
    }

    public static REPLJob resume(File path, Supplier<Serializable> supplier, boolean retrySuccess, boolean retryErrors) throws IOException {
        return resume(path, (x, y) -> supplier.get(), retrySuccess, retryErrors);
    }
    
    public static REPLJob load(String key) throws IOException {
        return load(Path.of(REPL.getWorkDir().getAbsolutePath(), STATE_FILE_PREFIX + "-" + key + "." + STATE_FILE_SUFFIX).toFile());
    }
    
    public static REPLJob load(File path) throws IOException {
        return new REPLJob(path, null, null, false, false);
    }

    protected String key;
    protected final transient BiFunction<Serializable, REPLJob, Serializable> function;
    protected int concurrency;
    protected String name;
    protected Instant createdTimestamp;
    protected final String resumedKey;
    protected Serializable[] inputs;
    protected final InputResult[] results;
    private final ConcurrentLinkedQueue<REPLLogEntry> jobLog = new ConcurrentLinkedQueue<>(); // a wait-free list, ATTENTION: .size() is not O(1)
    private REPLLogEntry lastLogEntry = null;
    private final transient AppendableObjectStore objectStore;
    private transient PausableThreadPoolExecutor executor;
    private transient Consumer<JobEvent> progressCallback = null;
    private transient Consumer<JobEvent> internalCallback = null;

    // state
    private transient Integer queuedInput = 0;
    private transient int queueLength = 0;
    private transient int remainingInputs = 0;
    private transient int skippedErrors = 0;
    private transient int errors = 0;
    private transient int skippedSuccess = 0;
    private transient int success = 0;
    private Instant startTimestamp = null;
    private Instant doneTimestamp = null;
    private transient Instant pausedSince = null;
    private transient Long pausedMillis = null;
    private transient Instant cancelledSince = null;
    private transient Integer cancelForceTimeoutSeconds = null;
    private transient CompletableFuture<JobProgress> future = null;

    private REPLJob(String key, BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, int concurrency, String name) {
        this.createdTimestamp = Instant.now();
        this.key = key;
        this.function = function;
        this.concurrency = concurrency;
        this.name = name != null ? name : ("Job " + key);
        this.resumedKey = null;
        if (inputs != null) {
            if (inputs.isEmpty()) throw new IllegalArgumentException("empty input list - please use null instead if you want to run without parameters");
            this.inputs = inputs.toArray(new Serializable[]{});
            this.results = new InputResult[this.inputs.length];
        } else {
            this.inputs = null;
            this.results = new InputResult[1];
        }
        resetProgress();

        try {
            objectStore = new AppendableObjectStore(getStateFile());
            objectStore.writeObject(this);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private REPLJob(File path, String newKey, BiFunction<Serializable, REPLJob, Serializable> function, boolean retrySuccess, boolean retryErrors) throws IOException {
        this.createdTimestamp = Instant.now();
        if (!path.isFile()) throw new RuntimeException("job state file " + path + " not found");
        try (AppendableObjectStore in = new AppendableObjectStore(path)) {
            REPLJob job = (REPLJob) in.next();
            this.key = job.key;
            this.function = function;
            this.concurrency = job.concurrency;
            this.name = "Resume: " + job.name;
            this.inputs = job.inputs;
            if (this.inputs != null) {
                this.results = new InputResult[this.inputs.length];
            } else {
                this.results = new InputResult[1];
            }
            while (in.hasNext()) {
                Serializable data = in.next();
                if (data == null) continue;
                if (data instanceof InputResult result) {
                    if (result.error == null) result = retrySuccess ? null : result;
                    else result = retryErrors ? null : result;
                    if (result != null) this.results[result.index] = result;
                } else if (data instanceof REPLLogEntry logEntry) {
                    this.jobLog.add(logEntry);
                    if (logEntry.getLevel().compareTo(REPLLogEntry.LOG_LEVEL.DEBUG) > 0) lastLogEntry = logEntry;
                } else {
                    throw new RuntimeException("entry of unexpected class " + data.getClass() + " in " + path);
                }
            }
        }
        resetProgress();

        if (newKey == null) {
            // for viewing only
            this.resumedKey = null;
            objectStore = null;
            queuedInput = null;
            remainingInputs -= success + errors;
        }
        else {
            this.resumedKey = key;
            this.key = newKey;
            this.startTimestamp = null;
            this.doneTimestamp = null;
            try {
                objectStore = new AppendableObjectStore(getStateFile());
                objectStore.writeObject(this);
                for (REPLLogEntry logEntry : this.jobLog) objectStore.writeObject(logEntry);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Future<JobProgress> start() {
        return start(null, null);
    }

    public Future<JobProgress> start(ThreadFactory threadFactory) {
        return start(threadFactory, null);
    }

    public Future<JobProgress> start(Consumer<JobEvent> progressCallback) {
        return start(null, progressCallback);
    }

    public Future<JobProgress> start(ThreadFactory threadFactory, Consumer<JobEvent> progressCallback) {
        if (future != null) throw new RuntimeException("this job has already been started");
        future = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                doCancel(mayInterruptIfRunning ? 10 : null);
                return super.cancel(mayInterruptIfRunning);
            }
        };
        this.progressCallback = progressCallback;
        new Thread(() -> {
            try {
                this.execute(threadFactory);
                future.complete(getProgress());
                tryCallback(doneTimestamp, cancelledSince == null ? JobEventType.JOB_DONE_SUCCESS : JobEventType.JOB_DONE_CANCELLED, null);
            } catch (Exception e) {
                log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Internal Error in job {}: {}", key, e), INTERNAL_LOG_TARGETS);
                future.completeExceptionally(e);
                tryCallback(doneTimestamp, JobEventType.JOB_DONE_INTERNALERROR, null);
            }
        }).start();
        return future;
    }

    private void tryCallback(Instant timestamp, JobEventType eventType, Integer inputIndex) {
        Consumer<JobEvent> icb = internalCallback;
        if (icb != null) {
            try {
                icb.accept(new JobEvent(this, timestamp, eventType, inputIndex));
            } catch (Exception e) {
                log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "Job {}: internal error in callback: {}", key, e), INTERNAL_LOG_TARGETS);
            }
        }
        Consumer<JobEvent> cb = progressCallback;
        if (cb == null) return;
        try {
            cb.accept(new JobEvent(this, timestamp, eventType, inputIndex));
        } catch (Exception e) {
            log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "Job {}: error in progress callback: {}", key, e), INTERNAL_LOG_TARGETS);
        }
    }

    private void resetProgress() {
        synchronized (this) {
            queuedInput = 0;
            queueLength = 0;
            remainingInputs = (inputs != null ? inputs.length : 1);
            pausedSince = null;
            pausedMillis = 0L;
            skippedErrors = 0;
            errors = 0;
            skippedSuccess = 0;
            success = 0;
            Long start = null;
            Long stop = null;
            for (InputResult result : results) {
                if (result == null) continue;
                if (result.error == null) success++;
                else errors++;
                if (start == null || result.epochMicrosFrom < start) start = result.epochMicrosFrom;
                if (stop == null || result.epochMicrosTo > stop) stop = result.epochMicrosTo;
            }
            if (start != null) startTimestamp = Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(start));
            if (stop != null) doneTimestamp = Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(stop));
            // don't touch future here - we determine if we have already run by its value
        }
    }

    private void execute(ThreadFactory threadFactory) {
        if (function == null) throw new RuntimeException("can't execute a job without closure");
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        synchronized (this) {
            startTimestamp = Instant.now();
            errors = 0;
            success = 0;
            executor = new PausableThreadPoolExecutor(concurrency, queue, threadFactory);
        }
        Instant pauseNotified = null;
        info("Starting job...");
        tryCallback(startTimestamp, JobEventType.JOB_START, null);
        CompletionService<InputResult> completionService = new ExecutorCompletionService<>(executor);
        try {
            while (remainingInputs > 0) {
                // there is room for a new batch
                int batchLimit = Math.min(queuedInput + MAX_PARAMS_QUEUED, inputs != null ? inputs.length : 1);
                int batchSubmitted = 0;
                while (queuedInput < batchLimit && queue.size() < MAX_PARAMS_QUEUED && cancelledSince == null) {
                    if (results[queuedInput] != null) {
                        if (TRACE || TRACE_JOBS) trace("skipped {} #{}", results[queuedInput].error == null ? "success" : "error", queuedInput);
                        try {
                            objectStore.writeObject(results[queuedInput]);
                        } catch (InterruptedException e) {
                            log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "Job {}: interrupted while skipping results: {}", key, e), INTERNAL_LOG_TARGETS);
                            throw new RuntimeException("Job " + key + " interrupted while skipping results", e);
                        }
                        synchronized (this) {
                            if (results[queuedInput].error == null) skippedSuccess++;
                            else skippedErrors++;
                            queuedInput++;
                            remainingInputs--;
                            queueLength = queue.size();
                        }
                        tryCallback(Instant.now(), JobEventType.INPUT_SKIPPED, queuedInput - 1);
                        continue;
                    }
                    final int paramPtr = queuedInput;
                    final Serializable param = inputs != null ? inputs[paramPtr] : null;
                    try {
                        completionService.submit(() -> {
                            long epochFrom = Helpers.epochMicros();
                            try {
                                Serializable result;
                                try {
                                    result = function.apply(param, this);
                                } catch (Exception ex) {
                                    long epochTo = Helpers.epochMicros();
                                    return new InputResult(key, paramPtr, epochFrom, epochTo, null, ex);
                                }
                                return new InputResult(key, paramPtr, epochFrom, Helpers.epochMicros(), result, null);
                            } catch (Exception ex) {
                                log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "Job {}: internal error while gathering result: {}", key, ex), INTERNAL_LOG_TARGETS);
                                return new InputResult(key, paramPtr, epochFrom, Helpers.epochMicros(), null, new RuntimeException("internal error while gathering result", ex));
                            }
                        });
                    } catch (RejectedExecutionException ex) {
                        if (TRACE || TRACE_JOBS) trace("executor rejected #{} (shuting down?)", paramPtr);
                        break;
                    }
                    if (TRACE || TRACE_JOBS) trace("submitted #{}", paramPtr);
                    batchSubmitted++;
                    synchronized (this) {
                        queuedInput++;
                        queueLength = queue.size();
                    }
                }
                // if we skipped the entire batch, don't poll results but submit a new batch instead
                if (batchSubmitted == 0 && queuedInput == batchLimit && queuedInput < (inputs != null ? inputs.length : 1) && cancelledSince == null) {
                    if (TRACE || TRACE_JOBS) trace("entire batch skipped, not polling yet @ {}", queuedInput);
                    continue;
                }

                // wait for results batch
                if (pausedSince != null && (pauseNotified == null || pausedSince.isAfter(pauseNotified)) && executor.getWorkingThreadCount() == 0) {
                    pauseNotified = pausedSince;
                    tryCallback(Instant.now(), JobEventType.JOB_PAUSED, null);
                }
                Integer cancelWaitTime = null;
                try {
                    synchronized (this) {
                        if (cancelledSince != null && cancelForceTimeoutSeconds != null) cancelWaitTime = cancelForceTimeoutSeconds;
                    }
                    for (Future<InputResult> future = completionService.poll(cancelWaitTime != null ? cancelWaitTime : 1, TimeUnit.SECONDS); future != null; future = completionService.poll()) {
                        InputResult result = future.get();
                        if (TRACE || TRACE_JOBS) trace("received {}", future.get());
                        synchronized (this) {
                            remainingInputs--;
                            if (result.error != null) errors++;
                            else success++;
                            results[result.index] = result;
                            queueLength = queue.size();
                        }
                        objectStore.writeObject(result);
                        tryCallback(Instant.now(), result.error == null ? JobEventType.INPUT_SUCCESS : JobEventType.INPUT_ERROR, result.index);
                    }
                } catch (InterruptedException ex) {
                    log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "Job {}: interrupted while waiting for results: {}", key, ex), INTERNAL_LOG_TARGETS);
                } catch (ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
                if (cancelledSince != null && cancelWaitTime != null) break;
                else if (cancelledSince != null && executor.getActiveCount() == 0) break;
            }
        } finally {
            executor.shutdownNow();
            info("Job done.");
            objectStore.close();
            doneTimestamp = Instant.now();
        }
        if (TRACE || TRACE_JOBS) trace("done.");
    }
    
    public synchronized JobProgress getProgress() {
        // eta
        ZonedDateTime eta = null;
        Long etaSeconds = null;
        if (doneTimestamp == null && startTimestamp != null && (success + errors) > 0) {
            Instant now = Instant.now();
            double elapsed = (double) (ChronoUnit.MILLIS.between(startTimestamp, now) - pausedMillis);
            double perInput = elapsed / ((double) (success + errors));
            eta = now.plus(Math.round(perInput * remainingInputs), ChronoUnit.MILLIS).atZone(ZoneId.systemDefault());
            etaSeconds = Math.round((perInput * remainingInputs) / 1000);

        }

        // percent
        double inlen = inputs != null ? inputs.length : 1;
        int percentDone = 100;
        if (remainingInputs > 0) percentDone = Math.toIntExact(Math.round(100.0d - ((((double) remainingInputs) * 100.0d) / inlen)));
        if (percentDone > 100) percentDone = 100;
        if (percentDone < 0) percentDone = 0;

        // state
        int threads = executor != null ? (executor.getWorkingThreadCount()) : 0;
        JobState state = null;
        if (future != null && future.isDone()) {
            try {
                future.get(0, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                state = JobState.INTERNAL_ERROR;
            } catch (CancellationException | InterruptedException | TimeoutException ignore) {}
        }
        if (state == null) {
            if (pausedSince != null && threads > 0) state = JobState.PAUSING;
            else if (pausedSince != null) state = JobState.PAUSED;
            else if (cancelledSince != null && JobProgress.isActive(startTimestamp, doneTimestamp)) state = JobState.CANCELLING;
            else if (cancelledSince != null) state = JobState.CANCELLED;
            else if (JobProgress.isActive(startTimestamp, doneTimestamp)) state = JobState.RUNNING;
            else if (JobProgress.isSuccess(remainingInputs, errors, skippedErrors, success, skippedSuccess, inputs != null ? inputs.length : 1)) state = JobState.COMPLETED_SUCCESSFULLY;
            else if (startTimestamp == null) state = JobState.NOT_YET_STARTED;
            else state = JobState.COMPLETED_WITH_ERRORS;
        }

        return new JobProgress(state, queuedInput != null ? queuedInput - queueLength : null, inputs == null ? 1 : inputs.length,
                remainingInputs, success, skippedSuccess, errors, skippedErrors, percentDone,
                pausedSince != null ? pausedSince.atZone(ZoneId.systemDefault()) : null,
                cancelledSince != null ? cancelledSince.atZone(ZoneId.systemDefault()) : null,
                startTimestamp != null ? startTimestamp.atZone(ZoneId.systemDefault()) : null,
                doneTimestamp != null ? doneTimestamp.atZone(ZoneId.systemDefault()) : null,
                pausedSince == null ? eta : null, pausedSince == null ? etaSeconds : null, threads, future);
    }

    public synchronized void retryIndex(int index) {
        if (startTimestamp != null) throw new RuntimeException("this job has already been started");
        results[index] = null;
    }

    public boolean cancel() {
        return doCancel(null);
    }

    public boolean cancelForce(int timeoutSeconds) {
        return doCancel(timeoutSeconds);
    }

    private boolean doCancel(Integer forceTimeoutSeconds) {
        if (doneTimestamp != null) return false;
        log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "Job {}: cancelled{}", key, forceTimeoutSeconds == null ? "" : (", force timeout=" + forceTimeoutSeconds)), INTERNAL_LOG_TARGETS);
        final Instant cs = Instant.now();
        tryCallback(cs, JobEventType.JOB_CANCEL_REQUESTED, null);
        synchronized (this) {
            if (forceTimeoutSeconds != null) {
                cancelForceTimeoutSeconds = forceTimeoutSeconds;
                executor.shutdownNow();
            } else executor.shutdown();
            executor.emptyQueue();
            if (pausedSince != null && inputs != null) unpause(false);
            cancelledSince = cs;
        }
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean pause() {
        if (inputs == null) throw new RuntimeException("Jobs without inputs can't be paused");
        if (pausedSince != null || doneTimestamp != null || cancelledSince != null) return false;
        if (TRACE || TRACE_JOBS) trace("paused");
        final Instant ps = Instant.now();
        tryCallback(ps, JobEventType.JOB_PAUSE_REQUESTED, null);
        synchronized (this) {
            pausedSince = ps;
            executor.pause();
        }
        return true;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Long unpause() {
        return unpause(true);
    }

    private Long unpause(boolean callback) {
        final Instant until;
        final long d;
        synchronized (this) {
            if (inputs == null) throw new RuntimeException("Jobs without inputs can't be paused");
            if (pausedSince == null || doneTimestamp != null || cancelledSince != null) return null;
            if (TRACE || TRACE_JOBS) trace("unpaused");
            until = Instant.now();
            d = ChronoUnit.MILLIS.between(pausedSince, until);
            pausedMillis += d;
            pausedSince = null;
            executor.resume();
        }
        if (callback) tryCallback(until, JobEventType.JOB_UNPAUSED, null);
        return d;
    }

    public String getKey() {
        return key;
    }

    public synchronized int getConcurrency() {
        return concurrency;
    }

    public synchronized void setConcurrency(int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("concurrency must be > 0");
        if (executor != null) {
            int c = executor.getCorePoolSize();
            int m = executor.getMaximumPoolSize();
            if (concurrency > m) executor.setMaximumPoolSize(concurrency);
            if (concurrency > c) executor.setCorePoolSize(concurrency);
            if (concurrency < c) executor.setCorePoolSize(concurrency);
            if (concurrency < m) executor.setMaximumPoolSize(concurrency);
        }
        this.concurrency = concurrency;
    }

    public String getName() {
        return name;
    }
    
    public Consumer<JobEvent> getProgressCallback() {
        return progressCallback;
    }

    public void setProgressCallback(Consumer<JobEvent> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public synchronized void setInternalCallback(Consumer<JobEvent> internalCallback) {
        if (startTimestamp != null) throw new RuntimeException("Can't change the internal callback after the job has started. This methods is for internal use only. You should generally use setProgressCallback() instead.");
        this.internalCallback = internalCallback;
    }

    public Consumer<JobEvent> getInternalCallback() {
        return internalCallback;
    }

    public List<Serializable> getInputs() {
        return inputs != null ? Collections.unmodifiableList(Arrays.asList(inputs)) : null;
    }

    public List<InputResult> getResults() {
        return results != null ? Collections.unmodifiableList(Arrays.asList(results)) : null;
    }

    public File getStateFile() {
        return Path.of(REPL.getWorkDir().getAbsolutePath(), STATE_FILE_PREFIX + "-" + this.key + "." + STATE_FILE_SUFFIX).toFile();
    }

    public List<REPLLogEntry> getJobLog() {
        return new ArrayList<>(jobLog);
    }

    public CompletableFuture<JobProgress> getFuture() {
        return future;
    }

    public String getResumedKey() {
        return resumedKey;
    }

    public Instant getCreatedTimestamp() {
        return createdTimestamp;
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(key);
        out.writeInt(concurrency);
        out.writeObject(name);
        out.writeObject(createdTimestamp);
        out.writeObject(inputs);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        key = (String) in.readObject();
        concurrency = in.readInt();
        name = (String) in.readObject();
        createdTimestamp = (Instant) in.readObject();
        inputs = (Serializable[]) in.readObject();
        // jobresults must be read outside of this method -> optionaldataexception
    }

    @Override
    public String toString() {
        return key + " (" + name + ")";
    }

    public REPLLogEntry getLastLogEntry() {
        return lastLogEntry;
    }

    public REPLLogEntry trace(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.trace(args);
        doLog(e);
        return e;
    }

    public REPLLogEntry debug(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.debug(args);
        doLog(e);
        return e;

    }

    public REPLLogEntry info(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.info(args);
        doLog(e);
        return e;

    }
    
    public REPLLogEntry warn(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.warn(args);
        doLog(e);
        return e;

    }

    public REPLLogEntry error(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.error(args);
        doLog(e);
        return e;
    }

    public REPLLogEntry log(Object... args) {
        return info(args);
    }

    public void log(REPLLogEntry entry) {
        log(entry, DEFAULT_LOG_TARGETS);
    }

    public void log(REPLLogEntry entry, Set<LOG_TARGETS> targets, PrintStream... streams) {
        REPLLog.log(entry, targets, streams);
        doLog(entry);
    }

    private void doLog(REPLLogEntry entry) {
        jobLog.add(entry);
        if (entry.getLevel().compareTo(REPLLogEntry.LOG_LEVEL.DEBUG) > 0) lastLogEntry = entry;
        try {
            if (objectStore != null) objectStore.writeObject(entry);
        } catch (AppendableObjectStore.ObjectStoreNotAvailableException | InterruptedException ignore) {}
    }


    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        try {
            if (objectStore != null) objectStore.close();
        } finally {
            super.finalize();
        }
    }

}
