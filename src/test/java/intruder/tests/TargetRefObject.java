package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetRefObject extends TargetSimpleObject {
    public TargetPrimitiveObject ref1;
    public TargetPrimitiveObject ref2;
}
