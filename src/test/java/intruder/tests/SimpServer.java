package intruder.tests;

import com.ibm.disni.util.MemoryUtils;
import com.ibm.disni.verbs.*;
import intruder.Endpoint;
import intruder.Factory;
import intruder.Listener;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class SimpServer {
    public static void main(String[] args) throws Exception {
        Factory.useODP();
        InetAddress ipAddress = InetAddress.getByName(args[0]);
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Listener listener = Factory.newListener(address);
        System.out.println("waiting for connection...");
        Endpoint ep = listener.accept();
        System.out.println("connected!");

        Recv(ep);
        ep.close();
        listener.close();
        Factory.close();
    }

    public static void Send(Endpoint ep) throws Exception{
        Thread.sleep(1000);
        IbvSge sge = new IbvSge();
        LinkedList<IbvSge> sgeList;
        IbvSendWR wr;
        LinkedList<IbvSendWR> wrList;
        SVCPostSend svcPostSend;
        int size = 64;
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        sge.setAddr(MemoryUtils.getAddress(buffer));
        sge.setLength(size);
        sge.setLkey(ep.registerMemoryODP(sge.getAddr(), size).execute().getMr().getLkey());

        sgeList = new LinkedList<IbvSge>();
        sgeList.add(sge);

        wr = new IbvSendWR();
        wr.setSg_list(sgeList);
        wr.setOpcode(IbvSendWR.IBV_WR_SEND);
//        wr.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);

        wrList = new LinkedList<IbvSendWR>();
        wrList.add(wr);
        svcPostSend = ep.postSend(wrList);
        svcPostSend.execute().free();

        Thread.sleep(500);
//        ep.waitEvent();
        System.out.println("Sent!");
    }


    public static void Recv(Endpoint ep) throws Exception {
        IbvSge sge = new IbvSge();
        LinkedList<IbvSge> sgeList;
        IbvRecvWR wr;
        LinkedList<IbvRecvWR> wrList;
        SVCPostRecv svcPostRecv;
        int size = 10;
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        sge.setAddr(MemoryUtils.getAddress(buffer));
        sge.setLength(size);
        sge.setLkey(ep.registerMemoryODP(sge.getAddr(), size).execute().getMr().getLkey());
        sgeList = new LinkedList<IbvSge>();
        sgeList.add(sge);

        wr = new IbvRecvWR();
        wr.setSg_list(sgeList);
        wrList = new LinkedList<IbvRecvWR>();
        wrList.add(wr);
        svcPostRecv = ep.postRecv(wrList);
        svcPostRecv.execute().free();
        ep.waitEvent();
        System.out.println("Recv!");
    }

}
