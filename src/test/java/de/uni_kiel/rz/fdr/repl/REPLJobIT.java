// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import groovy.util.Eval;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class REPLJobIT {
    @Test
    @Order(100)
    public void testJobFunctions() throws ExecutionException, InterruptedException {
        REPLJob j1 = REPLJob.repljob(() -> "foo1");
        j1.start().get();
        assertEquals(REPLJob.JobState.COMPLETED_SUCCESSFULLY, j1.getProgress().state());
        assertEquals(0, j1.getProgress().errors());
        assertEquals("foo1", j1.results[0].result());

        REPLJob j2 = REPLJob.repljob((x, y) -> "foo2-" + x, List.of(1, 2));
        j2.start().get();
        assertEquals(REPLJob.JobState.COMPLETED_SUCCESSFULLY, j2.getProgress().state());
        assertEquals(0, j2.getProgress().errors());
        assertEquals("foo2-1", j2.results[0].result());
    }

    @Test
    @Order(200)
    public void testJobFunctions_Dynamic() {
        Eval.me("""
                    import static org.junit.jupiter.api.Assertions.*
                    import de.uni_kiel.rz.fdr.repl.REPLJob
                    
                    REPLJob j1 = REPLJob.repljob({ return "bar1" })
                    j1.start().get()
                    assertEquals(REPLJob.JobState.COMPLETED_SUCCESSFULLY, j1.getProgress().state())
                    assertEquals(0, j1.getProgress().errors());
                    assertEquals("bar1", j1.results[0].result());
                    
                    REPLJob j2 = REPLJob.repljob({ -> return "bar2" })
                    j2.start().get()
                    assertEquals(REPLJob.JobState.COMPLETED_SUCCESSFULLY, j2.getProgress().state())
                    assertEquals(0, j2.getProgress().errors());
                    assertEquals("bar2", j2.results[0].result());
                    
                    REPLJob j3 = REPLJob.repljob({ x -> return "bar3" })
                    j3.start().get()
                    assertEquals(REPLJob.JobState.COMPLETED_SUCCESSFULLY, j3.getProgress().state())
                    assertEquals(0, j3.getProgress().errors());
                    assertEquals("bar3", j3.results[0].result());
                    
                    REPLJob j4 = REPLJob.repljob({ x, y -> return "bar4-" + x }, [1, 2])
                    j4.start().get()
                    assertEquals(REPLJob.JobState.COMPLETED_SUCCESSFULLY, j4.getProgress().state())
                    assertEquals(0, j4.getProgress().errors());
                    assertEquals("bar4-1", j4.results[0].result());
                """);
    }
}
