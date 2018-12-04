package intruder.tests;


import intruder.Endpoint;
import intruder.Factory;
import intruder.Listener;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Server {
    public static void main(String[] args) throws Exception {
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        InetAddress ipAddress = InetAddress.getByName("bigserver");
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Listener listener = Factory.newListener(address);
        System.out.println("waiting for connection...");
        Endpoint ep = listener.accept();
        ep.registerHeap();
        System.out.println("connected!");
        ep.sendIds(TargetSimpleObject.class);
        ep.waitIdsAck();
        TargetSimpleObject obj = new TargetSimpleObject();
        obj.B = 12345L;
        ep.sendObject(obj);
        System.out.println("object sent!");
        System.in.read();
    }

}
