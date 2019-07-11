package intruder;

import com.ibm.disni.util.MemoryUtils;
import intruder.RPC.RPCClient;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;

import java.io.IOException;
import java.nio.ByteBuffer;

public class StageBuffer {
    private RemoteBuffer current, last;
    private RPCClient rpcClient;
    private ByteBuffer buffer;
    private int length, lkey;
    private Address start;
    private int head;
    private Endpoint ep;
    private RdmaClassIdManager idManager = new RdmaClassIdManager();
    public StageBuffer(RPCClient rpcClient, Endpoint ep) throws IOException {
        int size = Buffer.getBufferSize() * 2;
        buffer = ByteBuffer.allocateDirect(size);
        length = size;
        start = Address.fromLong(MemoryUtils.getAddress(buffer));
        lkey = ep.registerMemory(buffer).execute().free().getMr().getLkey();
        this.rpcClient = rpcClient;
        this.ep = ep;
        current = RemoteBuffer.reserveBuffer(rpcClient, ep);
        rpcClient.getRemoteTIB(idManager);
    }

    @Inline
    private int alignAlloc() {
        assert((head & ObjectModel.MIN_ALIGNMENT - 1) == 0);
        int ret = ObjectModel.alignObjectAllocation(head);
        if (ret != head) {
            ObjectModel.fillGap(start.plus(head));
        }
        return ret;
    }
    public Address reserveOneSlot() throws IOException{
        assert((head & 7) == 0);
        reserve(8);
        Address ret = start.plus(head);
        head += 8;
        return ret;
    }

    public void reserve(int size) throws IOException {
        if (current.freeSpace() < head + size) {
            assert(last == null);
            current.setLimit(head);
            last = current;
            current = RemoteBuffer.reserveBuffer(rpcClient, ep);
        }
    }

    public Address getRemoteAddress(int ptr) {
        return RemoteBuffer.getRemoteAddress(ptr, last, current);
    }
    public int fillRootMarker() throws IOException {
        reserve(8 + 4);
        int aligned = alignAlloc();
        start.plus(aligned).store(Stream.ROOTMARKER);
        head = aligned + 8;
        return aligned;
    }
    public Address fillEnum(Enum e) throws IOException {
        reserve(8 + 4);
        int aligned = alignAlloc();
        Address ret = RemoteBuffer.getRemoteAddress(aligned, last, current);
        int id = Factory.query(e.getClass());
        HeaderEncoding.setEnumType(start.plus(aligned), id, e.ordinal());
        head = aligned + 8;
        assert (head < length);
        return ret;
    }

    public Address fillObject(Object object) throws IOException {
        int size = ObjectModel.getAlignedUpSize(object) + 4;
        reserve(size);
        int aligned = alignAlloc();
        Address ret = RemoteBuffer.getRemoteAddress(aligned, last, current);
        ObjectModel.copyObject(object, start.plus(aligned));
//        ObjectModel.setRegisteredID(object, start.plus(aligned));
        ObjectModel.setRemoteTIB(idManager, object, start.plus(aligned));
        head = aligned + size;
        assert(head < length);
        return ret;
    }

    public Address fillHandle(Stream.Handle hanle) {
        return Address.zero();
    }

    public void mayFlush() throws IOException {
        if (last != null) {
            if (Utils.enableLog)
                Utils.log("last remmote buffer exists, start flushing");
            flush();
        }
    }

    public void flush() throws IOException {
        if (last != null) {
            assert (last.limit != 0);
            assert (head > last.limit - last.lastFlush);
            assert (current.lastFlush == 0);
            RemoteBuffer.writeTwoBuffer(ep, start, head, lkey,last, current);
            last = null;
        } else {
            assert(current.limit == 0);
            assert(head >= current.lastFlush);
            current.write(ep, start, head, lkey);
        }
        head = 0;
        Utils.zeroMemory(start, length);
    }
    public void printStats() {
        Utils.log("=========stage buffer stats========");
        Utils.log("current size: " + head + "left: " + (length - head));
        Utils.log("==================================");
    }

    public void peekBytes(int offset, int size) {
        Utils.peekBytes("ring buffer", start, offset, size, 3);
    }

    public void peekBytes() {
        peekBytes(0, head);
    }
}
