/*
 * Copyright 2007 Bruce Fancher
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

// (C) Copyright 2015-2022 Denis Bazhenov
// SPDX-License-Identifier: Apache-2.0
// Source: https://github.com/bazhenov/groovy-shell-server/blob/c6e9781498be108529e2eeaa173cbbb19b805a47/groovy-shell-server/src/main/java/me/bazhenov/groovysh/GroovyShellService.java

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPL;
import de.uni_kiel.rz.fdr.repl.REPLLog;
import de.uni_kiel.rz.fdr.repl.REPLLogEntry;
import de.uni_kiel.rz.fdr.repl.SshTerminal;
import org.apache.groovy.groovysh.Groovysh;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.UserAuthNoneFactory;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.shell.ShellFactory;
import org.codehaus.groovy.tools.shell.util.Preferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.uni_kiel.rz.fdr.repl.REPLLog.INTERNAL_LOG_TARGETS;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jline.TerminalFactory.Flavor.UNIX;
import static jline.TerminalFactory.registerFlavor;
import static org.apache.groovy.groovysh.util.PackageHelper.IMPORT_COMPLETION_PREFERENCE_KEY;
import static org.apache.sshd.common.PropertyResolverUtils.updateProperty;
import static org.apache.sshd.core.CoreModuleProperties.IDLE_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.NIO2_READ_TIMEOUT;
import static org.apache.sshd.server.SshServer.setUpDefaultServer;

/**
 * Instantiate this class and call start() to start a GroovyShell
 *
 * @author Denis Bazhenov
 */
@SuppressWarnings("UnusedDeclaration")
public class GroovyShellService {

    public static final String HOST_KEY_FILENAME = "ssh-host.key";

    private int port;
    private String host;
    private Map<String, Object> bindings;
    private PasswordAuthenticator passwordAuthenticator;
    private long idleTimeOut = HOURS.toMillis(1);

    public static final Session.AttributeKey<Groovysh> SHELL_KEY = new Session.AttributeKey<>();
    private List<String> defaultScripts = new ArrayList<>();
    private SshServer sshd;
    private boolean disableImportCompletions = false;
    private final AtomicBoolean isServiceAlive = new AtomicBoolean(true);
    private final ClassLoader classLoader;


    @SuppressWarnings("WeakerAccess")
    public GroovyShellService(int port, ClassLoader classLoader) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Wrong port number");
        }
        this.port = port;
        this.classLoader = classLoader;
        registerFlavor(UNIX, SshTerminal.class);
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }

    public void setBindings(Map<String, Object> bindings) {
        this.bindings = bindings;
    }

    public int getPort() {
        return port;
    }

    /**
     * Adds a groovy script to be executed for each new client session.
     *
     * @param script script
     */
    public void addDefaultScript(String script) {
        defaultScripts.add(script);
    }

    /**
     * @return complete List of scripts to be executed for each new client session
     */
    public List<String> getDefaultScripts() {
        return defaultScripts;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public void setIdleTimeOut(long timeOut) {
        if (timeOut < 0) {
            throw new IllegalArgumentException("Wrong timeout");
        }
        this.idleTimeOut = timeOut;
    }

    /**
     * Disable import completion autoscan.
     * <p>
     * Import completion autoscan known to cause problems on a Spring Boot applications packaged in
     * uber-jar. Please, keep in mind that value is written (and persisted) using Java Preferences
     * API. So once written it should be removed by hand (you can use groovysh <code>:set</code>
     * command).
     *
     * @see <a href="https://github.com/bazhenov/groovy-shell-server/issues/26">groovy-shell-server
     * does not work with jdk11</a>
     */
    public void setDisableImportCompletions(boolean disableImportCompletions) {
        this.disableImportCompletions = disableImportCompletions;
    }

    public void setPasswordAuthenticator(PasswordAuthenticator passwordAuthenticator) {
        this.passwordAuthenticator = passwordAuthenticator;
    }

    public void setDefaultScripts(List<String> defaultScriptNames) {
        this.defaultScripts = defaultScriptNames;
    }

    /**
     * Starts Groovysh
     *
     * @throws IOException thrown if socket cannot be opened
     */
    public synchronized void start(File workDir, List<SessionListener> sessionListeners) throws IOException {
        sshd = buildSshServer(new File(workDir, HOST_KEY_FILENAME), sessionListeners);
        sshd.start();
        Preferences.put("interpreterMode", "false");
        if (disableImportCompletions) {
            Preferences.put(IMPORT_COMPLETION_PREFERENCE_KEY, "true");
        }
    }

    private SshServer buildSshServer(File hostKey, List<SessionListener> sessionListeners) {
        SshServer sshd = setUpDefaultServer();
        sshd.setPort(port);
        if (host != null) {
            sshd.setHost(host);
        }

        long idleTimeOut = this.idleTimeOut;
        updateProperty(sshd, IDLE_TIMEOUT.getName(), idleTimeOut);
        updateProperty(sshd, NIO2_READ_TIMEOUT.getName(), idleTimeOut + SECONDS.toMillis(15L));

        sshd.addSessionListener(new SessionListener() {
            @Override
            public void sessionCreated(Session session) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: {} new session", session.getRemoteAddress()), INTERNAL_LOG_TARGETS);
            }

            @Override
            public void sessionEvent(Session sesssion, Event event) {
                if (event.equals(Event.Authenticated)) REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: {}: authenticated as {}", sesssion.getRemoteAddress(), sesssion.getAttribute(REPL.USER_KEY)), INTERNAL_LOG_TARGETS);
            }

            @Override
            public void sessionException(Session session, Throwable t) {
            }

            @Override
            public void sessionClosed(Session session) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: {}: session closed", session.getRemoteAddress()), INTERNAL_LOG_TARGETS);
                Groovysh shell = session.getAttribute(SHELL_KEY);
                if (shell != null) {
                    shell.getRunner().setRunning(false);
                }
            }
        });

        if (sessionListeners != null) for (SessionListener sl : sessionListeners) sshd.addSessionListener(sl);

        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.toPath()));
        configureAuthentication(sshd);
        sshd.setShellFactory(new GroovyShellFactory());
        return sshd;
    }

    private void configureAuthentication(SshServer sshd) {
        UserAuthFactory auth;
        if (this.passwordAuthenticator != null) {
            sshd.setPasswordAuthenticator(this.passwordAuthenticator);
            auth = new UserAuthPasswordFactory();
        } else {
            auth = new UserAuthNoneFactory();
        }
        sshd.setUserAuthFactories(singletonList(auth));
    }

    public synchronized void destroy() throws IOException {
        isServiceAlive.set(false);
        sshd.stop(true);
    }

    class GroovyShellFactory implements ShellFactory {
        @Override
        public Command createShell(ChannelSession channel) {
            return new GroovyShellCommand(sshd, bindings, defaultScripts, isServiceAlive, classLoader);
        }
    }
}
