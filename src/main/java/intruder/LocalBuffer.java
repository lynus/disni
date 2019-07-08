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
    private boolean reachLimit() {
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
   
    public static AddrBufferRet getNextAddr(LocalBuffer buffer, AddrBufferRet ret) {
        if (!buffer.reachLimit()) {
            return buffer.bumpPointer(ret);
        }
        assert(buffer.pointer == buffer.limit);
        while(!buffer.consumed && buffer.reachLimit()) {}
        if (buffer.consumed) {
            //move to next buffer
            LocalBuffer nextBuffer = buffer.getNextBuffer();
            while (nextBuffer == null)
                nextBuffer = buffer.getNextBuffer();
            return getNextAddr(nextBuffer, ret);
        }
        //new data has filled into this buffer
        return buffer.bumpPointer(ret);
    }

    private AddrBufferRet bumpPointer(AddrBufferRet ret) {
        assert((pointer & 7) == 0 || (pointer & 7) ==4);
        assert((pointer & 7) == 0 || (start.plus(pointer).loadInt() ==
              org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_VALUE));
        if ((pointer & 7) != 0)
            pointer += 4;
        Address header = start.plus(pointer);
        ret.set(header, this);
        if (HeaderEncoding.isNoneObjectType(header)) {
            pointer += 8;
        } else {
            assert (HeaderEncoding.isObjectType(header));
            int size = ObjectModel.getAlignedUpSize(ObjectModel.getClassByHeader(header), header);
            pointer += size;
        }
        assert(pointer <= limit);
        return ret;
    }

    public void peekBytes(int offset, int size, int logBytesPerLine) {
        Utils.peekBytes("local buffer", start, offset, size, logBytesPerLine);
    }
    static class AddrBufferRet {
        private Address addr;
        private LocalBuffer buffer;
        public AddrBufferRet(Address a, LocalBuffer b) {
            this.addr = a;
            this.buffer = b;
        }
        public AddrBufferRet() {
        }
        @Inline
        public Address getAddr() {
            return addr;
        }
        @Inline
        public LocalBuffer getLocalBuffer() {
            return buffer;
        }
        @Inline
        public void set(Address addr, LocalBuffer buffer) {
            this.addr = addr;
            this.buffer = buffer;
        }
    }
}
