// (C) Copyright 2024 Ove Sörensen
// SPDX-License-Identifier: MIT

package test2

class Cyclic1 {
    static String foo

    static {
        foo = Cyclic2.spin()
    }


    public static String engage() {
        return "1.engage" + "/" + foo
    }

}
