package intruder.tests.cases;

import intruder.Utils;

public class TestShift {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static void main(String[] args) {
        byte vv = -128;
        Utils.log(""+(vv >>> 4));
        for (int i = 0; i < 255; i++) {
            byte v = (byte)(i & 0xff);
            Utils.log("0x" + hexArray[(v >>> 4)&0xf] + hexArray[v&0x0f]+ " ");
        }
        assert(vv == 128);
    }
}
