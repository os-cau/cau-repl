// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.net.URI;
import java.util.*;

public class GroovyLoadOrderTransformer extends CompilationCustomizer {
    public List<ClassNode> loadOrder = new ArrayList<>();
    public List<URI> sourceOrder = new ArrayList<>();
    private final Map<String, URI> sourceMap = new HashMap<>();

    public GroovyLoadOrderTransformer() {
        super(CompilePhase.FINALIZATION);
    }

    @Override
    public void call(SourceUnit sourceUnit, GeneratorContext generatorContext, ClassNode classNode) throws CompilationFailedException {
        sourceMap.put(classNode.getName(), sourceUnit.getSource().getURI());
        loadOrder.remove(classNode);
        loadOrder.add(classNode);
        loadOrder = sortLoadOrder(loadOrder);
        sourceOrder.clear();
        for (ClassNode cn : loadOrder) if (!cn.getName().contains("$")) sourceOrder.add(sourceMap.get(cn.getName()));
        if (new HashSet<>(sourceOrder).size() != sourceOrder.size()) throw new RuntimeException("Internal Error: inconsistent source order");
    }

    public static List<ClassNode> getAllSuperclasses(ClassNode classNode) {
        List<ClassNode> res = new ArrayList<>();
        for (ClassNode x = classNode.getSuperClass(); x != null; x = x.getSuperClass()) res.add(x);
        return res;
    }

    public static int getNumberOfSuperclasses(ClassNode classNode) {
        return getAllSuperclasses(classNode).size();
    }

    private static List<ClassNode> sortLoadOrder(List<ClassNode> classes) {
        boolean[][] edges = new boolean[classes.size()][classes.size()];
        boolean[] visited = new boolean[classes.size()];
        List<Integer> order = new ArrayList<>();
        final List<ClassNode> sortedClasses = new ArrayList<>(classes);

        // construct dependency graph
        sortedClasses.sort(Comparator.comparing(ClassNode::getName));
        for (int i = 0; i < sortedClasses.size(); i++) {
            visited[i] = false;
            List<String> sc = getAllSuperclasses(sortedClasses.get(i)).stream().map(ClassNode::getName).toList();
            for (int j = 0; j < sortedClasses.size(); j++) {
                if (i == j) continue;
                // superclasses before their subclasses
                if (sc.contains(sortedClasses.get(j).getName())) edges[i][j] = true;
                // outer classes before their inner classes
                else if (sortedClasses.get(i).getName().startsWith(sortedClasses.get(j).getName() + "$")) edges[i][j] = true;
            }
        }

        // topological sort
        for (int i = 0; i < sortedClasses.size(); i++) {
            if (!visited[i]) visitLoadOrder(i, edges, visited, order);
        }
        return new ArrayList<>(order.stream().map(sortedClasses::get).toList());
    }

    protected static void visitLoadOrder(int i, boolean[][] edges, boolean[] visited, List<Integer> order) {
        visited[i] = true;
        for (int j = 0; j < visited.length; j++) {
            if (i == j) continue;
            if (!visited[j] && edges[i][j]) visitLoadOrder(j, edges, visited, order);
        }
        order.add(i);
    }
}
