package intruder;

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.mmtk.utility.Constants;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

import static org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_VALUE;

public class ObjectModel {
    public final static int REF_OFFSET = 24;
    public final static int OBJECT_OFFSET_ALIGN = 16;
    public final static int ARRAY_OFFSET_ALIGN = 24;
    public final static int HEADER_SIZE = 16;
    public final static int ALIGNMENT = 8;
    public final static int ALIGNMENT_MASK = ALIGNMENT - 1;
    public final static int MIN_ALIGNMENT = Constants.MIN_ALIGNMENT;

    public static Address getObjectHeaderAddress(Object obj) {
        return Magic.objectAsAddress(obj).minus(REF_OFFSET);
    }

    public static int getInstanceSize(Class cls) {
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        RVMClass rvmcls = type.asClass();
        return rvmcls.getInstanceSize();
    }

    public static int getInstanceSize(Object obj) {
        return getInstanceSize(obj.getClass());
    }

    public static int getAlignedUpSize(Class cls) {
        int size = getInstanceSize(cls);
        return org.jikesrvm.runtime.Memory.alignUp(size, MIN_ALIGNMENT);
    }
    public static int getAlignedUpSize(Object obj) {
        return getAlignedUpSize(obj.getClass());
    }
    public static int getMaximumAlignedSize(Object obj) {
        //for x86-64, alignment is 8, MIN_ALIGNMENT is 4.
        //see org.mmtk.utility.alloc.Allocator.getMaximumAlignedSize()
        return getAlignedUpSize(obj) + 8 - 4;
    }

    public static int alignObjectAllocation(int head) {
        int delta = (-head - OBJECT_OFFSET_ALIGN) & ALIGNMENT_MASK;
        assert(delta == 0 || delta == 4);
        return head + delta;
    }
    //skip over header
    public static void copyObject(Object object, Address start) {
        assert((start.plus(OBJECT_OFFSET_ALIGN).toLong() & ALIGNMENT_MASK)== 0);
        Memory.memcopy(start.plus(HEADER_SIZE), getObjectHeaderAddress(object).plus(HEADER_SIZE), getAlignedUpSize(object) - HEADER_SIZE);
//        Utils.log("hash code: 0x" + Integer.toHexString(object.hashCode()));
//        Utils.peekBytes("object raw", getObjectHeaderAddress(object), 0, 32, 3);
    }
    public static void setRegisteredID(Object object, Address start) {
        int id = Factory.query(object.getClass());
        assert(id != -1);
        start.store((long)id);
    }
    public static void fillGap(Address addr) {
        addr.store(ALIGNMENT_VALUE);
    }

    public static Object initializeHeader(Address ptr) {
        int id = (int)ptr.loadLong();
        Class cls  = Factory.query(id);
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        //TODO handle array type
        /*
            if type.isArray {}
         */
        TIB tib = type.asClass().getTypeInformationBlock();
        ObjectReference ref = ptr.plus(REF_OFFSET).toObjectReference();
        JavaHeader.setTIB(ref.toObject(), tib);
        // status header should should be zero
        assert(ptr.plus(8).loadLong() == 0L);
        return ref.toObject();
    }
}
