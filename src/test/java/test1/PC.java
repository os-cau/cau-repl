// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

@SuppressWarnings("ALL")
public class PC extends PB {
    public static String publicStaticPC = "PC.J";
    private static String privateStaticPC = "PC.J";
    public static String publicStaticPC_X = "PC.J";
    private static String privateStaticPC_X = "PC.J";
    public static String publicStaticPB_X = "PC.J";
    private static String privateStaticPB_X = "PC.J";
    public static String publicStaticJA_X = "PC.J";
    private static String privateStaticJA_X = "PC.J";
    private static String privateStaticJ8_X = "PC.J";
    public static String publicStaticJ8_X = "PC.J";
    private static String privateStaticP9_X = "PC.J";
    public static String publicStaticP9_X = "PC.J";


    public static String getPrivateStaticPC() { return privateStaticPC; }
    public static String getPrivateStaticPC_X() { return privateStaticPC_X; }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X; }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X; }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X; }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    public PC() {
        this.constructor = "PC.J";
    }

    public PC(String foo, Integer bar) {
        this.constructor = foo;
    }

    public class PCInner1 {
        public static String origin = "PC.J";

        public class PCInner2 {
            public static String origin = "PC.J";
        }

        public class PCInner3 {
            public static String origin = "PC.J";
        }
    }

    public final String getPC() {
        return "PC.J";
    }

    public final String getPC_X() {
        return "PC.J";
    }

    public String getPB_X() {
        return "PC.J";
    }

    public String getJA_X () {
        return "PC.J";
    }

    public String getP9_X () {
        return "PC.J";
    }

    public String getJ8_X () {
        return "PC.J";
    }

    public String doIJA_X() {
        return "PC.J";
    }

    public String doIJA_Default() {
        return "PC.J";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

}
