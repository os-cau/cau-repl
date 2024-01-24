// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.Helpers;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.builder.GraphTypeBuilder;
import org.jgrapht.traverse.TopologicalOrderIterator;

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
        Graph<ClassNode, DefaultEdge> graph = GraphTypeBuilder
                .<ClassNode, DefaultEdge> directed()
                .allowingMultipleEdges(false)
                .allowingSelfLoops(false)
                .edgeClass(DefaultEdge.class)
                .buildGraph();
        for (ClassNode c : classes) {
            graph.addVertex(c);
            // superclasses before their subclasses
            for (ClassNode s : getAllSuperclasses(c)) {
                if (s.equals(c)) continue;
                if (!classes.contains(s)) continue;
                graph.addVertex(s);
                graph.addEdge(c, s);
            }
            // outer classes before their inner classes
            for (ClassNode x : classes) {
                if (x.equals(c)) continue;
                if (!classes.contains(x)) continue;
                graph.addVertex(x);
                if (Helpers.isInnerClassOf(c.getName(), x.getName())) {
                    graph.addEdge(x, c);
                }
            }
        }

        // topological sort
        List<ClassNode> loadOrder = new ArrayList<>();
        for (TopologicalOrderIterator<ClassNode, DefaultEdge> it = new TopologicalOrderIterator<>(graph); it.hasNext(); ) {
            ClassNode x = it.next();
            loadOrder.add(x);
        }
        Collections.reverse(loadOrder);
        return loadOrder;
    }
}
