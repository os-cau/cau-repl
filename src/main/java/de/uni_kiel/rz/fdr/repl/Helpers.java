// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import static de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamizeTransformer2.*;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyPatchesTransformer.PATCHEESTUB_PREFIX;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyPatchesTransformer.PATCHEE_SUFFIX;

/*
    IMPORTANT: if you change these methods, remember to also update their copies in REPLAgent.java
 */

public class Helpers {
        public static Object darkInvocation(Object target, String methodName, Class<?>[] signature, Object[] arguments) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InsufficientAccessRightsException {
            // requires --add-opens 'java.base/java.lang=ALL-UNNAMED'. maybe the agent's Instrumentation.redefineModule() can be used instead of this parameter?
            try {
                Class<?> klass = target.getClass();
                Method meth = null;
                while (klass != null && meth == null) {
                    try {
                        meth = klass.getDeclaredMethod(methodName, signature);
                    } catch (NoSuchMethodException ignore) {
                    }
                    klass = klass.getSuperclass();
                }

                if (meth == null) throw new NoSuchMethodException("Could not find method " + methodName + " in class");
                meth.setAccessible(true);
                return meth.invoke(target, arguments);
            } catch (InaccessibleObjectException e) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Could not access object of class {}" + InsufficientAccessRightsException.explanation, target.getClass()), REPLLog.INTERNAL_LOG_TARGETS);
                throw new InsufficientAccessRightsException("Could not access object of class " + target.getClass(), e);
            }
        }

        // keep this protected -> uses the agent's Instrumentation
        protected static boolean forbiddenExtendClassPath(ClassLoader classLoader, List<URL> urls) throws InsufficientAccessRightsException, IOException {
            if ((classLoader == null || classLoader == ClassLoader.getSystemClassLoader()) && REPLAgent.inst != null) {
                for (URL u : urls) {
                    REPLAgent.inst.appendToSystemClassLoaderSearch(new JarFile(u.getPath()));
                }
                return true;
            } else if (classLoader instanceof URLClassLoader ucl) {
                for (URL u : urls) {
                    try {
                        darkInvocation(ucl, "addURL", new Class<?>[]{URL.class}, new Object[]{u});
                    } catch (NoSuchMethodException | IllegalAccessException e) {
                        throw new RuntimeException("Failed to .addURL() to extend class path, possibly an internal error?", e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
                return true;
            } else {
                return false;
            }
        }

        public static List<URL> deglobClassPath(String classPath) {
            List<URL> urls = new ArrayList<>();
            if (classPath == null) return urls;
            for (String s : classPath.split(":")) {
                try {
                    if (s.endsWith("/*") || s.endsWith("\\*")) {
                        File[] globbed = new File(s.substring(0, s.length() - 1)).listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
                        if (globbed != null) for (File f : globbed) {
                            urls.add(f.toURI().toURL());
                        }
                    } else {
                        urls.add(new File(s).toURI().toURL());
                    }
                } catch (MalformedURLException ignore) {}
            }
            return urls;
        }


    public static String dynamizedMethod(String className, String method) {
            return DYNAMIZE_PREFIX + className.replaceAll("\\.", "_") + "$" + method;
    }

    public static String constructorMethod(String className) {
        return CONSTRUCTOR_PREFIX + className.replaceAll("\\.", "_") + "$init";
    }

    public static String superStubMethod(String className, String method) {
        return SUPERSTUB_PREFIX + className.replaceAll("\\.", "_") + "$" + method;
    }

    public static String patcheeStubMethod(String className, String method) {
        return PATCHEESTUB_PREFIX + className.replaceAll("\\.", "_") + "$" + method;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isGeneratedMethod(String method) {
        return method.startsWith(DYNAMIZE_PREFIX) || method.startsWith(SUPERSTUB_PREFIX) || method.startsWith(PATCHEESTUB_PREFIX) || method.startsWith(CONSTRUCTOR_PREFIX);
    }

    public static boolean isPatchedClass(String className) {
        return className.endsWith(PATCHEE_SUFFIX);
    }

    public static long epochMicros() {
        Instant i = Instant.now();
        return TimeUnit.SECONDS.toMicros(i.getEpochSecond()) + TimeUnit.NANOSECONDS.toMicros(i.getNano());
    }

    public static String shellCommand(String command) throws IOException, REPLAgentStartup.ExternalCommandException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        ProcessBuilder builder = new ProcessBuilder();
        if (windows) builder.command("cmd.exe", "/c", command);
        else builder.command("/bin/sh", "-c", command);
        Process proc = builder.start();
        try {
            String stdout = new String(proc.getInputStream().readAllBytes());
            int status = proc.waitFor();
            if (status != 0) throw new REPLAgentStartup.ExternalCommandException("Command " + command + " failed, status " + status);
            return stdout;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            proc.destroyForcibly();
            proc.getInputStream().close();
            proc.getErrorStream().close();
            proc.getOutputStream().close();
        }
    }
}
