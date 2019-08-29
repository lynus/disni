package intruder;

import com.ibm.disni.util.MemoryUtils;
import intruder.RPC.RPCClient;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

import java.io.IOException;
import java.nio.ByteBuffer;

public class StageBuffer {
    private final int bufferNum = 8;
    private RemoteBuffer current, last;
    private RPCClient rpcClient;
    private ByteBuffer buffer[] = new ByteBuffer[bufferNum];
    private int _lkeys[] = new int[bufferNum];
    private int bufferIndex = 0;
    private int length, lkey;
    private Address start;
    private int head;
    private Endpoint ep;
    private RdmaClassIdManager idManager = new RdmaClassIdManager();
    public StageBuffer(RPCClient rpcClient, Endpoint ep) throws IOException {
        int size = Buffer.getBufferSize() * 2;
        for (int i = 0; i < bufferNum; i++) {
            buffer[i] = ByteBuffer.allocateDirect(size);
            _lkeys[i] = ep.registerMemory(buffer[i]).execute().free().getMr().getLkey();
        }
        length = size;
        start = Address.fromLong(MemoryUtils.getAddress(buffer[bufferIndex]));
        lkey = _lkeys[bufferIndex];
        this.rpcClient = rpcClient;
        this.ep = ep;
        current = RemoteBuffer.reserveBuffer(rpcClient, ep);
        rpcClient.getRemoteTIB(idManager);
        rpcClient.getRemoteEnum(idManager);
    }
    @Inline
    public Address reserveOneSlot() throws IOException{
//        if ((head & 7) != 0)
//            Utils.log("head " + head);
        assert((head & 7) == 0);
        return reserve(8);
    }
    @Inline
    public Address reserve(int size) throws IOException {
        if (last != null) {
            assert(current.freeSpace() > head + size - (last.boundry - last.lastFlush));
        } else {
            if (current.freeSpace() < head + size) {
                if (Utils.warming) {
                    head = 0;
                }
                if (Utils.enableLog)
                    Utils.log("reserve: current buffer cannot reserve head: " + head + " current address: "
                            + Long.toHexString(current.start.toLong()) + " free: " + current.freeSpace()
                            + " size: " + size);
                current.setBoundry(head);
                last = current;
                current = RemoteBuffer.reserveBuffer(rpcClient, ep);

            }
        }
        Address ret = start.plus(head);
        head += size;
        return ret;
    }
    @Inline
    public Address getRemoteAddress(int ptr) {
        return RemoteBuffer.getRemoteAddress(ptr, last, current);
    }
    @Inline
    public Address getRemoteAddress(Address ptr) {
        return RemoteBuffer.getRemoteAddress(ptr.diff(start).toInt(), last, current);
    }
    @Inline
    public int fillRootMarker() throws IOException {
        Address mark = reserve(8);
        mark.store(Stream.ROOTMARKER);
        if (IntruderOutStream.debug)
            Utils.log("fillRootMark head: " + (head - 8) + " remote addr: 0x" +
                    Long.toHexString(RemoteBuffer.getRemoteAddress(mark.diff(start).toInt(), last, current).toLong()));

        return mark.diff(start).toInt();
    }

    public Address fillObject(Object object, AddressArray tworet) throws IOException {
        int size = ObjectModel.getAlignedUpSize(object);
        Address header = reserve(size);
        ObjectModel.copyObject(object, header);
        setRemoteTIB(object.getClass(), header);
        header = header.plus(ObjectModel.REF_OFFSET);
        Address ret = getRemoteAddress(header);
        tworet.set(0, header);
        assert(head < length);
        return ret;
    }

    public void setRemoteTIB(Class cls, Address start) {
        ObjectModel.setRemoteTIB(idManager, cls, start);
    }
    @Inline
    public Address fillHandle(Stream.Handle hanle) {
        return Address.zero();
    }
    @Inline
    public Address queryEnumRemoteAddress(Enum e) {
        return idManager.queryEnumRemoteAddress(e);
    }
    @Inline
    public void mayFlush() throws IOException {
        if (last != null) {
            flush();
        }
    }
    @Inline
    public void flush() throws IOException {
        if (last != null) {
            assert (last.boundry != 0);
            assert (head > last.boundry - last.lastFlush);
            assert (current.lastFlush == 0);
            RemoteBuffer.writeTwoBuffer(ep, start, head, lkey,last, current);
            last = null;
        } else {
            assert(current.boundry == 0);
            current.write(ep, start, head, lkey);
        }
        head = 0;
//        Utils.zeroMemory(start, length);
        bufferIndex = (bufferIndex + 1) % bufferNum;
        start = Address.fromLong(MemoryUtils.getAddress(buffer[bufferIndex]));
        lkey = _lkeys[bufferIndex];
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
