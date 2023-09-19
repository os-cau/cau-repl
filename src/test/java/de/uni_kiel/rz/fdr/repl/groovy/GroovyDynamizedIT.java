// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import org.junit.jupiter.api.*;
import groovy.util.Eval;
import test1.*;

import static org.junit.jupiter.api.Assertions.*;

/*
    Please run this from the commandline with "mvn verify". Running it directly from IDEA is broken. You would need to:
        Set Preferences | Build, Execution, Deployment | Build Tools | Maven | Runner | Delegate IDE build/run actions to maven option enabled.
        Make also sure all options under Maven | Running Tests settings are enabled.
    but this does not spawn a proper agent-enabled JVM usually
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GroovyDynamizedIT {

    // ======== Class Hierarchy

    @Test
    @Order(100)
    public void testClassHierarchy() {
        assertTrue(J8.class.isAssignableFrom(JA.class));
        assertTrue(P9.class.isAssignableFrom(JA.class));

        assertTrue(J8.class.isAssignableFrom(PB.class));
        assertTrue(P9.class.isAssignableFrom(PB.class));
        assertTrue(JA.class.isAssignableFrom(PB.class));

        assertTrue(J8.class.isAssignableFrom(PC.class));
        assertTrue(P9.class.isAssignableFrom(PC.class));
        assertTrue(JA.class.isAssignableFrom(PC.class));
        assertTrue(PB.class.isAssignableFrom(PC.class));

        assertTrue(J8.class.isAssignableFrom(JCA.class));
        assertTrue(P9.class.isAssignableFrom(JCA.class));
        assertTrue(JA.class.isAssignableFrom(JCA.class));
        assertTrue(PB.class.isAssignableFrom(JCA.class));
        assertTrue(PC.class.isAssignableFrom(JCA.class));

        assertTrue(J8.class.isAssignableFrom(JCB.class));
        assertTrue(P9.class.isAssignableFrom(JCB.class));
        assertTrue(JA.class.isAssignableFrom(JCB.class));
        assertTrue(PB.class.isAssignableFrom(JCB.class));
        assertTrue(PC.class.isAssignableFrom(JCB.class));
        assertTrue(JCA.class.isAssignableFrom(JCB.class));

        Eval.me("""
                     import test1.*;
                     import static org.junit.jupiter.api.Assertions.*;
                     
                     assertTrue(J8.class.isAssignableFrom(JA.class));
                     assertTrue(P9.class.isAssignableFrom(JA.class));
                     
                     assertTrue(J8.class.isAssignableFrom(GAA.class));
                     assertTrue(P9.class.isAssignableFrom(GAA.class));
                     assertTrue(JA.class.isAssignableFrom(GAA.class));
                             
                     assertTrue(J8.class.isAssignableFrom(PB.class));
                     assertTrue(P9.class.isAssignableFrom(PB.class));
                     assertTrue(JA.class.isAssignableFrom(PB.class));
                             
                     assertTrue(J8.class.isAssignableFrom(PC.class));
                     assertTrue(P9.class.isAssignableFrom(PC.class));
                     assertTrue(JA.class.isAssignableFrom(PC.class));
                     assertTrue(PB.class.isAssignableFrom(PC.class));
                     
                     assertTrue(J8.class.isAssignableFrom(JCA.class));
                     assertTrue(P9.class.isAssignableFrom(JCA.class));
                     assertTrue(JA.class.isAssignableFrom(JCA.class));
                     assertTrue(PB.class.isAssignableFrom(JCA.class));
                     assertTrue(PC.class.isAssignableFrom(JCA.class));
             
                     assertTrue(J8.class.isAssignableFrom(JCB.class));
                     assertTrue(P9.class.isAssignableFrom(JCB.class));
                     assertTrue(JA.class.isAssignableFrom(JCB.class));
                     assertTrue(PB.class.isAssignableFrom(JCB.class));
                     assertTrue(PC.class.isAssignableFrom(JCB.class));
                     assertTrue(JCA.class.isAssignableFrom(JCB.class));
                """);
    }


    // ======== Static Fields

    @Test
    @Order(200)
    public void testStaticJ8() {
        assertEquals("J8", J8.publicStaticJ8);
        assertEquals("J8", J8.getPrivateStaticJ8());
        assertEquals("J8", J8.publicStaticJ8_X);
        assertEquals("J8", J8.getPrivateStaticJ8_X());
    }

    @Test
    @Order(300)
    public void testStaticP9() {
        assertEquals("P9.G", P9.publicStaticP9);
        assertEquals("P9.G", P9.getPrivateStaticP9());
        assertEquals("P9.G", P9.publicStaticP9_X);
        assertEquals("P9.G", P9.getPrivateStaticP9_X());
        assertEquals("P9.G", P9.publicStaticJ8_X);
        assertEquals("P9.G", P9.getPrivateStaticJ8_X());
        assertEquals("J8", P9.publicStaticJ8);
        assertEquals("J8", P9.getPrivateStaticJ8());
    }

    @Test
    @Order(310)
    public void testStaticP9_Dynamic() {
        Eval.me("test1.P9.testStaticP9_Dynamic()");
    }

    @Test
    @Order(400)
    public void testStaticJA() {
        assertEquals("JA", JA.publicStaticJA);
        assertEquals("JA", JA.getPrivateStaticJA());
        assertEquals("JA", JA.publicStaticJA_X);
        assertEquals("JA", JA.getPrivateStaticJA_X());
        assertEquals("JA", JA.publicStaticP9_X);
        assertEquals("JA", JA.getPrivateStaticP9_X());
        assertEquals("JA", JA.publicStaticJ8_X);
        assertEquals("JA", JA.getPrivateStaticJ8_X());
        assertEquals("P9.G", JA.publicStaticP9);
        assertEquals("P9.G", JA.getPrivateStaticP9());
        assertEquals("J8", JA.publicStaticJ8);
        assertEquals("J8", JA.getPrivateStaticJ8());
    }

    @Test
    @Order(410)
    public void testStaticGAA_Dynamic() {
        Eval.me("test1.GAA.testStaticGAA_Dynamic()");
    }

    @Test
    @Order(500)
    public void testStaticPB() {
        assertEquals("PB.G", PB.publicStaticPB);
        assertEquals("PB.G", PB.getPrivateStaticPB());
        assertEquals("PB.G", PB.publicStaticPB_X);
        assertEquals("PB.G", PB.getPrivateStaticPB_X());
        assertEquals("PB.G", PB.publicStaticJA_X);
        assertEquals("PB.G", PB.getPrivateStaticJA_X());
        assertEquals("PB.G", PB.publicStaticP9_X);
        assertEquals("PB.G", PB.getPrivateStaticP9_X());
        assertEquals("PB.G", PB.publicStaticJ8_X);
        assertEquals("PB.G", PB.getPrivateStaticJ8_X());
        assertEquals("JA", PB.publicStaticJA);
        assertEquals("JA", PB.getPrivateStaticJA());
        assertEquals("P9.G", PB.publicStaticP9);
        assertEquals("P9.G", PB.getPrivateStaticP9());
        assertEquals("J8", PB.publicStaticJ8);
        assertEquals("J8", PB.getPrivateStaticJ8());
    }

    @Test
    @Order(510)
    public void testStaticPB_Dynamic() {
        Eval.me("test1.PB.testStaticPB_Dynamic()");
    }

    @Test
    @Order(600)
    public void testStaticJCA() {
        assertEquals("JCA", JCA.publicStaticJCA_X);
        assertEquals("JCA", JCA.getPrivateStaticJCA_X());
        assertEquals("JCA", JCA.publicStaticPC_X);
        assertEquals("JCA", JCA.getPrivateStaticPC_X());
        assertEquals("JCA", JCA.publicStaticPB_X);
        assertEquals("JCA", JCA.getPrivateStaticPB_X());
        assertEquals("JCA", JCA.publicStaticJA_X);
        assertEquals("JCA", JCA.getPrivateStaticJA_X());
        assertEquals("JCA", JCA.publicStaticP9_X);
        assertEquals("JCA", JCA.getPrivateStaticP9_X());
        assertEquals("JCA", JCA.publicStaticJ8_X);
        assertEquals("JCA", JCA.getPrivateStaticJ8_X());
        assertEquals("JCA", JCA.getPrivateStaticJCA());
        assertEquals("JCA", JCA.publicStaticJCA);
        assertEquals("PC.G", JCA.publicStaticPC);
        assertEquals("PC.G", JCA.getPrivateStaticPC());
        assertEquals("PB.G", JCA.publicStaticPB);
        assertEquals("PB.G", JCA.getPrivateStaticPB());
        assertEquals("JA", JCA.publicStaticJA);
        assertEquals("JA", JCA.getPrivateStaticJA());
        assertEquals("P9.G", JCA.publicStaticP9);
        assertEquals("P9.G", JCA.getPrivateStaticP9());
        assertEquals("J8", JCA.publicStaticJ8);
        assertEquals("J8", JCA.getPrivateStaticJ8());
    }

    @Test
    @Order(610)
    public void testStaticJCA_Dynamic() {
        Eval.me("""
                    import test1.*;
                    import static org.junit.jupiter.api.Assertions.*;
                         
                    assertEquals("JCA", JCA.publicStaticJCA_X);
                    assertEquals("JCA", JCA.getPrivateStaticJCA_X());
                    assertEquals("JCA", JCA.publicStaticPC_X);
                    assertEquals("JCA", JCA.getPrivateStaticPC_X());
                    assertEquals("JCA", JCA.publicStaticPB_X);
                    assertEquals("JCA", JCA.getPrivateStaticPB_X());
                    assertEquals("JCA", JCA.publicStaticJA_X);
                    assertEquals("JCA", JCA.getPrivateStaticJA_X());
                    assertEquals("JCA", JCA.publicStaticP9_X);
                    assertEquals("JCA", JCA.getPrivateStaticP9_X());
                    assertEquals("JCA", JCA.publicStaticJ8_X);
                    assertEquals("JCA", JCA.getPrivateStaticJ8_X());
                    assertEquals("JCA", JCA.getPrivateStaticJCA());
                    assertEquals("JCA", JCA.publicStaticJCA);
                    assertEquals("PC.G", JCA.publicStaticPC);
                    assertEquals("PC.G", JCA.getPrivateStaticPC());
                    assertEquals("PB.G", JCA.publicStaticPB);
                    assertEquals("PB.G", JCA.getPrivateStaticPB());
                    assertEquals("JA", JCA.publicStaticJA);
                    assertEquals("JA", JCA.getPrivateStaticJA());
                    assertEquals("P9.G", JCA.publicStaticP9);
                    assertEquals("P9.G", JCA.getPrivateStaticP9());
                    assertEquals("J8", JCA.publicStaticJ8);
                    assertEquals("J8", JCA.getPrivateStaticJ8());
                """);
    }

    @Test
    @Order(620)
    public void testStaticJCB() {
        assertEquals("JCB", JCB.publicStaticJCB_X);
        assertEquals("JCB", JCB.getPrivateStaticJCB_X());
        assertEquals("JCB", JCB.publicStaticJCA_X);
        assertEquals("JCB", JCB.getPrivateStaticJCA_X());
        assertEquals("JCB", JCB.publicStaticPC_X);
        assertEquals("JCB", JCB.getPrivateStaticPC_X());
        assertEquals("JCB", JCB.publicStaticPB_X);
        assertEquals("JCB", JCB.getPrivateStaticPB_X());
        assertEquals("JCB", JCB.publicStaticJA_X);
        assertEquals("JCB", JCB.getPrivateStaticJA_X());
        assertEquals("JCB", JCB.publicStaticP9_X);
        assertEquals("JCB", JCB.getPrivateStaticP9_X());
        assertEquals("JCB", JCB.publicStaticJ8_X);
        assertEquals("JCB", JCB.getPrivateStaticJ8_X());
        assertEquals("JCB", JCB.getPrivateStaticJCB());
        assertEquals("JCB", JCB.publicStaticJCB);
        assertEquals("JCA", JCB.publicStaticJCA);
        assertEquals("PC.G", JCB.publicStaticPC);
        assertEquals("PC.G", JCB.getPrivateStaticPC());
        assertEquals("PB.G", JCB.publicStaticPB);
        assertEquals("PB.G", JCB.getPrivateStaticPB());
        assertEquals("JA", JCB.publicStaticJA);
        assertEquals("JA", JCB.getPrivateStaticJA());
        assertEquals("P9.G", JCB.publicStaticP9);
        assertEquals("P9.G", JCB.getPrivateStaticP9());
        assertEquals("J8", JCB.publicStaticJ8);
        assertEquals("J8", JCB.getPrivateStaticJ8());
    }

    @Test
    @Order(630)
    public void testStaticJCB_Dynamic() {
        Eval.me("""
                    import test1.*;
                    import static org.junit.jupiter.api.Assertions.*;
                    
                    assertEquals("JCB", JCB.publicStaticJCB_X);
                    assertEquals("JCB", JCB.getPrivateStaticJCB_X());
                    assertEquals("JCB", JCB.publicStaticJCA_X);
                    assertEquals("JCB", JCB.getPrivateStaticJCA_X());
                    assertEquals("JCB", JCB.publicStaticPC_X);
                    assertEquals("JCB", JCB.getPrivateStaticPC_X());
                    assertEquals("JCB", JCB.publicStaticPB_X);
                    assertEquals("JCB", JCB.getPrivateStaticPB_X());
                    assertEquals("JCB", JCB.publicStaticJA_X);
                    assertEquals("JCB", JCB.getPrivateStaticJA_X());
                    assertEquals("JCB", JCB.publicStaticP9_X);
                    assertEquals("JCB", JCB.getPrivateStaticP9_X());
                    assertEquals("JCB", JCB.publicStaticJ8_X);
                    assertEquals("JCB", JCB.getPrivateStaticJ8_X());
                    assertEquals("JCB", JCB.getPrivateStaticJCB());
                    assertEquals("JCB", JCB.publicStaticJCB);
                    assertEquals("JCA", JCB.publicStaticJCA);
                    assertEquals("PC.G", JCB.publicStaticPC);
                    assertEquals("PC.G", JCB.getPrivateStaticPC());
                    assertEquals("PB.G", JCB.publicStaticPB);
                    assertEquals("PB.G", JCB.getPrivateStaticPB());
                    assertEquals("JA", JCB.publicStaticJA);
                    assertEquals("JA", JCB.getPrivateStaticJA());
                    assertEquals("P9.G", JCB.publicStaticP9);
                    assertEquals("P9.G", JCB.getPrivateStaticP9());
                    assertEquals("J8", JCB.publicStaticJ8);
                    assertEquals("J8", JCB.getPrivateStaticJ8());
                """);
    }

    @Test
    @Order(700)
    public void testStaticPC() {
        assertEquals("PC.G", PC.publicStaticPC);
        assertEquals("PC.G", PC.getPrivateStaticPC());
        assertEquals("PC.G", PC.publicStaticPC_X);
        assertEquals("PC.G", PC.getPrivateStaticPC_X());
        assertEquals("PC.G", PC.publicStaticJA_X);
        assertEquals("PC.G", PC.getPrivateStaticJA_X());
        assertEquals("PC.G", PC.publicStaticP9_X);
        assertEquals("PC.G", PC.getPrivateStaticP9_X());
        assertEquals("PC.G", PC.publicStaticJ8_X);
        assertEquals("PC.G", PC.getPrivateStaticJ8_X());
        assertEquals("PB.G", PC.publicStaticPB);
        assertEquals("PB.G", PC.getPrivateStaticPB());
        assertEquals("JA", PC.publicStaticJA);
        assertEquals("JA", PC.getPrivateStaticJA());
        assertEquals("P9.G", PC.publicStaticP9);
        assertEquals("P9.G", PC.getPrivateStaticP9());
        assertEquals("J8", PC.publicStaticJ8);
        assertEquals("J8", PC.getPrivateStaticJ8());
    }

    @Test
    @Order(800)
    public void testStaticPC_Dynamic() {
        Eval.me("test1.PC.testStaticPC_Dynamic()");
    }

    @Test
    @Order(900)
    public void testStaticGD_Dynamic() {
        Eval.me("test1.GD.testStaticGD_Dynamic()");
    }

    @Test
    @Order(1000)
    public void testStaticGE_Dynamic() {
        Eval.me("test1.GE.testStaticGE_Dynamic()");
    }


    // ======== Constructors

    @Test
    @Order(1100)
    public void testConstructors() {
        P9 p9 = new P9();
        assertEquals("P9.G", p9.constructor);
        p9 = new P9("asd", 111);
        assertEquals("P9.G", p9.constructor);

        PB pb = new PB();
        assertEquals("PB.J", pb.constructor);
        pb = new PB("test123", 123);
        assertEquals("test123", pb.constructor);

        PC pc = new PC();
        assertEquals("PC.G", pc.constructor);
        pc = new PC("test123", 123);
        assertEquals("PC.G.123", pc.constructor);
    }


    // ======== Inner Classes

    @Test
    @Order(1200)
    public void testInnerClassP9() {
        assertEquals("P9.G", P9.P9Inner1.origin);
        assertEquals("P9.J", P9.P9Inner1.origin_nooverride);
        assertEquals("P9.G", P9.P9Inner1.P9Inner2.origin);
        assertEquals("P9.G", P9.P9Inner1.P9Inner3.origin);
    }

    @Test
    @Order(1300)
    public void testInnerClassP9_Dynamic() {
        Eval.me("test1.P9.testInnerClassP9_Dynamic()");
    }

    @Test
    @Order(1400)
    public void testInnerClassJA() {
        assertEquals("JA", JA.JAInner1.origin);
        assertEquals("JA", JA.JAInner1.JAInner2.origin);
        assertEquals("JA", JA.JAInner1.JAInner3.origin);
    }

    @Test
    @Order(1500)
    public void testInnerClassGAA_Dynamic() {
        Eval.me("test1.GAA.testInnerClassGAA_Dynamic()");
    }

    @Test
    @Order(1600)
    public void testInnerClassPB() {
        assertEquals("PB.J", PB.PBInner1.origin);
        assertEquals("PB.J", PB.PBInner1.PBInner2.origin);
        assertEquals("PB.J", PB.PBInner1.PBInner3.origin);
    }

    @Test
    @Order(1700)
    public void testInnerClassPB_Dynamic() {
        Eval.me("test1.PB.testInnerClassPB_Dynamic()");
    }

    @Test
    @Order(1800)
    public void testInnerClassPC() {
        assertEquals("PC.G", PC.PCInner1.origin);
        assertEquals("PC.G", PC.PCInner1.PCInner2.origin);
        assertEquals("PC.G", PC.PCInner1.PCInner3.origin);
    }

    @Test
    @Order(1900)
    public void testInnerClassPC_Dynamic() {
        Eval.me("test1.PC.testInnerClassPC_Dynamic()");
    }


    // ======== Instance Methods

    @Test
    @Order(2000)
    public void testInstanceMethodsJ8() {
        J8 x = new J8();
        assertEquals("test1.J8", x.toString().split("@")[0]);
        assertEquals("J8", x.getJ8());
        assertEquals("J8", x.getJ8_X());
    }

    @Test
    @Order(2100)
    public void testInstanceMethodsP9() {
        P9 x = new P9();
        assertEquals("test1.P9", x.toString().split("@")[0]);
        assertEquals("P9.G", x.getP9());
        assertEquals("P9.G", x.getP9_X());
        assertEquals("P9.G", x.getJ8_X());
        assertEquals("J8", x.getJ8());
    }

    @Test
    @Order(2200)
    public void testInstanceMethodsP9_Dynamic() {
        Eval.me("test1.P9.testInstanceMethodsP9_Dynamic()");
    }

    @Test
    @Order(2300)
    public void testInstanceMethodsJA() {
        JA x = new JA();
        assertEquals("test1.JA", x.toString().split("@")[0]);
        assertEquals("JA", x.getJA());
        assertEquals("JA", x.getJA_X());
        assertEquals("JA", x.getP9_X());
        assertEquals("JA", x.getJ8_X());
        assertEquals("P9.G", x.getP9());
        assertEquals("J8", x.getJ8());
    }

    @Test
    @Order(2400)
    public void testInstanceMethodsGAA_Dynamic() {
        Eval.me("test1.GAA.testInstanceMethodsGAA_Dynamic()");
    }

    @Test
    @Order(2500)
    public void testInstanceMethodsPB() {
        PB x = new PB();
        assertEquals("test1.PB", x.toString().split("@")[0]);
        assertEquals("PB.G", x.getPB());
        assertEquals("PB.G", x.getPB_X());
        assertEquals("PB.G", x.getJA_X());
        assertEquals("PB.G", x.getP9_X());
        assertEquals("PB.G", x.getJ8_X());
        assertEquals("JA", x.getJA());
        assertEquals("P9.G", x.getP9());
        assertEquals("J8", x.getJ8());

        assertEquals("c", x.testVarargs("bla", 1, "a", "b", "c"));
    }

    @Test
    @Order(2600)
    public void testInstanceMethodsPB_Dynamic() {
        Eval.me("test1.PB.testInstanceMethodsPB_Dynamic()");
    }

    @Test
    @Order(2610)
    public void testInstanceMethodsJCA() {
        JCA x = new JCA();
        assertEquals("test1.JCA", x.toString().split("@")[0]);
        assertEquals("JCA", x.getJCA_X());
        assertEquals("PC.G", x.getPC_X_super()); // is final
        assertEquals("JCA", x.getPB_X());
        assertEquals("JCA", x.getJA_X());
        assertEquals("JCA", x.getP9_X());
        assertEquals("JCA", x.getJ8_X());
        assertEquals("PB.G", x.getPB());
        assertEquals("PC.G", x.getPC());
        assertEquals("JA", x.getJA());
        assertEquals("P9.G", x.getP9());
        assertEquals("J8", x.getJ8());

        assertEquals("PC.G", x.getPC_X_super());
        assertEquals("PC.G", x.getPB_X_super());
        assertEquals("PC.G", x.getJA_X_super());
        assertEquals("PC.G", x.getP9_X_super());
        assertEquals("PC.G", x.getJ8_X_super());
        assertEquals("PC.G", x.getPC_super());
        assertEquals("PB.G", x.getPB_super());
        assertEquals("JA", x.getJA_super());
        assertEquals("P9.G", x.getP9_super());
        assertEquals("J8", x.getJ8_super());
    }

    @Test
    @Order(2620)
    public void testInstanceMethodsJCA_Dynamic() {
        Eval.me("""
                 import test1.*;
                 import static org.junit.jupiter.api.Assertions.*;
                    
                 JCA x = new JCA();
                 assertEquals("test1.JCA", x.toString().split("@")[0]);
                 assertEquals("JCA", x.getJCA_X());
                 assertEquals("PC.G", x.getPC_X_super()); // is final
                 assertEquals("JCA", x.getPB_X());
                 assertEquals("JCA", x.getJA_X());
                 assertEquals("JCA", x.getP9_X());
                 assertEquals("JCA", x.getJ8_X());
                 assertEquals("PB.G", x.getPB());
                 assertEquals("PC.G", x.getPC());
                 assertEquals("JA", x.getJA());
                 assertEquals("P9.G", x.getP9());
                 assertEquals("J8", x.getJ8());
                         
                 assertEquals("PC.G", x.getPC_X_super());
                 assertEquals("PC.G", x.getPB_X_super());
                 assertEquals("PC.G", x.getJA_X_super());
                 assertEquals("PC.G", x.getP9_X_super());
                 assertEquals("PC.G", x.getJ8_X_super());
                 assertEquals("PC.G", x.getPC_super());
                 assertEquals("PB.G", x.getPB_super());
                 assertEquals("JA", x.getJA_super());
                 assertEquals("P9.G", x.getP9_super());
                 assertEquals("J8", x.getJ8_super());
                """);
    }

    @Test
    @Order(2630)
    public void testInstanceMethodsJCB() {
        JCB x = new JCB();
        assertEquals("test1.JCB", x.toString().split("@")[0]);
        assertEquals("JCB", x.getJCB_X());
        assertEquals("JCB", x.getJCA_X());
        assertEquals("JCB", x.getPB_X());
        assertEquals("JCB", x.getJA_X());
        assertEquals("JCB", x.getP9_X());
        assertEquals("JCB", x.getJ8_X());
        assertEquals("JCB", x.getJCB());
        assertEquals("JCA", x.getJCA());
        assertEquals("PB.G", x.getPB());
        assertEquals("PC.G", x.getPC());
        assertEquals("JA", x.getJA());
        assertEquals("P9.G", x.getP9());
        assertEquals("J8", x.getJ8());

        assertEquals("JCA", x.getJCA_X_super());
        assertEquals("PC.G", x.getPC_X_super()); // is final in java-land
        assertEquals("JCA", x.getPB_X_super());
        assertEquals("JCA", x.getJA_X_super());
        assertEquals("JCA", x.getP9_X_super());
        assertEquals("JCA", x.getJ8_X_super());
        assertEquals("JCA", x.getJCA_super());
        assertEquals("PC.G", x.getPC_super());
        assertEquals("PB.G", x.getPB_super());
        assertEquals("JA", x.getJA_super());
        assertEquals("P9.G", x.getP9_super());
        assertEquals("J8", x.getJ8_super());
    }

    @Test
    @Order(2640)
    public void testInstanceMethodsJCB_Dynamic() {
        Eval.me("""
                    import test1.*;
                    import static org.junit.jupiter.api.Assertions.*;
                 
                    JCB x = new JCB();
                    assertEquals("test1.JCB", x.toString().split("@")[0]);
                    assertEquals("JCB", x.getJCB_X());
                    assertEquals("JCB", x.getJCA_X());
                    assertEquals("JCB", x.getPB_X());
                    assertEquals("JCB", x.getJA_X());
                    assertEquals("JCB", x.getP9_X());
                    assertEquals("JCB", x.getJ8_X());
                    assertEquals("JCB", x.getJCB());
                    assertEquals("JCA", x.getJCA());
                    assertEquals("PB.G", x.getPB());
                    assertEquals("PC.G", x.getPC());
                    assertEquals("JA", x.getJA());
                    assertEquals("P9.G", x.getP9());
                    assertEquals("J8", x.getJ8());
                            
                    assertEquals("JCA", x.getJCA_X_super());
                    assertEquals("PC.G", x.getPC_X_super()); // is final
                    assertEquals("JCA", x.getPB_X_super());
                    assertEquals("JCA", x.getJA_X_super());
                    assertEquals("JCA", x.getP9_X_super());
                    assertEquals("JCA", x.getJ8_X_super());
                    assertEquals("JCA", x.getJCA_super());
                    assertEquals("PC.G", x.getPC_super());
                    assertEquals("PB.G", x.getPB_super());
                    assertEquals("JA", x.getJA_super());
                    assertEquals("P9.G", x.getP9_super());
                    assertEquals("J8", x.getJ8_super());
                """);
    }

    @Test
    @Order(2700)
    public void testInstanceMethodsPC() {
        PC x = new PC();
        assertEquals("test1.PC", x.toString().split("@")[0]);
        assertEquals("PC.G", x.getPC());
        assertEquals("PC.G", x.getPC_X());
        assertEquals("PC.G", x.getPB_X());
        assertEquals("PC.G", x.getJA_X());
        assertEquals("PC.G", x.getP9_X());
        assertEquals("PC.G", x.getJ8_X());
        assertEquals("JA", x.getJA());
        assertEquals("P9.G", x.getP9());
        assertEquals("J8", x.getJ8());
        assertEquals("PB.G", x.getPB());
    }

    @Test
    @Order(2800)
    public void testInstanceMethodsPC_Dynamic() {
        Eval.me("test1.PC.testInstanceMethodsPC_Dynamic()");
    }

    @Test
    @Order(2900)
    public void testInstanceMethodsGD_Dynamic() {
        Eval.me("test1.GD.testInstanceMethodsGD_Dynamic()");
    }

    @Test
    @Order(3000)
    public void testInstanceMethodsGE_Dynamic() {
        Eval.me("test1.GE.testInstanceMethodsGE_Dynamic()");
    }


    // ======== Interfaces

    @Test
    @Order(3100)
    public void testInterfaces() {
        assertTrue(IJA.class.isAssignableFrom(J8.class));
        assertTrue(IJA.class.isAssignableFrom(P9.class));
        assertTrue(IJA.class.isAssignableFrom(JA.class));
        assertTrue(IJA.class.isAssignableFrom(PB.class));
        assertTrue(IJA.class.isAssignableFrom(PC.class));
        assertTrue(IJA.class.isAssignableFrom(JCA.class));
        assertTrue(IJA.class.isAssignableFrom(JCB.class));

        assertEquals("J8", new J8().doIJA_X());
        assertEquals("P9.G", new P9().doIJA_X());
        assertEquals("JA", new JA().doIJA_X());
        assertEquals("PB.G", new PB().doIJA_X());
        assertEquals("PC.G", new PC().doIJA_X());
        assertEquals("JCA", new JCA().doIJA_X());
        assertEquals("JCB", new JCB().doIJA_X());

        assertEquals("IJA", new J8().doIJA());
        assertEquals("IJA", new P9().doIJA());
        assertEquals("IJA", new JA().doIJA());
        assertEquals("IJA", new PB().doIJA());
        assertEquals("IJA", new PC().doIJA());
        assertEquals("IJA", new JCA().doIJA());
        assertEquals("IJA", new JCB().doIJA());

        assertEquals("J8", new J8().doIJA_Default());
        assertEquals("P9.G", new P9().doIJA_Default());
        assertEquals("JA", new JA().doIJA_Default());
        assertEquals("PB.G", new PB().doIJA_Default());
        assertEquals("PC.G", new PC().doIJA_Default());
        assertEquals("JCA", new JCA().doIJA_Default());
        assertEquals("JCB", new JCB().doIJA_Default());

        assertEquals("P9.J", new P9().doIJA_X_super());
        assertEquals("P9.G", new JA().doIJA_X_super());
        assertEquals("PB.J", new PB().doIJA_X_super());
        assertEquals("PC.J", new PC().doIJA_X_super());
        assertEquals("PC.G", new JCA().doIJA_X_super());
        assertEquals("JCA", new JCB().doIJA_X_super());

        assertEquals("P9.J", new P9().doIJA_Default_super());
        assertEquals("P9.G", new JA().doIJA_Default_super());
        assertEquals("PB.J", new PB().doIJA_Default_super());
        assertEquals("PC.J", new PC().doIJA_Default_super());
        assertEquals("PC.G", new JCA().doIJA_Default_super());
        assertEquals("JCA", new JCB().doIJA_Default_super());
    }

    @Test
    @Order(3200)
    public void testInterfaces_Dynamic() {
        Eval.me("""                
                    import test1.*;
                    import test1b.*
                    import static org.junit.jupiter.api.Assertions.*;
                            
                    assertTrue(IJA.class.isAssignableFrom(J8.class));
                    assertTrue(IJA.class.isAssignableFrom(P9.class));
                    assertTrue(IJA.class.isAssignableFrom(JA.class));
                    assertTrue(IJA.class.isAssignableFrom(PB.class));
                    assertTrue(IJA.class.isAssignableFrom(PC.class));
                    assertTrue(IJA.class.isAssignableFrom(JCA.class));
                    assertTrue(IJA.class.isAssignableFrom(JCB.class));
                    assertTrue(IJA.class.isAssignableFrom(GAA.class));
                    assertTrue(IJA.class.isAssignableFrom(GD.class));
                    assertTrue(IJA.class.isAssignableFrom(GE.class));
                    assertTrue(IGB.class.isAssignableFrom(GD.class));
                    assertTrue(IGB.class.isAssignableFrom(GE.class));
                    
                    assertEquals("J8", new J8().doIJA_X());
                    assertEquals("P9.G", new P9().doIJA_X());
                    assertEquals("JA", new JA().doIJA_X());
                    assertEquals("PB.G", new PB().doIJA_X());
                    assertEquals("PC.G", new PC().doIJA_X());
                    assertEquals("JCA", new JCA().doIJA_X());
                    assertEquals("JCB", new JCB().doIJA_X());
                    assertEquals("GAA", new GAA().doIJA_X());
                    assertEquals("GD", new GD().doIJA_X());
                    assertEquals("GE", new GE().doIJA_X());
            
                    assertEquals("IJA", new J8().doIJA());
                    assertEquals("IJA", new P9().doIJA());
                    assertEquals("IJA", new JA().doIJA());
                    assertEquals("IJA", new PB().doIJA());
                    assertEquals("IJA", new PC().doIJA());
                    assertEquals("IJA", new JCA().doIJA());
                    assertEquals("IJA", new JCB().doIJA());
                    assertEquals("IJA", new GAA().doIJA());
                    assertEquals("IJA", new GD().doIJA());
                    assertEquals("IJA", new GE().doIJA());
            
                    assertEquals("J8", new J8().doIJA_Default());
                    assertEquals("P9.G", new P9().doIJA_Default());
                    assertEquals("JA", new JA().doIJA_Default());
                    assertEquals("PB.G", new PB().doIJA_Default());
                    assertEquals("PC.G", new PC().doIJA_Default());
                    assertEquals("JCA", new JCA().doIJA_Default());
                    assertEquals("JCB", new JCB().doIJA_Default());
                    assertEquals("GAA", new GAA().doIJA_Default());
                    assertEquals("GD", new GD().doIJA_Default());
                    assertEquals("GE", new GE().doIJA_Default());
            
                    assertEquals("P9.J", new P9().doIJA_X_super());
                    assertEquals("P9.G", new JA().doIJA_X_super());
                    assertEquals("PB.J", new PB().doIJA_X_super());
                    assertEquals("PC.J", new PC().doIJA_X_super());
                    assertEquals("PC.G", new JCA().doIJA_X_super());
                    assertEquals("JCA", new JCB().doIJA_X_super());
                    assertEquals("JA", new GAA().doIJA_X_super());
                    assertEquals("PC.G", new GD().doIJA_X_super());
                    assertEquals("GD", new GE().doIJA_X_super());
            
                    assertEquals("P9.J", new P9().doIJA_Default_super());
                    assertEquals("P9.G", new JA().doIJA_Default_super());
                    assertEquals("PB.J", new PB().doIJA_Default_super());
                    assertEquals("PC.J", new PC().doIJA_Default_super());
                    assertEquals("PC.G", new JCA().doIJA_Default_super());
                    assertEquals("JCA", new JCB().doIJA_Default_super());
                    assertEquals("JA", new GAA().doIJA_Default_super());
                    assertEquals("PC.G", new GD().doIJA_Default_super());
                    assertEquals("GD", new GE().doIJA_Default_super());
                    
                    assertEquals("GD", new GD().doIGB_X());
                    assertEquals("GD", new GD().doIGB_Default());
                    assertEquals("GE", new GE().doIGB_X());
                    assertEquals("GE", new GE().doIGB_Default());
                """);
    }


    // ======== Expandos

    @Test
    @Order(10000)
    public void testExpandos() {

        P9 p91 = new P9();
        PB pb1 = new PB();
        PC pc1 = new PC();
        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    
                    p91 = new P9()
                    pb1 = new PB()
                    pc1 = new PC()
                    
                    P9.metaClass.getP9 = { -> return "P9.G.EX" }
                    P9.metaClass.'static'.getPrivateStaticP9 = { -> return "P9.G.EX" }
                    p92 = new P9()
                    assertEquals("P9.G.EX", p91.getPrivateStaticP9());
                    assertEquals("P9.G.EX", p92.getPrivateStaticP9());
                    assertEquals("P9.G.EX", P9.getPrivateStaticP9());
                    assertEquals("P9.G.EX", p91.getP9())
                    assertEquals("P9.G.EX", p92.getP9())

                    PB.metaClass.getPB = { -> return "PB.G.EX" }
                    PB.metaClass.'static'.getPrivateStaticPB = { -> return "PB.G.EX" }
                    pb2 = new PB()
                    assertEquals("P9.G.EX", pb1.getPrivateStaticP9());
                    assertEquals("P9.G.EX", pb2.getPrivateStaticP9());
                    // XXX disabled for now - this will only hold if JA was auto-dynamized: assertEquals("P9.G.EX", pb1.getP9())
                    // XXX disabled for now - this will only hold if JA was auto-dynamized: assertEquals("P9.G.EX", pb2.getP9())
                    assertEquals("PB.G.EX", pb1.getPrivateStaticPB())
                    assertEquals("PB.G.EX", pb2.getPrivateStaticPB())
                    assertEquals("PB.G.EX", PB.getPrivateStaticPB())
                    assertEquals("PB.G.EX", pb1.getPB())
                    assertEquals("PB.G.EX", pb2.getPB())
                    
                    PC.metaClass.getPC = { -> return "PC.G.EX" }
                    PC.metaClass.'static'.getPrivateStaticPC = { -> return "PC.G.EX" }
                    pc2 = new PC()
                    assertEquals("PB.G.EX", pc1.getPrivateStaticPB()) // should this be PB.G? -> that appears to be normal Groovy behaviour: subclasses do not inherit static expandos. if so: no need to update the metaclass for each klass in _CAUREPL_findSuperMethod: just use the original in each iteration
                    assertEquals("PB.G.EX", pc2.getPrivateStaticPB()) // should this be PB.G? -> that appears to be normal Groovy behaviour: subclasses do not inherit static expandos. if so: no need to update the metaclass for each klass in _CAUREPL_findSuperMethod: just use the original in each iteration
                    assertEquals("PB.G.EX", pc1.getPB(), "pc1.getPB()")
                    assertEquals("PB.G.EX", pc2.getPB(), "pc2.getPB()")
                    assertEquals("PC.G.EX", pc1.getPrivateStaticPC())
                    assertEquals("PC.G.EX", pc2.getPrivateStaticPC())
                    assertEquals("PC.G.EX", PC.getPrivateStaticPC())
                    assertEquals("PC.G.EX", pc1.getPC())
                    assertEquals("PC.G.EX", pc2.getPC())
                """);
        P9 p92 = new P9();
        PB pb2 = new PB();
        PC pc2 = new PC();

        assertEquals("P9.G.EX", P9.getPrivateStaticP9());
        assertEquals("P9.G.EX", p91.getP9());
        assertEquals("P9.G.EX", p92.getP9());
        assertEquals("PB.G.EX", PB.getPrivateStaticPB());
        assertEquals("PB.G.EX", pb1.getPB());
        assertEquals("PB.G.EX", pb2.getPB());
        assertEquals("PC.G.EX", PC.getPrivateStaticPC());
        assertEquals("PC.G.EX", pc1.getPC());
        assertEquals("PC.G.EX", pc2.getPC());
        assertEquals("PB.G.EX", pc1.getPB());
        assertEquals("PB.G.EX", pc2.getPB());

        PC pc3 = new PC();
        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    
                    pc3 = new PC()
                    PC.metaClass.getPB = { -> return "PC.G.EX" }
                    PC.metaClass.'static'.getPrivateStaticPB = { -> return "PC.G.EX" }
                    pc4 = new PC()
                    assertEquals("PC.G.EX", pc3.getPrivateStaticPB());
                    assertEquals("PC.G.EX", pc4.getPrivateStaticPB());
                    assertEquals("PC.G.EX", pc3.getPB())
                    assertEquals("PC.G.EX", pc4.getPB())
                """);
        PC pc4 = new PC();
        assertEquals("PC.G.EX", pc3.getPB(), "pc3.getPB()");
        assertEquals("PC.G.EX", pc4.getPB(), "pc4.getPB()");

        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*

                    pb1 = new PB()
                    pc1 = new PC()
                    PB.metaClass.aNewExpando = { -> return "PB.G.EX" }
                    PB.metaClass.'static'.aNewStaticExpando = { -> return "PB.G.EX" }
                    pb2 = new PB()
                    pc2 = new PC()
                    assertEquals("PB.G.EX", PB.aNewStaticExpando())
                    assertEquals("PB.G.EX", pb1.aNewStaticExpando())
                    assertEquals("PB.G.EX", pb2.aNewStaticExpando())
                    assertEquals("PB.G.EX", pb1.aNewExpando())
                    assertEquals("PB.G.EX", pb2.aNewExpando())

                    assertEquals("PB.G.EX", pc1.aNewStaticExpando())
                    assertEquals("PB.G.EX", pc2.aNewStaticExpando())
                    assertEquals("PB.G.EX", pc1.aNewExpando())
                    assertEquals("PB.G.EX", pc2.aNewExpando())
                """);
    }


    @Test
    @Order(10050)
    public void testConstructorExpandos() {
        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    
                    P9.metaClass.constructor = { -> delegate.constructor = delegate.constructor + "-P9.G.EX" }  // this constructor is dynamized -> using the java-native modify-this style
                    p9 = new P9()
                    assertEquals("P9.G-P9.G.EX", p9.constructor)
                """);

        P9 p9 = new P9();
        assertEquals("P9.G-P9.G.EX", p9.constructor);

        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    
                    //PB.metaClass.constructor = { int i -> return new PB("PB.G.EX2", 1) }  // this is what a non-dynamized constructor would look like that super does not have -> using the groovy-native return-instance style
                    PB.metaClass.constructor = { int i -> delegate.constructor="PB.G.EX2" }
                    pb1 = new PB(1)
                    assertEquals("PB.G.EX2", pb1.constructor)
                    
                    PB.metaClass.constructor = { -> delegate.constructor="PB.G.EX3" }
                    pb2 = new PB()
                    assertEquals("PB.G.EX3", pb2.constructor)
                """);
        PB pb1 = new PB();
        assertEquals("PB.G.EX3", pb1.constructor);
    }


    @Test
    @Order(10100)
    public void testDynamicSuper() {

        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    
                    P9.testDynamicSuper_Dynamic()
                    PC.testDynamicSuper_Dynamic()
                """);
    }

    @Test
    @Order(10200)
    public void testPatcheeSuper() {
        Eval.me("""
                    import test1.*
                    import static org.junit.jupiter.api.Assertions.*
                    
                    PC.testPatcheeSuper_Dynamic()
                """);
    }


    // TODO interfaces that extend other interfaces
}
