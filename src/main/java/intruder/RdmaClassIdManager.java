package intruder;

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.vmmagic.pragma.Inline;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RdmaClassIdManager{
    private ConcurrentHashMap<RVMType, Integer> classToIdMap = new ConcurrentHashMap<RVMType, Integer>();
    private ConcurrentHashMap<Integer, RVMType> idToClassMap = new ConcurrentHashMap<Integer, RVMType>();
    private AtomicInteger counter = new AtomicInteger();
    static public final int ARRAYTYPEMASK = 1 << 31;
    static public final int SCALARTYPEMASK = ~ARRAYTYPEMASK;
    static public final int ARRAYTYPE = 1 << 31;
    static public final int NONARRAYTYPE = 0;
    public int registerClass(Class cls) {
       Integer ret;
       String className = cls.getCanonicalName();
       System.out.println("registerClass get name: " + className);
       RVMType type = java.lang.JikesRVMSupport.getTypeForClass(cls);
       ret = classToIdMap.get(type);
       if (ret != null)
           return ret.intValue();

       ret = counter.getAndIncrement();
       classToIdMap.put(type, ret);
       idToClassMap.put(ret, type);

       //XXX assume a scalar type registered
        Utils.ensureClassInitialized((RVMClass)type);
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

    @Inline
    public static RVMType getRvmtype(Class cls) {
        return java.lang.JikesRVMSupport.getTypeForClass(cls);
    }
}
