package intruder;

import intruder.RPC.RPCService;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer, currentBuffer;

    public IntruderInStream(Endpoint ep) throws IOException {
        super(ep);
        RPCService.setHost(ep.serverHost);
        RPCService.register(this);
    }

    public Object readObject() throws IOException {
        while(currentBuffer == null) {}
        LocalBuffer.AddrBufferRet ret = LocalBuffer.getNextAddr(currentBuffer);
        currentBuffer = ret.getLocalBuffer();
        Address header = ret.getAddr();
        Utils.log("get header address: 0x" + Long.toHexString(header.toLong()));
        /*设定header
          VM 部分 TIB status（lock，hash status)
          GC 部分
        */
        Object root = ObjectModel.initializeHeader(header);
        AddressArray slots = ObjectModel.getAllReferenceSlots(root);
        Queue<Address> queue = new LinkedList<Address>();
        for (int i = 0; i < slots.length(); i++)
            queue.add(slots.get(i));
        while (queue.size() != 0) {
            Address slot = queue.remove();
            ret = LocalBuffer.getNextAddr(currentBuffer);
            currentBuffer = ret.getLocalBuffer();
            Object obj = ObjectModel.initializeHeader(ret.getAddr());
            slot.store(ObjectReference.fromObject(obj).toAddress().toLong());
            if (obj == null)
                continue;
            slots = ObjectModel.getAllReferenceSlots(obj);
            for (int i = 0; i < slots.length(); i++)
                queue.add(slots.get(i));
        }
        return root;
    }
    @Override
    public void close() {
    }

    public LocalBuffer getLocalBuffer() {
        LocalBuffer _buffer = new LocalBuffer();
        if (firstBuffer == null) {
            firstBuffer = _buffer;
            lastBuffer = _buffer;
            currentBuffer = _buffer;
        }
            lastBuffer.setNextBuffer(_buffer);
            lastBuffer = _buffer;
        return _buffer;
    }
    public LocalBuffer getLastBuffer() {
        return lastBuffer;
    }
}
