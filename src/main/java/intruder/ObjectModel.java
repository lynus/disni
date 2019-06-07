package intruder;

import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.mmtk.utility.Constants;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

import static org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_VALUE;

public class ObjectModel {
    public final static int REF_OFFSET = 24;
    public final static int OBJECT_OFFSET_ALIGN = 16;
    public final static int ELEMENT_NUM_OFFSET = 16;
    public final static int ARRAY_OFFSET_ALIGN = 24;
    public final static int HEADER_SIZE = 16;
    public final static int ALIGNMENT = 8;
    public final static int ALIGNMENT_MASK = ALIGNMENT - 1;
    public final static int MIN_ALIGNMENT = Constants.MIN_ALIGNMENT;

    @Inline
    private static RVMType getType(Class cls) {
        return java.lang.JikesRVMSupport.getTypeForClass(cls);
    }

    public static Address getObjectHeaderAddress(Object obj) {
        return Magic.objectAsAddress(obj).minus(REF_OFFSET);
    }

    public static int getInstanceSize(Class cls, Address header) {
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        if (type.isClassType()) {
            RVMClass rvmcls = type.asClass();
            return rvmcls.getInstanceSize();
        }
        assert(type.isArrayType());
        int ele_num = (int)header.plus(ELEMENT_NUM_OFFSET).loadLong();
        RVMArray array = type.asArray();
        return array.getInstanceSize(ele_num);
    }

    public static int getAlignedUpSize(Class cls, Address header) {
        int size = getInstanceSize(cls, header);
        return org.jikesrvm.runtime.Memory.alignUp(size, MIN_ALIGNMENT);
    }
    public static int getAlignedUpSize(Object obj) {
        return getAlignedUpSize(obj.getClass(), getObjectHeaderAddress(obj));
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
        Memory.memcopy(start.plus(HEADER_SIZE), getObjectHeaderAddress(object).plus(HEADER_SIZE), getAlignedUpSize(object) - HEADER_SIZE);
    }
    // -1 for primitives; 0 for classes
    private static int getDimension(Object object) {
        RVMType type = getType(object.getClass());
        return type.getDimensionality();
    }
    public static void setRegisteredID(Object object, Address start) {
        int id = Factory.query(object.getClass());
        assert(id != -1);
        int dimension = getDimension(object);
        if (dimension > 0)
            id = (dimension << 24) | id;
        start.store((long)id);
    }
    public static void fillGap(Address addr) {
        addr.store(ALIGNMENT_VALUE);
    }

    public static Object initializeHeader(Address ptr) {
        int id = (int)ptr.loadLong();
        Class cls  = getClassByID(id);
        RVMType type = getType(cls);
        TIB tib;
        if (type.isClassType()) {
            tib = type.asClass().getTypeInformationBlock();
        } else {
            tib = type.asArray().getTypeInformationBlock();
        }

        ObjectReference ref = ptr.plus(REF_OFFSET).toObjectReference();
        JavaHeader.setTIB(ref.toObject(), tib);
        // status header should should be zero
        assert(ptr.plus(8).loadLong() == 0L);
        return ref.toObject();
    }

    private static RVMArray getNDimensionArrayType(RVMType type, int dimension) {
        RVMArray ret = type.getArrayTypeForElementType();
        for (int i = 0; i < dimension - 1; i++) {
            ret = ret.getArrayTypeForElementType();
        }
        return ret;
    }

    public static Class getInnerMostEleType(Class cls) {
        RVMType type = getType(cls);
        if (type.isClassType())
            return cls;
        RVMArray array = type.asArray();
        return array.getInnermostElementType().getClassForType();
    }

    public static Class getClassByID(int id) {
        int dimension = id >>> 24;
        id = id & ((1 << 24) - 1);
        Class cls = Factory.query(id);
        if (dimension == 0)
            return cls;
        return getNDimensionArrayType(getType(cls), dimension).getClassForType();
    }

    public static Address getArrayAddress(Object object) {
        return getObjectHeaderAddress(object).plus(ELEMENT_NUM_OFFSET + 8);
    }
}
