package intruder;

import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ArrayListWriter {
    private static Offset dataOffset;
    private static int instanceSize = ObjectModel.getAlignedUpSize(ArrayList.class);
    static {
        Unsafe unsafe = Unsafe.getUnsafe();
        try {
            Field field = ArrayList.class.getDeclaredField("data");
            dataOffset = Offset.fromLong(unsafe.objectFieldOffset(field));
        } catch (Exception ex) {
            Thread.dumpStack();
            System.exit(1);
        }

    }
    public static Address write(List list, StageBuffer stBuffer) throws IOException {
        Address start = stBuffer.reserve(instanceSize);
        stBuffer.setRemoteTIB(ArrayList.class, start);
        ObjectModel.copyObject(list, start);
        start = start.plus(24);
        Address ret = stBuffer.getRemoteAddress(start);
        Address arraySlot = start.plus(dataOffset);
        //copy data array
        Address arrayAddr  = Magic.objectAsAddress(list).plus(dataOffset).loadAddress();
        int eleNum = arrayAddr.minus(8).loadInt();
        start = stBuffer.reserve(24 + 8 * eleNum);
        stBuffer.setRemoteTIB(Object[].class, start);
        start = start.plus(24);
        arraySlot.store(stBuffer.getRemoteAddress(start));

        for (int i = 0; i < eleNum; i++) {
            Address _a = arrayAddr.plus(i * 8);
            Writable obj = (Writable)Magic.addressAsObject(_a.loadAddress());
            Address _o = obj.write(stBuffer);
            start.plus(8 * i).store(_o);
        }
        return ret;
    }

    public static Address writeString(List list, StageBuffer stBuffer) throws IOException {
        Address start = stBuffer.reserve(instanceSize);
        ObjectModel.copyObject(list, start);
        stBuffer.setRemoteTIB(ArrayList.class, start);
        start = start.plus(24);
        Address ret = stBuffer.getRemoteAddress(start);
        Address arraySlot = start.plus(dataOffset);
        //copy data array
        Address arrayAddr  = Magic.objectAsAddress(list).plus(dataOffset).loadAddress();
        int eleNum = arrayAddr.minus(8).loadInt();
        start = stBuffer.reserve(24 + 8 * eleNum);
        stBuffer.setRemoteTIB(Object[].class, start);
        start = start.plus(24);
        arraySlot.store(stBuffer.getRemoteAddress(start));

        for (int i = 0; i < eleNum; i++) {
            Address _a = arrayAddr.plus(i * 8);
            String obj = (String)Magic.addressAsObject(_a.loadAddress());
            Address _o = StringWriter.write(obj, stBuffer);
            start.plus(8 * i).store(_o);
        }
        return ret;
    }
}
