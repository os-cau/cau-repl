// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.*;
import de.uni_kiel.rz.fdr.repl.error.InsufficientAccessRightsException;
import de.uni_kiel.rz.fdr.repl.error.UncheckedCompilationException;
import groovyjarjarasm.asm.*;
import groovyjarjarasm.asm.commons.*;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.Collectors;

import static de.uni_kiel.rz.fdr.repl.Helpers.isGeneratedMethod;
import static de.uni_kiel.rz.fdr.repl.Helpers.patcheeStubMethod;
import static de.uni_kiel.rz.fdr.repl.REPLLog.*;
import static groovyjarjarasm.asm.Opcodes.*;

public class GroovyPatchesTransformer extends CompilationCustomizer {

    public record GroovyPatchResult(String mainTargetName, String mainPatchedName, List<String> targetNames, List<String> patchedNames, List<Class<?>> patchedClasses, Map<String, String> renameMap) {}

    public static final String PATCHEE_SUFFIX = "_CAUREPL";
    public static final String PATCHEESTUB_PREFIX = "_CAUREPL_P$";


    public record PatchesSettings (String classPath, String target, boolean makePublic, boolean preserveSuper, String[] stripAnnotations, boolean force) {}
    private record SimulationResult(LinkedHashSet<String> dependencies) {}

    private final GroovySourceDirectory sourceDirectory;
    private final String defaultClassPath;
    private final boolean dryRun;
    private final Set<String> processed = new HashSet<>();
    private final Map<String, LinkedHashSet<String>> dependencies = new HashMap<>();
    private final Map<String, URI> sources = new HashMap<>();
    private final Map<String, PatchesSettings> patchesSettings = new HashMap<>();

    public GroovyPatchesTransformer(GroovySourceDirectory sourceDirectory, String defaultClassPath, boolean dryRun) {
        super(CompilePhase.CONVERSION);
        this.sourceDirectory = sourceDirectory;
        this.defaultClassPath = defaultClassPath;
        this.dryRun = dryRun;
    }

