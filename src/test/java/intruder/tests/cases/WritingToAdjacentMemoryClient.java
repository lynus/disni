package intruder.tests.cases;

import com.ibm.disni.util.DiSNILogger;
import intruder.Endpoint;
import intruder.Factory;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetSimpleObject;
import org.jikesrvm.runtime.Magic;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;
public class WritingToAdjacentMemoryClient {
    public static void main(String[] args) throws Exception {
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.useODP();
        Logger log = DiSNILogger.getLogger();

        InetAddress ipAddress = InetAddress.getByName(args[0]);
        InetSocketAddress address = new InetSocketAddress(ipAddress, 8090);
        Endpoint ep = Factory.newEndpoint();
        System.out.println("connecting to server...");
        ep.connect(address, 10);
        System.out.println("connected!");
        ep.registerHeapODP();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetPrimitiveObject.class);

        int id, len, i;
        id = ep.waitIds();
        len = ep.getArrayLength();
        System.out.println("get len: " + len);
        TargetSimpleObject[] array = (TargetSimpleObject[]) ep.prepareArray(id, len);
        ep.sendIdsAck();
        // ODP page fault handling can be a few seconds long, during which, read/write to the
        // involving memory range would cause QP raise IBV_WC_FLUSH_ERR error. This pitfall should
        // have been warned by Mellanox. It costs me tens of hours debugging effort.

        TargetSimpleObject[] victim = new TargetSimpleObject[20];
        for (i = 0; i < victim.length; i++) {
            victim[i] = new TargetSimpleObject();
            log.warn("new victem object address: " + Long.toHexString(Magic.objectAsAddress(victim[i]).toLong()));
        }

        //I'm worried that it will raise flush error if writing to the addresses that belong to the same page with
        //the memory being served by RDMA requests.
        //Result show no flush error raised.
        log.warn("continuously write to victem objects");
        //writing the victem object
        while (ep.getEventNum() != ep.waitN) {
            for (TargetSimpleObject obj : victim) {
                obj.A += 1;
            }
        }
        ep.drainEvent();
        i = 0;
        for (TargetSimpleObject obj : array) {
            log.warn("array object address: 0x" + Long.toHexString(Magic.objectAsAddress(obj).toLong()) + " #" + i + " : " + obj.toString());
            i++;
        }


        ep.close();
        Factory.close();
    }
}
