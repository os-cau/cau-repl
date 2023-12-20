// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.*;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static de.uni_kiel.rz.fdr.repl.REPLChainloader.MF_CHAINLOAD;

public class REPLChainloaderInstaller {

    private static final String OPT_INSTALL = "install";
    private static final String OPT_ABSINSTALL = "absinstall";
    private static final String OPT_UNINSTALL = "uninstall";
    private static final String MANIFEST_PATH = "META-INF" + File.separator + "MANIFEST.MF";
    private static final String MF_MAIN_CLASS = "Main-Class";
    private static final String MF_CLASS_PATH = "Class-Path";
    private static final String CP_RE = "(^| )[^ ]*cau-repl-[^ ]* *";
    private static final Set<Class<?>> CLASSES_TO_INSTALL = Set.of(REPLChainloader.class, REPLAgent.class, REPLAgent.REPLAgentTransformer.class);

    private static void errorOut(String msg, int code) {
        System.err.println("ERROR: " + msg);
        System.exit(code);
    }

    public static void main(String[] args) throws MalformedURLException {
        System.err.println("cau-repl chainloader installer");
        if (args.length != 3 || !(args[0].equals(OPT_INSTALL) || args[0].equals(OPT_ABSINSTALL) || args[0].equals(OPT_UNINSTALL))) printHelp();

        String action = args[0];
        String caurepl = args[1];
        String target = args[2];

        System.err.println(action + "ing the REPL chainloader in " + target);

        if (action.equals(OPT_INSTALL) || action.equals(OPT_ABSINSTALL)) {
            install(caurepl, target, action.equals(OPT_ABSINSTALL));
            return;
        }
        else if (action.equals(OPT_UNINSTALL)) {
            uninstall(target);
            return;
        }

        //NOTREACHED
        errorOut("Internal error: unexpected fallthrough", 2);
    }

    private static void install(String caurepl, String target, boolean absolute) {
        try {
            Manifest manifest = getManifest(target);
            if (manifest == null || manifest.getMainAttributes() == null) errorOut("target has no manifest", 2);
            String mainClass = manifest.getMainAttributes().getValue(MF_MAIN_CLASS);
            if (mainClass == null) errorOut("target manifest has no main class", 2);
            assert mainClass != null;
            boolean update = mainClass.equals(REPLChainloader.class.getName());
            if (update) System.err.println("target already has the chainloader, will update it.");
            String cp = manifest.getMainAttributes().getValue(MF_CLASS_PATH);
            try (FileSystem zip = FileSystems.newFileSystem(Path.of(target), Map.of())) {
                // copy the classes into place
                for (Class<?> klass : CLASSES_TO_INSTALL) {
                    String klassPath = klass.getName().replaceAll("\\.", File.separator) + ".class";
                    Path klassTarget = zip.getPath(klassPath);
                    Files.createDirectories(klassTarget.getParent());
                    try ( InputStream inp = klass.getClassLoader().getResourceAsStream(klassPath);
                          OutputStream out = Files.newOutputStream(klassTarget)
                        ) {
                        out.write(inp.readAllBytes());
                    }
                }

                // adjust the manifest...
                if (!update) {
                    manifest.getMainAttributes().putValue(MF_MAIN_CLASS, REPLChainloader.class.getName());
                    manifest.getMainAttributes().putValue(MF_CHAINLOAD, mainClass);
                }
                // ...especially the class-path
                String newcp = cp != null ? cp.replaceAll(CP_RE, " ").trim() : "";
                if (absolute) {
                    newcp = Path.of(caurepl).toAbsolutePath().toUri() + " " + newcp;
                } else {
                    newcp = URLEncoder.encode(Path.of(target).getParent().relativize(Path.of(caurepl)).toString(), StandardCharsets.UTF_8) + " " + newcp;
                }
                newcp = newcp.trim();
                if (cp == null || !cp.equals(newcp)) System.err.println("class path: " + (cp == null ? "" : cp) + "  ->  " + newcp);
                manifest.getMainAttributes().putValue(MF_CLASS_PATH, newcp);
                try (OutputStream out = Files.newOutputStream(zip.getPath(MANIFEST_PATH))) {
                    manifest.write(out);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            errorOut("Could not access " + target, 2);
        }
    }

    private static void uninstall(String target) {
        try {
            Manifest manifest = getManifest(target);
            if (manifest == null || manifest.getMainAttributes() == null) errorOut("target has no manifest", 2);
            String mainClass = manifest.getMainAttributes().getValue(MF_MAIN_CLASS);
            if (mainClass == null) errorOut("target manifest has no main class", 2);
            String chainLoader = manifest.getMainAttributes().getValue(MF_CHAINLOAD);
            String cp = manifest.getMainAttributes().getValue(MF_CLASS_PATH);
            try (FileSystem zip = FileSystems.newFileSystem(Path.of(target), Map.of())) {
                // remove the classes
                for (Class<?> klass : CLASSES_TO_INSTALL) {
                    String klassPath = klass.getName().replaceAll("\\.", File.separator) + ".class";
                    Path klassTarget = zip.getPath(klassPath);
                    for (Path p = klassTarget; p != null && !p.equals(p.getParent()); p = p.getParent()) {
                        try {
                            Files.deleteIfExists(p);
                        } catch (DirectoryNotEmptyException ignore) {}
                    }
                }

                // adjust the manifest...
                if (chainLoader != null) {
                    manifest.getMainAttributes().putValue(MF_MAIN_CLASS, chainLoader);
                    manifest.getMainAttributes().remove(new Attributes.Name(MF_CHAINLOAD));
                }
                // ...especially the class-path
                if (cp != null) {
                    String newcp = cp.replaceAll(CP_RE, " ").trim();
                    if (!cp.equals(newcp)) System.err.println("class path: " + cp + "  ->  " + newcp);
                    if (!newcp.isBlank()) {
                        manifest.getMainAttributes().putValue(MF_CLASS_PATH, newcp);
                    } else {
                        manifest.getMainAttributes().remove(new Attributes.Name(MF_CLASS_PATH));
                    }
                }
                try (OutputStream out = Files.newOutputStream(zip.getPath(MANIFEST_PATH))) {
                    manifest.write(out);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            errorOut("Could not access " + target, 2);
        }
    }


    private static Manifest getManifest(String target) throws IOException {
        URL url = new URL("jar:file:" + target + "!/");
        JarURLConnection jar = (JarURLConnection) url.openConnection();
        return jar.getManifest();
    }

    private static void printHelp() {
        System.err.println("Usage: java -jar cau-repl-agent-X.Y.Z.jar [" + OPT_INSTALL + "|" + OPT_ABSINSTALL + "|" + OPT_UNINSTALL + "] /path/to/cau-repl.jar /path/to/target.jar");
        System.exit(1);
    }
}