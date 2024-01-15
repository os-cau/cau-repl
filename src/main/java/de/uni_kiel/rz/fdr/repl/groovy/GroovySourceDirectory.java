// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.error.ClassLoadingException;
import de.uni_kiel.rz.fdr.repl.error.CompilationException;
import de.uni_kiel.rz.fdr.repl.error.InsufficientAccessRightsException;
import de.uni_kiel.rz.fdr.repl.REPLLog;
import de.uni_kiel.rz.fdr.repl.REPLLogEntry;
import groovy.lang.*;
import groovyjarjarasm.asm.ClassWriter;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.control.ClassNodeResolver;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.uni_kiel.rz.fdr.repl.Helpers.darkInvocation;
import static de.uni_kiel.rz.fdr.repl.Helpers.isPatchedClass;
import static de.uni_kiel.rz.fdr.repl.REPLLog.*;

/**
 * Use this class to compile a directory of Groovy sources.
 */
public class GroovySourceDirectory {

    /**
     * All the Groovy classes that were successfully compiled by all GroovySourceDirectory instances.
     */
    public static Set<Class<?>> groovyClasses = new ConcurrentHashMap<Class<?>, Boolean>().keySet(true);
    private static boolean KEEP_TEMPFILES = false;


    private final Path root;
    private List<File> sources;
    private final ArrayList<Class<?>> classes = new ArrayList<>();
    private final ClassLoader classLoader;
    private final String patcheeClassPath;
    private final boolean deferredMetaClasses;
    private final boolean reorderSources;

    /**
     * Compiles the Groovy sources in a directory and its subdirectories, loading it into a private ClassLoader.
     * @param root The directory or alternatively a single file to compile.
     * @throws IOException A file or directory can't be accessed.
     * @throws IllegalAccessException There was a problem loading a class.
     * @throws CompilationException When your sources caused a compilation error.
     * @throws InsufficientAccessRightsException When cau-repl does not have the required JVM permissions.
     * @throws ClassLoadingException When a class could not be loaded.
     */
    @SuppressWarnings("unused")
    public GroovySourceDirectory(Path root) throws IOException, IllegalAccessException, InsufficientAccessRightsException, CompilationException, ClassLoadingException {
        this.root = root.toAbsolutePath();
        this.classLoader = new GroovyClassLoader();
        this.patcheeClassPath = System.getProperty("java.class.path", ".");
        this.deferredMetaClasses = false;
        this.reorderSources = true;
        compile();
    }

    /**
     * Compiles the Groovy sources in a directory and its subdirectories.
     * @param root The directory or alternatively a single file to compile.
     * @param classLoader The ClassLoader that the compiled classes will be put in.
     * @throws IOException A file or directory can't be accessed.
     * @throws IllegalAccessException There was a problem loading a class.
     * @throws CompilationException When your sources caused a compilation error.
     * @throws InsufficientAccessRightsException When cau-repl does not have the required JVM permissions.
     * @throws ClassLoadingException When a class could not be loaded.
     */
    @SuppressWarnings("unused")
    public GroovySourceDirectory(Path root, ClassLoader classLoader) throws IOException, IllegalAccessException, InsufficientAccessRightsException, CompilationException, ClassLoadingException {
        this.root = root.toAbsolutePath();
        this.classLoader = classLoader;
        this.patcheeClassPath = System.getProperty("java.class.path", ".");
        this.deferredMetaClasses = false;
        this.reorderSources = true;
        compile();
    }

    /**
     * Compiles the Groovy sources in a directory and its subdirectories.
     * @param root The directory or alternatively a single file to compile.
     * @param classLoader The ClassLoader that the compiled classes will be put in.
     * @param patcheeClassPath A custom class path that contains the target classes of the {@link de.uni_kiel.rz.fdr.repl.Patches @Patches} annotation.
     * @throws IOException A file or directory can't be accessed.
     * @throws IllegalAccessException There was a problem loading a class.
     * @throws CompilationException When your sources caused a compilation error.
     * @throws InsufficientAccessRightsException When cau-repl does not have the required JVM permissions.
     * @throws ClassLoadingException When a class could not be loaded.
     */
    @SuppressWarnings("unused")
    public GroovySourceDirectory(Path root, ClassLoader classLoader, String patcheeClassPath) throws IOException, IllegalAccessException, InsufficientAccessRightsException, CompilationException, ClassLoadingException {
        this.root = root.toAbsolutePath();
        this.classLoader = classLoader;
        this.patcheeClassPath = patcheeClassPath;
        this.deferredMetaClasses = false;
        this.reorderSources = true;
        compile();
    }

