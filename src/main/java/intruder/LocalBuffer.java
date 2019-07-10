package intruder;

import com.ibm.disni.verbs.IbvMr;
import org.mmtk.policy.SegregatedFreeListSpace;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;

import java.io.IOException;

public class LocalBuffer extends Buffer {
    private int rkey, lkey, limit, pointer;
    private LocalBuffer nextBuffer;
    private boolean consumed;
    public void register(Endpoint ep) throws IOException {
        IbvMr mr = ep.registerMemory(start.toLong(), length.toInt()).execute().free().getMr();
        rkey = mr.getRkey();
        lkey = mr.getLkey();
    }
    public int getLkey() {
        return lkey;
    }
    public int getRkey() {
        return rkey;
    }
    public void setLimit(int limit, boolean needGap) {
        if (needGap) {
            assert((limit & 7) == 4);
            start.plus(limit).store(org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_VALUE);
        }
        this.limit = limit;
    }

    public LocalBuffer getNextBuffer() {
        return nextBuffer;
    }

    public void setNextBuffer(LocalBuffer buffer) {
        this.nextBuffer = buffer;
    }
    @Inline
    public boolean reachLimit() {
        return pointer >= limit;
    }
    public void markConsumed() {
        consumed = true;
    }
    public void release() {
        if (Utils.enableLog)
            Utils.log("Localbuffer release addr: 0x " + Long.toHexString(start.toLong()));
        nextBuffer = null;
        ((SegregatedFreeListSpace)space).releaseMixedBlock(start);
    }

    public long getMarker() {
        assert((pointer & 7) == 0);
        long ret = start.plus(pointer).loadLong();
        pointer += 8;
        return ret;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public Address getJump() {
        assert((pointer & 7) == 0);
        Address ret = start.plus(pointer).loadAddress();
        pointer += 8;
        return ret;
    }
    public Object getRoot() {
        assert((pointer & 7) == 0);
        //no need to update pointer
        return start.plus(pointer + ObjectModel.REF_OFFSET).loadObjectReference().toObject();
    }
    public boolean inRange(Address jump) {
        return (jump.GE(start) && jump.LT(start.plus(length)));
    }
    public void setPointer(Address jump) {
        pointer = jump.diff(jump).toInt();
    }

   
    public void peekBytes(int offset, int size, int logBytesPerLine) {
        Utils.peekBytes("local buffer", start, offset, size, logBytesPerLine);
    }
}
