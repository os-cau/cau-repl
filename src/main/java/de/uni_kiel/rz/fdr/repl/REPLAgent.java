// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
    IMPORTANT: This class should not use any other classes that the REPL will need later on. This is the only way
    to ensure, that those classes will be loaded into the proper classloader later.
    So don't use any Groovy or de.uni_kiel classes here.
 */

public class REPLAgent {

    // static initialization
    public static void premain(String agentArgs, Instrumentation inst) {
        System.err.println("REPL: REPLAgent was loaded");
        String t = System.getProperty("CAU.JavaAgent.Triggers");
        if (t != null) {
            // delayed start
            inst.addTransformer(new REPLAgentTransformer(t.replaceAll("\\.", "/").split(",")), false);
        } else {
            // immediate start
            try {
                String cp = System.getProperty("CAU.JavaAgent.ClassPath");
                if (cp != null && !Helpers.forbiddenExtendClassPath(REPLAgent.class.getClassLoader(), Helpers.deglobClassPath(cp))) {
                    System.err.println("REPL: Could not extend classpath");
                }
                Class<?> klass = Class.forName("de.uni_kiel.rz.fdr.repl.REPLAgentStartup");
                Helpers.darkInvocation(klass.getDeclaredConstructor().newInstance(), "start", new Class<?>[]{ClassLoader.class}, new Object[]{null});
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("REPL: Could not locate class de.uni_kiel.rz.fdr.repl.REPLAgentStartup. Please make sure that cau-repl-*.jar is in your classpath");
            } catch (Exception e) {
                throw new RuntimeException("REPL: Could not initiate Agent Startup", e);
            }
        }
    }

    // dynamic initialization
    public static void agentmain(String agentArgs, Instrumentation inst) {
        premain(agentArgs, inst);
    }


    private static class REPLAgentTransformer implements ClassFileTransformer {

        private final String[] triggers;
        private boolean triggered = false;

        public REPLAgentTransformer(String[] triggers) {
            this.triggers = triggers;
        }


        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            if (!triggered) {
                for (String trig : triggers) {
                    if (!className.startsWith(trig)) continue;
                    if (!trig.endsWith("/") && !className.equals(trig)) continue;

                    triggered = true;
                    System.err.println("REPL: Agent Triggered on " + className);
                    try {
                        String cp = System.getProperty("CAU.JavaAgent.ClassPath");
                        if (cp != null && !forbiddenExtendClassPath(loader, deglobClassPath(cp))) {
                            System.err.println("REPL: Could not extend ClassPath");
                        }
                        Class<?> klass = loader.loadClass("de.uni_kiel.rz.fdr.repl.REPLAgentStartup");
                        darkInvocation(klass.getDeclaredConstructor().newInstance(), "start", new Class<?>[]{ClassLoader.class}, new Object[]{loader});
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                        System.err.println("REPL: Could not locate class de.uni_kiel.rz.fdr.repl.REPLAgentStartup. Please make sure that the system property CAU.JavaAgent.ClassPath is set correctly.");
                        System.exit(8888);
                    } catch (Exception e) {
                        e.printStackTrace();
                        System.err.println("REPL: Error during agent Startup");
                        System.exit(9999);
                    }
                    break;
                }
            }
            return classfileBuffer;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
            return transform(null, loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
        }

        /*
            The following static methods are copies of Helpers.*. We duplicate them here to avoid instantiating
            Helpers.class in the wrong classloader.
         */

        @SuppressWarnings("UnusedReturnValue")
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

        private static boolean forbiddenExtendClassPathF(ClassLoader classLoader, List<File> files) {
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

        private static boolean forbiddenExtendClassPath(ClassLoader classLoader, List<URL> urls) {
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

        private static List<URL> deglobClassPath(String classPath) {
            List<URL> urls = new ArrayList<>();
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
    }

}
