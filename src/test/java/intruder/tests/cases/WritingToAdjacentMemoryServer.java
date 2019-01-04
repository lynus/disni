package intruder.tests.cases;

import intruder.Endpoint;
import intruder.Factory;
import intruder.Listener;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetSimpleObject;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class WritingToAdjacentMemoryServer {
    public static void main(String[] args) throws Exception {
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.useODP();

        InetAddress ipAddress = InetAddress.getByName(args[0]);
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Listener listener = Factory.newListener(address);
        System.out.println("waiting for connection...");
        Endpoint ep = listener.accept();
        ep.registerHeapODP();
        System.out.println("connected!");

        TargetSimpleObject[] array = new TargetSimpleObject[Integer.parseInt(args[1])];
        for (int i = 0; i < array.length; i++) {
            array[i] = new TargetSimpleObject();
            array[i].A = i;
            array[i].B = i;

        }

        ep.sendArrayId(TargetSimpleObject[].class, array.length);
        ep.waitIdsAck();
        ep.sendArray(array);

        ep.close();
        listener.close();
        Factory.close();
    }
}
