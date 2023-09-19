// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1

import de.uni_kiel.rz.fdr.repl.REPL
import de.uni_kiel.rz.fdr.repl.REPLLog
import org.codehaus.groovy.runtime.InvokerHelper

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

import static org.junit.jupiter.api.Assertions.*;

@Dynamize
class GE extends GD {
    public static String publicStaticGE = "GE"
    private static String privateStaticGE = "GE"
    public static String publicStaticGE_X = "GE"
    private static String privateStaticGE_X = "GE"
    public static String publicStaticGD_X = "GE"
    private static String privateStaticGD_X = "GE"
    public static String publicStaticPC_X = "GE"
    private static String privateStaticPC_X = "GE"
    public static String publicStaticPB_X = "GE"
    private static String privateStaticPB_X = "GE"
    public static String publicStaticJA_X = "GE"
    private static String privateStaticJA_X = "GE"
    private static String privateStaticJ8_X = "GE"
    public static String publicStaticJ8_X = "GE"
    private static String privateStaticP9_X = "GE"
    public static String publicStaticP9_X = "GE"

    public static String getPrivateStaticGE() { return privateStaticGE }
    public static String getPrivateStaticGE_X() { return privateStaticGE_X }
    public static String getPrivateStaticGD_X() { return privateStaticGD_X }
    public static String getPrivateStaticPC_X() { return privateStaticPC_X }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X }

    private String privateJA_X = "GE";

    private String doPrivateJA() {
        return "GE";
    }

    public String getGE() {
        return "GE";
    }

    public String getGE_X() {
        return "GE";
    }

    public String getGD_X() {
        return "GE";
    }

    public String getPC_X() {
        return "GE";
    }

    public String getPB_X() {
        return "GE";
    }

    public String getJA_X() {
        return "GE";
    }

    public String getP9_X() {
        return "GE";
    }

    public String getJ8_X() {
        return "GE";
    }

    public String getGD_X_super() {
        return super.getGD_X()
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

    public String getGD_super() {
        return super.getGD()
    }

    public String getPC_super() {
        return super.getPC()
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
        return "GE"
    }

    public String doIJA_Default() {
        return "GE";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public String doIGB_X() {
        return "GE"
    }

    public String doIGB_Default() {
        return "GE"
    }

    public static void testStaticGE_Dynamic() {
        assertEquals("GE", publicStaticGE)
        assertEquals("GE", getPrivateStaticGE())
        assertEquals("GE", publicStaticGE_X)
        assertEquals("GE", getPrivateStaticGE_X())
        assertEquals("GE", publicStaticGD_X)
        assertEquals("GE", getPrivateStaticGD_X())
        assertEquals("GE", publicStaticPC_X)
        assertEquals("GE", getPrivateStaticPC_X())
        assertEquals("GE", publicStaticJA_X)
        assertEquals("GE", getPrivateStaticJA_X())
        assertEquals("GE", publicStaticP9_X)
        assertEquals("GE", getPrivateStaticP9_X())
        assertEquals("GE", publicStaticJ8_X)
        assertEquals("GE", getPrivateStaticJ8_X())
        assertEquals("GD", publicStaticGD)
        assertEquals("GD", getPrivateStaticGD())
        assertEquals("PC.G", publicStaticPC)
        assertEquals("PC.G", getPrivateStaticPC())
        assertEquals("PB.G", publicStaticPB)
        assertEquals("PB.G", getPrivateStaticPB())
        assertEquals("JA", publicStaticJA)
        assertEquals("JA", getPrivateStaticJA())
        assertEquals("P9.G", publicStaticP9)
        assertEquals("P9.G", getPrivateStaticP9())
        assertEquals("J8", publicStaticJ8)
        assertEquals("J8", getPrivateStaticJ8())
    }

    public static void testInstanceMethodsGE_Dynamic() {
        GE x = new GE()
        assertEquals("test1.GE", x.toString().split("@")[0])
        assertEquals("GE", x.getGE_X())
        assertEquals("GE", x.getGD_X())
        assertEquals("GE", x.getPC_X())
        assertEquals("GE", x.getPB_X())
        assertEquals("GE", x.getJA_X())
        assertEquals("GE", x.getP9_X())
        assertEquals("GE", x.getJ8_X())
        assertEquals("GD", x.getGD())
        assertEquals("PB.G", x.getPB())
        assertEquals("PC.G", x.getPC())
        assertEquals("JA", x.getJA())
        assertEquals("P9.G", x.getP9())
        assertEquals("J8", x.getJ8())

        assertEquals("GE", x.privateJA_X)
        assertEquals("GE", x.doPrivateJA())

        assertEquals("GD", x.getGD_X_super())
        assertEquals("GD", x.getPC_X_super())
        assertEquals("GD", x.getPB_X_super())
        assertEquals("GD", x.getJA_X_super())
        assertEquals("GD", x.getP9_X_super())
        assertEquals("GD", x.getJ8_X_super())
        assertEquals("GD", x.getGD_super())
        assertEquals("PC.G", x.getPC_super())
        assertEquals("PB.G", x.getPB_super())
        assertEquals("JA", x.getJA_super())
        assertEquals("P9.G", x.getP9_super())
        assertEquals("J8", x.getJ8_super())
    }
}
