// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Patches {
    String classPath() default "";
    String target() default "";
    boolean makePublic() default true;
    String stripAnnotations() default "";
    boolean preserveSuper() default true;
    boolean force() default false;
}
