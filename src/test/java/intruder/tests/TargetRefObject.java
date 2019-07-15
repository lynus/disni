package intruder.tests;

import org.vmmagic.pragma.RDMA;

@RDMA
public class TargetRefObject extends TargetSimpleObject {
    public TargetPrimitiveObject ref1;
    public TargetPrimitiveObject ref2;
    public TargetRefObject ref3;
//    public TestEnum testEnum = TestEnum.SUNDAY;
    @Override
    public String toString() {
        String ret;
        if (ref1 == null)
            ret = "ref1: NULL, ";
        else
            ret = "ref1: " + ref1.toString();
        if (ref2 == null)
            ret += "ref2: NULL, ";
        else
            ret += "ref2: " + ref2.toString();
//        ret +=" TestEnum: " + testEnum.toString();
        ret += " " + super.toString();
        return ret;
    }
}