    /**
     * Compiles the Groovy sources in a directory and its subdirectories.
     * @param root The directory or alternatively a single file to compile.
     * @param classLoader The ClassLoader that the compiled classes will be put in.
     * @param patcheeClassPath A custom class path that contains the target classes of the {@link de.uni_kiel.rz.fdr.repl.Patches @Patches} annotation.
     * @param deferredMetaClasses If set, the compiled Groovy classes will not receive our {@link GroovyDynamizedExpando} metaclass yet.
     *                            This postpones Java's class initialization, allowing you to trigger it at a later more convenient point in time.
     *                            You must call {@link GroovySourceDirectory#addDynamizedMetaClass(Class)} on your dynamized classes manually before you can
     *                            use them safely, which is also a method to trigger the initialization.
     * @throws IOException A file or directory can't be accessed.
     * @throws IllegalAccessException There was a problem loading a class.
     * @throws CompilationException When your sources caused a compilation error.
     * @throws InsufficientAccessRightsException When cau-repl does not have the required JVM permissions.
     * @throws ClassLoadingException When a class could not be loaded.
     */
    public GroovySourceDirectory(Path root, ClassLoader classLoader, String patcheeClassPath, boolean deferredMetaClasses) throws IOException, IllegalAccessException, InsufficientAccessRightsException, CompilationException, ClassLoadingException {
        this.root = root.toAbsolutePath();
        this.classLoader = classLoader;
        this.patcheeClassPath = patcheeClassPath;
        this.deferredMetaClasses = deferredMetaClasses;
        this.reorderSources = true;
        compile();
    }

    /**
     * Compiles the Groovy sources in a directory and its subdirectories.
     * @param root The directory or alternatively a single file to compile.
     * @param classLoader The ClassLoader that the compiled classes will be put in.
     * @param patcheeClassPath A custom class path that contains the target classes of the {@link de.uni_kiel.rz.fdr.repl.Patches @Patches} annotation.
     * @param deferredMetaClasses If set, the compiled Groovy classes will not receive our {@link GroovyDynamizedExpando} metaclass yet.
     *                            This postpones Java's class initialization, allowing you to trigger it at a later more convenient point in time.
     *                            You must call {@link GroovySourceDirectory#addDynamizedMetaClass(Class)} on your classes manually before you can
     *                            use them safely, which is also a method to trigger the initialization.
     * @param reorderSources Can be set to {@code false} to disable the source reordering logic. Only use this for debugging.
     *                       Your sources will probably not compile correctly if disabled.
     * @throws IOException A file or directory can't be accessed.
     * @throws InvocationTargetException There was a problem loading a class.
     * @throws IllegalAccessException There was a problem loading a class.
     * @throws CompilationException When your sources caused a compilation error.
     * @throws InsufficientAccessRightsException When cau-repl does not have the required JVM permissions.
     * @throws ClassLoadingException When a class could not be loaded.
     */
    public GroovySourceDirectory(Path root, ClassLoader classLoader, String patcheeClassPath, boolean deferredMetaClasses, boolean reorderSources) throws IOException, InvocationTargetException, IllegalAccessException, InsufficientAccessRightsException, CompilationException, ClassLoadingException {
        this.root = root.toAbsolutePath();
        this.classLoader = classLoader;
        this.patcheeClassPath = patcheeClassPath;
        this.deferredMetaClasses = deferredMetaClasses;
        this.reorderSources = reorderSources;
        compile();
    }

