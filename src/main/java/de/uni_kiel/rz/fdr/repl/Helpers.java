// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.File;
import java.io.IOException;
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

import static de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamizeTransformer2.*;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyPatchesTransformer.PATCHEESTUB_PREFIX;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyPatchesTransformer.PATCHEE_SUFFIX;

/*
    IMPORTANT: if you change these methods, remember to also update their copies in REPLAgent.java
 */

public class Helpers {
        public static Object darkInvocation(Object target, String methodName, Class<?>[] signature, Object[] arguments) throws InvocationTargetException, IllegalAccessException {
            // requires --add-opens 'java.base/java.lang=ALL-UNNAMED'
            Class<?> klass = target.getClass();
            Method meth = null;
            while (klass != null && meth == null) {
                try {
                    meth = klass.getDeclaredMethod(methodName, signature);
                } catch (NoSuchMethodException ignore) {
                }
                klass = klass.getSuperclass();
            }

            if (meth == null) throw new RuntimeException("Could not find method " + methodName + " in class");
            meth.setAccessible(true);
            return meth.invoke(target, arguments);
        }

        @SuppressWarnings("unused")
        public static boolean forbiddenExtendClassPathF(ClassLoader classLoader, List<File> files) {
            List<URL> urls = new ArrayList<>();
            for (File f : files) {
                try {
                    urls.add(f.toURI().toURL());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            return forbiddenExtendClassPath(classLoader, urls);
        }

        public static boolean forbiddenExtendClassPath(ClassLoader classLoader, List<URL> urls) {
            if (classLoader instanceof URLClassLoader ucl) {
                for (URL u : urls) {
                    try {
                        darkInvocation(ucl, "addURL", new Class<?>[]{URL.class}, new Object[]{u});
                    } catch (InvocationTargetException | IllegalAccessException e) {
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

    public static String shellCommand(String command) throws IOException {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows");
        ProcessBuilder builder = new ProcessBuilder();
        if (windows) builder.command("cmd.exe", "/c", command);
        else builder.command("/bin/sh", "-c", command);
        Process proc = builder.start();
        try {
            String stdout = new String(proc.getInputStream().readAllBytes());
            int status = proc.waitFor();
            if (status != 0) throw new RuntimeException("Command " + command + " returned exit status " + status);
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
