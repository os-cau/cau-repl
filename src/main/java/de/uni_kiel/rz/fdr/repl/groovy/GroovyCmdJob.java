// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPLJob;
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class GroovyCmdJob extends CommandSupport {

    private static final HashMap<Integer, String> speedDial = new HashMap<>();
    private static final HashMap<String, Integer> speedDialInverse = new HashMap<>();
    private static final AtomicInteger speedDialCounter = new AtomicInteger(1);

    private final OutputStream out;

    protected GroovyCmdJob(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
        out = (OutputStream) shell.execute("_cauOut");
    }

    @Override
    public String getDescription() {
        return "Lists or manages jobs";
    }

    @Override
    public String getHelp() {
        return "Lists or manages jobs";
    }

    @Override
    public String getUsage() {
        return "[] | [index] | [key] | pause [index|key] | unpause [index|key] | cancel [index|key] | cancelforce[index|key] | archive [index|key] | archived | prune";
    }

    private static String jobToList(REPLJob j) {
        String s = "[" + speedDialInverse.get(j.getKey()) + "] " + j.getKey() + " - " + j.getName() + " - ";
        REPLJob.JobProgress p = j.getProgress();
        s += p.state() + " (" + p.activeThreads() + "/" + j.getConcurrency() + " threads active), " + p.percentDone() + "%";
        if (p.etaText() != null) s += ", eta " + p.etaText();
        if (p.errors() > 0) s += ", " + p.errors() + " errors";
        return s;
    }

    @Override
    public Object execute(List<String> list) {
        if (list.isEmpty()) {
            // list command
            List<REPLJob> jobList = REPLJob.list();
            String l;
            synchronized (speedDial) {
                for (REPLJob j : jobList)
                    if (!speedDialInverse.containsKey(j.getKey())) {
                        speedDialInverse.put(j.getKey(), speedDialCounter.addAndGet(1) - 1);
                        speedDial.put(speedDialInverse.get(j.getKey()), j.getKey());
                    }
                l = jobList.stream()
                        .map(GroovyCmdJob::jobToList)
                        .collect(Collectors.joining("\n"));
            }
            return("\n" + l + "\n");
        }

        if (list.get(0).matches("^[0-9-]+$")) {
            // progress command
            REPLJob j = lookup(list.get(0));
            if (j == null) fail("no such job");
            assert j != null;
            new PrintWriter(out, true, StandardCharsets.UTF_8).println(j.getProgress());
            return j;
        }

        switch(list.get(0)) {
            case "archived" -> {
                if (list.size() != 1) fail("the archived command does not take any arguments");
                return String.join("\n", REPLJob.listArchived());
            }
            case "prune" -> {
                if (list.size() != 1) fail("the prune command does not take any arguments");
                List<String> p = REPLJob.pruneArchived();
                if (p.isEmpty()) return "pruned: nothing";
                return "pruned: " + String.join(", ", p);
            }
        }

        if (list.size() == 1) fail(list.get(0) + " is not a valid argument-less command");
        if (list.size() > 2) fail("please supply at most 2 arguments");
        REPLJob j = lookup(list.get(1));
        if (j == null) fail("no such job");
        assert j != null;
        try {
            switch (list.get(0)) {
                case "pause" -> {
                    return j.pause();
                }
                case "unpause" -> {
                    return j.unpause();
                }
                case "cancel" -> {
                    return j.cancel();
                }
                case "cancelforce" -> {
                    return j.cancelForce(10);
                }
                case "archive" -> {
                    return REPLJob.archive(j);
                }
                default -> fail("unknown subcommand: " + list.get(0));
            }
        } catch (REPLJob.JobException e) {
            throw new RuntimeException(e);
        }
        // NOTREACHED
        return null;
    }

    private static REPLJob lookup(String keyOrIdx) {
        try {
            return lookupSpeedDial(Integer.valueOf(keyOrIdx));
        } catch (NumberFormatException e) {
            return REPLJob.get(keyOrIdx);
        }
    }

    private static REPLJob lookupSpeedDial(int idx) {
        String key = null;
        synchronized (speedDial) {
            key = speedDial.get(idx);
        }
        if (key == null) return null;
        return REPLJob.get(key);
    }
}

