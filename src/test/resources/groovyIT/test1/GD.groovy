// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1

import de.uni_kiel.rz.fdr.repl.REPL
import de.uni_kiel.rz.fdr.repl.REPLLog
import org.codehaus.groovy.runtime.InvokerHelper

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

import test1b.*

import static org.junit.jupiter.api.Assertions.*;

class GD extends PC implements IGB {
    public static String publicStaticGD = "GD"
    private static String privateStaticGD = "GD"
    public static String publicStaticGD_X = "GD"
    private static String privateStaticGD_X = "GD"
    public static String publicStaticPC_X = "GD"
    private static String privateStaticPC_X = "GD"
    public static String publicStaticPB_X = "GD"
    private static String privateStaticPB_X = "GD"
    public static String publicStaticJA_X = "GD"
    private static String privateStaticJA_X = "GD"
    private static String privateStaticJ8_X = "GD"
    public static String publicStaticJ8_X = "GD"
    private static String privateStaticP9_X = "GD"
    public static String publicStaticP9_X = "GD"

    public static String getPrivateStaticGD() { return privateStaticGD }
    public static String getPrivateStaticGD_X() { return privateStaticGD_X }
    public static String getPrivateStaticPC_X() { return privateStaticPC_X }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X }

    private String privateJA_X = "GD";

    private String doPrivateJA() {
        return "GD";
    }

    public String getGD() {
        return "GD";
    }

    public String getGD_X() {
        return "GD";
    }

    public String getPC_X() {
        return "GD";
    }

    public String getPB_X() {
        return "GD";
    }

    public String getJA_X() {
        return "GD";
    }

    public String getP9_X() {
        return "GD";
    }

    public String getJ8_X() {
        return "GD";
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
        return "GD"
    }

    public String doIJA_Default() {
        return "GD";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public String doIGB_X() {
        return "GD"
    }

    public String doIGB_Default() {
        return "GD"
    }

    public static void testStaticGD_Dynamic() {
        assertEquals("GD", publicStaticGD)
        assertEquals("GD", getPrivateStaticGD())
        assertEquals("GD", publicStaticGD_X)
        assertEquals("GD", getPrivateStaticGD_X())
        assertEquals("GD", publicStaticPC_X)
        assertEquals("GD", getPrivateStaticPC_X())
        assertEquals("GD", publicStaticJA_X)
        assertEquals("GD", getPrivateStaticJA_X())
        assertEquals("GD", publicStaticP9_X)
        assertEquals("GD", getPrivateStaticP9_X())
        assertEquals("GD", publicStaticJ8_X)
        assertEquals("GD", getPrivateStaticJ8_X())
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
    
    public static void testInstanceMethodsGD_Dynamic() {
        GD x = new GD()
        assertEquals("test1.GD", x.toString().split("@")[0])
        assertEquals("GD", x.getGD_X())
        assertEquals("GD", x.getPC_X())
        assertEquals("GD", x.getPB_X())
        assertEquals("GD", x.getJA_X())
        assertEquals("GD", x.getP9_X())
        assertEquals("GD", x.getJ8_X())
        assertEquals("PB.G", x.getPB())
        assertEquals("PC.G", x.getPC())
        assertEquals("JA", x.getJA())
        assertEquals("P9.G", x.getP9())
        assertEquals("J8", x.getJ8())

        assertEquals("GD", x.privateJA_X)
        assertEquals("GD", x.doPrivateJA())

        assertEquals("PC.G", x.getPC_X_super())
        assertEquals("PC.G", x.getPB_X_super())
        assertEquals("PC.G", x.getJA_X_super())
        assertEquals("PC.G", x.getP9_X_super())
        assertEquals("PC.G", x.getJ8_X_super())
        assertEquals("PC.G", x.getPC_super())
        assertEquals("PB.G", x.getPB_super())
        assertEquals("JA", x.getJA_super())
        assertEquals("P9.G", x.getP9_super())
        assertEquals("J8", x.getJ8_super())
    }
}
