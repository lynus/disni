package intruder;

import com.ibm.disni.verbs.IbvMr;
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
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public LocalBuffer getNextBuffer() {
        return nextBuffer;
    }

    public void setNextBuffer(LocalBuffer buffer) {
        this.nextBuffer = buffer;
    }
    
    private boolean reachLimit() {
        if (pointer < limit)
            return false;
        else 
            return true;
    }
    public void markConsumed() {
        consumed = true;
    }
   
    public static AddrBufferRet getNextAddr(LocalBuffer buffer) {
        if (!buffer.reachLimit()) {
            return buffer.bumpPointer();
        }
        assert(buffer.pointer == buffer.limit);
        while(!buffer.consumed && buffer.reachLimit()) {}
        if (buffer.consumed) {
            //move to next buffer
            LocalBuffer nextBuffer = buffer.getNextBuffer();
            while (nextBuffer == null)
                nextBuffer = buffer.getNextBuffer();
            return getNextAddr(nextBuffer);
        }
        //new data has filled into this buffer
        return buffer.bumpPointer();
    }

    private AddrBufferRet bumpPointer() {
        assert((pointer & 7) == 0 || (pointer & 7) ==4);
        assert((pointer & 7) == 0 || (start.plus(pointer).loadInt() ==
              org.jikesrvm.objectmodel.JavaHeaderConstants.ALIGNMENT_VALUE));
        if ((pointer & 7) != 0)
            pointer += 4;
        AddrBufferRet ret = new AddrBufferRet(start.plus(pointer), this);
        if (HeaderEncoding.getHeaderEncoding(start.plus(pointer)).isNullType()) {
           pointer += 8;
        } else {
            int size = ObjectModel.getAlignedUpSize(ObjectModel.getClassByHeader(start.plus(pointer)), start.plus(pointer));
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
        public Address getAddr() {
            return addr;
        }
        public LocalBuffer getLocalBuffer() {
            return buffer;
        }
    }
}
