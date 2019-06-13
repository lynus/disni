package intruder.tests.intruderV2;

import intruder.*;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetRefObject;
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
        Factory.registerRdmaClass(Integer.class);
        Factory.registerRdmaClass(TargetRefObject.class);
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
        Integer N = 1024;
        outStream.writeObject(N);
        Random rand = new Random();
        for (int i = 0; i < N; i++) {
            long[] array = generateRandomLongArray(1 + rand.nextInt(4097));
//            Utils.log("#" + i + " digest: " + Long.toHexString(array[0]));
            outStream.writeObject(array);
        }
        outStream.flush();

        TargetRefObject[] array = new TargetRefObject[8];
        TargetRefObject ele = new TargetRefObject();
        ele.ref2 = new TargetPrimitiveObject();
        for (int i = 0; i < array.length; i++) {
            array[i] = ele;
        }
        outStream.writeObject(array);
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
