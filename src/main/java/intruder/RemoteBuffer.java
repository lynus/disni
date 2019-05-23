package intruder;

import com.ibm.disni.verbs.IbvSendWR;
import com.ibm.disni.verbs.IbvSge;
import com.ibm.disni.verbs.IbvWC;
import intruder.RPC.RPCClient;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

import java.io.IOException;
import java.util.LinkedList;

public class RemoteBuffer extends Buffer{
    protected int RKey;
    protected int begin;
    protected RPCClient rpcClient;
    protected Endpoint ep;
    public void setup(int RKey, long address, int size, RPCClient rpcClient) {
        this.RKey = RKey;
        this.start = Address.fromLong(address);
        this.length = Extent.fromIntZeroExtend(size);
        this.rpcClient = rpcClient;
        this.begin = 0;
    }

    public static RemoteBuffer reserveBuffer(RPCClient rpcClient, Endpoint ep) throws  IOException {
        RemoteBuffer buffer = new RemoteBuffer();
        rpcClient.reserveBuffer(buffer);
        buffer.ep = ep;
        return buffer;
    }

    public int freeSpace() {
        return length.toInt() - begin;
    }

    public void write(Address rBufferStart, int rBufferLength, int lkey) throws IOException {
        assert(rBufferLength <= freeSpace());
        IbvSendWR sendWR = newWriteWR(1);
        IbvSge sge = sendWR.getSg_list().getFirst();
        sge.setLength(rBufferLength);
        sge.setAddr(rBufferStart.toLong());
        sge.setLkey(lkey);
        sendWR.getRdma().setRkey(RKey);
        sendWR.getRdma().setRemote_addr(start.toLong() + begin);
        LinkedList<IbvSendWR> list = new LinkedList<IbvSendWR>();
        list.add(sendWR);
        ep.postSend(list).execute().free();
        try {
            IbvWC wc = ep.waitEvent();
            if (wc.getStatus() != 0)
                throw new IOException("write error");
        } catch (InterruptedException ex) {
        }
        begin += rBufferLength;
    }

    public void writeWarp(Address rBufferStart1, int length1, Address rBufferStart2, int length2, int lkey) throws IOException {
        assert (length1 + length2 <= freeSpace());
        IbvSendWR sendWR = newWriteWR(2);
        IbvSge sge = sendWR.getSge(0);
        sge.setLength(length1);
        sge.setAddr(rBufferStart1.toLong());
        sge.setLkey(lkey);
        sge = sendWR.getSge(1);
        sge.setLength(length2);
        sge.setAddr(rBufferStart2.toLong());
        sge.setLkey(lkey);
        sendWR.getRdma().setRkey(RKey);
        sendWR.getRdma().setRemote_addr(start.toLong() + begin);
        LinkedList<IbvSendWR> list = new LinkedList<IbvSendWR>();
        list.add(sendWR);
        ep.postSend(list).execute().free();
        try {
            IbvWC wc = ep.waitEvent();
            if (wc.getStatus() != 0)
                throw new IOException("write error");
        } catch (InterruptedException ex) {
        }
        begin += length1 + length2;
    }

    //notify remote host the limit pointer of this buffer
    public void notifyLimit() throws IOException{
        rpcClient.notifyBufferLimit(start.toLong(), begin);
    }

    public void reserve() throws IOException {
        rpcClient.reserveBuffer(this);
    }

    public void releaseAndReserve() throws IOException {
        rpcClient.releaseAndReserve(this);
    }
    private IbvSendWR newWriteWR(int numSge) {
        IbvSendWR wr = new IbvSendWR();
        LinkedList<IbvSge> sgeList = new LinkedList<IbvSge>();
        IbvSge sge;
        for (int i = 0; i < numSge; i++) {
            sge = new IbvSge();
            sgeList.add(sge);
        }
        wr.setSg_list(sgeList);
        wr.setOpcode(IbvSendWR.IBV_WR_RDMA_WRITE);
        wr.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        wr.setWr_id(999);
        return wr;
    }
}
