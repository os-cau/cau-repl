// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.groovy.GroovyShellService;
import groovy.lang.GroovyClassLoader;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unused")
public class REPL {

    public static final String VERSION = "0.1.0-SNAPSHOT";
    public static final Session.AttributeKey<String> USER_KEY = new Session.AttributeKey<>();
    public static final String DEFAULT_WORK_SUBDIR = "cau-repl";
    public static boolean HAVE_GPL = true;
    public static boolean HAVE_MYCORE = false;
    public static boolean HAVE_MYCORE_MODS = false;

    private static final File[] workDir = {null};
    private static final ClassLoader[] groovyClassLoader = {null};

    private String listenAddr = "127.0.0.1";
    private int port = 8512;
    private int timeoutSecs = 12 * 60 * 60;

    private PasswordAuthenticator authenticator = null;
    private final ArrayList<String> startupScripts = new ArrayList<>();
    private final ArrayList<String> startupCommands = new ArrayList<>();
    private final ArrayList<SessionListener> sessionListeners = new ArrayList<>();


    public REPL(String listenAddr, Integer port, Integer timeoutSecs, File workDir, ClassLoader groovyClassLoader) throws IOException {
        if (listenAddr != null) this.listenAddr = listenAddr;
        if (port != null) this.port = port;
        if (timeoutSecs != null) this.timeoutSecs = timeoutSecs;

        setWorkDir(workDir);

        synchronized (REPL.groovyClassLoader) {
            if (groovyClassLoader != null && REPL.groovyClassLoader[0] == null) {
                REPL.groovyClassLoader[0] = groovyClassLoader;
            } else if (groovyClassLoader != null && REPL.groovyClassLoader[0] != null && !groovyClassLoader.equals(REPL.groovyClassLoader[0])) {
                throw new RuntimeException("REPL global ClassLoader has already been initialized");
            } else if (groovyClassLoader == null && REPL.groovyClassLoader[0] == null) {
                REPL.groovyClassLoader[0] = new GroovyClassLoader();
            }
        }

        addInternalStartupScript("/repl-init.cmd");
        addInternalStartupScript("/repl-groovy-base.groovy");
        addStartupCommand(":set interpreterMode false");
    }

    public void start() throws IOException {
        GroovyShellService gs = new GroovyShellService(port, groovyClassLoader[0]);
        gs.setHost(listenAddr);
        gs.setIdleTimeOut(1000L * timeoutSecs);
        gs.setDisableImportCompletions(false);
        if (authenticator != null) gs.setPasswordAuthenticator(authenticator);

        for (String script : startupScripts) gs.addDefaultScript(script);
        if (!startupCommands.isEmpty()) gs.addDefaultScript(startupCommandsToTempfile());

        gs.start(getWorkDir(), sessionListeners);
    }

    public String getListenAddr() {
        return listenAddr;
    }

    public int getPort() {
        return port;
    }

    public int getTimeoutSecs() {
        return timeoutSecs;
    }

    public PasswordAuthenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(PasswordAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public void addStartupScript(String path) {
        startupScripts.add(path);
    }

    public List<String> getStartupScripts() {
        return new ArrayList<>(startupScripts);
    }

    public void addInternalStartupScript(String path) {
        try {
            File tmpfile = File.createTempFile("cau-repl.", ".tmp");
            tmpfile.deleteOnExit();
            Files.copy(Objects.requireNonNull(getClass().getResourceAsStream(path)), tmpfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            addStartupScript(tmpfile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getStartupCommands() {
        return new ArrayList<>(startupCommands);
    }

    public void addStartupCommand(String cmd) {
        startupCommands.add(cmd);
    }

    public List<SessionListener> getSessionListeners() {
        return new ArrayList<>(sessionListeners);
    }

    public void addSessionListener(SessionListener sessionListener) {
        sessionListeners.add(sessionListener);
    }

    public static File getWorkDir() {
        synchronized (workDir) {
            return workDir[0];
        }
    }

    public static ClassLoader getGroovyClassLoader() {
        synchronized (groovyClassLoader) {
            return groovyClassLoader[0];
        }
    }


    private String startupCommandsToTempfile() throws IOException {
        File tmpfile = File.createTempFile("cau-repl.", ".tmp");
        tmpfile.deleteOnExit();
        try (FileWriter fw = new FileWriter(tmpfile, StandardCharsets.UTF_8, true)) {
            for (String cmd : startupCommands) {
                fw.write(cmd);
                fw.write(System.lineSeparator());
            }
        }
        return tmpfile.getAbsolutePath();
    }

    public static void setWorkDir(File workDir) throws IOException {
        synchronized (REPL.workDir) {
            if (workDir != null) workDir = workDir.getAbsoluteFile(); // always use absolute paths
            if (workDir != null && workDir.equals(REPL.workDir[0])) return; //no-op
            if (workDir != null && REPL.workDir[0] == null) {
                // set new dir for the first time
                REPL.workDir[0] = workDir;
            } else if (workDir != null && REPL.workDir[0] != null && !workDir.equals(REPL.workDir[0])) {
                // will not change after set
                throw new RuntimeException("REPL work directory has already been initialized to " + REPL.workDir[0]);
            } else if (workDir == null && REPL.workDir[0] == null) {
                // set a default value
                REPL.workDir[0] = new File(DEFAULT_WORK_SUBDIR).getAbsoluteFile();
            }
            boolean newDir = false;
            if (!REPL.workDir[0].isDirectory()) {
                if (!REPL.workDir[0].mkdirs()) throw new RuntimeException("Failed to create work directory '" + REPL.workDir[0].getAbsolutePath() + "'");
                newDir = true;
            }
            // also point groovy subsystems to our workdir
            System.setProperty("groovy.root", new File(REPL.workDir[0], ".groovy").getAbsolutePath());
            REPLLog.rollOver();
            // now that the log is rolled over, we can use it
            if (newDir) REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "REPL: Created a new work directory at {}", REPL.workDir[0].getAbsolutePath()), REPLLog.INTERNAL_LOG_TARGETS);
            else REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.DEBUG, "REPL: Found existing work directory at {}", REPL.workDir[0].getAbsolutePath()), REPLLog.INTERNAL_LOG_TARGETS);
        }
    }

    public static void discoverEnvironment(ClassLoader classLoader) {
        HAVE_GPL = REPL.class.getResource("/de/uni_kiel/rz/fdr/repl/mycore/GroovySourceDirsStartupHandler.class") != null;
        HAVE_MYCORE = classLoader.getResource("/org/mycore/common/MCRException.class") != null;
        HAVE_MYCORE_MODS = classLoader.getResource("/org/mycore/mods/MCRMODSWrapper.class") != null;
        if (!HAVE_GPL && (HAVE_MYCORE || HAVE_MYCORE_MODS)) throw new RuntimeException("Internal error: could not determine GPL status");
    }


}
