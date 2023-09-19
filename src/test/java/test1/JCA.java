// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"})
public class JCA extends PC {
    public static String publicStaticJCA = "JCA";
    private static String privateStaticJCA = "JCA";
    public static String publicStaticJCA_X = "JCA";
    private static String privateStaticJCA_X = "JCA";
    public static String publicStaticPC_X = "JCA";
    private static String privateStaticPC_X = "JCA";
    public static String publicStaticPB_X = "JCA";
    private static String privateStaticPB_X = "JCA";
    public static String publicStaticJA_X = "JCA";
    private static String privateStaticJA_X = "JCA";
    private static String privateStaticJ8_X = "JCA";
    public static String publicStaticJ8_X = "JCA";
    private static String privateStaticP9_X = "JCA";
    public static String publicStaticP9_X = "JCA";

    public static String getPrivateStaticJCA() { return privateStaticJCA; }
    public static String getPrivateStaticJCA_X() { return privateStaticJCA_X; }
    public static String getPrivateStaticPC_X() { return privateStaticPC_X; }
    public static String getPrivateStaticPB_X() { return privateStaticPB_X; }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X; }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X; }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    private String privateJA_X = "JCA";

    private String doPrivateJA() {
        return "JCA";
    }

    public String getJCA() {
        return "JCA";
    }

    public String getJCA_X() {
        return "JCA";
    }

    public String getPB_X() {
        return "JCA";
    }

    public String getJA_X() {
        return "JCA";
    }

    public String getP9_X() {
        return "JCA";
    }

    public String getJ8_X() {
        return "JCA";
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
        return "JCA";
    }

    public String doIJA_Default() {
        return "JCA";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }
}
