package intruder.tests.intruderV2;

import intruder.Endpoint;
import intruder.Factory;
import intruder.IntruderOutStream;
import intruder.Utils;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Client {
    public static void main(String[] args) throws Exception{
//        Factory.useODP();
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(args[0]), 8090);
        Endpoint ep = Factory.newEndpoint();
        ep.connect(address, 10);
//        ep.registerHeapODP();
        IntruderOutStream outStream = ep.getOutStream();
        for (int i = 0; i < 160; i++) {
            outStream.writeObject(null);
            Utils.log("success #" + i);
        }

        System.out.println("outstream connectionID: " + outStream.getConnectionId());
        System.gc();
        System.in.read();
    }
}
