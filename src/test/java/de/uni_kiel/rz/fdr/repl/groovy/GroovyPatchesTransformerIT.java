// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import groovy.util.Eval;
import org.junit.jupiter.api.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GroovyPatchesTransformerIT {
    @Test
    @Order(100)
    public void testPatchesTranformer_Dynamic() {
        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    import java.rmi.AlreadyBoundException
                    import java.util.concurrent.BrokenBarrierException
                    
                    x = new P9()
                    assertThrows(AlreadyBoundException.class, { -> x._CAUREPL_P$test1_P9_CAUREPL$patchesSuperVoidVoid() })
                    assertThrows(AlreadyBoundException.class, { -> x._CAUREPL_P$test1_P9_CAUREPL$patchesSuperVoidInt(1) })
                    assertThrows(AlreadyBoundException.class, { -> x._CAUREPL_P$test1_P9_CAUREPL$patchesSuperVoidObj("1") })
                    assertEquals(1, x._CAUREPL_P$test1_P9_CAUREPL$patchesSuperLongObj("1"))
                    assertEquals("J8", x._CAUREPL_P$test1_P9_CAUREPL$patchesSuperObjObj("1"))
                    assertEquals("3", x._CAUREPL_P$test1_P9_CAUREPL$patchesSuperObjVarargs("1", 2, ["3", "4", "5"] as String[]))
                """);
    }

    // uncomment this to dump asm code of a class for debugging
    /*
    @Order(999999)
    public void testGenerateASM() {
        try {
            ASMifier.main(new String[]{"/home/devel/fdr/cau-repl/target/test-classes/test1/P9.class"});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
     */
}
