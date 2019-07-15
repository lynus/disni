package intruder;

import com.ibm.disni.verbs.*;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;

import java.io.IOException;
import java.util.LinkedList;

import static intruder.RdmaClassIdManager.SCALARTYPEMASK;

public class Utils {
    static final int MAXSGEPERWR = 10;
    public static char[] hexArray = "0123456789ABCDEF".toCharArray();
    static public void ensureClassInitialized(RVMType type) {
        if (!type.isInitialized()) {
            if (type.isClassType()) {
                RuntimeEntrypoints.initializeClassForDynamicLink(type.asClass());
            } else
                type.prepareForFirstUse();
        }
    }

    static public RVMType ensureIdValid(int id) throws IOException {
        id = id & SCALARTYPEMASK;
        RVMType type = Factory.query(id);
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
    public static boolean enableLog = true;

    public static void disableLog() {
        enableLog = false;
    }
    public static void log(String msg) {
            System.err.println(msg);
    }
    public static void memcopyAligned4(Address src, Address dst, int size) {
        Memory.aligned32Copy(dst, src, size);
    }
    public static void zeroMemory(Address addr, int size) {
        //use non-temporal store
        Memory.zero(false, addr, Extent.fromIntZeroExtend(size));
    }
    public static void peekBytes(String msg, Address start, int offset, int size, int logBytesPerLine) {
        int bytesPerLine = 1 << logBytesPerLine;
        offset = (offset >> logBytesPerLine) << logBytesPerLine;
        size = ((size + bytesPerLine - 1) >> logBytesPerLine) << logBytesPerLine ;
        Utils.log("=========peek " + msg + " bytes==========");
        Utils.log("buffer address: 0x" + Long.toHexString(start.toLong()));
        Utils.log("peek offset:length  0x" + Integer.toHexString(offset) + ":" + Integer.toHexString(size));
        String contents = "";
        for (int i = 0; i < size; i++) {
            byte v = start.loadByte(Offset.fromIntSignExtend(offset + i));
            contents += "0x" + hexArray[(v >> 4) & 0xf] + hexArray[v & 0xf] + " ";
            if (i % bytesPerLine == bytesPerLine -1) {
                Utils.log(contents);
                contents = "";
            }
        }
        Utils.log("===========================================");
    }
}
