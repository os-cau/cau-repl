// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPLLog;
import de.uni_kiel.rz.fdr.repl.REPLLogEntry;
import groovy.lang.GroovyObject;
import groovyjarjarasm.asm.Opcodes;
import org.apache.groovy.ast.tools.MethodNodeUtils;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static de.uni_kiel.rz.fdr.repl.Helpers.*;
import static de.uni_kiel.rz.fdr.repl.REPLLog.*;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyLoadOrderTransformer.getNumberOfSuperclasses;
import static de.uni_kiel.rz.fdr.repl.groovy.GroovyLoadOrderTransformer.getAllSuperclasses;
import static groovyjarjarasm.asm.Opcodes.*;

/*
    Disclaimer: The dynamization feature should be considered a very experimental proof-of-concept built for research
    purposes. It might work for some cases, but is by no means a sane, comprehensive solution. Do not use in production
    and expect a heavy performance impact.
 */

public class GroovyDynamizeTransformer2 extends CompilationCustomizer {

    public static final String DYNAMIZE_PREFIX = "_CAUREPL_D$";
    public static final String CONSTRUCTOR_PREFIX = "_CAUREPL_C$";
    public static final String SUPERSTUB_PREFIX = "_CAUREPL_S$";
    public static final Set<String> DYNAMIZE_BLACKLIST = new HashSet<>(
                                                            Stream.concat(
                                                                Stream.concat(
                                                                    Arrays.stream(GroovyObject.class.getDeclaredMethods()).map(Method::getName),
                                                                    Arrays.stream(GroovyDynamized.class.getDeclaredMethods()).map(Method::getName)
                                                                ), Stream.concat(
                                                                    Arrays.stream(GroovyPatching.class.getDeclaredMethods()).map(Method::getName),
                                                                    Stream.of("<clinit>", "finalize")
                                                                )
                                                            ).toList()
                                                         );

    private final Set<String> processed = new HashSet<>();
    private final GroovyDynamizeTransformer1 transformer1;

    public GroovyDynamizeTransformer2(GroovyDynamizeTransformer1 transformer1) {
        super(CompilePhase.CANONICALIZATION);
        this.transformer1 = transformer1;
    }

