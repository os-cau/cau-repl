// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

@SuppressWarnings("ALL")
public class PB extends JA {
    public static String publicStaticPB = "PB.J";
    private static String privateStaticPB = "PB.J";
    public static String publicStaticPB_X = "PB.J";
    private static String privateStaticPB_X = "PB.J";
    public static String publicStaticJA_X = "PB.J";
    private static String privateStaticJA_X = "PB.J";
    private static String privateStaticJ8_X = "PB.J";
    public static String publicStaticJ8_X = "PB.J";
    private static String privateStaticP9_X = "PB.J";
    public static String publicStaticP9_X = "PB.J";


    public static String getPrivateStaticPB() { return privateStaticPB; }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X; }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X; }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X; }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    public PB() {
        this.constructor = "PB.J";
    }

    public PB(String foo, Integer bar) {
        this.constructor = foo;
    }

    public class PBInner1 {
        public static String origin = "PB.J";

        public class PBInner2 {
            public static String origin = "PB.J";
        }

        public class PBInner3 {
            public static String origin = "PB.J";
        }
    }

    public String testVarargs(String foo, int bar, String... quux){ return quux[quux.length-1]; }

    public final String getPB() {
        return "PB.J";
    }

    public String getPB_X() {
        return "PB.J";
    }

    public String getJA_X () {
        return "PB.J";
    }

    public String getP9_X () {
        return "PB.J";
    }

    public String getJ8_X () {
        return "PB.J";
    }

    public String doIJA_X() {
        return "PB.J";
    }

    public String doIJA_Default() {
        return "PB.J";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }
}
