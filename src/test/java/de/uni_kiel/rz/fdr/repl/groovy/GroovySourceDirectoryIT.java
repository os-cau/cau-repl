// (C) Copyright 2024 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import groovy.util.Eval;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
}
