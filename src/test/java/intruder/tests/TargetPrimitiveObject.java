package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetPrimitiveObject {
    int A;
    Long B;
    Float F;
    Double D;
    public TargetPrimitiveObject() {
        A = 99;
        B = 7777L;
        F = 22.56f;
        D = 0.12345;
    }
}
