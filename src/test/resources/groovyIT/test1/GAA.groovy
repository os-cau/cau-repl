// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1

import de.uni_kiel.rz.fdr.repl.groovy.GroovyDynamized

import static org.junit.jupiter.api.Assertions.*;

class GAA extends JA {
    public static String publicStaticGAA = "GAA"
    private static String privateStaticGAA = "GAA"
    public static String publicStaticGAA_X = "GAA"
    private static String privateStaticGAA_X = "GAA"
    public static String publicStaticJA_X = "GAA"
    private static String privateStaticJA_X = "GAA"
    private static String privateStaticJ8_X = "GAA"
    public static String publicStaticJ8_X = "GAA"
    private static String privateStaticP9_X = "GAA"
    public static String publicStaticP9_X = "GAA"

    public static String getPrivateStaticGAA() { return privateStaticGAA }
    public static String getPrivateStaticGAA_X() { return privateStaticGAA_X }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X }

    private String privateJA_X = "GAA";

    private String doPrivateJA() {
        return "GAA";
    }

    public String getGAA(){
        return "GAA";
    }

    public String getGAA_X(){
        return "GAA";
    }

    public String getJA_X(){
        return "GAA";
    }

    public String getP9_X(){
        return "GAA";
    }

    public String getJ8_X() {
        return "GAA";
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

    public String doIJA_X() {
        return "GAA"
    }

    public String doIJA_Default() {
        return "GAA";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public static void testStaticGAA_Dynamic() {
        assertEquals("GAA", publicStaticGAA)
        assertEquals("GAA", getPrivateStaticGAA())
        assertEquals("GAA", publicStaticGAA_X)
        assertEquals("GAA", getPrivateStaticGAA_X())
        assertEquals("GAA", publicStaticJA_X)
        assertEquals("GAA", getPrivateStaticJA_X())
        assertEquals("GAA", publicStaticP9_X)
        assertEquals("GAA", getPrivateStaticP9_X())
        assertEquals("GAA", publicStaticJ8_X)
        assertEquals("GAA", getPrivateStaticJ8_X())
        assertEquals("JA", publicStaticJA)
        assertEquals("JA", getPrivateStaticJA())
        assertEquals("P9.G", publicStaticP9)
        assertEquals("P9.G", getPrivateStaticP9())
        assertEquals("J8", publicStaticJ8)
        assertEquals("J8", getPrivateStaticJ8())
    }

    public static void testInnerClassGAA_Dynamic() {
        assertEquals("JA", JA.JAInner1.origin)
        assertEquals("JA", JA.JAInner1.JAInner2.origin)
        assertEquals("JA", JA.JAInner1.JAInner3.origin)
    }

    public static void testInstanceMethodsGAA_Dynamic() {
        GAA x = new GAA()
        assertEquals("test1.GAA", x.toString().split("@")[0])
        assertEquals("GAA", x.getGAA())
        assertEquals("GAA", x.getGAA_X())
        assertEquals("GAA", x.getJA_X())
        assertEquals("GAA", x.getP9_X())
        assertEquals("GAA", x.getJ8_X())
        assertEquals("JA", x.getJA())
        assertEquals("P9.G", x.getP9())
        assertEquals("J8", x.getJ8())

        assertEquals("GAA", x.privateJA_X)
        assertEquals("GAA", x.doPrivateJA())

        assertEquals("JA", x.getJA_X_super())
        assertEquals("JA", x.getP9_X_super())
        assertEquals("JA", x.getJ8_X_super())
        assertEquals("JA", x.getJA_super())
        assertEquals("P9.G", x.getP9_super())
        assertEquals("J8", x.getJ8_super())
    }
}
