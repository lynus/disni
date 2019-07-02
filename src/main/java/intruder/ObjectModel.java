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
import org.vmmagic.pragma.Interruptible;
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
    @Interruptible
    public static RVMType getType(Class cls) {
        return java.lang.JikesRVMSupport.getTypeForClass(cls);
    }

    public static Address getObjectHeaderAddress(Object obj) {
        return Magic.objectAsAddress(obj).minus(REF_OFFSET);
    }
    @Interruptible
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
    @Interruptible
    public static int getAlignedUpSize(Class cls, Address header) {
        int size = getInstanceSize(cls, header);
        return org.jikesrvm.runtime.Memory.alignUp(size, MIN_ALIGNMENT);
    }
    @Interruptible
    public static int getAlignedUpSize(Object obj) {
        return getAlignedUpSize(obj.getClass(), getObjectHeaderAddress(obj));
    }
    @Interruptible
    public static int getMaximumAlignedSize(Object obj) {
        //for x86-64, alignment is 8, MIN_ALIGNMENT is 4.
        //see org.mmtk.utility.alloc.Allocator.getMaximumAlignedSize()
        return getAlignedUpSize(obj) + 8 - 4;
    }

    public static int alignObjectAllocation(int head) {
        int delta = (-head - OBJECT_OFFSET_ALIGN) & ALIGNMENT_MASK;
        return head + delta;
    }
    //skip over header
    @Interruptible
    public static void copyObject(Object object, Address start) {
        Memory.memcopy(start.plus(HEADER_SIZE), getObjectHeaderAddress(object).plus(HEADER_SIZE), getAlignedUpSize(object) - HEADER_SIZE);
    }
    // -1 for primitives; 0 for classes
    @Interruptible
    private static int getDimension(Object object) {
        RVMType type = getType(object.getClass());
        return type.getDimensionality();
    }
    @Interruptible
    public static void setRegisteredID(Object object, Address start) {
        int id = Factory.query(object.getClass());
        if (id == -1)
            Utils.log("setRegister class: " + object.getClass().getCanonicalName());
        assert(id != -1);
        int dimension = getDimension(object);
        HeaderEncoding.getHeaderEncoding(start).setObjctType(id, dimension);
    }
    public static void fillGap(Address addr) {
        addr.store(ALIGNMENT_VALUE);
    }
    @Interruptible
    public static Object initializeHeader(Address ptr) {
        HeaderEncoding he  = HeaderEncoding.getHeaderEncoding(ptr);
        if (he.isNullType())
            return null;
        if (he.isHandleType()) {
            return new Stream.Handle(he.getHandle());
        }
        if (he.isEnumType()) {
            return getEnumByHeader(he);
        }
        Class cls = getClassByHeader(ptr);
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
    @Interruptible
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
    @Interruptible
    public static Class getInnerMostEleType(Class cls) {
        RVMType type = getType(cls);
        if (type.isClassType())
            return cls;
        RVMArray array = type.asArray();
        return array.getInnermostElementType().getClassForType();
    }
    @Interruptible
    public static Class getClassByHeader(Address header) {
        HeaderEncoding he = new HeaderEncoding(header);
        int id = he.getID();
        int dimension = he.getDimension();
        Class cls = Factory.query(id);
        assert(cls != null);
        if (dimension == 0)
            return cls;
        return getNDimensionArrayType(getType(cls), dimension).getClassForType();
    }
    @Interruptible
    public static Enum getEnumByHeader(HeaderEncoding he) {
        int id = he.getID();
        int ordinal = he.getOrdinal();
        return Factory.query(id, ordinal);
    }

    public static Address getArrayAddress(Object object) {
        return getObjectHeaderAddress(object).plus(ELEMENT_NUM_OFFSET + 8);
    }

    //see SpecializedScanMethod.fallback()
    @Interruptible
    public static Object[] getAllReferences(Object object) {
        Address base = Magic.objectAsAddress(object);
        RVMType type = getType(object.getClass());
        Object[] ret;
        int[] offsets = type.getReferenceOffsets();
        if (offsets == REFARRAY_OFFSET_ARRAY) {
            ret = new Object[getArrayLength(object)];
            for (int i = 0; i < getArrayLength(object); i++) {
                Object obj = base.plus(i << 3).loadObjectReference().toObject();
                ret[i] = obj;
            }
        } else {
            ret = new Object[offsets.length];
            for (int i = 0; i < offsets.length; i++) {
                ret[i] = base.plus(offsets[i]).loadObjectReference().toObject();
            }
        }
        return ret;
    }
    @Interruptible
    public static AddressArray getAllReferenceSlots(Object object) {
        Address base = Magic.objectAsAddress(object);
        RVMType type = getType(object.getClass());
        AddressArray ret;
        int[] offsets = type.getReferenceOffsets();
        if (offsets == REFARRAY_OFFSET_ARRAY) {
            ret = AddressArray.create(getArrayLength(object));
            for (int i = 0; i < getArrayLength(object); i++) {
                ret.set(i, base.plus(i << 3));
            }
        } else {
            ret = AddressArray.create(offsets.length);
            for (int i = 0; i < offsets.length; i++) {
                ret.set(i, base.plus(offsets[i]));
            }
        }
        return ret;
    }

    private static int getArrayLength(Object object) {
        return (int)getObjectHeaderAddress(object).plus(ELEMENT_NUM_OFFSET).loadLong();
    }
}
