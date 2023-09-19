// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import groovy.util.Eval;
import org.junit.jupiter.api.*;
import test1.*;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GroovyDynamizeTransfomer1IT {

    @Test
    @Order(100)
    public void testDynamize() {
        assertFalse(Arrays.asList(JA.class.getInterfaces()).contains(GroovyDynamized.class));
        assertTrue(Arrays.asList(PB.class.getInterfaces()).contains(GroovyDynamized.class));
        assertTrue(Arrays.asList(PC.class.getInterfaces()).contains(GroovyDynamized.class));
        assertFalse(Arrays.asList(JCA.class.getInterfaces()).contains(GroovyDynamized.class));
        assertFalse(Arrays.asList(JCB.class.getInterfaces()).contains(GroovyDynamized.class));
    }

    @Test
    @Order(200)
    public void testDynamize_Dynamic() {
        Eval.me("""
                    import test1.*
                    import de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamized
                    import static org.junit.jupiter.api.Assertions.*
                    
                    assertFalse(Arrays.asList(JA.class.getInterfaces()).contains(GroovyDynamized.class))
                    assertFalse(Arrays.asList(GAA.class.getInterfaces()).contains(GroovyDynamized.class))
                    assertTrue(Arrays.asList(PB.class.getInterfaces()).contains(GroovyDynamized.class))
                    assertTrue(Arrays.asList(PC.class.getInterfaces()).contains(GroovyDynamized.class))
                    assertFalse(Arrays.asList(JCA.class.getInterfaces()).contains(GroovyDynamized.class))
                    assertFalse(Arrays.asList(JCB.class.getInterfaces()).contains(GroovyDynamized.class))
                """
        );
    }
}
