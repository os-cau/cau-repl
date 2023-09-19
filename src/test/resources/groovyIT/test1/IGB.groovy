// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package test1b

interface IGB {

    default String doIGB() {
        return "IGB"
    }

    default String doIGB_Default() {
        return "IGB"
    }

    String doIGB_X();
}