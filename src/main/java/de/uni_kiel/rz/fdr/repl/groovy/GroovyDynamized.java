// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPLLog;

import groovy.lang.ExpandoMetaClass;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.HashMap;

import static de.uni_kiel.rz.fdr.repl.Helpers.*;
import static de.uni_kiel.rz.fdr.repl.REPLLog.TRACE;
import static de.uni_kiel.rz.fdr.repl.REPLLog.TRACE_DYNAMIZE;

/*
    Disclaimer: The dynamization feature should be considered a very experimental proof-of-concept built for research
    purposes. It might work for some cases, but is by no means a sane, comprehensive solution. Do not use in production
    and expect a heavy performance impact.
 */

@SuppressWarnings("unused")
public interface GroovyDynamized {
    ThreadLocal<MetaMethod> _CAUREPL_currentMethod = new ThreadLocal<>();
    ThreadLocal<HashMap<Class<?>, Boolean>> _CAUREPL_dynamizedCache = new ThreadLocal<>();
    enum METHOD_TYPE {EXPANDO, NON_DYNAMIZED, DYNAMIZED, STUB}
    record FindSuperResult(MetaMethod method, METHOD_TYPE methodType, Boolean isSuper){}

    default Object _CAUREPL_dynamicContextSuper(String method, Class<?> contextClass, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("dynamicSuper {}::{}", getClass().getName(), method);
        Class<?> klass;
        if (contextClass != null && !getClass().equals(contextClass) && contextClass.isAssignableFrom(getClass())) {
            if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("using context: {}", contextClass.getName());
            klass = contextClass;
        } else {
            klass = getClass();
        }
        ExpandoMetaClass metaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(klass);

        MetaMethod ownStub = metaClass.getMetaMethod(superStubMethod(klass.getName(), method), parameters);
        if (ownStub == null) ownStub = metaClass.getMetaMethod(patcheeStubMethod(klass.getName(), method), parameters);
        FindSuperResult sm = _CAUREPL_findSuperMethod(klass.getSuperclass().getName(), klass.getSuperclass(), method, parameters);

        if (ownStub == null || (sm.method.getDeclaringClass().getTheClass().equals(klass.getSuperclass()))) {
            if (sm.method != null) return _CAUREPL_doInvoke(sm.method, this, sm.isSuper && sm.methodType != METHOD_TYPE.EXPANDO, parameters);
            else throw new RuntimeException("no super method " + method + " found in context " + klass.getName());

            // as a final attempt, in situations where we do not even have a super stub (e.g. for final methods of non-patched superclasses)
            // we could try the non-dynamic super
            //if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("closureSuper: fallback {}", method);
            //return InvokerHelper.invokeSuperMethod(this, method, parameters);
        } else {
            if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("dynamicSuper: own stub {}", ownStub);
            return _CAUREPL_doInvoke(ownStub, this, false, parameters);
        }
    }

    default Object dynamicSuper(String method, Object... parameters) {
        return dynamicSuper(1, method, parameters);
    }

    default Object dynamicPatcheeSuper(String method, Object... parameters) {
        Class<?> contextClass = getClass().getSuperclass();
        if (!isPatchedClass(contextClass.getName())) throw new RuntimeException("Superclass " + contextClass.getName() + " is not a patchee.");
        return _CAUREPL_dynamicContextSuper(method, contextClass, parameters);
    }

    default Object dynamicSuper(int parents, String method, Object... parameters) {
        if (parents < 1) throw new RuntimeException("parents parameter must be > 0");
        Class<?> contextClass = getClass();
        for (int i = 1; contextClass.getSuperclass() != null && i < parents; i++) contextClass = contextClass.getSuperclass();
        return _CAUREPL_dynamicContextSuper(method, contextClass, parameters);
    }

