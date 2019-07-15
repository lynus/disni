package intruder;

import com.ibm.disni.verbs.IbvMr;
import org.jikesrvm.runtime.Magic;
import org.mmtk.policy.SegregatedFreeListSpace;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;

import java.io.IOException;

public class LocalBuffer extends Buffer {
    private int rkey, lkey, limit;
    public  int pointer;
    private LocalBuffer nextBuffer;
    private boolean consumed;
    private int boundry;
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
    public void setLimit(int limit) {
        assert((limit & 7) == 0);
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

    public void setBoundry(int boundry) {
        this.boundry = boundry;
    }
    public int getBoundry() {
        return boundry;
    }
    @Inline
    public boolean reachBoundry() {
        if (boundry == 0)
            return false;
        return pointer >= boundry;
    }

    public void release() {
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

    public Address getJump() {
        assert((pointer & 7) == 0);
        Address ret = start.plus(pointer).loadAddress();
        pointer += 8;
        return ret;
    }
    public Address getJump1() {
        return start.plus(pointer - 8).loadAddress();
    }
    public Object getRoot() {
        assert((pointer & 7) == 0);
        //no need to update pointer
        return Magic.addressAsObject(start.plus(pointer + ObjectModel.REF_OFFSET));
    }
    public boolean inRange(Address jump) {
        return (jump.GE(start) && jump.LT(start.plus(length)));
    }
    public void setPointer(Address jump) {
        pointer = jump.diff(start).toInt();
    }

   
    public void peekBytes(int offset, int size, int logBytesPerLine) {
        Utils.peekBytes("local buffer", start, offset, size, logBytesPerLine);
    }
}
