package intruder;

import org.vmmagic.unboxed.Address;

public class HeaderEncoding {
    public static final int TYPE_OBJECT = 0;
    public static final int TYPE_NULL = 1;
    public static final int TYPE_HANDLE = 2;
    public static final int TYPE_ENUM = 3;
    Address address;
    long value;
    public HeaderEncoding(Address address) {
        this.address = address;
        value = address.loadLong();
    }
    public static HeaderEncoding getHeaderEncoding(Address address) {
        return new HeaderEncoding(address);
    }

    public int getType() {
        return (int)(value >> 32);
    }
    public int getID() {
        return (int)(value & ((1L << 24) - 1));
    }

    public int getDimension() {
        return (int)((value >> 24) & (255L));
    }

    public int getHandle() {
        return (int)(value & ((1L << 32) - 1));
    }
    public int getOrdinal() {
        return getDimension();
    }
    public void setObjctType(int id, int dimension) {
        value = (long)((dimension << 24) | id) | ((long)TYPE_OBJECT) << 32;
        address.store(value);
    }

    public void setNullType() {
        value = ((long)TYPE_NULL) << 32;
        address.store(value);

    }
    public void setHandleType(int handle) {
        value = ((long)TYPE_HANDLE) << 32;
        value |= handle;
        address.store(value);
    }

    public void setEnumType(int id, int ordinal) {
        value = (long)((ordinal << 24) | id) | ((long)TYPE_ENUM) << 32;
        address.store(value);
    }

    public boolean isNullType() {
        return getType() == TYPE_NULL;
    }

    public boolean isHandleType() {
        return getType() == TYPE_HANDLE;
    }
    public boolean isEnumType() {
        return getType() == TYPE_ENUM;
    }
}
