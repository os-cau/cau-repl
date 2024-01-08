// (C) Copyright 2023, 2024 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.uni_kiel.rz.fdr.repl.REPLAgent.immediateStart;

public class REPLChainloader {

    public static final String PROP_CHAINLOAD = "CAU.ChainLoader.Class";
    public static final String MF_CHAINLOAD = PROP_CHAINLOAD.replaceAll("\\.", "-");

    public static final Map<String, String> MANIFEST_PROPERTIES = Stream.of("CAU.Groovy.ClassPath", "CAU.Groovy.DeferMetaClasses",
            "CAU.Groovy.ReorderSources", "CAU.Groovy.SourceDirs", "CAU.Groovy.UseSystemClassLoader", "CAU.JavaAgent.AutoExec",
            "CAU.JavaAgent.ClassPath", "CAU.JavaAgent.SupportMode", "CAU.JavaAgent.Triggers", "CAU.REPL.EditorSSH", "CAU.REPL.Enabled",
            "CAU.REPL.Log.Internal", "CAU.REPL.Log.Trace", "CAU.REPL.MaxBreakpoints", "CAU.REPL.SSH.ListenAddr", "CAU.REPL.SSH.ListenPort",
            "CAU.REPL.SSH.PasswordCommand", "CAU.REPL.SSH.Timeout", "CAU.REPL.WorkDir", PROP_CHAINLOAD
    ).collect(Collectors.toMap(x -> x.replaceAll("\\.", "-"), Function.identity()));


    public static void main(String[] args) throws Throwable {
        String triggers = System.getProperty("CAU.JavaAgent.Triggers");
        if (triggers != null) {
            System.err.println("REPL: ERROR: CAU.JavaAgent.Triggers can't be used in chainloader mode");
            System.exit(9999);
        }

        String jarPath = REPLChainloader.class.getClassLoader().getResource(REPLChainloader.class.getName().replaceAll("\\.", File.separator) + ".class").toString();
        if (jarPath.startsWith("jar:")) {
            URL url = new URL(jarPath);
            JarURLConnection jar = (JarURLConnection) url.openConnection();
            Manifest manifest = jar.getManifest();
            for (Map.Entry<String, String> e : MANIFEST_PROPERTIES.entrySet()) {
                String mfProp = manifest.getMainAttributes().getValue(e.getKey());
                if (mfProp != null && System.getProperty(e.getValue()) == null) System.setProperty(e.getValue(), mfProp);
            }
        }
        String chainLoad = System.getProperty(PROP_CHAINLOAD);
        if (chainLoad == null) {
            System.err.println("REPL: ERROR: No class for chainloading was specified. Please set the chainloader target property " + PROP_CHAINLOAD + " either in the manifest or as a system property.");
            System.exit(9999);
        }

        System.err.println("REPL: Starting up");
        immediateStart();
        System.err.println("REPL: Chainloading " + chainLoad);
        Class<?> klass;
        Method mayn = null;
        try {
            klass = Class.forName(chainLoad);
            mayn = klass.getDeclaredMethod("main", args.getClass());
        } catch (ClassNotFoundException e) {
            System.err.println("REPL: ERROR: Chain loader could not find target class " + chainLoad);
            System.exit(8888);
        } catch (NoSuchMethodException e) {
            System.err.println("REPL: ERROR: Chain loader target class " + chainLoad + " does not have a usable main() method");
            System.exit(8888);
        }
        try {
            mayn.invoke(null, (Object) args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            System.err.println("REPL: Uncaught exception in chain loader target");
            throw e.getCause();
        }

    }

}
