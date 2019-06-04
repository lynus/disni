package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetSimpleObject {
    public int A = 0xabcdef33;
    public long B = 0xaabbccddeeff9911L;
    public int c = 0x7766;
    public int d = 0xdddd;
    @Override
    public String toString() {
        return "TargetSimpleObject A: " + Integer.toHexString(A) +" B: " + Long.toHexString(B) +" C: " +
                Integer.toHexString(c)+ " d: " + Integer.toHexString(d);
    }
}
