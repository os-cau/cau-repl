// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1

import de.uni_kiel.rz.fdr.repl.Patches
import de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamized

import static org.junit.jupiter.api.Assertions.*;


@Patches
class PC {
    public static String publicStaticPC = "PC.G"
    private static String privateStaticPC = "PC.G"
    public static String publicStaticPC_X = "PC.G"
    private static String privateStaticPC_X = "PC.G"
    public static String publicStaticPB_X = "PC.G"
    private static String privateStaticPB_X = "PC.G"
    public static String publicStaticJA_X = "PC.G"
    private static String privateStaticJA_X = "PC.G"
    private static String privateStaticJ8_X = "PC.G"
    public static String publicStaticJ8_X = "PC.G"
    private static String privateStaticP9_X = "PC.G"
    public static String publicStaticP9_X = "PC.G"

    public static String getPrivateStaticPC() { return privateStaticPC }

    public static String getPrivateStaticPC_X() { return privateStaticPC_X }

    public static String getPrivateStaticPB_X() { return privateStaticPB_X }

    public static String getPrivateStaticJA_X() { return privateStaticJA_X }

    public static String getPrivateStaticP9_X() { return privateStaticP9_X }

    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X }

    public PC() {
        super("PC.G", 1)
    }

    public PC(String foo, Integer bar) {
        this.constructor = "PC.G." + bar
    }

    public class PCInner1 {
        public static String origin = "PC.G";

        public class PCInner2 {
            public static String origin = "PC.G";
        }

        public class PCInner3 {
            public static String origin = "PC.G";
        }
    }

    private String privateJA_X = "PC.G";

    private String doPrivateJA() {
        return "PC.G";
    }

    public String getPC() {
        return "PC.G";
    }

    public String getPC_X() {
        return "PC.G";
    }

    public String getPB_X() {
        return "PC.G";
    }

    public String getJA_X() {
        return "PC.G";
    }

    public String getP9_X() {
        return "PC.G";
    }

    public String getJ8_X() {
        return "PC.G";
    }

    public String getPC_super() {
        return super.getPC()
    }

    public String getPC_X_super() {
        return super.getPC_X()
    }

    public String getPB_X_super() {
        return super.getPB_X()
    }

    public String getJA_X_super() {
        return super.getJA_X()
    }

    public String getP9_X_super() {
        return super.getP9_X()
    }

    public String getJ8_X_super() {
        return super.getJ8_X();
    }

    public String getPB_super() {
        return super.getPB()
    }

    public String getJA_super() {
        return super.getJA()
    }

    public String getP9_super() {
        return super.getP9()
    }

    public String getJ8_super() {
        return super.getJ8()
    }

    public String doIJA_X() {
        return "PC.G"
    }

    public String doIJA_Default() {
        return "PC.G";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public static void testStaticPC_Dynamic() {
        assertEquals("PC.G", publicStaticPC)
        assertEquals("PC.G", getPrivateStaticPC())
        assertEquals("PC.G", publicStaticPC_X)
        assertEquals("PC.G", getPrivateStaticPC_X())
        assertEquals("PC.G", publicStaticJA_X)
        assertEquals("PC.G", getPrivateStaticJA_X())
        assertEquals("PC.G", publicStaticP9_X)
        assertEquals("PC.G", getPrivateStaticP9_X())
        assertEquals("PC.G", publicStaticJ8_X)
        assertEquals("PC.G", getPrivateStaticJ8_X())
        assertEquals("PB.G", publicStaticPB)
        assertEquals("PB.G", getPrivateStaticPB())
        assertEquals("JA", publicStaticJA)
        assertEquals("JA", getPrivateStaticJA())
        assertEquals("P9.G", publicStaticP9)
        assertEquals("P9.G", getPrivateStaticP9())
        assertEquals("J8", publicStaticJ8)
        assertEquals("J8", getPrivateStaticJ8())

        assertEquals("PC.J", PC_CAUREPL.publicStaticPC)
        assertEquals("PC.J", PC_CAUREPL.getPrivateStaticPC())
        assertEquals("PC.J", PC_CAUREPL.publicStaticPC_X)
        assertEquals("PC.J", PC_CAUREPL.getPrivateStaticPC_X())
        assertEquals("PC.J", PC_CAUREPL.publicStaticJA_X)
        assertEquals("PC.J", PC_CAUREPL.getPrivateStaticJA_X())
        assertEquals("PC.J", PC_CAUREPL.publicStaticP9_X)
        assertEquals("PC.J", PC_CAUREPL.getPrivateStaticP9_X())
        assertEquals("PC.J", PC_CAUREPL.publicStaticJ8_X)
        assertEquals("PC.J", PC_CAUREPL.getPrivateStaticJ8_X())
        assertEquals("PB.G", PC_CAUREPL.publicStaticPB)
        assertEquals("PB.G", PC_CAUREPL.getPrivateStaticPB())
        assertEquals("P9.G", PC_CAUREPL.publicStaticP9)
        assertEquals("P9.G", PC_CAUREPL.getPrivateStaticP9())
    }

    public static void testInnerClassPC_Dynamic() {
        assertEquals("JA", JA.JAInner1.origin)
        assertEquals("JA", JA.JAInner1.JAInner2.origin)
        assertEquals("JA", JA.JAInner1.JAInner3.origin)
        assertEquals("PB.J", PB.PBInner1.origin)
        assertEquals("PB.J", PB.PBInner1.PBInner2.origin)
        assertEquals("PB.J", PB.PBInner1.PBInner3.origin)
        assertEquals("PC.G", test1.PC.PCInner1.origin)
        assertEquals("PC.G", test1.PC.PCInner1.PCInner2.origin)
        assertEquals("PC.G", test1.PC.PCInner1.PCInner3.origin)
    }

    public static void testInstanceMethodsPC_Dynamic() {
        PC x = new PC()
        assertEquals("test1.PC", x.toString().split("@")[0])
        assertEquals("PC.G", x.getPC())
        assertEquals("PC.G", x.getPC_X())
        assertEquals("PC.G", x.getPB_X())
        assertEquals("PC.G", x.getJA_X())
        assertEquals("PC.G", x.getP9_X())
        assertEquals("PC.G", x.getJ8_X())
        assertEquals("PB.G", x.getPB())
        assertEquals("JA", x.getJA())
        assertEquals("P9.G", x.getP9())
        assertEquals("J8", x.getJ8())

        assertEquals("PC.G", x.privateJA_X)
        assertEquals("PC.G", x.doPrivateJA())

        assertEquals("PC.J", x.getPC_super())
        assertEquals("PC.J", x.getPC_X_super())
        assertEquals("PC.J", x.getPB_X_super())
        assertEquals("PC.J", x.getJA_X_super())
        assertEquals("PC.J", x.getP9_X_super())
        assertEquals("PC.J", x.getJ8_X_super())
        assertEquals("PB.G", x.getPB_super())
        assertEquals("JA", x.getJA_super())
        assertEquals("P9.G", x.getP9_super())
        assertEquals("J8", x.getJ8_super())
    }

    private void testDynamicSuper() {
        assertEquals("PC.J", dynamicSuper("getPC"))
        assertEquals("PC.J", dynamicSuper("getPC_X"))
        assertEquals("PB.G.EX", dynamicSuper("getPB"), "getPB")
        assertEquals("PC.J", dynamicSuper("getPB_X"))
        assertEquals("P9.G", dynamicSuper("getP9"))
        assertEquals("PC.J", dynamicSuper("getP9_X"))
    }

    public static void testDynamicSuper_Dynamic() {
        def x = new PC()
        x.testDynamicSuper()
        assertEquals("PC.G", x.getJ8_X())
        PC.metaClass.getPC_X = { -> return dynamicSuper("getPC_X") }
        def y = new PC()
        assertEquals("PC.J", x.getPC_X())
        assertEquals("PC.J", y.getPC_X())
    }

    private void testPatcheeSuper() {
        assertEquals("PB.G", patcheeSuper("getPB"), "getPB")
        assertEquals("PB.G", patcheeSuper("getPB_X"))
        assertEquals("JA", patcheeSuper("getJA"))
        assertEquals("PB.G", patcheeSuper("getJA_X"))
        assertEquals("P9.G", patcheeSuper("getP9"))
        assertEquals("PB.G", patcheeSuper("getP9_X"))
        assertEquals("J8", patcheeSuper("getJ8"))
        assertEquals("PB.G", patcheeSuper("getJ8_X"))
        
        assertEquals("PB.G.EX", dynamicPatcheeSuper("getPB"), "getPB")
        assertEquals("PB.G", dynamicPatcheeSuper("getPB_X"))
        assertEquals("JA", dynamicPatcheeSuper("getJA"))
        assertEquals("PB.G", dynamicPatcheeSuper("getJA_X"))
        assertEquals("P9.G", dynamicPatcheeSuper("getP9"))
        assertEquals("PB.G", dynamicPatcheeSuper("getP9_X"))
        assertEquals("J8", dynamicPatcheeSuper("getJ8"))
        assertEquals("PB.G", dynamicPatcheeSuper("getJ8_X"))
    }

    public static void testPatcheeSuper_Dynamic() {
        def x = new PC()
        x.testPatcheeSuper()
        PC.metaClass.getJA_X = { -> return patcheeSuper("getJA_X") }
        PC.metaClass.getPB_X = { -> return dynamicPatcheeSuper("getPB_X") }
        def y = new PC()
        assertEquals("PB.G", x.getJA_X())
        assertEquals("PB.G", y.getJA_X())
        assertEquals("PB.G", x.getPB_X())
        assertEquals("PB.G", y.getPB_X())
        PB.metaClass.getJA_X = { -> return "PB.G.EX" }
        PB.metaClass.getPB_X = { -> return "PB.G.EX" }
        assertEquals("PB.G", x.getJA_X())
        assertEquals("PB.G", y.getJA_X())
        assertEquals("PB.G.EX", x.getPB_X(), "x.getPB_X")
        assertEquals("PB.G.EX", y.getPB_X(), "y.getPB_X")
    }
}