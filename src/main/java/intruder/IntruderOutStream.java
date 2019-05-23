package intruder;

import com.ibm.disni.util.MemoryUtils;
import com.ibm.disni.verbs.IbvMr;
import intruder.RPC.RPCClient;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

import java.io.IOException;
import java.nio.ByteBuffer;

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
          while(!fill((1 << 16) + (1 << 11) + (1<<8))) {
              Utils.log("writeObject retry");
          }
    }

    public void flush() throws IOException {
        ringBuffer.flush(false);
    }

    private boolean fill(int size) throws IOException{
        if (ringBuffer.reserve(size) == -1) {
            //ringBuffer is overflow, flush ring buffer and try again
            ringBuffer.flush(false);
            return false;
        }
        if (ringBuffer.reserve(size) > remoteBuffer.freeSpace()) {
            //remote buffer is overflow, flush ring buffer, allocate new remote buffer and try again
            Utils.log("remotebuffer freespace: "+remoteBuffer.freeSpace());
            Utils.log("ring buffer reserve: "+ringBuffer.reserve(size));
            ringBuffer.flush(true);
            return false;
        }
        ringBuffer.fillData(size);
        return true;
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

        public void fillData(int size) {
            for (int i = 0; i < size; i++) {
                addr.store((byte)(i & 0xff), Offset.fromIntZeroExtend(i).plus(head));
            }
            head = (head + size) % length;
//            peekBytes(head - 5, 10);
            assert(tailToHead(head) <= length);
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
            tail = head = 0;
        }

        public void printStats() {
            Utils.log("=========ring buffer stats========");
            Utils.log("head: " + head + "  tail: " + tail);
            Utils.log("current size: " + tailToHead() + "left: " + (length - tailToHead()));
            Utils.log("==================================");
        }

        public void peekBytes(int offset, int size) {
            Utils.log("=========peek ring buffer bytes==========");
            Utils.log("tail:" + tail + " head: " + head);
            Utils.log("peek offset:length  " + Integer.toHexString(offset) + ":" + Integer.toHexString(size));
            String contents = new String();
            for (int i = 0; i < size; i++) {
                byte v = addr.loadByte(Offset.fromIntZeroExtend(offset + i));
                contents += "0x" + Utils.hexArray[(v >> 4) & 0xf] + Utils.hexArray[v & 0x0F] + " ";
            }
            Utils.log(contents);
            Utils.log("===========================================");
        }
    }

}