    default void _CAUREPL_invokeVoid(String context, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeVoid {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), getClass().getName(), method);
        ExpandoMetaClass metaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(getClass());
        MetaMethod metaMethod = metaClass.getMetaMethod(method, parameters);
        // this is actually a super call if 1. we are in a different context and 2. the method actually needs super()ing because we have not declared it ourselves
        _CAUREPL_doInvoke(metaMethod, this, !context.equals(getClass().getName()) && metaMethod.getDeclaringClass().getTheClass().equals(getClass()), parameters);
    }

    default Object _CAUREPL_invoke(String context, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invoke {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), getClass().getName(), method);
        ExpandoMetaClass metaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(getClass());
        MetaMethod metaMethod = metaClass.getMetaMethod(method, parameters);
        // this is actually a super call if 1. we are in a different context and 2. the method actually needs super()ing because we have not declared it ourselves
        return _CAUREPL_doInvoke(metaMethod, this, !context.equals(getClass().getName()) && metaMethod.getDeclaringClass().getTheClass().equals(getClass()), parameters);
    }

    private static void _CAUREPL_invokeStaticVoid(String context, String className, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeStaticVoid {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), className, method);
        Class<?> klass;
        try {
            klass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Internal error: Could not find dynamized class " + className, e);
        }
        ExpandoMetaClass metaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(klass);
        // use the non-static getter -> the static getter misses inherited methods
        MetaMethod metaMethod = metaClass.getMetaMethod(method, parameters);
        _CAUREPL_doInvoke(metaMethod, klass, false, parameters);
    }

    private static Object _CAUREPL_invokeStatic(String context, String className, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeStatic {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), className, method);
        Class<?> klass;
        try {
            klass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Internal error: Could not find dynamized class " + className, e);
        }
        ExpandoMetaClass metaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(klass);
        // use the non-static getter -> the static getter misses inherited methods
        MetaMethod metaMethod = metaClass.getMetaMethod(method, parameters);
        return _CAUREPL_doInvoke(metaMethod, klass, false, parameters);
    }

    default void _CAUREPL_invokeSuperVoid(String context, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeSuperVoid {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), getClass().getName(), method);
        FindSuperResult sm = _CAUREPL_findSuperMethod(context, getClass(), method, parameters);
        MetaMethod metaMethod = sm.method;
        if (metaMethod == null) throw new RuntimeException("Internal Error: could not find super method for " + method);
        _CAUREPL_doInvoke(metaMethod, this, sm.isSuper && sm.methodType != METHOD_TYPE.EXPANDO, parameters);
    }

    default Object _CAUREPL_invokeSuper(String context, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeSuper {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), getClass().getName(), method);
        FindSuperResult sm = _CAUREPL_findSuperMethod(context, getClass(), method, parameters);
        MetaMethod metaMethod = sm.method;
        if (metaMethod == null) throw new RuntimeException("Internal Error: could not find super method for " + method);
        return _CAUREPL_doInvoke(metaMethod, this, sm.isSuper && sm.methodType != METHOD_TYPE.EXPANDO, parameters);
    }

    private static void _CAUREPL_invokeSuperStaticVoid(String context, String className, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeSuperStaticVoid {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), className, method);
        Class<?> klass;
        try {
            klass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Internal error: Could not find dynamized class " + className, e);
        }
        FindSuperResult sm = _CAUREPL_findSuperMethod(context, klass, method, parameters);
        if (sm.method == null) throw new RuntimeException("Internal Error: could not find static super method for " + method);
        _CAUREPL_doInvoke(sm.method, klass, false, parameters);
    }

    private static Object _CAUREPL_invokeSuperStatic(String context, String className, String method, Object... parameters) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{} ({})] invokeSuperStatic {}::{}", context, _CAUREPL_currentMethod.get() == null ? "" : _CAUREPL_currentMethod.get().getDeclaringClass().getName(), className, method);
        Class<?> klass;
        try {
            klass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Internal error: Could not find dynamized class " + className, e);
        }
        FindSuperResult sm = _CAUREPL_findSuperMethod(context, klass, method, parameters);
        if (sm.method == null) throw new RuntimeException("Internal Error: could not find static super method for " + method);
        return _CAUREPL_doInvoke(sm.method, klass, false, parameters);
    }

    private static Object _CAUREPL_doInvoke(MetaMethod metaMethod, Object o, boolean isSuper, Object... parameters) {
        _CAUREPL_currentMethod.set(metaMethod);

        if (!isSuper) return metaMethod.invoke(o, parameters);

        MetaClass mc = InvokerHelper.getMetaClass(o);
        return mc.invokeMethod(o.getClass(), o, metaMethod.getName(), parameters, true, true);
    }

    private static FindSuperResult _CAUREPL_findSuperMethod(String context, Class<?> theClass, String method, Object... parameters) {
        // one would assume, that we should use metaClass.getStaticMetaMethod for static methods, but this would
        // miss some inherited static methods. metaClass.getMetaMethod on the other hand has all static methods (as well as non-static ones)
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] findSuperMethod: {}::{}", theClass.getName(), context, method);
        MetaMethod stub = null;
        Class<?> stubClass = null;
        boolean belowContext = true;

        for (Class<?> klass = theClass; klass != null; klass = klass.getSuperclass()) {
            if (belowContext && klass.getName().equals(context)) belowContext = false;
            if (belowContext) continue;
            ExpandoMetaClass metaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(klass);

            MetaMethod dynamized = metaClass.getMetaMethod(dynamizedMethod(klass.getName(), method), parameters);
            if (dynamized != null) {
                if (metaClass.getExpandoMethods().contains(dynamized)) {
                    if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] findSuperMethod: dynamized expando found {}::{}", context, klass, dynamized);
                    return new FindSuperResult(dynamized, METHOD_TYPE.EXPANDO, !theClass.equals(klass));
                } else {
                    if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] findSuperMethod: dynamized found {}::{}", context, klass, dynamized);
                    return new FindSuperResult(dynamized, METHOD_TYPE.DYNAMIZED, !theClass.equals(klass));
                }
            }

            MetaMethod nonDynamized = metaClass.getMetaMethod(method, parameters);
            if (nonDynamized != null && metaClass.getExpandoMethods().contains(nonDynamized)) {
                if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] findSuperMethod: non-dynamized expando found {}::{}", context, klass, nonDynamized);
                return new FindSuperResult(nonDynamized, METHOD_TYPE.EXPANDO, !theClass.equals(klass));
            }

            // we found a super-/patchee stub in the last iteration, but no expando in this one. bail to return the stub
            if (stub != null) break;

            HashMap<Class<?>, Boolean> cache = _CAUREPL_dynamizedCache.get();
            if (cache == null) {
                cache = new HashMap<>();
                _CAUREPL_dynamizedCache.set(cache);
            }
            Boolean isDynamized = _CAUREPL_dynamizedCache.get().get(klass);
            if (isDynamized == null) {
                Class<?>[] ownInterfaces = klass.getInterfaces();
                isDynamized = isDynamizedClass(klass);
                _CAUREPL_dynamizedCache.get().put(klass, isDynamized);
            }

            if (!isDynamized) {
                ExpandoMetaClass nonDynamizedMetaClass = (ExpandoMetaClass) InvokerHelper.getMetaClass(klass);
                if (nonDynamized != null && klass.equals(nonDynamized.getDeclaringClass().getTheClass())) {
                    if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] findSuperMethod: super non-dynamized found {}::{}", context, klass, nonDynamized);
                    return new FindSuperResult(nonDynamized, METHOD_TYPE.NON_DYNAMIZED, true);
                }
            }

            stub = metaClass.getMetaMethod(superStubMethod(klass.getName(), method), parameters);
            if (stub == null) metaClass.getMetaMethod(patcheeStubMethod(klass.getName(), method), parameters);
            if (stub != null) stubClass = (stub.getDeclaringClass() != null) ? stub.getDeclaringClass().getTheClass() : null;
        }

        if (stub != null) {
            if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] findSuperMethod: stub found {}::{}", context, stubClass, stub);
            return new FindSuperResult(stub, METHOD_TYPE.STUB, false);
        }

        return new FindSuperResult(null, null, null);
    }

    static boolean isDynamizedClass(Class<?> theClass) {
        for (Class<?> ownInterface : theClass.getInterfaces()) if (GroovyDynamized.class.equals(ownInterface)) return true;
        return false;
    }

}
