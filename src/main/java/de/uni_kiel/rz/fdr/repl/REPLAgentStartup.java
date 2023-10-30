// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.groovy.GroovySourceDirectory;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyClassLoader;
import groovy.util.Eval;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Set;
import java.util.stream.Collectors;

import static de.uni_kiel.rz.fdr.repl.REPLLog.INTERNAL_LOG_TARGETS;

/**
 * This class control the startup process when using the agent. It is only used in a static context.
 */
public class REPLAgentStartup {
    /**
     * The REPL's configured class path
     */
    public static String classPath = System.getProperty("CAU.Groovy.ClassPath", System.getProperty("java.class.path", "."));
    /**
     * The REPL's configured ClassLoader
     */
    public static ClassLoader classLoader = null;
    private static boolean started = false;

    @SuppressWarnings("unused")
    protected static void start(ClassLoader forceClassLoader) throws IOException, InsufficientAccessRightsException, GroovySourceDirectory.CompilationException, StartupException, GroovySourceDirectory.ClassLoadingException {
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
        boolean scl = System.getProperty("CAU.Groovy.UseSystemClassLoader", "false").equalsIgnoreCase("true");
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
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Error during compilation: {}", e), INTERNAL_LOG_TARGETS);
            throw new GroovySourceDirectory.CompilationException("Error during compilation", e);
        } catch (GroovySourceDirectory.ClassLoadingException e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Class loading error during compilation: {}", e), INTERNAL_LOG_TARGETS);
            throw e;
        }

        try {
            // check our environment
            REPL.discoverEnvironment(classLoader);
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, REPL.getVersionString()), INTERNAL_LOG_TARGETS);
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
            // password
            String pass = System.getProperty("CAU.REPL.SSH.Password");
            // clear the property so the plaintext does not leak into debug dumps, logs, etc.
            System.clearProperty("CAU.REPL.SSH.Password");
            if (pass != null && pass.isBlank()) pass = null;
            String passCmd = System.getProperty("CAU.REPL.SSH.PasswordCommand");
            if (passCmd != null && passCmd.isBlank()) passCmd = null;
            if (pass != null && passCmd != null)
                throw new StartupException("You can't set CAU.REPL.SSH.Password and CAU.REPL.SSH.PasswordCommand at the same time.");
            if (passCmd != null) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Fetching password from {}", passCmd), INTERNAL_LOG_TARGETS);
                String output;
                try {
                    output = Helpers.shellCommand(passCmd);
                } catch (ExternalCommandException e) {
                    throw new StartupException("Password command failure", e);
                }
                if (output.split(System.lineSeparator()).length == 0) throw new StartupException("Password command returned no data");
                if (output.split(System.lineSeparator()).length != 1)
                    throw new StartupException("Password command returned more than one line");
                pass = output.split(System.lineSeparator())[0];
                if (pass.isBlank()) throw new StartupException("Password command returned empty password");
            } else if (pass == null) {
                pass = randomPassword(new SecureRandom().nextInt(30, 35));
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Session Password auto-generated: {}", pass),
                        Set.of(REPLLog.LOG_TARGETS.STDERR));
            }
            REPL repl = new REPL(addr, port, timeout, new File(workDir), classLoader);
            repl.setAuthenticator(new REPLPasswordAuthenticator(pass));
            // editor
            String editor = System.getProperty("CAU.REPL.Editor");
            if (editor == null) editor = "vim";
            repl.addStartupCommand(":set editor '" + editor + "'");
            String editorssh = System.getProperty("CAU.REPL.EditorSSH");
            if (editorssh == null)
                editorssh = "vim --not-a-term -c \"set mouse=\" -c \"set ttymouse=\" -c \"set filetype=groovy\"";
            repl.addStartupCommand(":set editorssh '" + editorssh + "'");

            // start the REPL
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: listening on {}:{}", repl.getListenAddr(), repl.getPort()), INTERNAL_LOG_TARGETS);
            repl.start();
        } catch (StartupException | RuntimeException e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Startup failure: {}", e.getMessage(), e), INTERNAL_LOG_TARGETS);
            throw e;
        }
    }

    private static String randomPassword(int len) {
        return new SecureRandom()
                .ints(len, 33, 127)
                .mapToObj(i -> String.valueOf((char)i))
                .collect(Collectors.joining());
    }

    /**
     * You can run this .jar as a standalone executable and get a SSH interface only.
     * @param args The command-line paramenters.
     * @throws IOException A file could not be accessed.
     * @throws InterruptedException The program was interrupted.
     */
    public static void main(String[] args) throws IOException, InterruptedException, InsufficientAccessRightsException, GroovySourceDirectory.CompilationException, StartupException, GroovySourceDirectory.ClassLoadingException {
        if (!started) {
            System.out.println("Starting Agent...");
            REPLAgentStartup.start(null);
        }
        System.out.println("Press CTRL-C to quit");
        Object dummy = new Object();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (dummy) { dummy.wait(); }
    }

    public static class ExternalCommandException extends Exception {
        public ExternalCommandException(String message) {
            super(message);
        }

        @SuppressWarnings("unused")
        public ExternalCommandException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class StartupException extends Exception {
        public StartupException(String message) {
            super(message);
        }

        public StartupException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
