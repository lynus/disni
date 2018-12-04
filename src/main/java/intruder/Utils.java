package intruder;

import com.ibm.disni.verbs.*;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;

import java.io.IOException;
import java.util.LinkedList;

public class Utils {
    static public void ensureClassInitialized(RVMClass cls) {
        if (!cls.isInitialized())
        RuntimeEntrypoints.initializeClassForDynamicLink(cls);
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
        wrList = new LinkedList<IbvSendWR>();
        wrList.add(wr);
        svcPostSend = ep.postSend(wrList);
        svcPostSend.execute().free();
    }
}
