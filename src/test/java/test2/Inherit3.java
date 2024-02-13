package test2;

public class Inherit3 {
    public static void asd(Inherit2 x, Inherit2 y) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void dsa(Inherit1 x, Inherit1 y) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
