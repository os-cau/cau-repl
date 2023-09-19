// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

import java.rmi.AlreadyBoundException;
import java.util.concurrent.BrokenBarrierException;

public class J8 implements IJA {
    public static String publicStaticJ8 = "J8";
    private static String privateStaticJ8 = "J8";
    public static String publicStaticJ8_X = "J8";
    private static String privateStaticJ8_X = "J8";

    public static String getPrivateStaticJ8() { return privateStaticJ8; }

    public static String getPrivateStaticJ8_X() { return privateStaticJ8_X; }

    public final String getJ8 () {
        return "J8";
    }

    public String getJ8_X () {
        return "J8";
    }

    public String doIJA_X() {
        return "J8";
    }

    public void patchesSuperVoidVoid() throws AlreadyBoundException, BrokenBarrierException { throw new AlreadyBoundException("J8"); }
    public void patchesSuperVoidInt(int a) throws AlreadyBoundException, BrokenBarrierException { throw new AlreadyBoundException("J8"); }
    public void patchesSuperVoidObj(String a) throws AlreadyBoundException, BrokenBarrierException { throw new AlreadyBoundException("J8"); }
    public long patchesSuperLongObj(String a) { return 1; }
    public String patchesSuperObjObj(String a) { return "J8"; }
    public String patchesSuperObjVarargs(String a, int b, String... c) { return c[0]; }


    public String doIJA_Default() {
        return "J8";
    }
}
