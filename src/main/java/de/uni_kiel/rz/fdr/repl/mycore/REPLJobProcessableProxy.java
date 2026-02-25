// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

package de.uni_kiel.rz.fdr.repl.mycore;

import de.uni_kiel.rz.fdr.repl.REPLJob;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.common.processing.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class REPLJobProcessableProxy implements MCRProcessable, Consumer<REPLJob.JobEvent> {

    public static final String COLLECTION_NAME = "Repl Jobs";
    private static MCRProcessableRegistry registry = null;
    private static MCRProcessableDefaultCollection collection = null;

    private final REPLJob job;
    private Exception error = null;
    private Integer progress = null;
    private String progressText = null;
    private MCRProcessableStatus processableStatus = MCRProcessableStatus.CREATED;
    private final LinkedHashSet<MCRProcessableStatusListener> statusListeners = new LinkedHashSet<>();
    private final LinkedHashSet<MCRProgressableListener> progressListeners = new LinkedHashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setDaemon(true);
        return t;
    });

    public REPLJobProcessableProxy(REPLJob job) {
        this.job = job;
        register();
    }

    private void register() {
        synchronized (REPLJobProcessableProxy.class) {
            if (registry == null) {
                registry = MCRProcessableRegistry.getSingleInstance();
                collection = new MCRProcessableDefaultCollection(COLLECTION_NAME);
                registry.register(collection);
            }
            collection.add(this);
        }
    }

   @Override
    public String getName() {
        return job.getName();
    }

    @Override
    public String getUserId() {
        return MCRSystemUserInformation.getSuperUserInstance().getUserID();
    }

    @Override
    public MCRProcessableStatus getStatus() {
        return processableStatus;
    }

    @Override
    public Throwable getError() {
        if (error != null) return error;
        if (job.getProgress().errors() == 0) return null;
        error = job.getResults().stream().filter(r -> r != null && r.error() != null).map(REPLJob.InputResult::error).findFirst().orElse(null);
        return error;
    }

    @Override
    public Instant getStartTime() {
        return job.getProgress().startTimestamp().toInstant();
    }

    @Override
    public Instant getCreateTime() {
        return job.getCreatedTimestamp();
    }

    @Override
    public Instant getEndTime() {
        return job.getProgress().doneTimestamp().toInstant();
    }

    @Override
    public Map<String, Object> getProperties() {
        REPLJob.JobProgress p = job.getProgress();
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("Key", job.getKey());
        m.put("Resumed Key", job.getResumedKey());
        m.put("State", p.state());
        m.put("Last Log Message", job.getLastLogEntry());
        m.put("Concurrency", job.getConcurrency());
        m.put("Active Threads", p.activeThreads());
        m.put("Next Input", p.nextInput());
        m.put("Total Inputs", p.totalInputs());
        m.put("Remaining Inputs", p.remainingInputs());
        m.put("Success", p.success());
        m.put("Skipped Success", p.skippedSuccess());
        m.put("Errors", p.errors());
        m.put("Skipped Errors", p.skippedErrors());
        m.put("Percent Done", p.percentDone());
        m.put("Paused Since", p.pausedSince());
        m.put("Cancelled Since", p.cancelledSince());
        m.put("ETA", p.eta());
        m.put("ETA Duration", p.etaText());
        for (String s : m.entrySet().stream().filter(e -> e.getValue() == null).map(Map.Entry::getKey).toList()) {
            m.remove(s);
        }
        return m;
    }

    @Override
    public void addStatusListener(MCRProcessableStatusListener mcrProcessableStatusListener) {
        synchronized (statusListeners) {
            statusListeners.add(mcrProcessableStatusListener);
        }
    }

    @Override
    public void removeStatusListener(MCRProcessableStatusListener mcrProcessableStatusListener) {
        synchronized (statusListeners) {
            statusListeners.remove(mcrProcessableStatusListener);
        }
    }

    @Override
    public void addProgressListener(MCRProgressableListener mcrProgressableListener) {
        synchronized (progressListeners) {
            progressListeners.add(mcrProgressableListener);
        }
    }

    @Override
    public void removeProgressListener(MCRProgressableListener mcrProgressableListener) {
        synchronized (progressListeners) {
            progressListeners.remove(mcrProgressableListener);
        }
    }

    @Override
    public Integer getProgress() {
        return progress;
    }

    @Override
    public String getProgressText() {
        return progressText;
    }

    @Override
    public void accept(REPLJob.JobEvent ev) {
        // progress
        REPLJob.JobProgress p = ev.job().getProgress();

        // progress text
        String s = p.state() + ", " + p.errors() + " errors";
        if (p.etaText() != null) {
            s += ", eta " + p.etaText();
        }
        String oldProgressText = progressText;
        progressText = s;
        Integer oldProgress = progress;
        progress = p.percentDone();

        // status
        MCRProcessableStatus oldStatus = getStatus();
        switch (p.state()) {
            case RUNNING, PAUSING, PAUSED, CANCELLING -> processableStatus = MCRProcessableStatus.PROCESSING;
            case INTERNAL_ERROR, COMPLETED_WITH_ERRORS -> processableStatus = MCRProcessableStatus.FAILED;
            case CANCELLED -> processableStatus = MCRProcessableStatus.CANCELED;
            case COMPLETED_SUCCESSFULLY -> processableStatus = MCRProcessableStatus.SUCCESSFUL;
            case NOT_YET_STARTED -> processableStatus = MCRProcessableStatus.CREATED;
        }

        // send updates
        if (!progress.equals(oldProgress)) {
            synchronized (progressListeners) {
                for (MCRProgressableListener listener : progressListeners) {
                    executor.submit(() -> listener.onProgressChange(this, oldProgress, progress));
                }
            }
        }

        if (!progressText.equals(oldProgressText)) {
            synchronized (progressListeners) {
                for (MCRProgressableListener listener : progressListeners) {
                    executor.submit(() -> listener.onProgressTextChange(this, oldProgressText, progressText));
                }
            }
        }

        if (!processableStatus.equals(oldStatus)) {
            synchronized (statusListeners) {
                for (MCRProcessableStatusListener statusListener : statusListeners) {
                    executor.submit(() -> statusListener.onStatusChange(this, oldStatus, processableStatus));
                }
            }
        }
    }
}
