// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Classes with this annotation will be set up to patch a different class. Name your patch like your target class
 * (including the package) and make sure this class is loaded before the target. <b>Do not patch classes that are backed
 * by a DB:</b> the modifications that they will be subjected to might confuse an ORM and lead to data loss.
 */
@SuppressWarnings("unused")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Patches {
    /**
     * Overrides the default classpath that contains the target and dependencies.
     * @return The class path.
     */
    String classPath() default "";

    /**
     * Can be used to set the target class if yours has a different name.
     * @deprecated Do not use. Use the same name and package as your target instead.
     * @return The target class.
     */
    @Deprecated
    String target() default "";

    /**
     * Controls whether the target class' methods and fiel will all be set to <i>public</i> to facilitate more
     * comprehensive overriding.
     * @return The public flag.
     */
    boolean makePublic() default true;

    /**
     * A comma separated list of annotations to remove from the target class before loading.
     * @return The strip annotations flag.
     */
    String stripAnnotations() default "";

    /**
     * Controls whether we will try to preserve the original {@code super} class using bytecode magic.
     * @return The preserve super flag.
     */
    boolean preserveSuper() default true;

    /**
     * Disables safety checks. Use this at your own risk and not in production.
     * @return The force flag.
     */
    boolean force() default false;
}