    /**
     * Get the ClassLoader of this directory's Groovy classes.
     * @return The ClassLoader of this directory's Groovy classes.
     */
    @SuppressWarnings("unused")
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Get a list of all the Groovy source files found in this directory.
     * @return A list of all the Groovy source files found in this directory.
     */
    @SuppressWarnings("unused")
    public List<File> getSources() { return List.copyOf(sources); }

    /**
     * Get the list of the Groovy classes that were loaded by this instance.
     * @return the list of the Groovy classes that were loaded by this instance.
     */
    @SuppressWarnings("unused")
    public List<Class<?>> getClasses() { return List.copyOf(classes); }

    /**
     * Add a {@link GroovyDynamizedExpando} meta class to a compiled groovy class. You need to do this manually for
     * dynamized classes if you explicitly deferred this in the settings.
     * @param theClass The class to augment with the meta class.
     */
    public static void addDynamizedMetaClass(Class<?> theClass) {
        // force static initializers of our class to run in a controlled way before adding the MetaClass will trigger it behind the scenes, silently dropping the initializer's exceptions
        try {
            Class.forName(theClass.getName(), true, theClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Internal error: could not initialize class " + theClass.getName(), e);
        }
        GroovyDynamizedExpando demc = new GroovyDynamizedExpando(theClass);
        demc.initialize();
        GroovySystem.getMetaClassRegistry().setMetaClass(theClass, demc);
    }

    private void compile() throws IOException, IllegalAccessException, InsufficientAccessRightsException, CompilationException, ClassLoadingException {
        List<File> files;
        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Compiling Groovy classes from '{}'", root), INTERNAL_LOG_TARGETS);
        try(Stream<Path> w = Files.walk(root)) {
            files = w.filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".groovy"))
                     .map(Path::toFile)
                     .toList();
        }
        List<URI> uris = files.stream().map(File::toURI).toList();
        List<List<URI>> compilationBatches = new ArrayList<>();
        if (reorderSources) {
            GroovyPatchesTransformer simulationRun = determineCompileOrder(files);
            List<URI> compileOrdered = simulationRun.getCompileOrder();
            for (URI x : compileOrdered) compilationBatches.add(List.of(x));
            // now add all the rest that were not important during the simulation run
            compilationBatches.add(new ArrayList<>(uris));
            compilationBatches.get(compilationBatches.size() - 1).removeAll(compileOrdered);
            compilationBatches.removeIf(List::isEmpty);
            if (TRACE || TRACE_COMPILE) REPLLog.trace("Patch order: {}", compileOrdered.stream().map(URI::toString).collect(Collectors.joining(", ")));
        } else {
            compilationBatches.add(uris);
        }

