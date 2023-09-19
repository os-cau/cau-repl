// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.uni_kiel.rz.fdr.repl.Dynamize;

public class GroovyDynamizeTransformer1 extends CompilationCustomizer {
    private final Set<String> processed = new HashSet<>();
    private final HashMap<String, Boolean> dynamizedClasses = new HashMap<>();

    public GroovyDynamizeTransformer1() {
        super(CompilePhase.CONVERSION);
    }

    @Override
    public void call(SourceUnit sourceUnit, GeneratorContext generatorContext, ClassNode classNode) throws CompilationFailedException {
        if (!processed.add(classNode.getName())) return;
        // only dynamize classes with @Dynamize annotations, as well as their inner classes
        boolean inherit = true;
        boolean hasAnnotation = false;
        for (AnnotationNode an : classNode.getAnnotations()) {
            // we can only match on the simple name in this compilation phase
            if (!an.getClassNode().getName().equals(Dynamize.class.getSimpleName())) continue;
            Expression ex = an.getMember("inherit");
            if (ex != null) inherit = !ex.getText().equalsIgnoreCase("false");
            hasAnnotation = true;
            break;
        }
        boolean isDynamicInner = dynamizedClasses.keySet().stream().anyMatch(p -> classNode.getName().startsWith(p + "$"));
        if (!hasAnnotation && !isDynamicInner) return;

        dynamizeClassNode(classNode, inherit);
    }

    public Set<String> getDynamizedClasses() {
        return dynamizedClasses.keySet();
    }

    public boolean isDynamizationInherited(String className) {
        return dynamizedClasses.getOrDefault(className, false);
    }

    public void dynamizeClassNode(ClassNode classNode, boolean inherit) {
        classNode.addInterface(ClassHelper.make(GroovyDynamized.class));
        dynamizedClasses.put(classNode.getName(), inherit);
    }

}
