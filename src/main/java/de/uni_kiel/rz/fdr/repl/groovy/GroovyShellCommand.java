// (C) Copyright 2015-2022 Denis Bazhenov
// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT
// Source: https://github.com/bazhenov/groovy-shell-server/blob/c6e9781498be108529e2eeaa173cbbb19b805a47/groovy-shell-server/src/main/java/me/bazhenov/groovysh/GroovyShellCommand.java

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.*;
import groovy.lang.Binding;
import groovy.lang.Closure;
import org.apache.groovy.groovysh.Groovysh;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.codehaus.groovy.tools.shell.IO;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyShellService.SHELL_KEY;
import static org.codehaus.groovy.tools.shell.IO.Verbosity.INFO;

public class GroovyShellCommand implements Command {

    public static final Map<GroovyShellCommand, Binding> activeShells = Collections.synchronizedMap(new WeakHashMap<>());
    public static final String THREAD_PREFIX = REPL.THREAD_PREFIX + "client: ";

    private final SshServer sshd;
    private final Map<String, Object> bindings;
    private final List<String> defaultScripts;
    private InputStream in;
    private OutputStream out;
    private OutputStream err;
    private ExitCallback callback;
    private Thread wrapper;
    private final AtomicBoolean isServiceAlive;
    private final ClassLoader classLoader;

    GroovyShellCommand(SshServer sshd, Map<String, Object> bindings, List<String> defaultScripts,
                       AtomicBoolean isServiceAlive, ClassLoader classLoader) {
        this.sshd = sshd;
        this.bindings = bindings;
        this.defaultScripts = defaultScripts;
        this.isServiceAlive = isServiceAlive;
        try {
            classLoader.loadClass("org.apache.groovy.groovysh.Groovysh");

        } catch (ClassNotFoundException e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "REPL: cannot use supplied ClassLoader for the groovy shell, substituting default."), REPLLog.INTERNAL_LOG_TARGETS);
            classLoader = GroovyShellCommand.class.getClassLoader();
        }
        this.classLoader = classLoader;
    }

    @Override
    public void setInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void setErrorStream(OutputStream err) {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.callback = callback;
    }

    @Override
    public void start(ChannelSession session, Environment env) {
        TtyFilterOutputStream out = new TtyFilterOutputStream(this.out, isServiceAlive);
        TtyFilterOutputStream err = new TtyFilterOutputStream(this.err, isServiceAlive);

        IO io = new IO(in, out, err);
        io.setVerbosity(INFO);
        Binding binding = createBinding(bindings, in, out, err, env);
        Groovysh shell = new Groovysh(this.classLoader, binding, io);
        shell.setErrorHook(new Closure<>(this) {
            @Override
            public Object call(Object... args) {
                if (args[0] instanceof Exception ex) {
                    REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Shell command error: {}",
                            ex, Arrays.stream(ex.getStackTrace()).map(s -> "  " + s.toString()).collect(Collectors.joining("\n"))),
                            REPLLog.INTERNAL_LOG_TARGETS);
                }
                if (args[0] instanceof InterruptedIOException || args[0] instanceof SshException) {
                    // Stopping groovysh thread in case of broken client channel
                    shell.getRunner().setRunning(false);
                }
                return shell.getDefaultErrorHook().call(args);
            }
        });

        try {
            loadDefaultScripts(shell);
        } catch (Exception e) {
            createPrintStream(err).println("Unable to load default scripts: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }

        session.getSession().setAttribute(SHELL_KEY, shell);

        Runnable runnable = () -> {
            try {
                SshTerminal.registerEnvironment(env);
                shell.run("");
                callback.onExit(0);
            } catch (RuntimeException | Error e) {
                callback.onExit(-1, e.getMessage());
            } finally {
                activeShells.remove(this);
            }
        };
        wrapper = newThread(runnable, session);
        wrapper.start();
        activeShells.put(this, binding);
    }

    private static Thread newThread(Runnable r, ChannelSession session) {
        String address = session.getSession().getIoSession().getRemoteAddress().toString();
        String threadName = THREAD_PREFIX + address;
        return new Thread(r, threadName);
    }

    private Binding createBinding(Map<String, Object> objects, InputStream in, OutputStream out, OutputStream err, Environment env) {
        Binding binding = new Binding();

        if (objects != null) {
            for (Map.Entry<String, Object> row : objects.entrySet()) {
                binding.setVariable(row.getKey(), row.getValue());
            }
        }

        binding.setVariable("_cauIn", in);
        binding.setVariable("_cauOut", out);
        binding.setVariable("_cauErr", err);
        binding.setVariable("_cauEnv", env.getEnv());
        binding.setVariable("out", createPrintStream(out));
        binding.setVariable("err", createPrintStream(err));
        binding.setVariable("activeSessions", new Closure<List<AbstractSession>>(this) {
            @Override
            public List<AbstractSession> call() {
                return sshd.getActiveSessions();
            }
        });

        return binding;
    }

    private static PrintStream createPrintStream(OutputStream out) {
        return new PrintStream(out, true, StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked"})
    private void loadDefaultScripts(Groovysh shell) {
        if (!defaultScripts.isEmpty()) {
            Closure<Groovysh> defaultResultHook = shell.getResultHook();

            try {
                // Set a "no-op closure so we don't get per-line value output when evaluating the default script
                shell.setResultHook(new Closure<Groovysh>(this) {
                    @Override
                    public Groovysh call(Object... args) {
                        return shell;
                    }
                });

                org.apache.groovy.groovysh.Command cmd = shell.getRegistry().find(":load");
                for (String script : defaultScripts) {
                    cmd.execute(singletonList(script));
                }
            } finally {
                // Restoring original result hook
                shell.setResultHook(defaultResultHook);
            }
        }
    }

    @Override
    public void destroy(ChannelSession channel) {
        wrapper.interrupt();
    }
}
