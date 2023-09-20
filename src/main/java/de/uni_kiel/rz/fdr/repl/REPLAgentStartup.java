// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.groovy.GroovySourceDirectory;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyClassLoader;
import groovy.util.Eval;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Set;
import java.util.stream.Collectors;

import static de.uni_kiel.rz.fdr.repl.REPLLog.INTERNAL_LOG_TARGETS;

public class REPLAgentStartup {
    public static String classPath = System.getProperty("CAU.Groovy.ClassPath", System.getProperty("java.class.path", "."));
    public static ClassLoader classLoader = null;
    private static boolean started = false;

    @SuppressWarnings("unused")
    public static void start(ClassLoader forceClassLoader) throws IOException {
        started = true;

        // use groovy expando metaclasses globally
        ExpandoMetaClass.enableGlobally();

        // enable debug tracing
        REPLLog.TRACE = System.getProperty("CAU.REPL.Log.Trace", "false").equalsIgnoreCase("true");

        // are we in support mode?
        boolean supportMode = (System.getProperty("CAU.JavaAgent.SupportMode", "false").equalsIgnoreCase("true"));

        // set up work dir before compiling, but not if we are in Support Mode
        String workDir = System.getProperty("CAU.REPL.WorkDir");
        if (workDir == null) {
            workDir = REPL.DEFAULT_WORK_SUBDIR;
        }
        if (!supportMode) REPL.setWorkDir(new File(workDir));

        // initialize sources first
        boolean scl = System.getProperty("CAU.Groovy.UseSystemClassLoader", "true").equalsIgnoreCase("true");
        boolean defer = System.getProperty("CAU.Groovy.DeferMetaClasses", "false").equalsIgnoreCase("true");
        boolean reorderSources = !System.getProperty("CAU.Groovy.ReorderSources", "true").equalsIgnoreCase("false");
        if (forceClassLoader != null) {
            classLoader = forceClassLoader;
        } else if (supportMode || scl) {
            // or {REPLAgent.class/this.getClass()}.getClassLoader(), or ClassLoader.getPlatformClassLoader(), or Thread.currentThread().getContextClassLoader()
            classLoader = ClassLoader.getSystemClassLoader();
        } else {
            classLoader = new GroovyClassLoader();
        }
        String s = System.getProperty("CAU.Groovy.SourceDirs");
        try {
            if (s != null) {
                for (String d : s.split(",")) {
                    if (d.isEmpty()) continue;
                    Path p = Path.of(d).toAbsolutePath();
                    new GroovySourceDirectory(p, classLoader, classPath, defer || supportMode, reorderSources); // in Support support mode, we defer the loading of MetaClasses. this is necessary, because setting a metaclass forces static class initializers to run, and MyCoRe needs a specific ordering
                }
            }
        } catch (Exception e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Error during compilation: {}", e), INTERNAL_LOG_TARGETS);
            throw new RuntimeException(e);
        }

        // check our environment
        REPL.discoverEnvironment(classLoader);
        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "cau-repl v{}-{}", REPL.VERSION, REPL.HAVE_GPL ? "gpl" : "nogpl"), INTERNAL_LOG_TARGETS);
        boolean replEnabled = System.getProperty("CAU.REPL.Enabled", "true").equalsIgnoreCase("true");
        if (!replEnabled) REPLBreakpoint.MAX_BREAKPOINTS = 0; // disable breakpoints if we can't act on them

        // run autoexec command
        String autoexec = System.getProperty("CAU.JavaAgent.AutoExec");
        if (autoexec != null) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Running AutoExec Command:", autoexec), INTERNAL_LOG_TARGETS);
            Eval.me(autoexec);
        }

        // bail if the repl was not configured
        if (supportMode || !replEnabled) return;

        // now initialize the REPL
        String maxbp = System.getProperty("CAU.REPL.MaxBreakpoints");
        if (maxbp != null) REPLBreakpoint.MAX_BREAKPOINTS = Integer.parseInt(maxbp);
        String addr = System.getProperty("CAU.REPL.SSH.ListenAddr");
        s = System.getProperty("CAU.REPL.SSH.ListenPort");
        Integer port = (s == null) ? null : Integer.valueOf(s);
        s = System.getProperty("CAU.REPL.SSH.Timeout");
        Integer timeout = (s == null) ? null : Integer.valueOf(s);
        String pass = System.getProperty("CAU.REPL.SSH.Password");
        if (pass == null) {
            pass = randomPassword(new SecureRandom().nextInt(30, 35));
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Session Password auto-generated: {}", pass),
                    Set.of(REPLLog.LOG_TARGETS.STDERR));
        }
        REPL repl = new REPL(addr, port, timeout, new File(workDir), classLoader);
        repl.setAuthenticator(new REPLPasswordAuthenticator(pass));

        String editor = System.getProperty("CAU.REPL.Editor");
        if (editor == null) editor = "vim";
        repl.addStartupCommand(":set editor '" + editor +"'");
        String editorssh = System.getProperty("CAU.REPL.EditorSSH");
        if (editorssh == null) editorssh = "vim --not-a-term -c \"set mouse=\" -c \"set ttymouse=\" -c \"set filetype=groovy\"";
        repl.addStartupCommand(":set editorssh '" + editorssh +"'");

        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: listening on {}:{}", repl.getListenAddr(), repl.getPort()), INTERNAL_LOG_TARGETS);
        try {
            repl.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String randomPassword(int len) {
        return new SecureRandom()
                .ints(len, 33, 127)
                .mapToObj(i -> String.valueOf((char)i))
                .collect(Collectors.joining());
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (!started) {
            System.out.println("Starting Agent...");
            REPLAgentStartup.start(null);
        }
        System.out.println("Press CTRL-C to quit");
        Object dummy = new Object();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (dummy) { dummy.wait(); }
    }
}
