// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

import java.rmi.AlreadyBoundException;
import java.util.concurrent.BrokenBarrierException;

public class P9 extends J8 {
    public static String publicStaticP9 = "P9.J";
    private static String privateStaticP9 = "P9.J";
    public static String publicStaticP9_X = "P9.J";
    private static String privateStaticP9_X = "P9.J";
    private static String privateStaticJ8_X = "P9.J";
    public static String publicStaticJ8_X = "P9.J";

    public static String getPrivateStaticP9() { return privateStaticP9; }
    public static String getPrivateStaticP9_X() { return privateStaticP9_X; }
    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    public String constructor;

    public P9() {
        this.constructor = "P9.J";
    }

    public P9(String foo, Integer bar) {
        this.constructor = foo;
    }

    public class P9Inner1 {
        public static String origin_nooverride = "P9.J";
        public static String origin = "P9.J";

        public class P9Inner2 {
            public static String origin = "P9.J";
        }

        public class P9Inner3 {
            public static String origin = "P9.J";
        }
    }

    public final String getP9 () {
        return "P9.J";
    }

    public String getP9_X () {
        return "P9.J";
    }

    public String getJ8_X () {
        return "P9.J";
    }

    public String doIJA_X() {
        return "P9.J";
    }

    public String doIJA_Default() {
        return "P9.J";
    }

    public String doIJA_X_super() {
        return super.doIJA_X();
    }

    public String doIJA_Default_super() {
        return super.doIJA_X();
    }

    public void patchesSuperVoidVoid() throws AlreadyBoundException, BrokenBarrierException { throw new BrokenBarrierException("P9.J"); }
    public void patchesSuperVoidInt(int a) throws AlreadyBoundException, BrokenBarrierException { throw new BrokenBarrierException("P9.J"); }
    public void patchesSuperVoidObj(String a) throws AlreadyBoundException, BrokenBarrierException { throw new BrokenBarrierException("P9.J"); }
    public long patchesSuperLongObj(String a) { return 2; }
    public String patchesSuperObjObj(String a) { return "P9.J"; }
    public String patchesSuperObjVarargs(String a, int b, String... c) { return c[1]; }

}
