// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.error.JobException;
import de.uni_kiel.rz.fdr.repl.error.ObjectStoreInvalidException;
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


/**
 * This class represents a single job to be executed in the background. It also provides static methods to manage all
 * jobs that are currently on file.
 */
@SuppressWarnings("unused")
public class REPLJob implements Serializable {

    /**
     * Threads of cau-repls job workers have names that start with this prefix. Threads belonging to a single job will
     * also be placed in a common thread group.
     */
    public static final String THREAD_PREFIX = REPL.THREAD_PREFIX + "job: ";

    /**
     * A record containing a job's results for one single input item.
     * @param key The key of the job that produced this result
     * @param index The index of the item in the input list of the job
     * @param epochMicrosFrom Timestamp when we started processing this result's input
     * @param epochMicrosTo Timestamp when we finished processing this result's input, generating this record
     * @param result The result of this input
     * @param error Any exception that was thrown during processing
     */
    public record InputResult(String key, int index, long epochMicrosFrom, long epochMicrosTo, Serializable result, Exception error) implements Serializable {}

    /**
     * The possible states that a job can be in.
     */
    public enum JobState {
        /**
         * The job terminated with an internal error not related to your code
         */
        INTERNAL_ERROR,
        /**
         * The job is currently transitioning to {@code PAUSED}. Residual workers are finishing their inputs.
         */
        PAUSING,
        /**
         * The job is currently fully paused
         */
        PAUSED,
        /**
         * The job is currently transitioning to {@code CANCELLED}. Residual workers are finishing their inputs or are
         * currently forcefully terminated.
         */
        CANCELLING,
        /**
         * The job was fully cancelled
         */
        CANCELLED,
        /**
         * The job is currently running
         */
        RUNNING,
        /**
         * The job has completed without errors
         */
        COMPLETED_SUCCESSFULLY,
        /**
         * The job has not been started yet
         */
        NOT_YET_STARTED,
        /**
         * The job has completed with errors
         */
        COMPLETED_WITH_ERRORS;
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replaceAll("_", " ");
        }
    }

    /**
     * A record that summarizes a job's state at one instant in time.
     * @param state The job's state
     * @param nextInput Index of the next input that we would enqueue for processing
     * @param totalInputs Number of total inputs that the job was started with
     * @param remainingInputs Number of inputs that have not yet been processed
     * @param success Number of inputs that were successfully processed (excluding skipped successful inputs when resuming)
     * @param skippedSuccess Number of inputs that were skipped when resuming a job because they were successful during an earlier run
     * @param errors Number of inputs that raised an exception during processing (excluding skipped errors when resuming)
     * @param skippedErrors Number of inputs that were skipped when resuming a job because they were not successful during an earlier run
     * @param percentDone Current progress of the job
     * @param pausedSince If currently paused: since when?
     * @param cancelledSince If cancelled: since when?
     * @param startTimestamp If started: since when?
     * @param doneTimestamp If done (successfully or not): since when?
     * @param eta Estimated time of completion
     * @param etaSeconds Number of remaining seconds until estimated time of completion
     * @param activeThreads Number of active worker threads processing inputs
     * @param future A future for this job, so you can wait for its completion
     */
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

        /**
         * Determines whether the job is active. A job ist active after it has been started and may still produce further
         * output in the future.
         * @return The job's active status
         */
        public boolean isActive() {
            return JobProgress.isActive(startTimestamp, doneTimestamp);
        }

        /**
         * Determines whether the job is complete. A job is complete if all its inputs have been processed or skipped
         * (either successfully or unsuccessfully)
         * @return The job's completion status
         */
        public boolean isComplete() {
            return JobProgress.isComplete(remainingInputs);
        }

        /**
         * Determines whether the job was a success. A job is a success if there all inputs were successfully processed
         * (during this or resumed previous runs)
         * @return The job's success status
         */
        public boolean isSuccess() {
            return JobProgress.isSuccess(remainingInputs, errors, skippedErrors, success, skippedSuccess, totalInputs);
        }

        /**
         * Get a textual representation of the job's ETA.
         * @return A textual representation of the job's ETA
         */
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

    /**
     * The different event types that a {@code JobEvent} handler might receive.
     */
    public enum JobEventType {
        /**
         * The job was started
         */
        JOB_START,
        /**
         * The job finished without errors
         */
        JOB_DONE_SUCCESS,
        /**
         * The job has been fully cancelled
         */
        JOB_DONE_CANCELLED,
        /**
         * The job has finished with an internal error not related to your code
         */
        JOB_DONE_INTERNALERROR,
        /**
         * The job is fully paused
         */
        JOB_PAUSED,
        /**
         * The job was resumed from paused state
         */
        JOB_UNPAUSED,
        /**
         * Transition to pause state was requested, workers are finishing up
         */
        JOB_PAUSE_REQUESTED,
        /**
         * Transition to cancelled state was requested, workers are finishing up or being forecefully terminated
         */
        JOB_CANCEL_REQUESTED,
        /**
         * A single input was successfully processed
         */
        INPUT_SUCCESS,
        /**
         * A single input was skipped while resuming
         */
        INPUT_SKIPPED,
        /**
         * A single input generated an error during processing
         */
        INPUT_ERROR
    }

    /**
     * Represents an event that occured during job processing, to be consumed by an event handler.
     * @param job The job that generated the event
     * @param timestamp The event's timestamp
     * @param eventType The type of event that happened
     * @param inputIndex The index of the input that generated the event (if applicable)
     */
    public record JobEvent(REPLJob job, Instant timestamp, JobEventType eventType, Integer inputIndex) {}

    private static final String STATE_FILE_PREFIX = "job";
    private static final String STATE_FILE_SUFFIX = "state";

    /**
     * A predefined job event handler that you can use; it logs regular process messages to your SSH session. This
     * will make it hard to use the session interactively while the job is running.
     */
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

    /**
     * A predefined job event handler you can use, it pauses the job whenever an error happens. if you enabled
     * concurrency, multiple errors might accumulate in pause event.
     */
    public static final Consumer<JobEvent> CALLBACK_PAUSE_ON_ERROR = evt -> {
        if (evt.eventType != JobEventType.INPUT_ERROR) return;
        evt.job.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "Job {}: Pausing due to input #{} error: {}", evt.job.key, evt.inputIndex, evt.job.results[evt.inputIndex].error), Set.of(LOG_TARGETS.REPL_ALL_SHELLS));
        if (evt.job.inputs != null) {
            try {
                evt.job.pause();
            } catch (JobException ignore) {}
        }
    };

    @Serial
    private static final long serialVersionUID = 1L;
    private static final int MAX_PARAMS_QUEUED = 100;

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyMMdd-HHmmss-nnnnnnnnn");

    private static final Map<String, REPLJob> jobs = Collections.synchronizedMap(new LinkedHashMap<>());


    /**
     * Get the list of all jobs that are not archived - that is: loaded in memory.
     * @return the list of all jobs that are not archived
     */
    public static List<REPLJob> list() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * Get the list of all archived jobs' keys.
     * @return The list of all archived jobs' keys.
     */
    public static List<String> listArchived() {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(STATE_FILE_PREFIX) + "-(.*)\\." + Pattern.quote(STATE_FILE_SUFFIX));
        ArrayList<String> result = new ArrayList<>();
        for (File f : Objects.requireNonNull(REPL.getWorkDir().listFiles(File::isFile))) {
             Matcher m = pattern.matcher(f.getName());
             if (!m.matches()) continue;
             if (jobs.containsKey(m.group(1))) continue;
             result.add(m.group(1));
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /**
     * Stream all archived jobs without adding them to the list of active jobs. This can be a time-consuming operation,
     * because all inputs and results will be loaded as well. Note that the job instances that this method streams are
     * not resumable by design. If this is important, use one of the various
     * {@link REPLJob#resume(String, Closure<Serializable>) resume()} methods instead.
     * @return A stream of all archived jobs.
     */
    public static Stream<REPLJob> streamArchived() {
        return listArchived().stream().map(k -> {
            try {
                return load(k);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (ObjectStoreInvalidException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Removes the state files of all archived jobs that were completed {@link JobProgress#isSuccess() successfully}.
     * @return The keys of all pruned jobs.
     */
    public static List<String> pruneArchived() {
        return streamArchived()
                        .filter(j -> j.getProgress().isSuccess())
                        .filter(j -> j.getStateFile().delete())
                        .map(j -> j.key)
                        .sorted()
                        .toList();
    }

    /**
     * Get a loaded job by key.
     * To retrieve an archived job, use one of the {@link REPLJob#load(String) load()} or {@link REPLJob#resume resume()} methods instead.
     * @param key The key to look up.
     * @return The Job with the key, or {@code null} if none was found.
     */
    public static REPLJob get(String key) {
        return jobs.get(key);
    }

    /**
     * Archives a loaded job. Jobs that are still {@link JobProgress#isActive() active} can't be archived. Archiving
     * an active job will throw an exception.
     * @param job The job to archive.
     * @return Flag indicating whether this job was archived or not.
     */
    public static boolean archive(REPLJob job) throws JobException {
        return archive(job.key);
    }

    /**
     * Archives a loaded job. Jobs that are still {@link JobProgress#isActive() active} can't be archived. Archiving
     * an active job will throw an exception.
     * @param key The key of the job to archive.
     * @return Flag indicating whether this job was archived or not.
     */
    public static boolean archive(String key) throws JobException {
        REPLJob j = jobs.get(key);
        if (j != null && j.getProgress().isActive()) throw new JobException("can't archive a job that is still active");
        return jobs.remove(key) != null;
    }

    /**
     * Creates a new job without inputs or concurrency that is ready to be started.
     * @param supplier The job action.
     * @return The new job.
     */
    public static REPLJob repljob(Supplier<Serializable> supplier) throws IOException {
        return repljob((x, y) -> supplier.get(), null, 1, null);
    }

    /**
     * Creates a new job without inputs that is ready to be started.
     * @param supplier The job action.
     * @param concurrency The concurrency level to use.
     * @return The new job.
     */
    public static REPLJob repljob(Supplier<Serializable> supplier, int concurrency) throws IOException {
        return repljob((x, y) -> supplier.get(), null, concurrency, null);
    }

    /**
     * Creates a new job without inputs or concurrency that is ready to be started.
     * @param supplier The job action.
     * @param name A name for the job that will be displayed in the job list.
     * @return The new job.
     */
    public static REPLJob repljob(Supplier<Serializable> supplier, String name) throws IOException {
        return repljob((x, y) -> supplier.get(), null, 1, name);
    }

    /**
     * Creates a new job with inputs or concurrency that is ready to be started.
     * @param function The job action.
     * @param inputs The inputs that the job action will operate on.
     * @return The new job.
     */
    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs) throws IOException {
        return repljob(function, inputs, 1, null);
    }

    /**
     * Creates a new job with inputs and no concurrency that is ready to be started.
     * @param closure The job action.
     * @param inputs The inputs that the job action will operate on.
     * @return The new job.
     */
    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs) throws IOException {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, 1, null);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Creates a new job with inputs that is ready to be started.
     * @param function The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param concurrency The concurrency level to use.
     * @return The new job.
     */
    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, int concurrency) throws IOException {
        return repljob(function, inputs, concurrency, null);
    }

    /**
     * Creates a new job with inputs that is ready to be started.
     * @param closure The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param concurrency The concurrency level to use.
     * @return The new job.
     */
    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, int concurrency) throws IOException {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, concurrency, null);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Creates a new job with inputs and no concurrency that is ready to be started.
     * @param function The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param name A name for the job that will be displayed in the job list.
     * @return The new job.
     */
    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, String name) throws IOException {
        return repljob(function, inputs, 1, name);
    }

    /**
     * Creates a new job with inputs and no concurrency that is ready to be started.
     * @param closure The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param name A name for the job that will be displayed in the job list.
     * @return The new job.
     */
    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, String name) throws IOException {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, name);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Creates a new job with inputs that is ready to be started.
     * @param function The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param concurrency The concurrency level to use.
     * @param name A name for the job that will be displayed in the job list.
     * @return The new job.
     */
    public static REPLJob repljob(BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, int concurrency, String name) throws IOException {
        REPLJob job = new REPLJob(TIMESTAMP_FORMAT.format(LocalDateTime.now()), function, inputs, concurrency, name);
        if (jobs.putIfAbsent(job.getKey(), job) != null) throw new RuntimeException("key collision: " + job.getKey() + ", internal error?");
        if (REPL.HAVE_MYCORE) job.setInternalCallback(new REPLJobProcessableProxy(job));
        return job;
    }

    /**
     * Creates a new job with inputs that is ready to be started.
     * @param closure The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param concurrency The concurrency level to use.
     * @param name A name for the job that will be displayed in the job list.
     * @return The new job.
     */
    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, int concurrency, String name) throws IOException {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, concurrency, name);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Creates a new job with inputs that is ready to be started.
     * @param closure The job action.
     * @param inputs The inputs that the job action will operate on.
     * @param concurrency The concurrency level to use.
     * @param name A name for the job that will be displayed in the job list.
     * @param becomeDelegate Controls whether the job instance should be set as <a href="https://groovy-lang.org/closures.html#_delegate_of_a_closure">the Closure's delegate</a>.
     * @return The new job.
     */
    public static REPLJob repljob(Closure<Serializable> closure, List<Serializable> inputs, int concurrency, String name, boolean becomeDelegate) throws IOException {
        REPLJob j = repljob(inputs != null ? closure::call : (x, y) -> closure.call(), inputs, concurrency, name);
        if (becomeDelegate) closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares unfinished and unsuccessful inputs for resuming.
     * @param key The key of the archived job to resume.
     * @param closure The job action.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, Closure<Serializable> closure) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob j = resume(key, (x, y) -> closure.call(x, y), false, true);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares unfinished and unsuccessful inputs for resuming.
     * @param key The key of the archived job to resume.
     * @param function The job action.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, BiFunction<Serializable, REPLJob, Serializable> function) throws IOException, ObjectStoreInvalidException, JobException {
        return resume(key, function, false, true);
    }

    /**
     * Loads an archived job instance and prepares unfinished and unsuccessful inputs for resuming.
     * @param key The key of the archived job to resume.
     * @param supplier The job action.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, Supplier<Serializable> supplier) throws IOException, ObjectStoreInvalidException, JobException {
        return resume(key, supplier, false, true);
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param key The key of the archived job to resume.
     * @param closure The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob j = resume(key, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param key The key of the archived job to resume.
     * @param closure The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @param becomeDelegate Controls whether the job instance should be set as <a href="https://groovy-lang.org/closures.html#_delegate_of_a_closure">the Closure's delegate</a>.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors, boolean becomeDelegate) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob j = resume(key, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        if (becomeDelegate) closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param key The key of the archived job to resume.
     * @param function The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, BiFunction<Serializable, REPLJob, Serializable> function, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob oldJob = jobs.get(key);
        if (oldJob != null && oldJob.getProgress().isActive()) throw new JobException("Can't resume a job that is still active");
        return resume(Path.of(REPL.getWorkDir().getAbsolutePath(), STATE_FILE_PREFIX + "-" + key + "." + STATE_FILE_SUFFIX).toFile(), function, retrySuccess, retryErrors);
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param key The key of the archived job to resume.
     * @param supplier The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(String key, Supplier<Serializable> supplier, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException, JobException {
        return resume(key, (x, y) -> supplier.get(), retrySuccess, retryErrors);
    }

    /**
     * Loads an archived job instance and prepares unfinished and unsuccessful inputs for resuming.
     * @param path The location of the archived job's state file.
     * @param closure The job action.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, Closure<Serializable> closure) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob j = resume(path, (x, y) -> closure.call(x, y), false, true);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares unfinished and unsuccessful inputs for resuming.
     * @param path The location of the archived job's state file.
     * @param function The job action.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, BiFunction<Serializable, REPLJob, Serializable> function) throws IOException, ObjectStoreInvalidException, JobException {
        return resume(path, function, false, true);
    }

    /**
     * Loads an archived job instance and prepares unfinished and unsuccessful inputs for resuming.
     * @param path The location of the archived job's state file.
     * @param supplier The job action.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, Supplier<Serializable> supplier) throws IOException, ObjectStoreInvalidException, JobException {
        return resume(path, supplier, false, true);
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param path The location of the archived job's state file.
     * @param closure The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob j = resume(path, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param path The location of the archived job's state file.
     * @param closure The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @param becomeDelegate Controls whether the job instance should be set as <a href="https://groovy-lang.org/closures.html#_delegate_of_a_closure">the Closure's delegate</a>.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, Closure<Serializable> closure, boolean retrySuccess, boolean retryErrors, boolean becomeDelegate) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob j = resume(path, (x, y) -> closure.call(x, y), retrySuccess, retryErrors);
        if (becomeDelegate) closure.setDelegate(j);
        return j;
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param path The location of the archived job's state file.
     * @param function The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, BiFunction<Serializable, REPLJob, Serializable> function, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException, JobException {
        REPLJob job = new REPLJob(path, TIMESTAMP_FORMAT.format(LocalDateTime.now()), function, retrySuccess, retryErrors);
        if (job.inputs == null || job.inputs.length == 0) throw new JobException("can't resume a job that had no inputs");
        if (jobs.putIfAbsent(job.getKey(), job) != null) throw new RuntimeException("key collision: " + job.getKey() + ", internal error?");
        if (REPL.HAVE_MYCORE) job.setInternalCallback(new REPLJobProcessableProxy(job));
        return job;
    }

    /**
     * Loads an archived job instance and prepares it for resuming.
     * @param path The location of the archived job's state file.
     * @param supplier The job action.
     * @param retrySuccess Controls whether previously successful inputs should be resubmitted.
     * @param retryErrors Controls whether previously unsuccessful inputs should be resubmitted.
     * @return A new instance of the archived job that can be resumed.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob resume(File path, Supplier<Serializable> supplier, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException, JobException {
        return resume(path, (x, y) -> supplier.get(), retrySuccess, retryErrors);
    }

    /**
     * Loads an archived job without preparing it for resuming. Such instances can't be started, but allow you to
     * inspect their state unaltered.
     * @param key The key of the job to load.
     * @return The job.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob load(String key) throws IOException, ObjectStoreInvalidException {
        return load(Path.of(REPL.getWorkDir().getAbsolutePath(), STATE_FILE_PREFIX + "-" + key + "." + STATE_FILE_SUFFIX).toFile());
    }

    /**
     * Loads an archived job without preparing it for resuming. Such instances can't be started, but allow you to
     * inspect their state unaltered.
     * @param path The location of the archived job's state file.
     * @return The job.
     * @throws IOException A file could not be accessed.
     */
    public static REPLJob load(File path) throws IOException, ObjectStoreInvalidException {
        return new REPLJob(path, null, null, false, false);
    }

    /**
     * Internal use only.
     */
    protected String key;
    /**
     * Internal use only.
     */
    protected final transient BiFunction<Serializable, REPLJob, Serializable> function;
    /**
     * Internal use only.
     */
    protected int concurrency;
    /**
     * Internal use only.
     */
    protected String name;
    /**
     * Internal use only.
     */

    protected Instant createdTimestamp;
    /**
     * Internal use only.
     */

    protected final String resumedKey;
    /**
     * Internal use only.
     */
    protected Serializable[] inputs;
    /**
     * Internal use only.
     */
    protected final InputResult[] results;
    /**
     * Internal use only.
     */
    private final ConcurrentLinkedQueue<REPLLogEntry> jobLog = new ConcurrentLinkedQueue<>(); // a wait-free list, ATTENTION: .size() is not O(1)
    /**
     * Internal use only.
     */
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
    /**
     * Internal use only.
     */
    private Instant startTimestamp = null;
    /**
     * Internal use only.
     */
    private Instant doneTimestamp = null;
    private transient Instant pausedSince = null;
    private transient Long pausedMillis = null;
    private transient Instant cancelledSince = null;
    private transient Integer cancelForceTimeoutSeconds = null;
    private transient CompletableFuture<JobProgress> future = null;

    private REPLJob(String key, BiFunction<Serializable, REPLJob, Serializable> function, List<Serializable> inputs, int concurrency, String name) throws IOException {
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
        } catch (InterruptedException | ObjectStoreInvalidException e) {
            throw new RuntimeException(e);
        }
    }

    private REPLJob(File path, String newKey, BiFunction<Serializable, REPLJob, Serializable> function, boolean retrySuccess, boolean retryErrors) throws IOException, ObjectStoreInvalidException {
        this.createdTimestamp = Instant.now();
        if (!path.isFile()) throw new IOException("job state file " + path + " not found");
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
                    throw new RuntimeException("entry of unexpected class " + data.getClass() + " in " + path + ", internal error?");
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
            } catch (InterruptedException | ObjectStoreInvalidException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Starts processing. Each job instance can only be started once.
     * @return A Future that will complete once the job is no longer active.
     */
    public Future<JobProgress> start() throws JobException {
        return start(null, null);
    }

    /**
     * Starts processing. Each job instance can only be started once.
     * @param threadFactory A custom Thread Factory that will be used for the worker threads.
     * @return A Future that will complete once the job is no longer active.
     */
    public Future<JobProgress> start(ThreadFactory threadFactory) throws JobException {
        return start(threadFactory, null);
    }

    /**
     * Starts processing. Each job instance can only be started once.
     * @param progressCallback A callback that can monitor and control the job's execution. Multiple callbacks can
     *                         be chained with {@link Consumer#andThen(Consumer)}. Note that your callback will be run
     *                         from the job's main control thread, so you should offload long-running activities triggered
     *                         by it to a different thread or risk lowering the job's throughput.
     * @return A Future that will complete once the job is no longer active.
     */
    public Future<JobProgress> start(Consumer<JobEvent> progressCallback) throws JobException {
        return start(null, progressCallback);
    }

    /**
     * Starts processing. Each job instance can only be started once.
     * @param threadFactory A custom Thread Factory that will be used for the worker threads.
     * @param progressCallback A callback that can monitor and control the job's execution. Multiple callbacks can
     *                         be chained with {@link Consumer#andThen(Consumer)}. Note that your callback will be run
     *                         from the job's main control thread, so you should offload long-running activities triggered
     *                         by it to a different thread or risk lowering the job's throughput.
     * @return A Future that will complete once the job is no longer active.
     */
    public Future<JobProgress> start(ThreadFactory threadFactory, Consumer<JobEvent> progressCallback) throws JobException {
        if (future != null) throw new JobException("this job has already been started");
        future = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                try {
                    doCancel(mayInterruptIfRunning ? 10 : null);
                } catch (JobException e) {
                    throw new RuntimeException(e);
                }
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

    private void execute(ThreadFactory threadFactory) throws JobException, InterruptedException {
        if (function == null) throw new JobException("can't execute a job without closure");
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        synchronized (this) {
            startTimestamp = Instant.now();
            errors = 0;
            success = 0;
            executor = new PausableThreadPoolExecutor(concurrency, queue, threadFactory, THREAD_PREFIX + key + " - ");
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
                            throw e;
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
                    // do not propagate the exception, we will proceed to wind down the future in an orderly fashion
                } catch (ExecutionException ex) {
                    throw new RuntimeException("internal error: exception from worker", ex);
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

    /**
     * Gets the job's current progress.
     * @return The job's current progress.
     */
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

    /**
     * Flag the input item with the specified index for resuming. This makes sure that it will be reprocessed regardless
     * of its previous success. You must call this method before starting the job.
     * @param index The index of the input item to resume.
     */
    public synchronized void retryIndex(int index) throws JobException {
        if (startTimestamp != null) throw new JobException("this job has already been started");
        results[index] = null;
    }

    /**
     * Cancels a running job. The instance will immediately transition to {@code CANCELLING} status and wait for all
     * currently active workers to finish their current inputs. When they all have terminated, the job will transition
     * to {@code CANCELLED} status. If some workers hang around for too long, it is safe to subsequently call
     * {@link REPLJob#cancelForce(int) cancelForce()} again on the job. Jobs without inputs must use
     * {@link REPLJob#cancelForce(int) cancelForce()}.
     * @return A flag indicating whether a concel was actually requested, or unneccesary because the job had already
     * finished in the meantime.
     */
    public boolean cancel() throws JobException {
        return doCancel(null);
    }

    /**
     * Forcefully cancels a running job. The instance will immediately transition to {@code CANCELLING} status and wait
     * the specified number of seconds for all currently active workers to finish their current inputs. If a worker is
     * still active after the timeout, an attempt is made to forecefully terminate its thread.
     * After this, the job will transition to {@code CANCELLED} status.
     * @param timeoutSeconds The number of seconds to wait before forcefully terminating a worker's thread.
     * @return A flag indicating whether a concel was actually requested, or unneccesary because the job had already
     * finished in the meantime.
     */
    public boolean cancelForce(int timeoutSeconds) throws JobException {
        return doCancel(timeoutSeconds);
    }

    private boolean doCancel(Integer forceTimeoutSeconds) throws JobException {
        if (doneTimestamp != null) return false;
        if (inputs == null && forceTimeoutSeconds == null) throw new JobException("Jobs without inputs can only be force-cancelled");
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

    /**
     * Pauses a running job. The job will immediately transition to {@code PAUSING} state and workers will no longer be
     * supplied with new inputs. After all workers have finisihed their current inputs, the job will transition to
     * {@code PAUSED}. Note that jobs without inputs can't be paused. The invocation will raise an exception in this
     * case.
     * @return A flag indicating whether the job was newly paused, or whether no actual change was made (e.g. because it
     * had already been paused).
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean pause() throws JobException {
        if (inputs == null) throw new JobException("Jobs without inputs can't be paused");
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

    /**
     * Restarts processing after a job has been paused. Note that jobs without inputs can't be paused. The invocation
     * will raise an exception in this case.
     * @return The length of this pause period in milliseconds, or {@code null} if the job was not actually unpaused
     * (e.g. because it was not actually paused before).
     */
    @SuppressWarnings("UnusedReturnValue")
    public Long unpause() throws JobException {
        return unpause(true);
    }

    private Long unpause(boolean callback) throws JobException {
        final Instant until;
        final long d;
        synchronized (this) {
            if (inputs == null) throw new JobException("Jobs without inputs can't be paused");
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

    /**
     * Get the job's key.
     * @return The job's key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the job's concurrency level.
     * @return The job's concurrency level.
     */
    public synchronized int getConcurrency() {
        return concurrency;
    }

    /**
     * Set the job's concurrency level. This method is safe to use while the job is running, unless you are using
     * {@link REPLJobCallbackAutoTune the Auto Tune feature}. If you reduce a job's
     * concurrency, your change will take effect gradually as old workers finish their prior input.
     * @param concurrency The new concurrency level.
     */
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

    /**
     * Get the job's name.
     * @return The job's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the job's current progress callback.
     * @return The job's current progress callback.
     */
    public Consumer<JobEvent> getProgressCallback() {
        return progressCallback;
    }

    /**
     * Sets the job's progress callback. This method is safe to use while the job is active.
     * @param progressCallback The new progress callback.
     */
    public void setProgressCallback(Consumer<JobEvent> progressCallback) {
        this.progressCallback = progressCallback;
    }

    /**
     * For internal purposes only. <b>Do not use.</b>
     * @param internalCallback For internal purposes only.
     */
    public synchronized void setInternalCallback(Consumer<JobEvent> internalCallback) {
        if (startTimestamp != null) throw new RuntimeException("Can't change the internal callback after the job has started. This methods is for internal use only. You should generally use setProgressCallback() instead.");
        this.internalCallback = internalCallback;
    }

    /**
     * For internal purposes only. <b>Do not use.</b>
     * @return The internal callback.
     */
    public Consumer<JobEvent> getInternalCallback() {
        return internalCallback;
    }

    /**
     * Gets the list of the job's inputs.
     * @return The job's input as an unmodifiable list.
     */
    public List<Serializable> getInputs() {
        return inputs != null ? Collections.unmodifiableList(Arrays.asList(inputs)) : null;
    }

    /**
     * Gets the list of the job's results.
     * @return The job's results as an unmodifiable list.
     */
    public List<InputResult> getResults() {
        return results != null ? Collections.unmodifiableList(Arrays.asList(results)) : null;
    }

    /**
     * Get the state file of this job.
     * @return The state file of this job.
     */
    public File getStateFile() {
        return Path.of(REPL.getWorkDir().getAbsolutePath(), STATE_FILE_PREFIX + "-" + this.key + "." + STATE_FILE_SUFFIX).toFile();
    }

    /**
     * Get the job's private log, which contains messages logged with {@link REPLJob#info(Object...)} and similar methods.
     * @return The job's private log.
     */
    public List<REPLLogEntry> getJobLog() {
        return new ArrayList<>(jobLog);
    }

    /**
     * Returns a future that you can wait on. It will be completed as soon as the job has finished.
     * @return The future, or {@code null} if the job has not yet been started.
     */
    public CompletableFuture<JobProgress> getFuture() {
        return future;
    }

    /**
     * Gets the key of the job which was resumed with this instance.
     * @return The previous key, or {@code null} if this job did not resume another one.
     */
    public String getResumedKey() {
        return resumedKey;
    }

    /**
     * Gets the time at which this instance was created. This is not the start time, which you can instead
     * determine from {@link REPLJob#getProgress()}.
     * @return The time of the job's instantiation.
     */
    public Instant getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Allows serializing this object to an {@link ObjectOutputStream}.
     * @param out The output stream.
     * @throws IOException Data could not be written.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(key);
        out.writeInt(concurrency);
        out.writeObject(name);
        out.writeObject(createdTimestamp);
        out.writeObject(inputs);
    }

    /**
     * Allows deserializing this object from an {@link ObjectInputStream}.
     * @param in The input stream.
     * @throws IOException Data could not be read.
     * @throws ClassNotFoundException A class could not be loaded.
     */
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

    /**
     * Returns the latest entry from the job's private log.
     * @return The latest log entry.
     */
    public REPLLogEntry getLastLogEntry() {
        return lastLogEntry;
    }

    /**
     * Logs a message with log level {@code TRACE} to the global REPL log, as well as the job's private persistent log.
     * @param args The log message.
     * @return The log entry which has been persisted to the logs.
     */
    public REPLLogEntry trace(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.trace(args);
        doLog(e);
        return e;
    }

    /**
     * Logs a message with log level {@code DEBUG} to the global REPL log, as well as the job's private persistent log.
     * @param args The log message.
     * @return The log entry which has been persisted to the logs.
     */
    public REPLLogEntry debug(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.debug(args);
        doLog(e);
        return e;

    }

    /**
     * Logs a message with log level {@code INFO} to the global REPL log, as well as the job's private persistent log.
     * @param args The log message.
     * @return The log entry which has been persisted to the logs.
     */
    public REPLLogEntry info(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.info(args);
        doLog(e);
        return e;

    }

    /**
     * Logs a message with log level {@code WARN} to the global REPL log, as well as the job's private persistent log.
     * @param args The log message.
     * @return The log entry which has been persisted to the logs.
     */
    public REPLLogEntry warn(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.warn(args);
        doLog(e);
        return e;

    }

    /**
     * Logs a message with log level {@code ERROR} to the global REPL log, as well as the job's private persistent log.
     * @param args The log message.
     * @return The log entry which has been persisted to the logs.
     */
    public REPLLogEntry error(Object... args) {
        if (args != null && args.length > 0 && args[0] instanceof String s) args[0] = "Job " + key + ": " + s;
        REPLLogEntry e = REPLLog.error(args);
        doLog(e);
        return e;
    }

    /**
     * Logs a message with log level {@code INFO} to the global REPL log, as well as the job's private persistent log.
     * @param args The log message.
     * @return The log entry which has been persisted to the logs.
     */
    public REPLLogEntry log(Object... args) {
        return info(args);
    }

    /**
     * Logs a given message to the global REPL log, as well as the job's private persistent log.
     * @param entry The log message.
     */
    public void log(REPLLogEntry entry) {
        log(entry, DEFAULT_LOG_TARGETS);
    }


    /**
     * Logs a given message to the global REPL log, as well as the job's private persistent log.
     * @param entry The log message.
     * @param targets The log targets. The job's internal log is always implicitly a target.
     * @param streams Additional PrintStreams that will receive this message.
     */
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
