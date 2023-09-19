// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

package de.uni_kiel.rz.fdr.repl.mycore;

import de.uni_kiel.rz.fdr.repl.REPL;
import de.uni_kiel.rz.fdr.repl.REPLBreakpoint;
import de.uni_kiel.rz.fdr.repl.REPLLog;
import de.uni_kiel.rz.fdr.repl.REPLLogEntry;
import de.uni_kiel.rz.fdr.repl.groovy.GroovyShellService;
import jakarta.servlet.ServletContext;
import org.apache.groovy.groovysh.Groovysh;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.mycore.common.MCRSession;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.events.MCRStartupHandler;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import static de.uni_kiel.rz.fdr.repl.REPLLog.INTERNAL_LOG_TARGETS;
import static de.uni_kiel.rz.fdr.repl.mycore.GroovySourceDirsStartupHandler.TRACE_ENABLED;

@SuppressWarnings("unused")
public class REPLStartupHandler implements MCRStartupHandler.AutoExecutable {
    
    public static final String SESSION_AUTOCLOSE = "CAUREPL-AUTOCLOSE";

    private static final String HANDLER_NAME = REPLStartupHandler.class.getName();
    private static final boolean REPL_ENABLED = MCRConfiguration2.getBoolean("CAU.REPL.Enabled").orElse(false);
    private static final String SSH_ADDR = MCRConfiguration2.getString("CAU.REPL.SSH.ListenAddr").orElse(null);
    private static final Integer SSH_PORT = MCRConfiguration2.getInt("CAU.REPL.SSH.ListenPort").orElse(null);
    private static final Integer SSH_TIMEOUT = MCRConfiguration2.getInt("CAU.REPL.SSH.TimeoutSeconds").orElse(null);
    private static final Integer MAX_BREAKPOINTS = MCRConfiguration2.getInt("CAU.REPL.MaxBreakpoints").orElse(null);

    private static final String EDITOR = MCRConfiguration2.getString("CAU.REPL.Editor").orElse(null);
    private static final String EDITORSSH = MCRConfiguration2.getString("CAU.REPL.EditorSSH").orElse(null);
    private static final Pattern EDITOR_RE = Pattern.compile("^[a-zA-Z0-9_/. =\"\\-]+$");
    private static final Map<String, String> USER_SCRIPTS = MCRConfiguration2.getSubPropertiesMap("CAU.REPL.Groovy.Startup.Scripts.");
    private static final Map<String, String> USER_COMMANDS = MCRConfiguration2.getSubPropertiesMap("CAU.REPL.Groovy.Startup.Commands.");

    @Override
    public String getName() {
        return HANDLER_NAME;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void startUp(ServletContext servletContext) {
        if (servletContext == null) return;
        if (!REPL_ENABLED) {
            REPLBreakpoint.MAX_BREAKPOINTS = 0;
            return;
        }

        REPLLog.TRACE = TRACE_ENABLED;
        if (MAX_BREAKPOINTS != null) REPLBreakpoint.MAX_BREAKPOINTS = MAX_BREAKPOINTS;
        REPLLog.initializeLibs();
        ClassLoader classLoader = GroovySourceDirsStartupHandler.classLoader;
        if (classLoader == null) classLoader = servletContext.getClassLoader();

        REPL repl;
        try {
            repl = new REPL(SSH_ADDR, SSH_PORT, SSH_TIMEOUT, GroovySourceDirsStartupHandler.getWorkDir(), classLoader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        repl.setAuthenticator(new REPLMyCoReAuthenticator());
        repl.addSessionListener(new SessionListener() {
            @Override
            public void sessionClosed(Session session) {
                try {
                    Groovysh shell = session.getAttribute(GroovyShellService.SHELL_KEY);
                    if (shell != null) {
                        String shellSessionID = (String) shell.execute("mcrsessionid");
                        if (shellSessionID == null) throw new RuntimeException("no session ID found");
                        MCRSession mcrSession = MCRSessionMgr.getSession(shellSessionID);
                        if (mcrSession == null) throw new RuntimeException("session is unknown");
                        mcrSession.close();
                        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.DEBUG, "REPL: MyCoRe Session {} closed", shellSessionID), INTERNAL_LOG_TARGETS);
                    } else throw new RuntimeException("no shell found");
                } catch (Exception e) {
                    REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: error while closing MyCoRe Session after disconnect: {}", e), INTERNAL_LOG_TARGETS);
                }
            }
        });
        repl.addInternalStartupScript("/repl-mycore-init.groovy");
        repl.addInternalStartupScript("/repl-mycore-base.groovy");
        if (REPL.HAVE_MYCORE_MODS) repl.addInternalStartupScript("/repl-mycore-mods.groovy");

        if (EDITOR != null) {
            if (EDITOR_RE.matcher(EDITOR).matches()) {
                repl.addStartupCommand(":set editor '" + EDITOR + "'");
            } else {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Ignoring weird editor path: {}", EDITOR), INTERNAL_LOG_TARGETS);
            }
        }

        if (EDITORSSH != null) {
            if (EDITOR_RE.matcher(EDITORSSH).matches()) {
                repl.addStartupCommand(":set editorssh '" + EDITORSSH + "'");
            } else {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Ignoring weird editorssh path: {}", EDITORSSH), INTERNAL_LOG_TARGETS);
            }
        }

        for (String prop : USER_SCRIPTS.keySet().stream().sorted().filter(k -> USER_SCRIPTS.get(k) != null && !USER_SCRIPTS.get(k).isBlank()).toList()) {
            repl.addStartupScript(USER_SCRIPTS.get(prop));
        }

        for (String prop : USER_COMMANDS.keySet().stream().sorted().filter(k -> USER_COMMANDS.get(k) != null && !USER_COMMANDS.get(k).isBlank()).toList()) {
            repl.addStartupCommand(USER_COMMANDS.get(prop));
        }

        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL listening on {}:{}", repl.getListenAddr(), repl.getPort()), INTERNAL_LOG_TARGETS);
        try {
            repl.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
