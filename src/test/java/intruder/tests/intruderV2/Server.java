package intruder.tests.intruderV2;

import intruder.Endpoint;
import intruder.Factory;
import intruder.IntruderInStream;
import intruder.Listener;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetSimpleObject;
import intruder.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Server {
    public static void main(String []args) throws Exception {
//        Factory.useODP();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetSimpleObject.class);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(args[0]), 8090);
        Listener listener = Factory.newListener(address);
        Endpoint ep = listener.accept();
//        ep.registerHeapODP();
        IntruderInStream instream = ep.getInStream();
        Utils.log("instream connectID: " + instream.getConnectionId());
        TargetSimpleObject object = (TargetSimpleObject)instream.readObject();
        Utils.log(object.toString());
        object = (TargetSimpleObject)instream.readObject();
        Utils.log(object.toString());

        int [] array = (int[])instream.readObject();
        for (int i: array) {
            Utils.log(" "+i);
        }
        System.in.read();
    }
}
