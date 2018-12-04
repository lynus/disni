package intruder.tests;

import intruder.Endpoint;
import intruder.Factory;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Client {
    public static void main(String[] args) throws Exception {
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);

        InetAddress ipAddress = InetAddress.getByName("bigserver");
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Endpoint ep = Factory.newEndpoint();
        System.out.println("connecting to server...");
        ep.connect(address, 10);
        System.out.println("connected!");
        ep.registerHeap();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);

        int id = ep.waitIds();
        TargetSimpleObject obj = (TargetSimpleObject)ep.prepareObject(id);
        System.out.println("before trans, " + obj.toString());
        ep.sendIdsAck();
        ep.waitEvent();
        System.out.println("after trans, " + obj.toString());
    }
}
