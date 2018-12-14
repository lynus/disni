package intruder.tests;

import intruder.Endpoint;
import intruder.Factory;
import intruder.RdmaClassIdManager;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Client {
    public static void main(String[] args) throws Exception {
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);

        InetAddress ipAddress = InetAddress.getByName(args[0]);
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Endpoint ep = Factory.newEndpoint();
        System.out.println("connecting to server...");
        ep.connect(address, 10);
        System.out.println("connected!");
        int rc_odp_cap = ep.queryODPSupport();
        ep.registerHeap();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);

        int id = ep.waitIds();
        TargetSimpleObject obj = (TargetSimpleObject)ep.prepareObject(id);
        System.out.println("before trans, " + obj.toString());
        ep.sendIdsAck();
        ep.waitEvent();
        System.out.println("after trans, " + obj.toString());
        ep.readyToReiveId();

        id = ep.waitIds();
        assert((id & RdmaClassIdManager.ARRAYTYPEMASK) == RdmaClassIdManager.ARRAYTYPE);
        int len = ep.getArrayLength();
        System.out.println("get len: " + len);
        TargetSimpleObject[] array = (TargetSimpleObject[])ep.prepareArray(id, len);
        ep.sendIdsAck();
        ep.waitEvent();
        System.out.println("got array!");
        int i = 0;
        for (TargetSimpleObject object: array) {
            System.out.println("#" + i + " : " + object.toString());
            i++;
        }

        ep.close();
        Factory.close();
    }
}
