// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.jar.Manifest;

import static de.uni_kiel.rz.fdr.repl.REPLAgent.immediateStart;

public class REPLChainloader {

    public static final String MF_CHAINLOAD = "CAU-Repl-Chainload";

    public static void main(String[] args) throws Throwable {
        String triggers = System.getProperty("CAU.JavaAgent.Triggers");
        if (triggers != null) {
            System.err.println("REPL: ERROR: CAU.JavaAgent.Triggers can't be used in chainloader mode");
            System.exit(9999);
        }

        String chainLoad = System.getProperty("CAU.JavaAgent.ChainLoad");

        if (chainLoad == null) {
            String jarPath = REPLChainloader.class.getClassLoader().getResource(REPLChainloader.class.getName().replaceAll("\\.", File.separator) + ".class").toString();
            if (!jarPath.startsWith("jar:")) {
                System.err.println("REPL: ERROR: It looks like the chainloader was not invoked from a .jar file - please set the chainloader target property CAU.JavaAgent.ChainLoad manually.");
                System.exit(8888);
            }
            URL url = new URL(jarPath);
            JarURLConnection jar = (JarURLConnection) url.openConnection();
            Manifest manifest = jar.getManifest();
            chainLoad = manifest.getMainAttributes().getValue(MF_CHAINLOAD);
        }

        if (chainLoad != null) {
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
            return;
        }

        System.err.println("REPL: ERROR: The CAU.JavaAgent.ChainLoad property is not set - can't chainload");
        System.exit(9999);
    }

}