        CompilerConfiguration cc = new CompilerConfiguration();
        GroovyDynamizeTransformer1 dynamize1 = new GroovyDynamizeTransformer1();
        GroovyPatchesTransformer patches = new GroovyPatchesTransformer(this, patcheeClassPath, false);
        GroovyDynamizeTransformer2 dynamize2 = new GroovyDynamizeTransformer2(dynamize1);
        GroovyLoadOrderTransformer loadOrder = new GroovyLoadOrderTransformer();
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStaticImport("de.uni_kiel.rz.fdr.repl.REPLBreakpoint", "replbreakpoint");
        imports.addImport("Patches", "de.uni_kiel.rz.fdr.repl.Patches");
        imports.addImport("Dynamize", "de.uni_kiel.rz.fdr.repl.Dynamize");
        cc.addCompilationCustomizers(imports, dynamize1, patches, dynamize2, loadOrder);
        File tmpdir = Files.createTempDirectory("cau-repl-compiletmp.").toFile();
        try {
            if (!KEEP_TEMPFILES) tmpdir.deleteOnExit();
            cc.setTargetDirectory(tmpdir);

            for (List<URI> batch : compilationBatches) {
                if (TRACE || TRACE_COMPILE)
                    REPLLog.trace("Compiling {}", batch.stream().map(URI::toString).collect(Collectors.joining(", ")));
                // collect all compiled classes, including generated inner classes (closures etc.)
                final HashMap<String, byte[]> bytecode = new HashMap<>();
                try {
                    CompilationUnit cu = new CompilationUnit(cc);
                    cu.setClassNodeResolver(new CompilationOutputFirstClassNodeResolver(classLoader, classes.stream().map(Class::getName).toList()));
                    cu.setClassLoader(new GroovyClassLoader(classLoader));
                    for (URI u : batch) cu.addSource(u.toURL());
                    cu.setClassgenCallback((classVisitor, classNode) -> {
                        ClassWriter writer = (ClassWriter) classVisitor;
                        bytecode.put(classNode.getName(), writer.toByteArray());
                    });
                    cu.compile();
                } catch (RuntimeException e) {
                    throw new CompilationException("Compilation error", e);
                }

                LinkedHashMap<String, byte[]> newClasses = new LinkedHashMap<>();
                // sort compiled classes in proper load order
                for (org.codehaus.groovy.ast.ClassNode cn : loadOrder.loadOrder) {
                    if (bytecode.containsKey(cn.getName())) newClasses.put(cn.getName(), bytecode.get(cn.getName()));
                }
                // now append generated inner classes
                List<String> leftover = new ArrayList<>(bytecode.keySet().stream().filter(x -> !newClasses.containsKey(x)).toList());
                Collections.reverse(leftover);
                for (String l : leftover) {
                    newClasses.put(l, bytecode.get(l));
                }

                // load the compiled classes, making sure to do it one class at a time. this ensures that,
                // in the case of a compilation error, the subset of classes that were actually loaded are in the list.
                for (Map.Entry<String, byte[]> toLoad : newClasses.entrySet()) {
                    if (TRACE || TRACE_COMPILE) REPLLog.trace("Loading compiled class: {}", toLoad.getKey());
                    classes.addAll(loadClassBatch(Map.of(toLoad.getKey(), toLoad.getValue())));
                }
            }

            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Compiled Groovy classes: {}",
                    classes.stream().map(Class::getName).collect(Collectors.joining(", "))), INTERNAL_LOG_TARGETS);
            this.sources = files;
        } finally {
            if (KEEP_TEMPFILES) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Compiled Class Files remain in: {}", tmpdir), INTERNAL_LOG_TARGETS);
            } else {
                // delete tempdir
                try (Stream<Path> paths = Files.walk(tmpdir.toPath())) {
                    //noinspection ResultOfMethodCallIgnored
                    paths.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                }
            }
        }
    }

    private List<Class<?>> loadClassBatch(Map<String, byte[]> classes) throws IllegalAccessException, InsufficientAccessRightsException, ClassLoadingException {
        // Instead of injecting the bytecode directly into a classloader, it might also be possible to have groovy generate
        // .class files at runtime and add the directory to the classpath / module path
        ArrayList<Class<?>> result = new ArrayList<>();
        for (Map.Entry<String, byte[]> gc : classes.entrySet()) {
            if (gc.getValue() == null) {
                if (TRACE || TRACE_COMPILE) REPLLog.trace("Not loading uncompiled class {}", gc.getKey());
                continue;
            }
            try {
                Class<?> loadedClass = defineClass(classLoader, gc.getKey(), gc.getValue());
                groovyClasses.add(loadedClass);
                result.add(loadedClass);
                if (GroovyDynamized.isDynamizedClass(loadedClass)) {
                    if (!deferredMetaClasses) addDynamizedMetaClass(loadedClass);
                    Class<?> topDynamized = loadedClass;
                    Integer idx = null;
                    ArrayList<String> intermediate = new ArrayList<>();
                    for (Class<?> klass = loadedClass.getSuperclass(); klass != null; klass = klass.getSuperclass()) {
                        if (isPatchedClass(klass.getName())) continue;
                        if (!GroovyDynamized.isDynamizedClass(klass)) {
                            intermediate.add(klass.getName());
                        } else {
                            topDynamized = klass;
                            idx = intermediate.size();
                        }
                    }
                    if (idx != null) REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "REPL: Intermediate classes between dynamized classes {} and {} are not dynamized: {}", loadedClass.getName(), topDynamized.getName(), String.join(", ", intermediate.subList(0, idx))), INTERNAL_LOG_TARGETS);
                }
            } catch (InaccessibleObjectException e) {
                throw new InsufficientAccessRightsException("Could not load your class '" + gc.getKey() + "'", e);
            } catch (InvocationTargetException e) {
                throw new ClassLoadingException("Can't load your Groovy class '" + gc.getKey() + "'. If it already exists in the JVM, you may need to use the REPL agent to load your Groovy sources before the conflicting version gets loaded.", e);
            } catch (ExceptionInInitializerError e) {
                throw new ClassLoadingException("Can't load your Groovy class '" + gc.getKey() + "' because its static initializer threw an exception. Maybe you need to set the 'CAU.Groovy.DeferMetaClasses' system property to true and add the MetaClasses yourself at a later time.", e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Could not define class, possibly an internal error?", e);
            }
        }
        return result;
    }

    private GroovyPatchesTransformer determineCompileOrder(List<File> files) throws IOException {
        CompilerConfiguration cc = new CompilerConfiguration();
        GroovyPatchesTransformer patchOrder = new GroovyPatchesTransformer(this, patcheeClassPath, true);
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStaticImport("de.uni_kiel.rz.fdr.repl.REPLBreakpoint", "replbreakpoint");
        imports.addImport("Patches", "de.uni_kiel.rz.fdr.repl.Patches");
        imports.addImport("Dynamize", "de.uni_kiel.rz.fdr.repl.Dynamize");
        cc.addCompilationCustomizers(imports, patchOrder);

        File tmpdir = Files.createTempDirectory("cau-repl-compiletmp.").toFile();
        try {
            tmpdir.deleteOnExit();
            CompilationUnit cu = new CompilationUnit(cc);
            cu.setClassLoader(new GroovyClassLoader(classLoader));
            cc.setTargetDirectory(tmpdir);
            cu.addSources(files.toArray(new File[0]));
            cu.compile(patchOrder.getPhase().getPhaseNumber());
        } finally {
            // delete tempdir
            try (Stream<Path> paths = Files.walk(tmpdir.toPath())) {
                //noinspection ResultOfMethodCallIgnored
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }

        return patchOrder;
    }



    @SuppressWarnings("UnusedReturnValue")
    protected static Class<?> defineClass(ClassLoader cl, String name, byte[] bytecode) throws InvocationTargetException, IllegalAccessException, InsufficientAccessRightsException, NoSuchMethodException {
        // maybe try to use the agent's Instrumentation.redefineClasses for this?
        Class<?> loadedClass;
        if (TRACE || TRACE_COMPILE) REPLLog.trace("Define Class: {}", name);

        if (cl instanceof GroovyClassLoader gcl) {
            // it's easier for groovy classloaders
            loadedClass = gcl.defineClass(name, bytecode);
        } else {
            // try to inject it into an unsuspecting normal classloader using dark powers: requires --add-opens 'java.base/java.lang=ALL-UNNAMED'. maybe the agent's Instrumentation.redefineModule() can be used instead of this parameter?
            loadedClass = (Class<?>) darkInvocation(cl, "defineClass", new Class<?>[]{String.class, byte[].class, int.class, int.class}, new Object[]{name, bytecode, 0, bytecode.length});
        }

        return loadedClass;
    }

    private static class CompilationOutputFirstClassNodeResolver extends ClassNodeResolver {

        private final ClassLoader classLoader;
        private final List<String> compiledClasses;

        public CompilationOutputFirstClassNodeResolver(ClassLoader classLoader, List<String> compiledClasses) {
            this.classLoader = classLoader;
            this.compiledClasses = compiledClasses;
        }

        @Override
        public LookupResult findClassNode(String name, CompilationUnit compilationUnit) {
            if (compiledClasses.contains(name)) {
                try {
                    Class<?> res = classLoader.loadClass(name);
                    return new LookupResult(null, ClassHelper.make(res));
                } catch (ClassNotFoundException ignore) {}
            }
            return super.findClassNode(name, compilationUnit);
        }
    }
}
