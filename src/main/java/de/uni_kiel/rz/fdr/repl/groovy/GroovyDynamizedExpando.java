// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPLLog;
import groovy.lang.Closure;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.runtime.metaclass.ClosureMetaMethod;

import static de.uni_kiel.rz.fdr.repl.Helpers.*;
import static de.uni_kiel.rz.fdr.repl.REPLLog.TRACE;
import static de.uni_kiel.rz.fdr.repl.REPLLog.TRACE_DYNAMIZE;

// XXX remember that plain java subclasses of dynamized classes do not get a GroovyDynamizedExpando MetaClass

/**
 * This is the Groovy meta class that cau-repl uses for Groovy classes that it compiles. It is derived from Groovy's
 * {@code ExpandoMetaClass} and allows for dynamization.
 */
public class GroovyDynamizedExpando extends ExpandoMetaClass {

    private static final String CONSTRUCTOR_METHOD = "<init>";

    public GroovyDynamizedExpando(Class theClass) {
        super(theClass, true, true);
    }

    @Override
    public void registerInstanceMethod(MetaMethod metaMethod) {
        if (!isGeneratedMethod(metaMethod.getName())) {
            String dynamizedName = metaMethod.getName().equals(CONSTRUCTOR_METHOD) ? constructorMethod(theClass.getName()) : dynamizedMethod(theClass.getName(), metaMethod.getName());
            // only rename expandos if the method is dynamized
            if (getMetaMethod(dynamizedName, metaMethod.getNativeParameterTypes()) != null) {
                // rename the method
                if (metaMethod instanceof ClosureMetaMethod cmm) {
                    metaMethod = new ClosureMetaMethod(dynamizedName, cmm.getDeclaringClass().getTheClass(), cmm.getClosure(), cmm.getDoCall());
                } else {
                    // TODO we can probably wrap other types of metaMethods in a new renamed ClosureMetaMethod and call them from there
                    throw new RuntimeException("Internal error: A non-closure MetaMethod was added to the Expando. It could not be renamed: " + metaMethod);
                }
            }
        }

        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] registerInstanceMethod {}", theClass.getName(), metaMethod);
        super.registerInstanceMethod(metaMethod);
    }


    @Override
    public void registerInstanceMethod(String name, Closure closure) {
        if (!isGeneratedMethod(name)) {
            String dynamizedName = name.equals(CONSTRUCTOR_METHOD) ? constructorMethod(theClass.getName()) : dynamizedMethod(theClass.getName(), name);
            // only rename expandos if the method is dynamized
            if (getMetaMethod(dynamizedName, closure.getParameterTypes()) != null) {
                // rename the method
                name = dynamizedName;
            }
        }

        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] registerInstanceMethod {}", theClass.getName(), name);
        super.registerInstanceMethod(name, closure);
    }

    @Override
    protected void registerStaticMethod(String name, Closure callable, Class[] paramTypes) {
        if (!isGeneratedMethod(name)) {
            String dynamizedName = dynamizedMethod(theClass.getName(), name);
            // only rename expandos if the method is dynamized
            if (getMetaMethod(dynamizedName, paramTypes == null ? callable.getParameterTypes() : paramTypes) != null) {
                // rename the method
                name = dynamizedName;
            }
        }

        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] registerStaticMethod {}", theClass.getName(), name);
        super.registerStaticMethod(name, callable, paramTypes);
    }

    @Override
    protected void registerStaticMethod(String name, Closure callable) {
        registerStaticMethod(name, callable, null);
    }

    @Override
    public Object invokeMethod(Class sender, Object object, String methodName, Object[] originalArguments, boolean isCallToSuper, boolean fromInsideClass) {
        if (TRACE || TRACE_DYNAMIZE) REPLLog.trace("[{}] invokeMethod {} -> {}::{}{}{}", theClass.getName(), sender.getName(), object.getClass().getName(), methodName, isCallToSuper ? " SUPER" : "", fromInsideClass ? " FROMINSIDE": "");
        return super.invokeMethod(sender, object, methodName, originalArguments, isCallToSuper, fromInsideClass);
    }

}
