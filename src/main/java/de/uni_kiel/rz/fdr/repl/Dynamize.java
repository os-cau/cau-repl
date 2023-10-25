// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation enables the (very experimental) dynamization feature for a given class.
 */
@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Dynamize {
    /**
     * Controls whether all subclasses should automatically be dynamized as well. This should generally be left enabled.
     * @return The inherit flag.
     */
    boolean inherit() default true;
}
