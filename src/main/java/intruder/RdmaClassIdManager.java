package intruder;

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RdmaClassIdManager{
    private SimpleRVMTypeHashTable classToIdMap = new SimpleRVMTypeHashTable();
    private HashMap<Integer, RVMType> idToClassMap = new HashMap<Integer, RVMType>();
    private HashMap<Integer, Enum[]> idToEnumArray = new HashMap<Integer, Enum[]>();
    private AtomicInteger counter;
    static public final int ARRAYTYPEMASK = 1 << 31;
    static public final int SCALARTYPEMASK = ~ARRAYTYPEMASK;
    static public final int ARRAYTYPE = 1 << 31;
    static public final int NONARRAYTYPE = 0;
    public RdmaClassIdManager () {
        try {
            classToIdMap.put(RVMType.BooleanType, Integer.valueOf(0));
            classToIdMap.put(RVMType.ByteType, Integer.valueOf(1));
            classToIdMap.put(RVMType.ShortType, Integer.valueOf(2));
            classToIdMap.put(RVMType.IntType, Integer.valueOf(3));
            classToIdMap.put(RVMType.LongType, Integer.valueOf(4));
            classToIdMap.put(RVMType.FloatType, Integer.valueOf(5));
            classToIdMap.put(RVMType.DoubleType, Integer.valueOf(6));
            classToIdMap.put(RVMType.CharType, Integer.valueOf(7));
            classToIdMap.put(RVMType.JavaLangStringType, Integer.valueOf(8));
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        counter = new AtomicInteger(9);

        idToClassMap.put(Integer.valueOf(0), RVMType.BooleanType);
        idToClassMap.put(Integer.valueOf(1), RVMType.ByteType);
        idToClassMap.put(Integer.valueOf(2), RVMType.ShortType);
        idToClassMap.put(Integer.valueOf(3), RVMType.IntType);
        idToClassMap.put(Integer.valueOf(4), RVMType.LongType);
        idToClassMap.put(Integer.valueOf(5), RVMType.FloatType);
        idToClassMap.put(Integer.valueOf(6), RVMType.DoubleType);
        idToClassMap.put(Integer.valueOf(7), RVMType.CharType);
        idToClassMap.put(Integer.valueOf(8), RVMType.JavaLangStringType);
    }
    public int registerClass(Class cls) {
        int ret;
        String className = cls.getCanonicalName();
        if (Utils.enableLog)
            Utils.log("registerClass get name: " + className);
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        ret = classToIdMap.get(type);
        if (ret != -1)
            return ret;

        ret = counter.getAndIncrement();
        try {
            classToIdMap.put(type, ret);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
        idToClassMap.put(ret, type);
        //XXX assume a scalar type registered
        Utils.ensureClassInitialized((RVMClass)type);

        if (cls.isEnum()) {
            Method method = null;
            try {
                method = cls.getMethod("values");
                Enum[] array = (Enum[])method.invoke(null);
                idToEnumArray.put(ret, array);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return ret;
    }

    public int query(RVMType type) {
        return classToIdMap.get(type);
    }

    public RVMType query(int id) {
        return idToClassMap.get(id);
    }

    public Enum queryEnum(int id, int ordinal) {
        Enum[] array = idToEnumArray.get(id);
        assert(array != null);
        return array[ordinal];
    }
    @Inline
    public static RVMType getRvmtype(Class cls) {
        return java.lang.JikesRVMSupport.getTypeForClass(cls);
    }

    private static class SimpleRVMTypeHashTable {
        private final int SIZE = 512, MAXCOLI = 8;
        private final int KEY = 0, VALUE = 1;
        private int[][][] table = new int[SIZE][MAXCOLI][2];
        public void put(RVMType type, Integer id) throws Exception{
            int key = hash(type);
            int d1 = key & (SIZE - 1);
            int d2;
            for (d2 = 0; d2 < MAXCOLI; d2++) {
                if (table[d1][d2][KEY] == 0) {
                    table[d1][d2][KEY]= key;
                    table[d1][d2][VALUE]= id;
                    break;
                }
            }
            if (d2 == MAXCOLI) throw new Exception("MAXCOLISION");
        }
        public int get(RVMType type) {
            int key = hash(type);
            int d1 = key & (SIZE - 1);
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
