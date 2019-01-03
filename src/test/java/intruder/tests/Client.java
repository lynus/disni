package intruder.tests;

import intruder.Endpoint;
import intruder.Factory;
import intruder.RdmaClassIdManager;
import intruder.Utils;

import javax.swing.text.html.HTML;
import java.lang.annotation.Target;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class Client {
    public static void main(String[] args) throws Exception {
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.useODP();

        InetAddress ipAddress = InetAddress.getByName(args[0]);
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Endpoint ep = Factory.newEndpoint();
        System.out.println("connecting to server...");
        ep.connect(address, 10);
        System.out.println("connected!");
        ep.registerHeapODP();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);

        int id,len,i;
        id = ep.waitIds();
        len = ep.getArrayLength();
        System.out.println("get len: " + len);
        TargetSimpleObject[] array = (TargetSimpleObject[])ep.prepareArray(id, len);
        ep.sendIdsAck();
        // ODP page fault handling can be a few seconds long, during which, read/write to the
        // involving memory range would cause QP raise IBV_WC_FLUSH_ERR error. This pitfall should
        // have been warned by Mellanox. It costs me tens of hours debugging effort.
        for (i = 0; i < ep.waitN; i++)
            ep.waitEvent();
        i = 0;
        for (TargetSimpleObject obj: array) {
            System.out.println("#" + i + " : " + obj.toString());
            i++;
        }


        ep.close();
        Factory.close();

    }
}
