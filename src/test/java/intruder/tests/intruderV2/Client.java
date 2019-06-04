package intruder.tests.intruderV2;

import intruder.Endpoint;
import intruder.Factory;
import intruder.IntruderOutStream;
import intruder.Utils;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetSimpleObject;

import java.lang.annotation.Target;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Client {
    public static void main(String[] args) throws Exception{
//        Factory.useODP();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetSimpleObject.class);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(args[0]), 8090);
        Endpoint ep = Factory.newEndpoint();
        ep.connect(address, 10);
//        ep.registerHeapODP();
        TargetSimpleObject target = new TargetSimpleObject();
        IntruderOutStream outStream = ep.getOutStream();
//        for (int i = 0; i < 160; i++) {
//            outStream.writeObject(target);
//            Utils.log("success #" + i);
//        }
        outStream.writeObject(target);
        target.d = 'y';
        outStream.writeObject(target);
        outStream.flush();
        int array[] = new int[5];
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
        outStream.writeObject(array);
        outStream.flush();

        System.out.println("outstream connectionID: " + outStream.getConnectionId());
        System.gc();
        System.in.read();
    }
}
