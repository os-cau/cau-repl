// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
public class JCB extends JCA {
    public static String publicStaticJCB = "JCB";
    private static String privateStaticJCB = "JCB";
    public static String publicStaticJCB_X = "JCB";
    private static String privateStaticJCB_X = "JCB";
    public static String publicStaticJCA_X = "JCB";
    private static String privateStaticJCA_X = "JCB";
    public static String publicStaticPC_X = "JCB";
    private static String privateStaticPC_X = "JCB";
    public static String publicStaticPB_X = "JCB";
    private static String privateStaticPB_X = "JCB";
    public static String publicStaticJA_X = "JCB";
    private static String privateStaticJA_X = "JCB";
    private static String privateStaticJ8_X = "JCB";
    public static String publicStaticJ8_X = "JCB";
    private static String privateStaticP9_X = "JCB";
    public static String publicStaticP9_X = "JCB";

    public static String getPrivateStaticJCB() { return privateStaticJCB; }
    public static String getPrivateStaticJCB_X() { return privateStaticJCB_X; }
    public static String getPrivateStaticJCA_X() { return privateStaticJCA_X; }
    public static String getPrivateStaticPC_X() { return privateStaticPC_X; }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X; }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X; }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X; }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    private String privateJA_X = "JCB";

    private String doPrivateJA() {
        return "JCB";
    }

    public String getJCB() {
        return "JCB";
    }

    public String getJCB_X() {
        return "JCB";
    }

    public String getJCA_X() {
        return "JCB";
    }

    public String getPB_X() {
        return "JCB";
    }

    public String getJA_X() {
        return "JCB";
    }

    public String getP9_X() {
        return "JCB";
    }

    public String getJ8_X() {
        return "JCB";
    }

    public String getJCA_X_super() {
        return super.getJCA_X();
    }

    public String getPC_X_super() {
        return super.getPC_X();
    }

    public String getPB_X_super() {
        return super.getPB_X();
    }

    public String getJA_X_super() {
        return super.getJA_X();
    }

    public String getP9_X_super() {
        return super.getP9_X();
    }

    public String getJ8_X_super() {
        return super.getJ8_X();
    }

    public String getJCA_super() {
        return super.getJCA();
    }

    public String getPC_super() {
        return super.getPC();
    }

    public String getPB_super() {
        return super.getPB();
    }

    public String getJA_super() {
        return super.getJA();
    }

    public String getP9_super() {
        return super.getP9();
    }

    public String getJ8_super() {
        return super.getJ8();
    }

    public String doIJA_X() {
        return "JCB";
    }

    public String doIJA_Default() {
        return "JCB";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }
}
