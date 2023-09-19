// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1

import de.uni_kiel.rz.fdr.repl.Dynamize
import de.uni_kiel.rz.fdr.repl.Patches

import static org.junit.jupiter.api.Assertions.*;

@Patches
@Dynamize
class P9 {
    public static String publicStaticP9 = "P9.G"
    private static String privateStaticP9 = "P9.G"
    public static String publicStaticP9_X = "P9.G"
    private static String privateStaticP9_X = "P9.G"
    private static String privateStaticJ8_X = "P9.G"
    public static String publicStaticJ8_X = "P9.G"

    public P9() {
        super("P9.G", 1)
    }

    public P9(String foo, Integer bar) {
        this.constructor = "P9.G"
    }

    @Patches
    public class P9Inner1 {
        public static String origin = "P9.G"

        public class P9Inner2 {
            public static String origin = "P9.G"
        }

        public class P9Inner3 {
            public static String origin = "P9.G"
        }
    }

    public static String getPrivateStaticP9() { return privateStaticP9 }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X }

    public String getP9 () {
        return "P9.G";
    }

    public String getP9_X () {
        return "P9.G";
    }

    public String getJ8_X () {
        return "P9.G";
    }

    public String getP9_super () {
        return super.getP9()
    }

    public String getP9_X_super () {
        return super.getP9_X()
    }

    public String getJ8_X_super () {
        return super.getJ8_X()
    }

    public String getJ8_super () {
        return super.getJ8()
    }

    public String doIJA_X() {
        return "P9.G"
    }

    public String doIJA_Default() {
        return "P9.G";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public static void testStaticP9_Dynamic() {
        assertEquals("P9.G", publicStaticP9)
        assertEquals("P9.G", getPrivateStaticP9())
        assertEquals("P9.G", publicStaticP9_X)
        assertEquals("P9.G", getPrivateStaticP9_X())
        assertEquals("P9.G", publicStaticJ8_X)
        assertEquals("P9.G", getPrivateStaticJ8_X())
        assertEquals("J8", publicStaticJ8)
        assertEquals("J8", getPrivateStaticJ8())

        assertEquals("P9.J", P9_CAUREPL.publicStaticP9)
        assertEquals("P9.J", P9_CAUREPL.getPrivateStaticP9())
        assertEquals("P9.J", P9_CAUREPL.publicStaticP9_X)
        assertEquals("P9.J", P9_CAUREPL.getPrivateStaticP9_X())
        assertEquals("P9.J", P9_CAUREPL.publicStaticJ8_X)
        assertEquals("P9.J", P9_CAUREPL.getPrivateStaticJ8_X())

    }

    public static void testInnerClassP9_Dynamic() {
        assertEquals("P9.G", test1.P9.P9Inner1.origin)
        assertEquals("P9.J", test1.P9.P9Inner1.origin_nooverride)
        assertEquals("P9.G", test1.P9.P9Inner1.P9Inner2.origin)
        assertEquals("P9.G", test1.P9.P9Inner1.P9Inner3.origin)
    }

    public static void testInstanceMethodsP9_Dynamic() {
        test1.P9 x = new test1.P9()
        assertEquals("test1.P9", x.toString().split("@")[0])
        assertEquals("P9.G", x.getP9())
        assertEquals("P9.G", x.getP9_X())
        assertEquals("P9.G", x.getJ8_X())
        assertEquals("J8", x.getJ8())

        assertEquals("P9.J", x.getP9_super())
        assertEquals("P9.J", x.getP9_X_super())
        assertEquals("P9.J", x.getJ8_X_super())
        assertEquals("J8", x.getJ8_super())
    }

    private void testDynamicSuper() {
        assertEquals("J8", dynamicSuper("getJ8")) // is final and unpatched, so P9 does not have it
        assertEquals("P9.J", dynamicSuper("getJ8_X"))
        assertEquals("P9.J", dynamicSuper("getP9"))
        assertEquals("P9.J", dynamicSuper("getP9_X"))
    }

    def cl = { -> { -> "dummy" }; return { -> return "keep me as a compilation test case"} }

    public static void testDynamicSuper_Dynamic() {
        def x = new P9()
        x.testDynamicSuper()
        assertEquals("P9.G", x.getJ8_X())
        P9.metaClass.getJ8_X = { -> return dynamicSuper("getJ8_X") }
        def y = new P9()
        assertEquals("P9.J", x.getJ8_X())
        assertEquals("P9.J", y.getJ8_X())
    }
}