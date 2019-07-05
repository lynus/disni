package intruder;

import org.mmtk.plan.Plan;
import org.mmtk.policy.SegregatedFreeListSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.BlockAllocator;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;

import java.io.IOException;
import java.lang.reflect.Field;

public class Buffer {
    private static int MIN_ALIGN;
    private static Space space = Plan.nonMovingSpace;
    protected Address start;
    protected Extent length;
    protected Offset limit = Offset.fromIntSignExtend(-1);
    static {
        try {
            Field f = BlockAllocator.class.getField("LOG_MIXED_BLOCK");
            MIN_ALIGN = 1 << f.getInt(null);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public static Buffer allocate(Buffer buffer) throws IOException {
        Address region = allocateBlock();
        if (region.isZero())
            throw new IOException("region returned zero.");
        buffer.start = region;
        buffer.length = Extent.fromIntZeroExtend(MIN_ALIGN);
        if (Utils.enableLog)
            Utils.log("get buffer start: "+region.toLong()+" log size:(k) "+ (buffer.length.toInt() >> 10));
        return buffer;
    }

    public Address getStart() {
        return start;
    }

    public Extent getLength() {
        return length;
    }

    private static Address allocateBlock() {
        return ((SegregatedFreeListSpace)space).allocMixedBlock();
    }

    public static int getBufferSize(){
        return MIN_ALIGN;
    }
}
