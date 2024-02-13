package test2;

import static test2.Inherit3.asd;
import static test2.Inherit3.dsa;

public abstract class Inherit1 {

    public static String aField = "asd";

    public void foo(Inherit2 a, Inherit1 b) {
        // this is not strictly necessary as a reproducer
        if (b instanceof Inherit2) {
          asd((Inherit2) b, a);
        }

        dsa(b, a);
    }



}
