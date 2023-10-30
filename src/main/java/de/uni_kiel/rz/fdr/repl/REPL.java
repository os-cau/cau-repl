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
import java.util.*;

/**
 * This class represents a single REPL interface with an associated SSH service.
 */
@SuppressWarnings("unused")
public class REPL {

    /**
     * cau-repl's version
     */
    // remember to also adjust the version in pom.xml
    public static final String VERSION = "0.1.0-SNAPSHOT";

    /**
     * The user name used for login will be accessible from each SSH Session using this key.
     */
    public static final Session.AttributeKey<String> USER_KEY = new Session.AttributeKey<>();
    /**
     * The default name of the work subdirectory containing the REPL's persistent state.
     */
    public static final String DEFAULT_WORK_SUBDIR = "cau-repl";
    /**
     * Threads of cau-repl have a name that starts with this prefix
     */
    public static final String THREAD_PREFIX = "cau-repl ";
    /**
     * Does this build of cau-repl contain GPL licensed code?
     */
    public static boolean HAVE_GPL = true;
    /**
     * Are we running in a JVM whith MyCoRe?
     */
    public static boolean HAVE_MYCORE = false;
    /**
     * Are we running in a JVM with MyCoRe's MODS module?
     */
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


    /**
     * Creates the REPL. You should only have one instance of this per VM.
     *
     * @param listenAddr IP address that the SSH service will bind to (default 127.0.0.1)
     * @param port Port that the SSH service will bind to (default 8512)
     * @param timeoutSecs Terminate idle SSH connections after this many seconds (default 12h)
     * @param workDir The directory that will contain the REPL's perisistent state. It will be created if it does not exists.
     *                (default {@code cau-repl} in the cwd)
     * @param groovyClassLoader The ClassLoader to use for the REPL (defaults to a new private ClassLoader)
     * @throws IOException A file could not be accessed.
     */
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

    /**
     * Start listening for incoming SSH connections.
     * @throws IOException The server could not be started.
     */
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

    /**
     * Gets the IP that the SSH service will bind to.
     * @return The IP that the SSH service will bind to.
     */
    public String getListenAddr() {
        return listenAddr;
    }

    /**
     * Gets the port that the SSH service will listen on.
     * @return The port that the SSH service will listen on.
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the timeout for idle SSH connections.
     * @return The timeout for idle SSH connections.
     */
    public int getTimeoutSecs() {
        return timeoutSecs;
    }

    /**
     * Gets the authenticator in use for the SSH session.
     * @return The authenticator in use for the SSH session.
     */
    public PasswordAuthenticator getAuthenticator() {
        return authenticator;
    }

    /**
     * Sets a custom authenticator for the SSH service. Must be called before {@code start()}.
     * @param authenticator The authenticator to use
     */
    public void setAuthenticator(PasswordAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * Add a startup script to be executed in the REPL each time a user has connected. Must be called before {@code start()}.
     * @param path Location of the script
     */
    public void addStartupScript(String path) {
        startupScripts.add(path);
    }

    /**
     * Get the startup scripts that were configured for the REPL.
     * @return The startup scripts that were configured for the REPL.
     */
    public List<String> getStartupScripts() {
        return new ArrayList<>(startupScripts);
    }

    /**
     * Add a startup script from our .jar to be executed in the REPL each time a user has connected. Must be called before {@code start()}.
     * @param path Location of the script inside our .jar
     */
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

    /**
     * Get the startup commands that were configured for the REPL.
     * @return The startup commands that were configured for the REPL.
     */
    public List<String> getStartupCommands() {
        return new ArrayList<>(startupCommands);
    }

    /**
     * Adds a command to be executed inside the REPL each time a user has connected. Must be called before {@code start()}.
     * @param cmd The command to execute.
     */
    public void addStartupCommand(String cmd) {
        startupCommands.add(cmd);
    }

    /**
     * Gets the session listeners that were configured for the SSH service.
     * @return The session listeners that were configured for the SSH service.
     */
    public List<SessionListener> getSessionListeners() {
        return new ArrayList<>(sessionListeners);
    }

    /**
     * Respond to SSH events by installing your own listener. Must be called before {@code start()}.
     * @param sessionListener The listener to add
     */
    public void addSessionListener(SessionListener sessionListener) {
        sessionListeners.add(sessionListener);
    }

    /**
     * Get the REPL's work directory
     * @return the REPL's work directory
     */
    public static File getWorkDir() {
        synchronized (workDir) {
            return workDir[0];
        }
    }

    /**
     * Gets the ClassLoader that will be used for the REPL.
     * @return The ClassLoader that will be used for the REPL.
     */
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

    /**
     * Allows you to initialize the global work directory before creating a REPL instance. This might be neccessary when
     * you precompile Groovy sources). You can only set this directory once.
     * @param workDir The work directory to use
     * @throws IOException The directory could not be accessed.
     */
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
                if (!REPL.workDir[0].mkdirs()) throw new IOException("Failed to create work directory '" + REPL.workDir[0].getAbsolutePath() + "'");
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

    /**
     * Update the various {@code HAVE_*} fields.
     * @param classLoader The ClassLoader to use as a reference
     */
    public static void discoverEnvironment(ClassLoader classLoader) {
        HAVE_GPL = REPL.class.getResource("/de/uni_kiel/rz/fdr/repl/mycore/GroovySourceDirsStartupHandler.class") != null;
        HAVE_MYCORE = classLoader.getResource("/org/mycore/common/MCRException.class") != null;
        HAVE_MYCORE_MODS = classLoader.getResource("/org/mycore/mods/MCRMODSWrapper.class") != null;
        if (!HAVE_GPL && (HAVE_MYCORE || HAVE_MYCORE_MODS)) throw new RuntimeException("Internal error: could not determine GPL status");
    }

    /**
     * Generates a textual representation of cau-repl's version
     * @return cau-repl's version string.
     */
    public static String getVersionString() {
        return "cau-repl " + REPL.VERSION + "-" + (HAVE_GPL ? "gpl" : "mit");
    }


}
