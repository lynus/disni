package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetSimpleObject {
    public int A = 0xabcdef33;
    public long B = 0xaabbccddeeff9911L;
    @Override
    public String toString() {
        return "TargetSimpleObject A: " + Integer.toHexString(A) +" B: " + Long.toHexString(B);
    }
}
