// (C) Copyright 2024 Ove Sörensen
// SPDX-License-Identifier: MIT

package test2

class Cyclic2 {
    static String bar

    static {
        bar = Cyclic1.engage()
    }


    public static String spin() {
        return "2.spin" + "/" + bar
    }

}
