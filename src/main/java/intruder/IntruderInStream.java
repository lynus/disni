package intruder;

import intruder.RPC.RPCService;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer, currentBuffer;
    private HashMap<Integer, Object> int2ObjectMap = new HashMap<Integer, Object>();
    private int readItem = 0;
    private volatile boolean finish = false;
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
//        Utils.log("get header address: 0x" + Long.toHexString(header.toLong()));
        /*设定header
          VM 部分 TIB status（lock，hash status)
          GC 部分
        */
        Object root = ObjectModel.initializeHeader(header);
        if (root.getClass() == Handle.class) {
            root = int2ObjectMap.get(((Handle) root).index);
            assert(root != null);
            return root;
        }
        if (root.getClass().isEnum()) {
            return root;
        }
        int2ObjectMap.put(readItem, root);
        readItem++;
        AddressArray slots = ObjectModel.getAllReferenceSlots(root);
        Queue<Address> queue = new LinkedList<Address>();
        for (int i = 0; i < slots.length(); i++)
            queue.add(slots.get(i));
        while (queue.size() != 0) {
            Address slot = queue.remove();
            ret = LocalBuffer.getNextAddr(currentBuffer);
            currentBuffer = ret.getLocalBuffer();
            Object item = ObjectModel.initializeHeader(ret.getAddr());
            Object obj = null;
            if (item != null) {
                if (item.getClass() == Handle.class) {
                    obj = int2ObjectMap.get(((Handle) item).index);
                    if (obj == null)
                        Utils.log("null obj, readItem: " + readItem);
                    assert (obj != null);
                } else if (item.getClass().isEnum()) {
                    obj = item;
                } else {
                    obj = item;
                    int2ObjectMap.put(readItem, obj);
                }
            }
            readItem++;
            slot.store(ObjectReference.fromObject(obj).toAddress().toLong());
            if (obj == null || item.getClass() == Handle.class || item.getClass().isEnum())
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

    public void setFinish() {
        finish = true;
    }
    public void setUnfinish() {
        finish = false;
    }
    public boolean isFinish() {
        return finish;
    }
}
