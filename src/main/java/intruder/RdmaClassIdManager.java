package intruder;

import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.vmmagic.pragma.Inline;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RdmaClassIdManager{
    private ConcurrentHashMap<RVMType, Integer> classToIdMap = new ConcurrentHashMap<RVMType, Integer>();
    private ConcurrentHashMap<Integer, RVMType> idToClassMap = new ConcurrentHashMap<Integer, RVMType>();
    private ConcurrentHashMap<Integer, Enum[]> idToEnumArray = new ConcurrentHashMap<Integer, Enum[]>();
    private AtomicInteger counter;
    static public final int ARRAYTYPEMASK = 1 << 31;
    static public final int SCALARTYPEMASK = ~ARRAYTYPEMASK;
    static public final int ARRAYTYPE = 1 << 31;
    static public final int NONARRAYTYPE = 0;
    public RdmaClassIdManager () {
        classToIdMap.put(RVMType.BooleanType, Integer.valueOf(0));
        classToIdMap.put(RVMType.ByteType, Integer.valueOf(1));
        classToIdMap.put(RVMType.ShortType, Integer.valueOf(2));
        classToIdMap.put(RVMType.IntType, Integer.valueOf(3));
        classToIdMap.put(RVMType.LongType, Integer.valueOf(4));
        classToIdMap.put(RVMType.FloatType, Integer.valueOf(5));
        classToIdMap.put(RVMType.DoubleType, Integer.valueOf(6));
        classToIdMap.put(RVMType.CharType, Integer.valueOf(7));
        classToIdMap.put(RVMType.JavaLangStringType, Integer.valueOf(8));
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
        Integer ret;
        String className = cls.getCanonicalName();
        Utils.log("registerClass get name: " + className);
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        ret = classToIdMap.get(type);
        if (ret != null)
            return ret.intValue();

        ret = counter.getAndIncrement();
        classToIdMap.put(type, ret);
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

    public int query(Class cls) {
        Integer ret;
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
        if (type == null)
            return -1;
        ret = classToIdMap.get(type);
        if (ret != null)
            return ret.intValue();
        else
            return -1;
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
}
