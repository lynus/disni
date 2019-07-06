package intruder;

import com.ibm.disni.util.MemoryUtils;
import com.ibm.disni.verbs.IbvMr;
import intruder.RPC.RPCClient;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class IntruderOutStream extends Stream{
    private RemoteBuffer remoteBuffer;
    private RPCClient rpcClient;
    private RingBuffer ringBuffer;
    private HashMap<Object, Integer> obj2HandleMap = new HashMap<Object, Integer>();
    private int writtenItem = 0;
    private boolean useHandle = false;
    private intruder.Queue queue = new intruder.Queue(512);
    private Object[] refArray = new Object[32];
    public IntruderOutStream(Endpoint ep) throws IOException{
        super(ep);
        rpcClient = new RPCClient(connectionId);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex){}
        rpcClient.connect(ep.serverHost);
        if (Utils.enableLog)
            Utils.log("rpc client connected!");
        remoteBuffer = RemoteBuffer.reserveBuffer(rpcClient, ep);
        ringBuffer = new RingBuffer(1 << 20);
    }

    public void enableHandle() {
        this.useHandle = true;
    }
    public void disableHandle() {
        this.useHandle = false;
    }
    public void waitRemoteFinish() throws IOException{
        rpcClient.waitRemoteFinish();
    }
    public void startRPCCount() {
        rpcClient.startCount();
    }
    public String rpcCountReport() {
        return "rpc count, reserve: " + rpcClient.getReserveTimes() + " notify: " + rpcClient.getNotifyTimes();
    }
    public void writeObject(Object object) throws IOException {
        if (object.getClass().isEnum()) {
            fillEnum((Enum)object);
            return;
        }
        if (useHandle) {
            Integer handle = obj2HandleMap.get(object);
            if (handle != null) {
                fillHandle(new Handle(handle));
                return;
            }
            obj2HandleMap.put(object, writtenItem);
        }
        //TODO: replace LinkedList with lightweight array
        writtenItem++;
        queue.add(object);
        while (queue.size() != 0) {
            object = queue.remove();
            if (object == null) {
                fillNull();
                continue;
            }
            if (useHandle) {
                if (object.getClass() == Handle.class) {
                    fillHandle((Handle) object);
                    continue;
                }
            }
            if (object.getClass().isEnum()) {
                fillEnum((Enum)object);
                continue;
            }
            fill(object);
            int reflen = ObjectModel.getAllReferences(object, refArray);
            for (int i = 0; i < reflen; i++) {
                Object o = refArray[i];
                if (o == null) {
                    queue.add(null);
                } else if (!useHandle) {
                    //normal object and enum
                    queue.add(o);
                } else {
                    Integer handle = obj2HandleMap.get(o);
                    if (handle != null) {
                        queue.add(new Handle(handle));
                    } else if (o.getClass().isEnum()) {
                        queue.add(o);
                    } else {
                        obj2HandleMap.put(o, writtenItem);
                        queue.add(o);
                    }
                }
                writtenItem++;
            }
        }
    }

    public void flush() throws IOException {
        ringBuffer.flush(false);
    }


    private Address fill(Object object) throws IOException {
        int size = ObjectModel.getMaximumAlignedSize(object);
        while (true) {
            //TODO: It's commone case that neither ringBuffer and remoteBuffer can reserve
            //the object. Consolidate two rpc calls into one.
            int reserved = ringBuffer.reserve(size);
            if (reserved == -1) {
                ringBuffer.flush(false);
                continue;
            }
            if (reserved > remoteBuffer.freeSpace()) {
                if (Utils.enableLog) {
                    Utils.log("remotebuffer freespace: " + remoteBuffer.freeSpace());
                    Utils.log("ring buffer reserve: " + reserved);
                }
                ringBuffer.flush(true);
                continue;
            }
            break;
        }
	    ringBuffer.fillObject(object);
        return Address.zero();
    }
    private void fillNull() throws IOException {
        int size = 8;
        while (true) {
            int reserved = ringBuffer.reserve(size);
            if (reserved == -1) {
                ringBuffer.flush(false);
                continue;
            }
            if (reserved > remoteBuffer.freeSpace()) {
                ringBuffer.flush(true);
                continue;
            }
            break;
        }
        ringBuffer.fillNull();
    }

    private void fillHandle(Handle handle) throws IOException {
        int size = 8;
        while (true) {
            int reserved = ringBuffer.reserve(size);
            if (reserved == -1) {
                ringBuffer.flush(false);
                continue;
            }
            if (reserved > remoteBuffer.freeSpace()) {
                ringBuffer.flush(true);
                continue;
            }
            break;
        }
        ringBuffer.fillHandle(handle);
    }

    private void fillEnum(Enum e) throws IOException {
        int size = 8;
        while (true) {
            int reserved = ringBuffer.reserve(size);
            if (reserved == -1) {
                ringBuffer.flush(false);
                continue;
            }
            if (reserved > remoteBuffer.freeSpace()) {
                ringBuffer.flush(true);
                continue;
            }
            break;
        }
        ringBuffer.fillEnum(e);
    }
    @Override
    public void close() {

    }

    private class RingBuffer {
        public ByteBuffer buffer;
        private int head, length;
        private Address addr;
        private int lkey, rkey;
        public RingBuffer() throws IOException{
            this(Buffer.getBufferSize());
        }
        public RingBuffer(int size) throws IOException {
            buffer = ByteBuffer.allocateDirect(size);
            addr = Address.fromLong(MemoryUtils.getAddress(buffer));
            length = size;
            IbvMr mr = ep.registerMemory(buffer).execute().free().getMr();
            lkey = mr.getLkey();
            rkey = mr.getRkey();
        }
        @Inline
        public int reserve(int size) {
            //we should avoid the situation where the ring buffer is full, which leads to head == tail.
            int reserved = head + size;
            if (reserved >= length)
                return -1;
            return reserved;
        }
        @Inline
        private int alignAlloc() {
            assert((head & ObjectModel.MIN_ALIGNMENT - 1) == 0);
            int ret = ObjectModel.alignObjectAllocation(head);
            if (ret != head) {
                ObjectModel.fillGap(addr.plus(head));
            }
            return ret;
        }

        public void fillNull() {
            int start = alignAlloc();
            HeaderEncoding.setNullType(addr.plus(start));
            head = start + 8;
            assert(head < length);
        }

        public void fillHandle(Handle handle) {
            int start = alignAlloc();
//            HeaderEncoding.getHeaderEncoding(addr.plus(start)).setHandleType(handle.index);
            HeaderEncoding.setHandleType(addr.plus(start), handle.index);
            head = start + 8;
            assert (head < length);
        }

        public void fillEnum(Enum e) {
            int start = alignAlloc();
            int id = Factory.query(e.getClass());
            HeaderEncoding.setEnumType(addr.plus(start), id, e.ordinal());
            head = start + 8;
            assert (head < length);
        }

        //copy all primitive slots and returns all reference slots.
        private void fillObject(Object object) {
            //找到满足对齐要求的起始地址
            //跳过header，复制数据
            //改写头部数据:将TIB指针改为类注册ID;status的部分留给接收端处理
            //填充padding
            int start = alignAlloc();
            int size = ObjectModel.copyObject(object, addr.plus(start));
            ObjectModel.setRegisteredID(object, addr.plus(start));
            head = start + size;
            assert(head < length);
        }

        public void flush(boolean allocRemoteBuffer) throws IOException{
            remoteBuffer.write(addr, head, lkey);
            reset();
            remoteBuffer.notifyLimit();
            if (allocRemoteBuffer)
                remoteBuffer.reserve();
        }

        private void reset() {
	        Utils.zeroMemory(addr, length);
            head = 0;
        }

        public void printStats() {
            Utils.log("=========ring buffer stats========");
            Utils.log("current size: " + head + "left: " + (length - head));
            Utils.log("==================================");
        }

        public void peekBytes(int offset, int size) {
            Utils.peekBytes("ring buffer", addr, offset, size, 3);
        }

        public void peekBytes() {
            peekBytes(0, head);
        }

    }

}
