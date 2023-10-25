// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;

import static de.uni_kiel.rz.fdr.repl.Helpers.isPatchedClass;
import static de.uni_kiel.rz.fdr.repl.Helpers.patcheeStubMethod;

/**
 * All Groovy classes that have the {@link de.uni_kiel.rz.fdr.repl.Patches @Patches} annotation implement this interface.
 */
public interface GroovyPatching {
    /**
     * Allows access to the patchee's {@code super} methods. They are shadowed after patching, because the patchee is
     * now the superclass of your Groovy class. With this method, you may still access the target's original superclass.
     * @param method The name of the method to invoke on the target's superclass.
     * @param parameters The parameters of the method call.
     * @return The return value of the mehod call.
     */
    default Object patcheeSuper(String method, Object... parameters) {
        Class<?> patcheeClass = getClass().getSuperclass();
        if (!isPatchedClass(patcheeClass.getName())) throw new RuntimeException("Superclass " + patcheeClass.getName() + " is not a patchee.");
        return InvokerHelper.invokeMethod(this, patcheeStubMethod(patcheeClass.getName(), method), parameters);
    }
}
