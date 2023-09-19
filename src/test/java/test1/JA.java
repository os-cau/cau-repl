// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

@SuppressWarnings("ALL")
public class JA extends P9 {

    public static String publicStaticJA = "JA";
    private static String privateStaticJA = "JA";
    public static String publicStaticJA_X = "JA";
    private static String privateStaticJA_X = "JA";
    private static String privateStaticJ8_X = "JA";
    public static String publicStaticJ8_X = "JA";
    private static String privateStaticP9_X = "JA";
    public static String publicStaticP9_X = "JA";

    public static String getPrivateStaticJA() { return privateStaticJA; }
    public static String getPrivateStaticJA_X() { return privateStaticJA_X; }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X; }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    public class JAInner1 {
        public static String origin = "JA";

        public class JAInner2 {
            public static String origin = "JA";
        }

        public class JAInner3 {
            public static String origin = "JA";
        }
    }

    private String privateJA_X = "JA";

    private String doPrivateJA() {
        return "JA";
    }

    public final String getJA () {
        return "JA";
    }

    public String getJA_X () {
        return "JA";
    }

    public String getP9_X () {
        return "JA";
    }

    public String getJ8_X () {
        return "JA";
    }

    public String doIJA_X() {
        return "JA";
    }

    public String doIJA_Default() {
        return "JA";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }
}
