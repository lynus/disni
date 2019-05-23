package intruder.tests.intruderV2;

import intruder.Endpoint;
import intruder.Factory;
import intruder.IntruderInStream;
import intruder.Listener;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Server {
    public static void main(String []args) throws Exception {
//        Factory.useODP();
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(args[0]), 8090);
        Listener listener = Factory.newListener(address);
        Endpoint ep = listener.accept();
//        ep.registerHeapODP();
        IntruderInStream instream = ep.getInStream();
        System.err.println("instream connectID: " + instream.getConnectionId());
        System.in.read();
    }
}
