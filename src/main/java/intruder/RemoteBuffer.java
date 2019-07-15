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
    public int boundry;
    public int lastFlush;
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
        return buffer;
    }

    public int freeSpace() {
        return length.toInt() - lastFlush;
    }

    public void setBoundry(int stageHead) {
        this.boundry = stageHead + lastFlush;
    }

    public static Address getRemoteAddress(int stagHead, RemoteBuffer last, RemoteBuffer current) {
        if (last != null) {
            assert (last.boundry != 0);
            assert(current.lastFlush == 0);
            stagHead -= last.boundry - last.lastFlush;
            Address ret = current.start.plus(stagHead);
            return ret;
        } else {
            assert(current.lastFlush + stagHead  <= current.length.toInt());
            Address ret = current.start.plus(current.lastFlush + stagHead);
            return ret;
        }

    }

    private IbvSendWR assembleWR(Address rBufferStart, int rBufferLength, int lkey) throws IOException {
        assert (rBufferLength <= freeSpace());
        IbvSendWR sendWR = newWriteWR(1);
        IbvSge sge = sendWR.getSg_list().getFirst();
        sge.setLength(rBufferLength);
        sge.setAddr(rBufferStart.toLong());
        sge.setLkey(lkey);
        sendWR.getRdma().setRkey(RKey);
        sendWR.getRdma().setRemote_addr(start.toLong() + lastFlush);
        Utils.log("assembleWR local addr 0x" + Long.toHexString(rBufferStart.toLong()) +
                " remote addr 0x" + Long.toHexString(start.plus(lastFlush).toLong()) + " length: " + rBufferLength);
        return sendWR;
    }

    public void write(Endpoint ep, Address stageBuffer, int stageHead, int lkey) throws IOException {
        IbvSendWR sendWR = assembleWR(stageBuffer, stageHead, lkey);
        LinkedList<IbvSendWR> list = new LinkedList<IbvSendWR>();
        list.add(sendWR);
        ep.postSend(list).execute().free();
        try {
            IbvWC wc = ep.waitEvent();
            if (wc.getStatus() != 0)
                throw new IOException("write error");
        } catch (InterruptedException ex) {
        }
        lastFlush += stageHead;
        notifyLimit();
    }

    public static void writeTwoBuffer(Endpoint ep, Address stageBuffer, int stageHead, int lkey, RemoteBuffer last, RemoteBuffer current) throws IOException{
        int last_size = last.boundry - last.lastFlush;
        IbvSendWR sendWRLast = last.assembleWR(stageBuffer, last_size, lkey);
        int remain = stageHead - last_size;
        IbvSendWR sendWRCurrent = current.assembleWR(stageBuffer.plus(last_size), remain, lkey);
        LinkedList<IbvSendWR> list = new LinkedList<IbvSendWR>();
        list.add(sendWRLast);
        list.add(sendWRCurrent);
        ep.postSend(list).execute().free();
        try {
            IbvWC wc = ep.waitEvent();
            if (wc.getStatus() != 0)
                throw new IOException("write error");
        } catch (InterruptedException ex) {
        }
        last.notifyLimit();
        current.lastFlush = remain;
        current.notifyLimit();
    }


    //notify remote host the boundry pointer of this buffer
    public void notifyLimit() throws IOException{
        if (boundry != 0) {
            assert ((boundry & 7) == 0);
            rpcClient.notifyBufferLimit(start.toLong(), boundry, true);
        } else {
            //lastFlush is update before calling this
            assert((lastFlush & 7) == 0);
            rpcClient.notifyBufferLimit(start.toLong(), lastFlush, false);
        }
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