    @Override
    public void call(SourceUnit sourceUnit, GeneratorContext generatorContext, ClassNode classNode) throws CompilationFailedException {
        if (!processed.add(classNode.getName())) return;
        sources.put(classNode.getName(), sourceUnit.getSource().getURI());
        PatchesSettings settings = null;
        for (AnnotationNode an : classNode.getAnnotations()) {
            String cp = defaultClassPath;
            String ta = null;
            boolean mp = true;
            boolean ps = true;
            boolean fo = false;
            String[] sa = new String[]{};

            // we can only match on the simple name in this compilation phase
            if (!an.getClassNode().getName().equals(Patches.class.getSimpleName())) continue;
            if (!classNode.getSuperClass().getName().equals("java.lang.Object")) throw new UncheckedCompilationException("Class " + classNode.getName() + " mixes @Patches and 'extends'");
            if (settings != null) throw new UncheckedCompilationException("Class " + classNode.getName() + " has multiple @Patches annotations");

            Expression ex = an.getMember("classPath");
            if (ex != null) cp = ex.getText();

            ex = an.getMember("target");
            ta = ex == null ? classNode.getName() : ex.getText();

            ex = an.getMember("makePublic");
            if (ex != null) mp = !ex.getText().equalsIgnoreCase("false");

            ex = an.getMember("preserveSuper");
            if (ex != null) ps = !ex.getText().equalsIgnoreCase("false");

            ex = an.getMember("stripAnnotations");
            if (ex != null) sa = ex.getText().split(",");
            if (sa.length == 0) sa = null;

            ex = an.getMember("force");
            if (ex != null) fo = ex.getText().equalsIgnoreCase("true");

            settings = new PatchesSettings(cp, ta, mp, ps, sa, fo);
            patchesSettings.put(classNode.getName(), settings);
        }

        if (!dryRun && settings != null) {
            // load the patchee class under a different name...
            doPatch(classNode, settings);
        } else if (dryRun) {
            // dry-run only to determine patch order
            try {
                SimulationResult sr = doDryRun(classNode, sourceUnit, settings);
                dependencies.put(classNode.getName(), sr.dependencies);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            if (TRACE || TRACE_COMPILE) REPLLog.trace("Class {} dependency candidates: {}", classNode.getName(), String.join(", ", dependencies.get(classNode.getName())));
        }
    }

    private void doPatch(ClassNode classNode, PatchesSettings settings) {
        GroovyPatchResult patchResult;
        try {
            patchResult = loadPatchee(sourceDirectory.getClassLoader(), settings.target, PATCHEE_SUFFIX, settings, new HashMap<>());
            Class<?> patchee = patchResult.patchedClasses().get(0);
            if (patchee.isInterface() || patchee.isAnnotation())
                throw new UncheckedCompilationException(patchResult.mainTargetName() + " is not a plain class and can't be patched");
            if (!settings.force) for (Annotation a : patchee.getAnnotations()) {
                String an = a.annotationType().getName();
                if (an.startsWith("javax.persistence.") || an.startsWith("org.hibernate.")) {
                    throw new UncheckedCompilationException("Patchee " + patchResult.mainTargetName() + " has a " + an + " annotation. Patching it might have harmful side effects on your DB. Set the 'force' flag and maybe 'stripAnnotations' to continue anyway.");
                }
            }
        } catch (Exception e) {
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: Error while patching {} from {} with Groovy class {}", settings.target, settings.classPath, classNode.getName()), REPLLog.INTERNAL_LOG_TARGETS);
            throw new UncheckedCompilationException("Could not load class " + settings.target + " from " + settings.classPath + " for patching", e);
        }
        // ... and set it as the superclass of the groovy class
        classNode.setSuperClass(ClassHelper.make(patchResult.mainPatchedName()));
        classNode.addInterface(ClassHelper.make(GroovyPatching.class));
        // automatically inherit all constructors
        classNode.addAnnotation(ClassHelper.make(groovy.transform.InheritConstructors.class));
        // TODO do we want to inherit annotations?
        if (!patchResult.targetNames().isEmpty())
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.INFO, "REPL: Groovy class {} patches {} from {}", classNode.getName(), String.join(", ", patchResult.targetNames()), settings.classPath), REPLLog.INTERNAL_LOG_TARGETS);
        if (!classNode.getName().equals(settings.target))
            REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "REPL: Transformed Groovy class {} and its target {} have different names", classNode.getName(), settings.target), REPLLog.INTERNAL_LOG_TARGETS);
    }

    private SimulationResult doDryRun(ClassNode classNode, SourceUnit sourceUnit, PatchesSettings settings) throws IOException {
        LinkedHashMap<String, SimulationResult> toCheck = new LinkedHashMap<>();

        if (settings != null) {
            // this is a @Patches class - load the patchee in a dummy classloader and find its dependencies
            toCheck.put(settings.target, null);
        }

        // now check the ast directly...
        // ...superclasses...
        for (ClassNode kl = classNode.getSuperClass(); kl != null; kl = kl.getSuperClass()) {
            for (String imp : locateImport(kl.getName(), sourceUnit)) {
                toCheck.put(imp, null);
            }
        }
        // ...interfaces...
        for (ClassNode iface : classNode.getAllInterfaces()) {
            for (String imp : locateImport(iface.getName(), sourceUnit)) {
                toCheck.put(imp, null);
            }
        }
        // ...and annotations
        for (AnnotationNode ann : classNode.getAnnotations()) {
            for (String imp : locateImport(ann.getClassNode().getName(), sourceUnit)) {
                toCheck.put(imp, null);
            }
        }

        // load each candidate in a dummy classloader and check dependencies
        for (String chk : toCheck.keySet()) {
            try {
                SimulationResult sr = simulateDependencies(chk, settings != null ? settings.classPath : defaultClassPath);
                toCheck.put(chk, sr);
            } catch (ClassNotFoundException ex) {
                if (TRACE || TRACE_COMPILE)
                    REPLLog.trace("Ignoring speculative transitive dependencies of {} -> {} for now", classNode.getName(), chk);
            }
        }

        // collect results
        SimulationResult result = new SimulationResult(new LinkedHashSet<>());
        for (Map.Entry<String, SimulationResult> x : toCheck.entrySet()) {
            result.dependencies.add(x.getKey());
            if (x.getValue() != null && x.getValue().dependencies != null) result.dependencies.addAll(x.getValue().dependencies);
        }
        return result;
    }

    private SimulationResult simulateDependencies(String className, String classPath) throws ClassNotFoundException, IOException {
        try (URLClassLoader loader = new URLClassLoader(Helpers.deglobClassPath(classPath).toArray(new URL[]{}), null)) {
            Class<?> simulatedClass = loader.loadClass(className);
            LinkedList<Class<?>> queue = new LinkedList<>();
            HashSet<String> seen = new HashSet<>();
            queue.add(simulatedClass.getSuperclass());
            queue.addAll(Arrays.stream(simulatedClass.getInterfaces()).toList());

            LinkedHashSet<String> simulatedDeps = new LinkedHashSet<>();
            while (!queue.isEmpty()) {
                Class<?> klass = queue.removeFirst();
                if (klass == null || !seen.add(klass.getName())) continue;
                simulatedDeps.add(klass.getName());
                queue.add(klass.getSuperclass());
                queue.addAll(Arrays.stream(klass.getInterfaces()).toList());
                queue.addAll(Arrays.stream(klass.getAnnotations()).map(Annotation::annotationType).toList());
            }

            return new SimulationResult(simulatedDeps);
        }
    }

    public List<URI> getCompileOrder() {
        boolean[][] edges = new boolean[dependencies.size()][dependencies.size()];
        boolean[] visited = new boolean[dependencies.size()];
        List<Integer> order = new ArrayList<>();
        final List<String> sortedClasses = new ArrayList<>(dependencies.keySet());

        // construct dependency graph
        sortedClasses.sort(Comparator.naturalOrder());
        for (int i = 0; i < sortedClasses.size(); i++) {
            visited[i] = false;
            LinkedHashSet<String> sc = dependencies.get(sortedClasses.get(i));
            for (int j = 0; j < sortedClasses.size(); j++) {
                if (i == j) continue;
                // superclasses before their subclasses
                if (sc.contains(sortedClasses.get(j))) edges[i][j] = true;
            }
        }

        // topological sort
        for (int i = 0; i < sortedClasses.size(); i++) {
            if (!visited[i]) GroovyLoadOrderTransformer.visitLoadOrder(i, edges, visited, order);
        }
        return new ArrayList<>(order.stream().map(sortedClasses::get).map(sources::get).distinct().toList());
    }


    // FIXME this is an ugly hack. we run in a compilation phase where imports are not yet resolved,
    // to work around this, we employ a heuristic which can break in certain situations
    private static List<String> locateImport(String className, SourceUnit sourceUnit) {
        // starts with a lower case letter -> probably fully qualified
        if (className.startsWith(className.substring(0,1).toLowerCase())) return new ArrayList<>(List.of(className));

        // find an exact match
        for (ImportNode imp : sourceUnit.getAST().getImports()) {
            if (className.equals(imp.getAlias()) || className.startsWith(imp.getAlias() + ".")) {
                return new ArrayList<>(List.of(imp.getClassName()));
            }
        }

        // no exact match found. heuristically return dependencies in all *-imports and our own package
        List<String> res = new ArrayList<>(List.of(sourceUnit.getAST().getPackageName() + className));
        res.addAll(new ArrayList<>(sourceUnit.getAST().getStarImports().stream().map(i -> i.getPackageName() + className).toList()));

        return res;
    }

    public GroovyPatchResult loadPatchee(ClassLoader classLoader, String originalName, String suffix, PatchesSettings settings, Map<String, String> renameMap) throws IOException, InvocationTargetException, IllegalAccessException, InsufficientAccessRightsException, NoSuchMethodException {
        // find top class loader
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        while (scl.getParent() != null) scl = scl.getParent();

        // resolve globs in classpath
        List<URL> urls = Helpers.deglobClassPath(settings.classPath);

        // rename and load class
        String newName = appendSuffix(originalName, suffix);
        String originalNameRes = originalName.replaceAll("\\.", "/");
        String newNameRes = newName.replaceAll("\\.", "/");
        renameMap = new HashMap<>(renameMap);
        GroovyPatchResult result = new GroovyPatchResult(originalName, newName, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), renameMap);
        try (URLClassLoader patcheeClassLoader = new URLClassLoader(urls.toArray(new URL[0]), scl);
             InputStream klass = patcheeClassLoader.getResourceAsStream(originalNameRes + ".class")) {
            if (klass == null) throw new UncheckedCompilationException("Could not find resource '" + originalNameRes + ".class' in supplied classpath: " + settings.classPath);

            // find inner classes (non-recursively) to construct rename map
            ClassReader classReader = new ClassReader(klass);
            groovyjarjarasm.asm.tree.ClassNode classNode = new groovyjarjarasm.asm.tree.ClassNode();
            classReader.accept(classNode, 0);
            List<groovyjarjarasm.asm.tree.InnerClassNode> innerClasses = classNode.innerClasses.stream().filter(ic -> ic.name.startsWith(originalNameRes + "$")).toList();

            // construct rename map
            renameMap.put(originalNameRes, newNameRes);
            for (groovyjarjarasm.asm.tree.InnerClassNode ic : innerClasses) renameMap.put(ic.name, appendSuffix(ic.name, suffix));
            if (TRACE || TRACE_COMPILE) REPLLog.trace("Renaming {}: {}", originalName, renameMap.entrySet().stream().map(e -> e.getKey() + " -> " + e.getValue()).collect(Collectors.joining(", ")));

            // rewrite class
            Remapper remapper = new SimpleRemapper(renameMap);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ClassVisitor classVisitor = new ClassRemapper(classWriter, remapper);
            if (settings.stripAnnotations != null) classVisitor = new StripAnnotationsAdapter(ASM9, classVisitor, settings.stripAnnotations);
            if (settings.makePublic) classVisitor = new AllPublicAdapter(ASM9, classVisitor);
            if (settings.preserveSuper) classVisitor = new AddSuperStubsAdapter(ASM9, classVisitor, newName, classLoader);
            classReader.accept(classVisitor, 0);
            try {
                result.patchedClasses.add(GroovySourceDirectory.defineClass(classLoader, newName, classWriter.toByteArray()));
            } catch (LinkageError | InvocationTargetException ex) {
                Throwable t = ex;
                if (ex instanceof InvocationTargetException iex) t = iex.getCause();
                if (t instanceof LinkageError) {
                    // ignoring duplicate definition of patchee: this can happen e.g. if both an inner and outer class are patched
                    if (TRACE || TRACE_COMPILE) REPLLog.trace("Ignoring duplicate definition of patchee {}", newName);
                    try {
                        result.patchedClasses.add(classLoader.loadClass(newName));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException("Could not load original class of patchee " + newName + ", internal error?", e);
                    }
                } else {
                    throw ex;
                }
            }
            result.targetNames.add(originalName);
            result.patchedNames.add(newName);

            // now process all inner classes as well
            for (groovyjarjarasm.asm.tree.InnerClassNode innerClass : innerClasses) {
                String innerClassName = innerClass.name.replaceAll("/", ".");
                if (result.targetNames.contains(innerClassName)) continue;
                GroovyPatchResult subResult = loadPatchee(classLoader, innerClassName, suffix, settings, renameMap);
                result.patchedClasses.addAll(subResult.patchedClasses);
                result.targetNames.addAll(subResult.targetNames);
                result.patchedNames.addAll(subResult.patchedNames);
                result.renameMap.putAll(subResult.renameMap);
            }
        }

        return result;
    }


    private String appendSuffix(String className, String suffix) {
        int i = className.indexOf("$");
        if (i < 0) return className + suffix;
        return className.substring(0, i) + suffix + className.substring(i);
    }


    private static class AddSuperStubsAdapter extends ClassVisitor {
        private final ClassVisitor classVisitor;
        private final String newName;
        private String superName = null;
        private List<java.lang.reflect.Method> superMethods = null;

        private final ClassLoader classLoader;

        protected AddSuperStubsAdapter(int api, ClassVisitor classVisitor, String newName, ClassLoader classLoader) {
            super(api, classVisitor);
            this.classVisitor = classVisitor;
            this.newName = newName;
            this.classLoader = classLoader;
        }


        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            try {
                Class<?> superKlass = classLoader.loadClass(superName.replaceAll("/", "."));
                superMethods = Arrays.stream(superKlass.getMethods()).toList();
                this.superName = superName;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not load superclass, internal error?", e);
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public void visitEnd() {
            if (superMethods != null) for (java.lang.reflect.Method method : superMethods) {
                String name = method.getName();
                int acc = method.getModifiers();
                String descriptor = Type.getMethodDescriptor(method);
                String stubName = patcheeStubMethod(newName, name);

                if ((acc & (ACC_SYNTHETIC | ACC_NATIVE | ACC_STATIC)) != 0
                        || name.contains("$") || name.contains("<") || name.contains(">")
                        || isGeneratedMethod(name)
                        || GroovyDynamizeTransformer2.DYNAMIZE_BLACKLIST.contains(name)) continue;
                if (TRACE_COMPILE_METHODS) REPLLog.trace("PatcheeStub: {} < {} -- {}{}", newName, superName, name, descriptor);

                Method stub = new Method(stubName, descriptor);
                MethodVisitor mv = classVisitor.visitMethod(ACC_PUBLIC, stubName, descriptor, null, null);
                GeneratorAdapter mg = new GeneratorAdapter(ACC_PUBLIC, stub, mv);
                mg.loadThis();
                mg.loadArgs();
                mg.visitMethodInsn(INVOKESPECIAL, superName, name, descriptor, false);
                mg.returnValue();
                mg.endMethod();
            }

            super.visitEnd();
        }
    }



    private static class StripAnnotationsAdapter extends ClassVisitor {
        private final ClassVisitor classVisitor;
        private final String[] annotations;

        protected StripAnnotationsAdapter(int api, ClassVisitor classVisitor, String[] annotations) {
            super(api, classVisitor);
            this.classVisitor = classVisitor;
            if (annotations == null) {
                this.annotations = new String[]{};
            } else {
                this.annotations = Arrays.stream(annotations).map(a -> a.replaceAll("\\.", "/") + ";").toArray(String[]::new);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            for (String annotation : annotations) {
                if (
                        (descriptor.equals("L" + annotation))
                                || (!annotation.contains("/") && descriptor.endsWith("/" + annotation))
                ) {
                    if (TRACE || TRACE_COMPILE) REPLLog.trace("REPL: Stripped Annotation: {}", descriptor);
                    return null;
                }
            }
            return classVisitor.visitAnnotation(descriptor, visible);
        }

    }

    private static class AllPublicAdapter extends ClassVisitor {

        private final ClassVisitor classVisitor;

        protected AllPublicAdapter(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
            this.classVisitor = classVisitor;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            int acc = ((access & ACC_SYNTHETIC) != 0) ? access : ((access & ~ACC_FINAL & ~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC);
            classVisitor.visit(version, acc, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            int acc = ((access & ACC_SYNTHETIC) != 0) ? access : ((access & ~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC);
            return classVisitor.visitField(acc, name, descriptor, signature, value);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            int acc = ((access & ACC_SYNTHETIC) != 0) ? access : ((access & ~ACC_FINAL & ~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC);
            return classVisitor.visitMethod(acc, name, descriptor, signature, exceptions);
        }
    }




}