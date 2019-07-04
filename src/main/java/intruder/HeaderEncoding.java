package intruder;

import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class HeaderEncoding {
    public static final int TYPE_OBJECT = 0;
    public static final int TYPE_NULL = 1;
    public static final int TYPE_HANDLE = 2;
    public static final int TYPE_ENUM = 4;

    @Inline
    public static int getType(Address addr) {
        long value = addr.loadLong();
        return (int)(value >> 32);
    }
    @Inline
    public static int getID(Address addr) {
        long value = addr.loadLong();
        return (int)(value & ((1L << 24) - 1));
    }
    @Inline
    public static int getDimension(Address addr) {
        long value = addr.loadLong();
        return (int)((value >> 24) & (255L));
    }
    @Inline
    public static  int getHandle(Address addr) {
        long value = addr.loadLong();
        return (int)(value & ((1L << 32) - 1));
    }
    @Inline
    public static int getOrdinal(Address addr) {
        return getDimension(addr);
    }
    @Inline
    public static void setObjctType(Address addr, int id, int dimension) {
        long value = (long)((dimension << 24) | id) | ((long)TYPE_OBJECT) << 32;
        addr.store(value);
    }
    @Inline
    public static void setNullType(Address addr) {
        long value = ((long)TYPE_NULL) << 32;
        addr.store(value);

    }
    @Inline
    public static void setHandleType(Address addr, int handle) {
        long value = ((long)TYPE_HANDLE) << 32;
        value |= handle;
        addr.store(value);
    }

    @Inline
    public static void setEnumType(Address addr, int id, int ordinal) {
        long value = (long)((ordinal << 24) | id) | ((long)TYPE_ENUM) << 32;
        addr.store(value);
    }
    @Inline
    public static boolean isNullType(Address addr) {
        return getType(addr) == TYPE_NULL;
    }
    @Inline
    public static boolean isHandleType(Address addr) {
        return getType(addr) == TYPE_HANDLE;
    }
    @Inline
    public static boolean isEnumType(Address addr) {
        return getType(addr) == TYPE_ENUM;
    }

    @Inline
    public static boolean isObjectType(Address addr) {
        return getType(addr) == TYPE_OBJECT;
    }
    @Inline
    public static boolean isNoneObjectType(Address addr) {
        return (getType(addr) & 7) != 0;
    }
}
