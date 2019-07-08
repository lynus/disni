package intruder;

import intruder.RPC.RPCService;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;

import java.io.IOException;
import java.util.HashMap;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer, currentBuffer;
    public LocalBuffer retireBuffer;
    private HashMap<Integer, Object> int2ObjectMap = new HashMap<Integer, Object>();
    private int readItem = 0;
    private volatile boolean finish = false;
    private boolean useHandle = false;
    private LocalBuffer.AddrBufferRet _ret = new LocalBuffer.AddrBufferRet();
    private AddressArray slots = AddressArray.create(64);
    private intruder.Queue queue = new intruder.Queue(512);
    public IntruderInStream(Endpoint ep) throws IOException {
        super(ep);
        RPCService.setHost(ep.serverHost);
        RPCService.register(this);
    }

    public Object readObject() throws IOException {
        while(currentBuffer == null) {}
        LocalBuffer.getNextAddr(currentBuffer, _ret);
        currentBuffer = _ret.getLocalBuffer();
        Address header = _ret.getAddr();
//        Utils.log("get header address: 0x" + Long.toHexString(header.toLong()));
        /*设定header
          VM 部分 TIB status（lock，hash status)
          GC 部分
        */
        Object root = ObjectModel.initializeHeader(header);
        if (useHandle) {
            if (root.getClass() == Handle.class) {
                root = int2ObjectMap.get(((Handle) root).index);
                assert (root != null);
                return root;
            }
        }
        if (root.getClass().isEnum()) {
            return root;
        }
        if (useHandle)
            int2ObjectMap.put(readItem, root);
        readItem++;
        ObjectModel.getAllReferenceSlots(root, slots);
        int i = 0;
        while (true) {
            Address addr = slots.get(i);
            if (addr.isZero())
                break;
            queue.add(addr);
            i++;
        }
        while (queue.size() != 0) {
            Address slot = queue.removeAddress();
            LocalBuffer.getNextAddr(currentBuffer, _ret);
            //TODO: release the block prior to currentBuffer, we need decent block release implementation
            if (currentBuffer != _ret.getLocalBuffer()) {
                if (retireBuffer != null)
                    retireBuffer.release();
                retireBuffer  = currentBuffer;
            }
            currentBuffer = _ret.getLocalBuffer();
            Object item = ObjectModel.initializeHeader(_ret.getAddr());
            Object obj = null;
            if (item != null) {
                if (item.getClass() == Handle.class) {
                    obj = int2ObjectMap.get(((Handle) item).index);
                    assert (obj != null);
                } else if (item.getClass().isEnum()) {
                    obj = item;
                } else {
                    obj = item;
                    if (useHandle)
                        int2ObjectMap.put(readItem, obj);
                }
            }
            readItem++;
            slot.store(ObjectReference.fromObject(obj).toAddress().toLong());
            if (obj == null || item.getClass() == Handle.class || item.getClass().isEnum())
                continue;
            ObjectModel.getAllReferenceSlots(obj, slots);
            i = 0;
            while (true) {
                Address addr = slots.get(i);
                if (addr.isZero())
                    break;
                queue.add(addr);
                i++;
            }
        }
        return root;
    }
    @Override
    public void close() {
    }

    public LocalBuffer getLocalBuffer() {
        LocalBuffer _buffer = new LocalBuffer();
        if (firstBuffer == null) {
            retireBuffer = null;
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