    @Override
    public void call(SourceUnit sourceUnit, GeneratorContext generatorContext, ClassNode classNode) throws CompilationFailedException {
        if (!classNode.declaresInterface(ClassHelper.make(GroovyDynamized.class))) {
            ClassNode realParent = realParent(classNode);
            if (realParent == null || !transformer1.isDynamizationInherited(realParent.getName())) return;
            if (TRACE || TRACE_COMPILE) REPLLog.trace("Class {} inherited dynamization from {}", classNode.getName(), realParent.getName());
            transformer1.dynamizeClassNode(classNode, true);
        }
        if (!processed.add(classNode.getName())) return;

        if (classNode.isInterface() || classNode.isAnnotationDefinition()) {
            if (classNode.getName().contains("$")) return;
            throw new GroovySourceDirectory.UncheckedCompilationException(classNode.getName() + " is not a plain class and can't by dynamized.");
        }

        if (TRACE || TRACE_COMPILE) REPLLog.trace("{}: {} < {} ({})", classNode.isInterface() ? "INTERFACE" : "CLASS", classNode.getName(), String.join(", ", getAllSuperclasses(classNode).stream().map(ClassNode::getName).toList()), getNumberOfSuperclasses(classNode));

        for (MethodNode mn : selectDynamizableMethods(classNode)) {
            ClassNode staticClass = mn.isStatic() ? classNode : null;
            ConstructorNode constructorNode = null;
            if (mn instanceof ConstructorNode cn) constructorNode = cn;
            boolean isVoid = mn.getReturnType().getName().equals("void");
            boolean isSuper = !mn.getDeclaringClass().equals(classNode);
            int mod = mn.getModifiers();

            if (TRACE || TRACE_COMPILE) REPLLog.trace("    {}{}{}: {}<{}::{} ({})", mn.isStatic() ? "STATIC " : "", mn.getDeclaringClass().isInterface() ? "INTERFACE ": "", constructorNode != null ? "CONSTRUCTOR" : isTopDynamized(classNode) ? "SUPERSTUB" : "METHOD", classNode.getName(), mn.getDeclaringClass().getName(), mn.getTypeDescriptor(), mn.getModifiers());

            // groovy complains if finalize is not public
            if (mn.getName().equals("finalize") && mn.getDeclaringClass().getName().equals("java.lang.Object")) mod = (mod & ~ACC_PRIVATE & ~ACC_PROTECTED) | ACC_PUBLIC;
            // groovy does not use ACC_VARARGS at this stage, and confuses it with ACC_TRANSIENT, leading to a compiler error
            // we must strip it here. groovy's compiler will automatically add it back if the last parameter is an array (which is internally the case for varargs)
            mod = mod & ~ACC_VARARGS;

            if (constructorNode == null) {
                // handle normal method
                // generate superstubs for all methods if we are the topmost dynamic class
                if (isTopDynamized(classNode)) {
                    // TODO default methods from interfaces? these can be added below the topmost groovy superclass
                    MethodNode superStub = new MethodNode(superStubMethod(classNode.getName(), mn.getName()), mod, mn.getReturnType(), mn.getParameters(), mn.getExceptions(), emitSuperStub(staticClass, mn.getName(), mn.getParameters(), isVoid));
                    superStub.setDeclaringClass(classNode);
                    classNode.addMethod(superStub);
                }

                // if we have our own implementation, rename it to prepare dynamization
                if (!isSuper) {
                    MethodNode renamed = new MethodNode(dynamizedMethod(classNode.getName(), mn.getName()), mod, mn.getReturnType(), mn.getParameters(), mn.getExceptions(), mn.getCode());
                    renamed.setDeclaringClass(classNode);
                    classNode.addMethod(renamed);
                }

                // replace the method with a dynamized stub that either calls our own renamed version, or invokes a super method -> but not for default methods of interfaces
                if (!mn.getDeclaringClass().isInterface()) {
                    // don't change mn in the process: inherited method objects are shared between all classes and this would mess everything up
                    // we will add a new method instead
                    classNode.removeMethod(mn);
                    MethodNode ourStub = new MethodNode(mn.getName(), mod, mn.getReturnType(), mn.getParameters(), mn.getExceptions(), emitInvocation(staticClass != null ? staticClass.getName() : null, classNode.getName(), mn.getName(), mn.getParameters(), isVoid, isSuper));
                    ourStub.setDeclaringClass(classNode);
                    // TODO we probably want to inherit method annotations, maybe even from the _CAUREPL patchee
                    classNode.addMethod(ourStub);
                }
            } else {
                // handle constructor
                // rename it, converting it to a normal method. also strip the super constructor call if it was present
                Statement code = constructorNode.getCode();
                ExpressionStatement constructorSuper = null;
                if (constructorNode.firstStatementIsSpecialConstructorCall()) {
                    if (code instanceof BlockStatement bs && !bs.isEmpty() && !bs.getStatements().isEmpty() && bs.getStatements().get(0) instanceof ExpressionStatement es && es.getExpression() instanceof ConstructorCallExpression) {
                        LinkedList<Statement> patched = new LinkedList<>(bs.getStatements());
                        constructorSuper = (ExpressionStatement) patched.remove(0);
                        code = new BlockStatement(patched, bs.getVariableScope());
                    } else {
                        REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "REPL: {}: constructor {} has a super-constructor call, but does not start with a constructor call statement", classNode.getName(), constructorNode), INTERNAL_LOG_TARGETS);
                        throw new GroovySourceDirectory.UncheckedCompilationException("Class " + classNode.getName() + ": constructor " + constructorNode + " has a super-constructor call, but does not start with a constructor call statement");
                    }
                }
                MethodNode renamed = new MethodNode(constructorMethod(classNode.getName()), mod, constructorNode.getReturnType(), constructorNode.getParameters(), constructorNode.getExceptions(), code);
                renamed.setDeclaringClass(classNode);
                classNode.addMethod(renamed);

                // replace the constructor with a dynamized stub
                classNode.removeConstructor(constructorNode);
                ConstructorNode ourStub = new ConstructorNode(mod, constructorNode.getParameters(), constructorNode.getExceptions(), emitConstructor(classNode.getName(), constructorNode.getParameters(), constructorSuper));
                ourStub.setDeclaringClass(classNode);
                classNode.addConstructor(ourStub);
            }
        }
    }

    private boolean isTopDynamized(ClassNode classNode) {
        ClassNode realParent = realParent(classNode);
        if (realParent != null && processed.contains(realParent.getName())) return false;
        return classNode.declaresInterface(ClassHelper.make(GroovyDynamized.class));
    }

    private ClassNode realParent(ClassNode classNode) {
        return getAllSuperclasses(classNode).stream()
                .filter(c -> !isPatchedClass(c.getName()))
                .findFirst()
                .orElse(null);
    }

    private List<MethodNode> selectDynamizableMethods(ClassNode classNode) {
        final Set<String> seen = new HashSet<>();
        final ArrayList<MethodNode> result = new ArrayList<>();
        final ClassNode go = ClassHelper.make(GroovyObject.class);

        Stream.concat(classNode.getAllDeclaredMethods().stream(), classNode.getDeclaredConstructors().stream())
                .filter(mn -> !mn.isStaticConstructor() &&
                              !isGeneratedMethod(mn.getName()) &&
                              !DYNAMIZE_BLACKLIST.contains(mn.getName()) &&
                              !mn.getName().contains("$") &&
                              (mn instanceof ConstructorNode || (!mn.getName().contains("<") && !mn.getName().contains(">"))) &&
                              !mn.getDeclaringClass().getName().equals(GroovyDynamized.class.getName()) &&
                              go.getMethod(mn.getName(), mn.getParameters()) == null &&
                              (mn.getModifiers() & Opcodes.ACC_NATIVE) == 0)
                // make sure that declarations of subclasses are given priority
                .sorted((x, y) -> Integer.compare(getNumberOfSuperclasses(y.getDeclaringClass()), getNumberOfSuperclasses(x.getDeclaringClass())))
                .forEachOrdered(mn -> { if (seen.add(MethodNodeUtils.methodDescriptorWithoutReturnType(mn))) result.add(mn); });

        // now filter out methods that don't make sense if the nearest implementation has these properties
        // don't filter on isSyntheticPublic() -> this is set for static methods
        // TODO can the expandometaclass override originally final methods? as an experiment, remove isFinal() in this condition and strip ACC_FINAL in the renamed MethodNode
        return result.stream()
                .filter(mn -> !mn.isAbstract() &&
                        !mn.isSynthetic() &&
                        !mn.isFinal() &&
                        // shadowing private methods of other classes doesn't make any sense and will cause groovy compilation to fail
                        !(mn.isPrivate() && !mn.getDeclaringClass().equals(classNode)))
                .toList();
    }

    private Statement emitConstructor(String contextClassName, Parameter[] parameters, ExpressionStatement constructorSuper) {
        ArgumentListExpression a = new ArgumentListExpression();
        a.addExpression(new ConstantExpression(contextClassName));
        a.addExpression(new ConstantExpression(constructorMethod(contextClassName)));
        for (Parameter p : parameters) a.addExpression(new VariableExpression(p));

        Expression expr = new MethodCallExpression(
                            new VariableExpression("this"),
                            new ConstantExpression("_CAUREPL_invokeVoid"),
                            a
        );

        if (constructorSuper == null) return new ExpressionStatement(expr);

        return new BlockStatement(
                new Statement[]{
                        constructorSuper,
                        new ExpressionStatement(expr)
                },
                new VariableScope()
        );

    }



    @SuppressWarnings("ConstantValue")
    private Statement emitInvocation(String staticClassName, String contextClassName, String method, Parameter[] parameters, boolean isVoid, boolean isSuper) {
        ArgumentListExpression a = new ArgumentListExpression();
        a.addExpression(new ConstantExpression(contextClassName));
        if (staticClassName != null) a.addExpression(new ConstantExpression(staticClassName));
        a.addExpression(new ConstantExpression(isSuper ? method : dynamizedMethod(contextClassName, method)));
        for (Parameter p : parameters) a.addExpression(new VariableExpression(p));

        Expression expr;

        if (staticClassName == null && isVoid) {
            String invoker = isSuper ? "_CAUREPL_invokeSuperVoid" : "_CAUREPL_invokeVoid";
            expr =  new MethodCallExpression(
                        new VariableExpression("this"),
                        new ConstantExpression(invoker),
                        a
                    );
            return new ExpressionStatement(expr);
        }

        if (staticClassName == null && !isVoid) {
            String invoker = isSuper ? "_CAUREPL_invokeSuper" : "_CAUREPL_invoke";
            expr =  new MethodCallExpression(
                        new VariableExpression("this"),
                        new ConstantExpression(invoker),
                        a
                    );
            return new ReturnStatement(expr);
        }

        if (staticClassName != null && isVoid) {
            String invoker = isSuper ? "_CAUREPL_invokeSuperStaticVoid" : "_CAUREPL_invokeStaticVoid";
            expr =  new StaticMethodCallExpression(
                        ClassHelper.make(GroovyDynamized.class),
                        invoker,
                        a
                    );
            return new ExpressionStatement(expr);
        }

        if (staticClassName != null && !isVoid) {
            String invoker = isSuper ? "_CAUREPL_invokeSuperStatic" : "_CAUREPL_invokeStatic";
            expr =  new StaticMethodCallExpression(
                        ClassHelper.make(GroovyDynamized.class),
                        invoker,
                        a
                    );
            return new ReturnStatement(expr);
        }

        // NOTREACHED
        throw new RuntimeException("Internal Error: this should be an unreachable statement.");
    }

    @SuppressWarnings("ConstantValue")
    private Statement emitSuperStub(ClassNode staticClass, String method, Parameter[] parameters, boolean isVoid) {
        ArgumentListExpression a = new ArgumentListExpression();
        for (Parameter p : parameters) a.addExpression(new VariableExpression(p));

        Expression expr;

        if (staticClass == null && isVoid) {
            expr =  new MethodCallExpression(
                    new VariableExpression("super"),
                    new ConstantExpression(method),
                    a
            );
            return new ExpressionStatement(expr);
        }

        if (staticClass == null && !isVoid) {
            expr =  new MethodCallExpression(
                    new VariableExpression("super"),
                    new ConstantExpression(method),
                    a
            );
            return new ReturnStatement(expr);
        }

        if (staticClass != null && isVoid) {
            expr =  new StaticMethodCallExpression(
                    staticClass.getSuperClass(),
                    method,
                    a
            );
            return new ExpressionStatement(expr);
        }

        if (staticClass != null && !isVoid) {
            expr =  new StaticMethodCallExpression(
                    staticClass.getSuperClass(),
                    method,
                    a
            );
            return new ReturnStatement(expr);
        }

        // NOTREACHED
        throw new RuntimeException("Internal Error: this should be an unreachable statement.");
    }


}
