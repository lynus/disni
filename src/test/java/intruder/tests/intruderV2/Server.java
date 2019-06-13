package intruder.tests.intruderV2;

import intruder.*;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetRefObject;
import intruder.tests.TargetSimpleObject;
import org.vmmagic.unboxed.AddressArray;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Server {
    public static void main(String []args) throws Exception {
//        Factory.useODP();
        Factory.registerRdmaClass(TargetPrimitiveObject.class);
        Factory.registerRdmaClass(TargetSimpleObject.class);
        Factory.registerRdmaClass(Integer.class);
        Factory.registerRdmaClass(TargetRefObject.class);
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

        Integer N = (Integer)instream.readObject();
        long [][] arrays = new long[N][];
        for (int i = 0; i < arrays.length; i++) {
            arrays[i] = (long[])instream.readObject();
        }
        int i = 0;
        for (long[] array : arrays) {
            boolean pass = checkRandomLongArray(array);
            if (!pass)
                Utils.log("check received array #" + i + " failed!");
            i++;
        }
        Utils.log("done checking received arrays");

        TargetRefObject[] array = (TargetRefObject[])instream.readObject();
        Utils.log("array length: " + array.length);
        for (TargetRefObject obj : array) {
            Utils.log(obj.toString());
        }
        System.in.read();
    }

    private static boolean checkRandomLongArray(long[] array) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {}
        for (int i = 1; i < array.length; i++) {
            long v = array[i];
            for (int j = 0; j < 8; j++) {
//                byte tmp = (byte) (v & 0xff);
                md.update((byte)v);
                v = v >>> 8;
            }
        }
        byte[] digest = md.digest();
        long[] v = new long[1];
        Utils.memcopyAligned4(ObjectModel.getArrayAddress(digest), ObjectModel.getArrayAddress(v), 8);
        return v[0] == array[0];
    }

}