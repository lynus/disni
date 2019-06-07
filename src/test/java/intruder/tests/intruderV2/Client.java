package intruder.tests.intruderV2;

import intruder.*;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetSimpleObject;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

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
        outStream.writeObject(target);
        target.d = 'y';
        outStream.writeObject(target);
        outStream.flush();
        for (int i = 0; i < 512; i++) {
            long[] array = generateRandomLongArray(4096);
            Utils.log("#" + i + " digest: " + Long.toHexString(array[0]));
            outStream.writeObject(array);
        }
        outStream.flush();

        System.out.println("outstream connectionID: " + outStream.getConnectionId());
        System.gc();
        System.in.read();
    }

    private static long [] generateRandomLongArray(int length) {
        long []ret = new long[length];
        Random rand = new Random();
        MessageDigest md = null;
        long v;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {}
        for (int i = 1; i < length; i++) {
            v = rand.nextLong();
            ret[i] = v;
            for (int j = 0; j < 8; j++) {
//                byte tmp = (byte)(v & 0xff);
                md.update((byte)v);
                v = v >>> 8;
            }
        }
        byte[] digest = md.digest();
        Utils.memcopyAligned4(ObjectModel.getArrayAddress(digest), ObjectModel.getArrayAddress(ret), 8);
        return ret;
    }
}
