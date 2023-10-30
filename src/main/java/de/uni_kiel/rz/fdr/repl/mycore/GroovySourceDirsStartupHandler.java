// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

package de.uni_kiel.rz.fdr.repl.mycore;

import de.uni_kiel.rz.fdr.repl.*;
import de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamized;
import de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamizedExpando;
import de.uni_kiel.rz.fdr.repl.groovy.GroovySourceDirectory;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;
import jakarta.servlet.ServletContext;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.events.MCRStartupHandler;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Map;

import static de.uni_kiel.rz.fdr.repl.REPLLog.*;

@SuppressWarnings("unused")
public class GroovySourceDirsStartupHandler implements MCRStartupHandler.AutoExecutable {

    private static final String HANDLER_NAME = GroovySourceDirsStartupHandler.class.getName();
    private static final Map<String, String> GROOVY_SOURCE_DIRS = MCRConfiguration2.getSubPropertiesMap("CAU.Groovy.SourceDirs.");
    private static final boolean REORDER_SOURCES = MCRConfiguration2.getBoolean("CAU.REPL.Groovy.ReorderSources").orElse(true);
    protected static final boolean TRACE_ENABLED = MCRConfiguration2.getBoolean("CAU.REPL.Log.Trace").orElse(false);
    public static ClassLoader classLoader = null;
    public static String classPath = System.getProperty("CAU.Groovy.ClassPath", System.getProperty("java.class.path", "."));


    @Override
    public String getName() {
        return HANDLER_NAME;
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void startUp(ServletContext servletContext) {
        if (servletContext == null) return;
        REPLLog.TRACE = TRACE_ENABLED;
        REPLLog.initializeLibs();
        classLoader = servletContext.getClassLoader();
        REPL.discoverEnvironment(classLoader);
        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, REPL.getVersionString()), INTERNAL_LOG_TARGETS);
        classPath = classPath + ":" + servletContext.getRealPath("/WEB-INF/lib/*");

        // use groovy expando metaclasses globally
        ExpandoMetaClass.enableGlobally();

        // initialize our workDir before compilation
        try {
            REPL.setWorkDir(getWorkDir());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // retroactively add dynamized metaclasses to dynamized groovy classes that we compiled using the agent in deferred mode
        MetaClassRegistry mcr = GroovySystem.getMetaClassRegistry();
        for (Class<?> klass : GroovySourceDirectory.groovyClasses) {
            if (!GroovyDynamized.isDynamizedClass(klass)) continue;
            if (mcr.getMetaClass(klass).getClass().equals(GroovyDynamizedExpando.class)) continue;
            if (TRACE || TRACE_COMPILE) REPLLog.trace("Adding MetaClass to deferred class: {}", klass.getName());
            GroovySourceDirectory.addDynamizedMetaClass(klass);
        }

        // compile our sources
        try {
            for (String prop : GROOVY_SOURCE_DIRS.keySet().stream().sorted().filter(k -> GROOVY_SOURCE_DIRS.get(k) != null && !GROOVY_SOURCE_DIRS.get(k).isBlank()).toList()) {
                Path p = Path.of(GROOVY_SOURCE_DIRS.get(prop)).toAbsolutePath();
                new GroovySourceDirectory(p, classLoader, classPath, false, REORDER_SOURCES);
            }
        } catch (IOException | InvocationTargetException | IllegalAccessException |
                 InsufficientAccessRightsException | GroovySourceDirectory.CompilationException |
                 GroovySourceDirectory.ClassLoadingException e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "GroovySourceDirs: Error during compilation: {}", e), INTERNAL_LOG_TARGETS);
            if (e instanceof IOException eio) throw new UncheckedIOException(eio);
            throw new RuntimeException(e);
        }

    }

    protected static File getWorkDir() {
        return new File(MCRConfiguration2.getString("MCR.basedir").orElseThrow(), REPL.DEFAULT_WORK_SUBDIR);
    }
}
