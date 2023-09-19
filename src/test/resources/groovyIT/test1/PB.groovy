// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1

import de.uni_kiel.rz.fdr.repl.Patches

import static org.junit.jupiter.api.Assertions.*;

@Patches
@Dynamize
class PB {
    public static String publicStaticPB = "PB.G"
    private static String privateStaticPB = "PB.G"
    public static String publicStaticPB_X = "PB.G"
    private static String privateStaticPB_X = "PB.G"
    public static String publicStaticJA_X = "PB.G"
    private static String privateStaticJA_X = "PB.G"
    private static String privateStaticJ8_X = "PB.G"
    public static String publicStaticJ8_X = "PB.G"
    private static String privateStaticP9_X = "PB.G"
    public static String publicStaticP9_X = "PB.G"

    public static String getPrivateStaticPB() { return privateStaticPB }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X }

    private String privateJA_X = "PB.G"

    public PB(int foo) {}

    private String doPrivateJA() {
        return "PB.G";
    }

    public String getPB(){
        return "PB.G";
    }

    public String getPB_X(){
        return "PB.G";
    }

    public String getJA_X(){
        return "PB.G";
    }

    public String getP9_X(){
        return "PB.G";
    }

    public String getJ8_X() {
        return "PB.G";
    }

    public String doIJA_X() {
        return "PB.G"
    }

    public String doIJA_Default() {
        return "PB.G";
    }

    public String getPB_super(){
        return super.getPB()
    }

    public String getPB_X_super(){
        return super.getPB_X()
    }

    public String getJA_X_super(){
        return super.getJA_X()
    }

    public String getP9_X_super(){
        return super.getP9_X()
    }

    public String getJ8_X_super() {
        return super.getJ8_X()
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

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public static void testStaticPB_Dynamic() {
        assertEquals("PB.G", publicStaticPB)
        assertEquals("PB.G", getPrivateStaticPB())
        assertEquals("PB.G", publicStaticPB_X)
        assertEquals("PB.G", getPrivateStaticPB_X())
        assertEquals("PB.G", publicStaticJA_X)
        assertEquals("PB.G", getPrivateStaticJA_X())
        assertEquals("PB.G", publicStaticP9_X)
        assertEquals("PB.G", getPrivateStaticP9_X())
        assertEquals("PB.G", publicStaticJ8_X)
        assertEquals("PB.G", getPrivateStaticJ8_X())
        assertEquals("JA", publicStaticJA)
        assertEquals("JA", getPrivateStaticJA())
        assertEquals("P9.G", publicStaticP9)
        assertEquals("P9.G", getPrivateStaticP9())
        assertEquals("J8", publicStaticJ8)
        assertEquals("J8", getPrivateStaticJ8())

        assertEquals("PB.J", PB_CAUREPL.publicStaticPB)
        assertEquals("PB.J", PB_CAUREPL.getPrivateStaticPB())
        assertEquals("PB.J", PB_CAUREPL.publicStaticPB_X)
        assertEquals("PB.J", PB_CAUREPL.getPrivateStaticPB_X())
        assertEquals("PB.J", PB_CAUREPL.publicStaticJA_X)
        assertEquals("PB.J", PB_CAUREPL.getPrivateStaticJA_X())
        assertEquals("PB.J", PB_CAUREPL.publicStaticP9_X)
        assertEquals("PB.J", PB_CAUREPL.getPrivateStaticP9_X())
        assertEquals("PB.J", PB_CAUREPL.publicStaticJ8_X)
        assertEquals("PB.J", PB_CAUREPL.getPrivateStaticJ8_X())
        assertEquals("P9.G", PB_CAUREPL.publicStaticP9)
        assertEquals("P9.G", PB_CAUREPL.getPrivateStaticP9())
    }

    public static void testInnerClassPB_Dynamic() {
        assertEquals("JA", JA.JAInner1.origin)
        assertEquals("JA", JA.JAInner1.JAInner2.origin)
        assertEquals("JA", JA.JAInner1.JAInner3.origin)
        assertEquals("PB.J", test1.PB.PBInner1.origin)
        assertEquals("PB.J", test1.PB.PBInner1.PBInner2.origin)
        assertEquals("PB.J", test1.PB.PBInner1.PBInner3.origin)
    }

    public static void testInstanceMethodsPB_Dynamic() {
        PB x = new PB()
        assertEquals("test1.PB", x.toString().split("@")[0])
        assertEquals("PB.G", x.getPB())
        assertEquals("PB.G", x.getPB_X())
        assertEquals("PB.G", x.getJA_X())
        assertEquals("PB.G", x.getP9_X())
        assertEquals("PB.G", x.getJ8_X())
        assertEquals("JA", x.getJA())
        assertEquals("P9.G", x.getP9())
        assertEquals("J8", x.getJ8())

        assertEquals("PB.G", x.privateJA_X)
        assertEquals("PB.G", x.doPrivateJA())

        assertEquals("PB.J", x.getPB_super())
        assertEquals("PB.J", x.getPB_X_super())
        assertEquals("PB.J", x.getJA_X_super())
        assertEquals("PB.J", x.getP9_X_super())
        assertEquals("PB.J", x.getJ8_X_super())
        assertEquals("JA", x.getJA_super())
        assertEquals("P9.G", x.getP9_super())
        assertEquals("J8", x.getJ8_super())

        assertEquals("c", x.testVarargs("bla", 1, "a", "b", "c"))
    }
}
