package intruder;

import com.ibm.disni.util.MemoryUtils;
import com.ibm.disni.verbs.IbvMr;
import intruder.RPC.RPCClient;
import org.vmmagic.unboxed.Address;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

public class IntruderOutStream extends Stream{
    private RemoteBuffer remoteBuffer;
    private RPCClient rpcClient;
    private RingBuffer ringBuffer;
    public IntruderOutStream(Endpoint ep) throws IOException{
        super(ep);
        rpcClient = new RPCClient(connectionId);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex){}
        rpcClient.connect(ep.serverHost);
        Utils.log("rpc client connected!");
        remoteBuffer = RemoteBuffer.reserveBuffer(rpcClient, ep);
        ringBuffer = new RingBuffer(1 << 20);
    }

    public void writeObject(Object object) throws IOException {
        if (Factory.query(object.getClass()) == -1)
            throw new IOException("type not registered: " + object.getClass().getCanonicalName());
        Queue<Object> queue = new LinkedList<Object>();
        queue.add(object);
        while (queue.size() != 0) {
            object = queue.remove();
            if (object == null) {
                fillNull();
                continue;
            }
            Object[] refs = ObjectModel.getAllReferences(object);
            for (Object o : refs)
                queue.add(o);
            fill(object);
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
                Utils.log("remotebuffer freespace: " + remoteBuffer.freeSpace());
                Utils.log("ring buffer reserve: " + reserved);
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
        ringBuffer.fillValue(-1L);
    }
    @Override
    public void close() {

    }

    private class RingBuffer {
        public ByteBuffer buffer;
        private int tail, head, length;
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

        public int reserve(int size) {
            //we should avoid the situation where the ring buffer is full, which leads to head == tail.
            if (tailToHead() + size >= length)
                return -1;
            return tailToHead() + size;
        }

        public void fillValue(long v) {
            assert((head & ObjectModel.MIN_ALIGNMENT - 1) == 0);
            int start = ObjectModel.alignObjectAllocation(head);
            if (head != start)
                ObjectModel.fillGap(addr.plus(head));
            addr.plus(head).store(v);
            head = start + 8;
            assert(tailToHead(head) < length);
        }

    //copy all primitive slots and returns all reference slots.
	private void fillObject(Object object) {
	    //找到满足对齐要求的起始地址
	    //跳过header，复制数据
	    //改写头部数据:将TIB指针改为类注册ID;status的部分留给接收端处理
	    //填充padding
	    assert((head & ObjectModel.MIN_ALIGNMENT - 1) == 0);
	    int start = ObjectModel.alignObjectAllocation(head);
	    if (head != start)
            ObjectModel.fillGap(addr.plus(head));
	    ObjectModel.copyObject(object, addr.plus(start));
	    ObjectModel.setRegisteredID(object, addr.plus(start));
	    head = start + ObjectModel.getAlignedUpSize(object);
	    assert(tailToHead(head) < length);
	}

        public void flush(boolean allocRemoteBuffer) throws IOException{
            if (head == tail) {
                Utils.log("warn:ringBuffer head == tail " + head);
            } else {
                if (head < tail) {
                    remoteBuffer.writeWarp(addr.plus(tail), length - tail + 1, addr, head, lkey);
                } else
                    remoteBuffer.write(addr.plus(tail), head - tail, lkey);
                reset();
            }
            remoteBuffer.notifyLimit();
            if (allocRemoteBuffer)
                remoteBuffer.reserve();
        }

        private int tailToHead(int head) {
            if (head >= tail) return head - tail;
            return head + length - tail;
        }

        private int tailToHead() {
            if (head >= tail) return head - tail;
            return head + length - tail;
        }
        private void reset() {
	        Utils.zeroMemory(addr, length);
            tail = head = 0;
        }

        public void printStats() {
            Utils.log("=========ring buffer stats========");
            Utils.log("head: " + head + "  tail: " + tail);
            Utils.log("current size: " + tailToHead() + "left: " + (length - tailToHead()));
            Utils.log("==================================");
        }

        public void peekBytes(int offset, int size) {
            Utils.peekBytes("ring buffer", addr, offset, size, 3);
        }
    }

}