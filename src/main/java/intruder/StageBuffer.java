package intruder;

import com.ibm.disni.util.MemoryUtils;
import intruder.RPC.RPCClient;
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

    public Address reserveOneSlot() throws IOException{
        assert((head & 7) == 0);
        reserve(8);
        Address ret = start.plus(head);
        head += 8;
        return ret;
    }

    public void reserve(int size) throws IOException {
        if (last != null) {
            assert(current.freeSpace() > head + size - (last.boundry - last.lastFlush));
            return;
        }
        if (current.freeSpace() < head + size) {
            if (Utils.enableLog)
                Utils.log("reserve: current buffer cannot reserve head: " + head + " current address: "
                        + Long.toHexString(current.start.toLong()) + " free: " + current.freeSpace());
            current.setBoundry(head);
            last = current;
            current = RemoteBuffer.reserveBuffer(rpcClient, ep);
        }
    }

    public Address getRemoteAddress(int ptr) {
        return RemoteBuffer.getRemoteAddress(ptr, last, current);
    }
    public int fillRootMarker() throws IOException {
        reserve(8);
        start.plus(head).store(Stream.ROOTMARKER);
        if (IntruderOutStream.debug)
            Utils.log("fillRootMark head: " + head + " remote addr: 0x" +
                    Long.toHexString(RemoteBuffer.getRemoteAddress(head, last, current).toLong()));

        int ret = head;
        head += 8;
        return ret;
    }

    public Address fillObject(Object object, AddressArray tworet) throws IOException {
        int size = ObjectModel.getAlignedUpSize(object);
        reserve(size);
        Address ret = RemoteBuffer.getRemoteAddress(head + ObjectModel.REF_OFFSET, last, current);
        Address stageAddr = start.plus(head + ObjectModel.REF_OFFSET);
        tworet.set(0, stageAddr);
        ObjectModel.copyObject(object, start.plus(head));
//        ObjectModel.setRegisteredID(object, start.plus(aligned));
        ObjectModel.setRemoteTIB(idManager, object, start.plus(head));
        head += size;
        assert(head < length);
        return ret;
    }

    public Address fillHandle(Stream.Handle hanle) {
        return Address.zero();
    }

    public Address queryEnumRemoteAddress(Enum e) {
        return idManager.queryEnumRemoteAddress(e);
    }

    public void mayFlush() throws IOException {
        if (last != null) {
            flush();
        }
    }

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
