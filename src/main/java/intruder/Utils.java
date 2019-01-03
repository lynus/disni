package intruder;

import com.ibm.disni.verbs.*;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;

import java.io.IOException;
import java.util.LinkedList;

import static intruder.RdmaClassIdManager.SCALARTYPEMASK;

public class Utils {
    static final int MAXSGEPERWR = 10;
    static public void ensureClassInitialized(RVMClass cls) {
        if (!cls.isInitialized())
        RuntimeEntrypoints.initializeClassForDynamicLink(cls);
    }

    static public RVMType ensureIdValid(int id) throws IOException {
        id = id & SCALARTYPEMASK;
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(Factory.query(id));
        if (type == null || !type.isInitialized()) {
            throw new IOException("class not found or not initialized!");
        }
        return type;
    }

    static public void postReceiveObject(Address start, int size, Endpoint ep) throws IOException {
        IbvSge sge = new IbvSge();
        LinkedList<IbvSge> sgeList;
        IbvRecvWR wr;
        LinkedList<IbvRecvWR> wrList;
        SVCPostRecv svcPostRecv;

        sge.setAddr(start.toLong());
        sge.setLength(size);
        sge.setLkey(ep.heapLKey);

        sgeList = new LinkedList<IbvSge>();
        sgeList.add(sge);

        wr = new IbvRecvWR();
        wr.setSg_list(sgeList);
        //wr.setWr_id();

        wrList = new LinkedList<IbvRecvWR>();
        wrList.add(wr);
        svcPostRecv = ep.postRecv(wrList);
        svcPostRecv.execute().free();
    }
    static private IbvRecvWR wrapRecvWR(Endpoint.AllocBuf[] allocBufs, int index, int size, int LKey) {
        LinkedList<IbvSge> sgeList = new LinkedList<IbvSge>();
        IbvRecvWR wr = new IbvRecvWR();
        IbvSge sge;
        int length = allocBufs[0].len;
        for (int i = index; i < index + size; i++) {
            sge = new IbvSge();
            sge.setLkey(LKey);
            sge.setLength(length);
            sge.setAddr(allocBufs[i].start.toLong());
            sgeList.add(sge);
        }
        wr.setSg_list(sgeList);
        wr.setWr_id(2323);
        return wr;
    }

    static private IbvSendWR wrapSendWR(Endpoint.AllocBuf[] allocBufs, int index, int size, int LKey) {
        LinkedList<IbvSge> sgeList = new LinkedList<IbvSge>();
        IbvSendWR wr = new IbvSendWR();
        IbvSge sge;
        int length = allocBufs[0].len;
        for (int i = index; i < index + size; i++) {
            sge = new IbvSge();
            sge.setLkey(LKey);
            sge.setLength(length);
            sge.setAddr(allocBufs[i].start.toLong());
            sgeList.add(sge);
        }
        wr.setSg_list(sgeList);
        wr.setOpcode(IbvSendWR.IBV_WR_SEND);
        wr.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        wr.setWr_id(1212);
        return wr;
    }

    static public void postReceiveMultiObjects(Endpoint.AllocBuf[] allocBufs, Endpoint ep) throws IOException {
        SVCPostRecv svcPostRecv;
        LinkedList<IbvRecvWR> wrList = new LinkedList<IbvRecvWR>();
        int index = 0, len = allocBufs.length;
        int sgeN;
        while (index < len) {
            if ((len - index) > MAXSGEPERWR)
               sgeN = MAXSGEPERWR;
            else
                sgeN = len - index;
            wrList.add(wrapRecvWR(allocBufs, index, sgeN, ep.heapLKey));
            index += sgeN;
        }
        ep.waitN = wrList.size();
        svcPostRecv = ep.postRecv(wrList);
        svcPostRecv.execute().free();
    }

    static public void postSendObject(Address start, int size, Endpoint ep) throws IOException ,InterruptedException{
        IbvSge sge = new IbvSge();
        LinkedList<IbvSge> sgeList;
        IbvSendWR wr;
        LinkedList<IbvSendWR> wrList;
        SVCPostSend svcPostSend;

        sge.setAddr(start.toLong());
        sge.setLength(size);
        sge.setLkey(ep.heapLKey);

        sgeList = new LinkedList<IbvSge>();
        sgeList.add(sge);

        wr = new IbvSendWR();
        wr.setSg_list(sgeList);
        wr.setOpcode(IbvSendWR.IBV_WR_SEND);
        wr.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        wr.setWr_id(999L);
        wrList = new LinkedList<IbvSendWR>();
        wrList.add(wr);
        svcPostSend = ep.postSend(wrList);
        svcPostSend.execute().free();
        ep.waitEvent();
    }

    static public void postSendMultiObjects(Endpoint.AllocBuf[] allocBufs, Endpoint ep) throws IOException, InterruptedException {
        SVCPostSend svcPostSend;
        LinkedList<IbvSendWR> wrList = new LinkedList<IbvSendWR>();
        int index = 0, len = allocBufs.length;
        int sgeN;
        int wrN = 0;
        while (index < len) {
            wrN++;
            if ((len - index) > MAXSGEPERWR)
                sgeN = MAXSGEPERWR;
            else
                sgeN = len - index;
            wrList.add(wrapSendWR(allocBufs, index, sgeN, ep.heapLKey));
            index += sgeN;
        }
        svcPostSend = ep.postSend(wrList);
        svcPostSend.execute().free();
        for (int i = 0; i < wrN; i++)
            ep.waitEvent();
    }
}