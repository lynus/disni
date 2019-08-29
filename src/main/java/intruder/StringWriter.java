package intruder;

import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;

public class StringWriter {
    private static int instanceSize = ObjectModel.getAlignedUpsize(String.class);
    private static Offset valueOffset;
    static {
        Unsafe unsafe = Unsafe.getUnsafe();
        try {
            Field f = String.class.getDeclaredField("value");
            valueOffset = Offset.fromLong(unsafe.objectFieldOffset(f));
        } catch (Exception ex) {
            Thread.dumpStack();
            System.exit(1);
        }
    }
    public static Address write(String string, StageBuffer stBuffer) throws IOException {
        Address start = stBuffer.reserve(instanceSize);
        ObjectModel.copyObject(string, start);
        stBuffer.setRemoteTIB(String.class, start);
        start = start.plus(24);
        Address ret = stBuffer.getRemoteAddress(start);
        Address valueSlot = start.plus(valueOffset);

        Address valueAddr = Magic.objectAsAddress(string).plus(valueOffset).loadAddress();
        int valueLeng = valueAddr.minus(8).loadInt();
        valueLeng = (valueLeng + 7) & (~7);
        start = stBuffer.reserve(24 + valueLeng);
        stBuffer.setRemoteTIB(char[].class, start);
        start = start.plus(24);
        valueSlot.store(stBuffer.getRemoteAddress(start));
        ObjectModel.memcopy(start, valueAddr.plus(24), valueLeng);
        return ret;
    }
}
