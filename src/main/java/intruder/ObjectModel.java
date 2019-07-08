package intruder;

import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.mmtk.utility.Constants;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;

import static org.jikesrvm.classloader.RVMType.REFARRAY_OFFSET_ARRAY;
import static org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_VALUE;
@Uninterruptible
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
    public static RVMType getType(Class cls) {
        return java.lang.JikesRVMSupport.getTypeForClass(cls);
    }
    @Inline
    public static Address getObjectHeaderAddress(Object obj) {
        return Magic.objectAsAddress(obj).minus(REF_OFFSET);
    }
    @Inline
    public static int getAlignedUpSize(RVMType type, Address header) {
        if (type.isClassType()) {
            return (type.asClass().getInstanceSize() + (MIN_ALIGNMENT - 1)) & ~(MIN_ALIGNMENT - 1);
        } else {
            int ele_num = (int)header.plus(ELEMENT_NUM_OFFSET).loadLong();
            return type.asArray().getInstanceSize(ele_num) + (MIN_ALIGNMENT -1) & ~(MIN_ALIGNMENT -1);
        }
    }
    @Inline
    public static int getAlignedUpSize(Object obj) {
        RVMType type = Magic.getObjectType(obj);
        if (type.isClassType()) {
            return (type.asClass().getInstanceSize() + (MIN_ALIGNMENT - 1)) & ~(MIN_ALIGNMENT - 1);
        } else {
            int ele_num = (int)Magic.objectAsAddress(obj).minus(8).loadLong();
            return type.asArray().getInstanceSize(ele_num) + (MIN_ALIGNMENT -1) & ~(MIN_ALIGNMENT -1);
        }
    }
    @Inline
    public static int getMaximumAlignedSize(Object obj) {
        //for x86-64, alignment is 8, MIN_ALIGNMENT is 4.
        //see org.mmtk.utility.alloc.Allocator.getMaximumAlignedSize()
        return getAlignedUpSize(obj) + 4;
    }

    @Inline
    public static int alignObjectAllocation(int head) {
        int delta = (-head - OBJECT_OFFSET_ALIGN) & ALIGNMENT_MASK;
        return head + delta;
    }
    //skip over header
    @Inline
    public static int copyObject(Object object, Address start) {
        int ret = getAlignedUpSize(object);
        memcopy(start.plus(HEADER_SIZE), getObjectHeaderAddress(object).plus(HEADER_SIZE), ret - HEADER_SIZE);
        return ret;
    }

    @Inline
    private static void memcopy(Address dst, Address src, int size) {
        Address end = src.plus(size);
        while (src.LT(end)) {
            dst.store(src.loadInt());
            src = src.plus(4);
            dst = dst.plus(4);
        }
    }
    // -1 for primitives; 0 for classes
    @Inline
    private static int getDimension(Object object) {
        RVMType type = getType(object.getClass());
        return type.getDimensionality();
    }
    @Inline
    public static void setRegisteredID(Object object, Address start) {
        int id = Factory.query(object.getClass());
        assert(id != -1);
        int dimension = getDimension(object);
        HeaderEncoding.setObjctType(start, id, dimension);
    }
    @Inline
    public static void fillGap(Address addr) {
        addr.store(ALIGNMENT_VALUE);
    }
    @Inline
    public static Object initializeHeader(Address ptr) {
        int _type = HeaderEncoding.getType(ptr);
        if (_type == HeaderEncoding.TYPE_NULL)
            return null;
        if (_type == HeaderEncoding.TYPE_HANDLE) {
            return new Stream.Handle(HeaderEncoding.getHandle(ptr));
        }
        if (_type == HeaderEncoding.TYPE_ENUM) {
            return getEnumByHeader(ptr);
        }
        RVMType type = getClassByHeader(ptr);
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
    @Inline
    private static RVMArray getNDimensionArrayType(RVMType type, int dimension) {
        RVMArray ret = type.getArrayTypeForElementType();
        for (int i = 0; i < dimension - 1; i++) {
            ret = ret.getArrayTypeForElementType();
        }
        if (!ret.isInitialized()) {
            ret.resolve();
            ret.instantiate();
            ret.initialize();
        }
        return ret;
    }
    @Inline
    public static RVMType getInnerMostEleType(Class cls) {
        RVMType type = getType(cls);
        if (type.isClassType())
            return type;
        RVMArray array = type.asArray();
        return array.getInnermostElementType();
    }
    @Inline
    public static RVMType getClassByHeader(Address header) {
        int id = HeaderEncoding.getID(header);
        int dimension = HeaderEncoding.getDimension(header);
        RVMType type = Factory.query(id);
        assert(type != null);
        if (dimension == 0)
            return type;
        return getNDimensionArrayType(type, dimension);
    }
    @Inline
    public static Enum getEnumByHeader(Address header) {
        int id = HeaderEncoding.getID(header);
        int ordinal = HeaderEncoding.getOrdinal(header);
        return Factory.query(id, ordinal);
    }

    @Inline
    public static Address getArrayAddress(Object object) {
        return getObjectHeaderAddress(object).plus(ELEMENT_NUM_OFFSET + 8);
    }

    //see SpecializedScanMethod.fallback()
    @Inline
    public static int getAllReferences(Object object, Object[] refArray) {
        Address base = Magic.objectAsAddress(object);
        RVMType type = Magic.getObjectType(object);
        int[] offsets = type.getReferenceOffsets();
        int length;
        if (offsets == REFARRAY_OFFSET_ARRAY) {
            length = getArrayLength(object);
            for (int i = 0; i < length; i++) {
                Object obj = base.plus(i << 3).loadObjectReference().toObject();
                refArray[i] = obj;
            }
        } else {
            length = offsets.length;
            for (int i = 0; i < length; i++) {
                refArray[i] = base.plus(offsets[i]).loadObjectReference().toObject();
            }
        }
        return length;
    }
    @Inline
    public static AddressArray getAllReferenceSlots(Object object, AddressArray slots) {
        Address base = Magic.objectAsAddress(object);
        RVMType type = Magic.getObjectType(object);
        int[] offsets = type.getReferenceOffsets();
        if (offsets == REFARRAY_OFFSET_ARRAY) {
            int length = getArrayLength(object);
            for (int i = 0; i < length; i++) {
                slots.set(i, base.plus(i << 3));
            }
            slots.set(length, Address.zero());
        } else {
            for (int i = 0; i < offsets.length; i++) {
                slots.set(i, base.plus(offsets[i]));
            }
            slots.set(offsets.length, Address.zero());
        }
        return slots;
    }

    @Inline
    private static int getArrayLength(Object object) {
        return (int)getObjectHeaderAddress(object).plus(ELEMENT_NUM_OFFSET).loadLong();
    }
}
