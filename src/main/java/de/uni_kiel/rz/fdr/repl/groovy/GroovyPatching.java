// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;

import static de.uni_kiel.rz.fdr.repl.Helpers.isPatchedClass;
import static de.uni_kiel.rz.fdr.repl.Helpers.patcheeStubMethod;

public interface GroovyPatching {
    default Object patcheeSuper(String method, Object... parameters) {
        Class<?> patcheeClass = getClass().getSuperclass();
        if (!isPatchedClass(patcheeClass.getName())) throw new RuntimeException("Superclass " + patcheeClass.getName() + " is not a patchee.");
        return InvokerHelper.invokeMethod(this, patcheeStubMethod(patcheeClass.getName(), method), parameters);
    }
}
