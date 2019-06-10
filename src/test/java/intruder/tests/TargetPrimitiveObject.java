package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetPrimitiveObject {
    int A;
    long B;
    float F;
    double D;
    public TargetPrimitiveObject() {
        A = 99;
        B = 7777L;
        F = 22.56f;
        D = 0.12345;
    }
    public void setA(int i) {
        this.A = i;
    }
    public String toString() {
        return new String("TargetPrimitiveObject: A:" + A + " B:" + B + " F:" + F);
    }
}
