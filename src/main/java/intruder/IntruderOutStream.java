package intruder;

import intruder.RPC.RPCClient;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

import java.io.IOException;
import java.util.HashMap;

public class IntruderOutStream extends Stream{
    private RPCClient rpcClient;
    private StageBuffer stageBuffer;
    private HashMap<Object, Integer> obj2HandleMap = new HashMap<Object, Integer>();
    private int writtenItem = 0;
    private boolean useHandle = false;
    private intruder.Queue queue = new intruder.Queue(512);
    private Object[] refArray = new Object[32];
    private AddressArray slotArray = AddressArray.create(32);
    public static boolean debug = false;
    public IntruderOutStream(Endpoint ep) throws IOException{
        super(ep);
        rpcClient = new RPCClient(connectionId);
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex){}
        rpcClient.connect(ep.serverHost);
        if (Utils.enableLog)
            Utils.log("rpc client connected!");
        stageBuffer = new StageBuffer(rpcClient, ep);
    }

    public void enableHandle() {
        this.useHandle = true;
    }
    public void disableHandle() {
        this.useHandle = false;
    }
    public void waitRemoteFinish() throws IOException{
        rpcClient.waitRemoteFinish();
    }
    public void startRPCCount() {
        rpcClient.startCount();
    }
    public String rpcCountReport() {
        return "rpc count, reserve: " + rpcClient.getReserveTimes() + " notify: " + rpcClient.getNotifyTimes();
    }
    public void writeObject(Object object) throws IOException {
        Address jumpSlot = stageBuffer.reserveOneSlot();
        if (object.getClass().isEnum()) {
//            stageBuffer.fillEnum((Enum)object);
            int marker = stageBuffer.fillRootMarker();
            jumpSlot.store(stageBuffer.getRemoteAddress(marker));
            stageBuffer.mayFlush();
            return;
        }
        if (useHandle) {
            Integer handle = obj2HandleMap.get(object);
            if (handle != null) {
                stageBuffer.fillHandle(new Handle(handle));
                return;
            }
            obj2HandleMap.put(object, writtenItem);
        }
        writtenItem++;
        queue.addObjSlot(object, Address.zero());
        while (queue.size() != 0) {
            Address pair = queue.removeObjSlot();
            object = pair.loadObjectReference().toObject();
            Address slot = pair.plus(8).loadAddress();
            if (object == null) {
                assert(false);
                continue;
            }
            if (useHandle) {
                if (object.getClass() == Handle.class) {
                    stageBuffer.fillHandle((Handle) object);
                    continue;
                }
            }
            if (object.getClass().isEnum()) {
//                Address remoteAddress = stageBuffer.fillEnum((Enum)object);
//                if (!slot.isZero())
//                    slot.store(remoteAddress);
                continue;
            }
            Address remoteAddress = stageBuffer.fillObject(object);
            if (!slot.isZero())
                slot.store(remoteAddress);
            int reflen = ObjectModel.getAllReferences(object, refArray, slotArray);
            for (int i = 0; i < reflen; i++) {
                Object o = refArray[i];
//                if (o == null) {
//                    queue.add(null);
                if (!useHandle) {
                    //normal object and enum
                    if (o != null)
                        queue.addObjSlot(o, slotArray.get(i));
                } else {
//                    Integer handle = obj2HandleMap.get(o);
//                    if (handle != null) {
//                        queue.add(new Handle(handle));
//                    } else if (o.getClass().isEnum()) {
//                        queue.add(o);
//                    } else {
//                        obj2HandleMap.put(o, writtenItem);
//                        queue.add(o);
//                    }
                }
                writtenItem++;
            }
        }
        int marker = stageBuffer.fillRootMarker();
        if (debug)
            Utils.log("jump to: 0x" + Long.toHexString(stageBuffer.getRemoteAddress(marker).toLong()));
        jumpSlot.store(stageBuffer.getRemoteAddress(marker));
        stageBuffer.mayFlush();
    }

    public void flush() throws IOException {
        stageBuffer.flush();
    }

    @Override
    public void close() {

    }


}
