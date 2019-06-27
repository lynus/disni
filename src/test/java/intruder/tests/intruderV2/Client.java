package intruder.tests.intruderV2;

import intruder.*;
import intruder.tests.TargetPrimitiveObject;
import intruder.tests.TargetRefObject;
import intruder.tests.TargetSimpleObject;
import intruder.tests.TestEnum;

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
        Factory.registerRdmaClass(String.class);
        Factory.registerRdmaClass(TestEnum.class);
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

        TargetRefObject r1 = new TargetRefObject();
        TargetRefObject r2= new TargetRefObject();
        TargetRefObject r3 = new TargetRefObject();
        r1.ref3 = r2; r2.ref3 = r3; r3.ref3 = r1;
        outStream.writeObject(r1);
        outStream.writeObject(r2);
        outStream.writeObject(r3);

        outStream.flush();
        TargetRefObject[] refObjects = new TargetRefObject[128];
        for (int i = 0; i < refObjects.length; i++) {
            if (i % 3 == 0)
                refObjects[i] = r1;
            else if (i % 3 == 1)
                refObjects[i] = r2;
            else
                refObjects[i] = r3;
        }
        outStream.writeObject(refObjects);

        TargetSimpleObject[] bigArray = new TargetSimpleObject[2048];
        for (int i = 0; i < bigArray.length; i++)
            bigArray[i] = new TargetSimpleObject();
        TargetSimpleObject[][] sharedElementArray = new TargetSimpleObject[4000][];
        for (int i = 0; i < sharedElementArray.length; i++)
            sharedElementArray[i] = bigArray;
        outStream.writeObject(sharedElementArray);
        outStream.flush();

        String str = new String("hello world!");
        outStream.writeObject(str);
        TestEnum myday = TestEnum.FRIDAY;
        outStream.writeObject(myday);
        outStream.flush();

        outStream.disableHandle();
        r1.ref1 = new TargetPrimitiveObject();
        r2.ref1 = r1.ref1;
        r3.ref2 = r1.ref1;
        r1.ref3 = r2.ref3 = r3.ref3 = null;
        outStream.writeObject(r1);
        outStream.writeObject(r2);
        outStream.writeObject(r3);
        outStream.flush();

        outStream.enableHandle();
        outStream.writeObject(r1);
        outStream.writeObject(r2);
        outStream.writeObject(r3);
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
