package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetSimpleObject {
    public int A = 123;
    public long B = 999L;
    @Override
    public String toString() {
        return "TargetSimpleObject A: " + A +" B: " + B;
    }
}
