package intruder;

import com.ibm.disni.util.MemoryUtils;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Offset;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

public class RdmaClassIdManager{
    private SimpleRVMTypeHashTable typeToRemoteTIB = new SimpleRVMTypeHashTable();
    private SimpleRVMTypeHashTable enumToRemoteAddress = new SimpleRVMTypeHashTable();
    private ByteBuffer remoteEnumAddressBuffer = ByteBuffer.allocateDirect(4096);
    private static SimpleRVMTypeHashTable classToIdMap = new SimpleRVMTypeHashTable();
    private static RVMType[] idToClassMap = new RVMType[512];
    private static RVMType[] idToEnumType = new RVMType[32];
    private static AddressArray[] enumAddresses = new AddressArray[32];
    private static long[] tibs = new long[512];
    private static AtomicInteger counter;
    private static int enumCounter = 0;
    static public final int ARRAYTYPEMASK = 1 << 31;
    static public final int SCALARTYPEMASK = ~ARRAYTYPEMASK;
    static public final int ARRAYTYPE = 1 << 31;
    static public final int NONARRAYTYPE = 0;
    static {
        try {
            classToIdMap.put(ObjectModel.getType(boolean[].class), Integer.valueOf(0));
            classToIdMap.put(ObjectModel.getType(byte[].class), Integer.valueOf(1));
            classToIdMap.put(ObjectModel.getType(short[].class), Integer.valueOf(2));
            classToIdMap.put(ObjectModel.getType(int[].class), Integer.valueOf(3));
            classToIdMap.put(ObjectModel.getType(long[].class), Integer.valueOf(4));
            classToIdMap.put(ObjectModel.getType(float[].class), Integer.valueOf(5));
            classToIdMap.put(ObjectModel.getType(double[].class), Integer.valueOf(6));
            classToIdMap.put(ObjectModel.getType(char[].class), Integer.valueOf(7));
            classToIdMap.put(ObjectModel.getType(Boolean.class), Integer.valueOf(8));
            classToIdMap.put(ObjectModel.getType(Byte.class), Integer.valueOf(9));
            classToIdMap.put(ObjectModel.getType(Short.class), Integer.valueOf(10));
            classToIdMap.put(ObjectModel.getType(Integer.class), Integer.valueOf(11));
            classToIdMap.put(ObjectModel.getType(Long.class), Integer.valueOf(12));
            classToIdMap.put(ObjectModel.getType(Float.class), Integer.valueOf(13));
            classToIdMap.put(ObjectModel.getType(Double.class), Integer.valueOf(14));
            classToIdMap.put(ObjectModel.getType(Character.class), Integer.valueOf(15));
            classToIdMap.put(ObjectModel.getType(String.class), Integer.valueOf(16));
            classToIdMap.put(ObjectModel.getType(Object.class), Integer.valueOf(17));
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        counter = new AtomicInteger(18);

        idToClassMap[0] = ObjectModel.getType(boolean[].class);
        idToClassMap[1] = ObjectModel.getType(byte[].class);
        idToClassMap[2] = ObjectModel.getType(short[].class);
        idToClassMap[3] = ObjectModel.getType(int[].class);
        idToClassMap[4] = ObjectModel.getType(long[].class);
        idToClassMap[5] = ObjectModel.getType(float[].class);
        idToClassMap[6] = ObjectModel.getType(double[].class);
        idToClassMap[7] = ObjectModel.getType(char[].class);
        idToClassMap[8] = ObjectModel.getType(Boolean.class);
        idToClassMap[9] = ObjectModel.getType(Byte.class);
        idToClassMap[10] = ObjectModel.getType(Short.class);
        idToClassMap[11] = ObjectModel.getType(Integer.class);
        idToClassMap[12] = ObjectModel.getType(Long.class);
        idToClassMap[13] = ObjectModel.getType(Float.class);
        idToClassMap[14] = ObjectModel.getType(Double.class);
        idToClassMap[15] = ObjectModel.getType(Character.class);
        idToClassMap[16] = ObjectModel.getType(String.class);
        idToClassMap[17] = ObjectModel.getType(Object.class);

        for (int i = 0; i < counter.get(); i++)
            tibs[i] = Magic.objectAsAddress(idToClassMap[i].getTypeInformationBlock()).toLong();

    }
    public static int registerClass(Class cls) {
        int ret;
        String className = cls.getCanonicalName();
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        ret = (int)classToIdMap.get(type);
        if (ret != -1)
            return ret;

        ret = counter.getAndIncrement();
        try {
            classToIdMap.put(type, ret);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        idToClassMap[ret] = type;
        Utils.ensureClassInitialized(type);
        tibs[ret] = Magic.objectAsAddress(type.getTypeInformationBlock()).toLong();
        assert(tibs[ret] != 0L);
        if (cls.isEnum()) {
            idToEnumType[enumCounter] = ObjectModel.getType(cls);
            Method method = null;
            try {
                method = cls.getMethod("values");
                Enum[] array = (Enum[])method.invoke(null);
                AddressArray enumRefs = AddressArray.create(array.length);
                for (int i = 0; i < enumRefs.length(); i++) {
                    enumRefs.set(i, Magic.objectAsAddress(array[i]));
                }
                enumAddresses[enumCounter] = enumRefs;
                enumCounter++;
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return ret;
    }

    public static int query(RVMType type) {
        return (int)classToIdMap.get(type);
    }

    public static RVMType query(int id) {
        return idToClassMap[id];
    }

    public long queryTIB(RVMType type) {
        return typeToRemoteTIB.get(type);
    }
    public static Enum queryEnum(int id, int ordinal) {
        return null;
    }
    public Address queryEnumRemoteAddress(Enum obj) {
        RVMType enumType = ObjectModel.getType(obj.getDeclaringClass());
        Address base = Address.fromLong(enumToRemoteAddress.get(enumType));
        return base.plus(obj.ordinal() * 8).loadAddress();
    }


    public void installRemoteTIB(long[] tibs) {
        assert(counter.get() == tibs.length);
        try {
            for (int i = 0; i < tibs.length; i++) {
                typeToRemoteTIB.put(idToClassMap[i], tibs[i]);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public void installRemoteEnum(AddressArray[] enumAddresses) {
        assert (enumCounter == enumAddresses.length);
        Address base = Address.fromLong(MemoryUtils.getAddress(remoteEnumAddressBuffer));
        Offset ptr = Offset.zero();
        try {
            for (int i = 0; i < enumCounter; i++) {
                long start = base.plus(ptr).toLong();
                for (int j = 0; j < enumAddresses[i].length(); j++) {
                    base.store(enumAddresses[i].get(j), ptr);
                    ptr = ptr.plus(8);
                }
                ptr = ptr.plus(8);
                enumToRemoteAddress.put(idToEnumType[i], start);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public static long[] getTibs() {
        return tibs;
    }

    public static AddressArray[] getEnumAddresses() {
        return enumAddresses;
    }
    public static int getEnumCounter() {
        return enumCounter;
    }
    public static int getCount() {
        return counter.get();
    }
    @Inline
    public static RVMType getRvmtype(Class cls) {
        return java.lang.JikesRVMSupport.getTypeForClass(cls);
    }

    private static class SimpleRVMTypeHashTable {
        private final int SIZE = 512, MAXCOLI = 8;
        private final int KEY = 0, VALUE = 1;
        private long[][][] table = new long[SIZE][MAXCOLI][2];

        public void put(RVMType type, long value) throws Exception {
            long key = Magic.objectAsAddress(type).toLong();
            int d1 = (int)key & (SIZE - 1);
            int d2;
            for (d2 = 0; d2 < MAXCOLI; d2++) {
                if (table[d1][d2][KEY] == 0) {
                    table[d1][d2][KEY]= key;
                    table[d1][d2][VALUE]= value;
                    break;
                }
//                if (Utils.enableLog)
//                    Utils.log("hashtable collision! " + type.getDescriptor() +
//                            " with " + ((RVMType)Magic.addressAsObject(Address.fromLong(table[d1][d2][KEY]))).getDescriptor());
            }
            if (d2 == MAXCOLI) throw new Exception("MAXCOLISION");
        }
        public long get(RVMType type) {
            long key = Magic.objectAsAddress(type).toLong();
            int d1 = (int)key & (SIZE - 1);
            for (int d2 = 0; d2 < MAXCOLI; d2++) {
                if (table[d1][d2][KEY] == 0) {
                    return -1;
                }
                if (table[d1][d2][KEY] == key) {
                    return table[d1][d2][VALUE];
                }
            }
            return -1;
        }

        private int hash(RVMType type) {
            return Magic.objectAsAddress(type).toInt();
        }
    }
}
