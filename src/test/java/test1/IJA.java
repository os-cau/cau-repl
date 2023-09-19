// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1;

public interface IJA {
    String IJA = "IJA";

    default String doIJA() {
        return "IJA";
    }

    String doIJA_X();

    default String doIJA_Default() {
        return "IJA";
    }
}
