// (C) Copyright 2024 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.error.CompilationException;
import groovy.util.Eval;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GroovySourceDirectoryIT {
    @Test
    @Order(100)
    public void testCyclicClasses_Dynamic() {
        Eval.me("""
                import test2.*
                import static org.junit.jupiter.api.Assertions.*

                assertTrue(Cyclic1.foo.startsWith("2.spin/"), "Cyclic1.foo.startsWith(\\"2.spin/\\")")
                assertTrue(Cyclic2.bar.startsWith("1.engage/"), "Cyclic2.bar.startsWith(\\"1.engage/\\")")
                """);
    }

    @Test
    @Order(200)
    public void testVerifyError() throws Exception {
        CompilationException ex = null;
        try {
            new GroovySourceDirectory(Path.of("src", "test", "resources", "groovyRT", "Inherit1.groovy"));
        } catch (CompilationException e) {
            ex = e;
        }
        assertNotNull(ex, "The expected CompilationException was not thrown.");
        assertTrue(ex.getMessage().startsWith("Compilation error: there are cyclic dependencies between an @Patches (target-)class and another class: "), "The thrown CompilationException does not seem to have the desired message.");
        assertFalse(ex.getMessage().endsWith("<unknown location>"), "The thrown CompilationException does not contain a location.");
    }

}
