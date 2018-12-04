package intruder;

import com.ibm.disni.RdmaActiveEndpoint;
import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.verbs.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.JavaHeaderConstants;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Magic;
import org.mmtk.plan.Plan;
import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.heap.layout.HeapLayout;
import org.mmtk.utility.heap.layout.Map64;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;

import static org.mmtk.utility.Constants.MIN_ALIGNMENT;

public class Endpoint extends RdmaActiveEndpoint {
    static private Space rdmaSpace = Plan.nonMovingSpace;
    private Address baseAddress, mappedMark;
    static private final int ReservedSpaceForReceive = 1024;
    static private final int allocator = Plan.ALLOC_NON_MOVING;
    int heapLKey;
    private ArrayBlockingQueue<IbvWC> wcEvents;
    protected IdBuf idBuf;
    public Endpoint(RdmaActiveEndpointGroup<? extends RdmaActiveEndpoint> group, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(group, idPriv, serverSide);
        wcEvents = new ArrayBlockingQueue<IbvWC>(10);
    }

    @Override
    public void dispatchCqEvent(IbvWC ibvWC) throws IOException {
        wcEvents.add(ibvWC);
    }

    public  IbvWC waitEvent() throws InterruptedException {
        return wcEvents.take();
    }
    @Override
    protected synchronized void init() throws IOException {
        super.init();
        idBuf = new IdBuf(32);
        idBuf.execRecv();
    }

    public void writeObject(Object obj) throws IOException {
        RVMType type = ObjectModel.getObjectType(obj);
        if (!type.isRDMAObject())
            throw new IOException("target object is not");


    }

    public void sendIds(Class cls) throws IOException, InterruptedException {
        idBuf.sendIds(cls);
    }

    public int waitIds() throws IOException, InterruptedException {
        return idBuf.waitIds();
    }

    public void waitIdsAck() throws IOException, InterruptedException {
        idBuf.waitIdsAck();
    }

    public void sendIdsAck() throws IOException, InterruptedException {
        idBuf.sendIdsAck();
    }

    public Object prepareObject(int id) throws IOException {
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(Factory.query(id));
        if (type == null || !type.isInitialized()) {
            throw new IOException("class not found or not initialized!");
        }


        //allocate space
        RVMClass cls = type.asClass();
        //see MemoryManager.allocateScalar()
        int allocator = Plan.ALLOC_NON_MOVING;
        int site = MemoryManager.getAllocationSite(false);
        int align = 8;
        int offset = ObjectModel.getOffsetForAlignment(cls, false);
        int size = cls.getInstanceSize();
        Selected.Mutator mutator = Selected.Mutator.get();
        int bytes = org.jikesrvm.runtime.Memory.alignUp(cls.getInstanceSize(), MIN_ALIGNMENT);
        Address region = mutator.alloc(bytes, align, offset, allocator, site);
        Object obj = ObjectModel.initializeScalar(region, cls.getTypeInformationBlock(), size);
        mutator.postAlloc(ObjectReference.fromObject(obj), ObjectReference.fromObject(cls.getTypeInformationBlock()),
                size, allocator);

        if (cls.hasFinalizer())
            MemoryManager.addFinalizer(obj);
        //end of allocation
        Utils.postReceiveObject(region.plus(JavaHeader.minimumObjectSize()), size - JavaHeader.minimumObjectSize(), this);
        //note obj is empty
        return obj;
    }

    public void sendObject(Object obj) throws IOException, InterruptedException {
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(obj.getClass());
        if (type == null || !type.isInitialized()) {
            throw new IOException("class not found or not initialized!");
        }

        RVMClass cls = type.asClass();
        Address region = Magic.objectAsAddress(obj).minus(JavaHeaderConstants.ARRAY_LENGTH_BYTES);
        int size = cls.getInstanceSize() - JavaHeader.minimumObjectSize();
        Utils.postSendObject(region, size, this);
    }
    public void registerHeap() throws IOException {
        baseAddress = ((Map64) HeapLayout.vmMap).getSpaceBaseAddress(rdmaSpace);
        System.out.println("registerHeap rdmaspace base: " + Long.toHexString(baseAddress.toLong()));
        mappedMark = ((MarkSweepSpace)rdmaSpace).getMappedHighMark();
        System.out.println("get mapped to highMark: " + Long.toHexString(mappedMark.toLong()));
        IbvMr mr = registerMemory(baseAddress.toLong(), mappedMark.diff(baseAddress).toInt()).execute().free().getMr();
        heapLKey = mr.getLkey();
    }

    protected class IdBuf {
        private ByteBuffer buf;
        private IbvMr mr;
        private IbvSge sge;
        private LinkedList<IbvSge> sgeList;
        private IbvRecvWR wr;
        private IbvSendWR sendWR;
        private LinkedList<IbvRecvWR> wrList;
        private LinkedList<IbvSendWR> sendwrList;
        private SVCPostRecv svcPostRecv;
        private SVCPostSend svcPostSend;
        public IdBuf(int maxId) throws IOException {
            buf = ByteBuffer.allocateDirect(1024);
            mr = registerMemory(buf).execute().free().getMr();
            sge = new IbvSge();
            sge.setAddr(mr.getAddr());
            sge.setLength(mr.getLength());
            sge.setLkey(mr.getLkey());

            sgeList = new LinkedList<IbvSge>();
            sgeList.add(sge);

            wr = new IbvRecvWR();
            wr.setSg_list(sgeList);
            wr.setWr_id(99L);
            wrList = new LinkedList<IbvRecvWR>();
            wrList.add(wr);

            sendWR = new IbvSendWR();
            sendWR.setSg_list(sgeList);
            sendWR.setWr_id(98L);
            sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
            sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);

            sendwrList = new LinkedList<IbvSendWR>();
            sendwrList.add(sendWR);

            svcPostRecv = postRecv(wrList);
            svcPostSend = postSend(sendwrList);
        }

        public void execRecv() throws IOException {
            svcPostRecv.execute();
        }

        public int waitIds() throws InterruptedException, IOException {
            IbvWC wc = wcEvents.take();
            if (wc.getWr_id() != 99L) {
                throw new IOException("expected readid complete");
            }
            int num = buf.getInt();
            Class cls = Factory.query(num);
            System.out.println("get num of id: " + num + " class: " + cls.getCanonicalName());
            return num;
        }
        public void sendIds(Class cls) throws  IOException, InterruptedException {
            int id = Factory.query(cls);
            if (id == -1)
                throw new IOException("type not registered: " + cls.getCanonicalName());
            buf.putInt(id);
            svcPostSend.execute();
            IbvWC wc = wcEvents.take();
            if (wc.getWr_id() != 98L) {
                throw new IOException("expected sendIds complete");
            }
        }
        public void waitIdsAck() throws InterruptedException, IOException {
            IbvWC wc = wcEvents.take();
            if (wc.getWr_id() != 99L) {
                throw new IOException("expected waitIdsAck complete");
            }
            buf.clear();
            int num = buf.getInt();
            System.out.println("get peer ready msg: 0x" + Integer.toHexString(num));
        }

        public void sendIdsAck() throws IOException, InterruptedException {
            buf.clear();
            buf.putInt(0xbeef);
            //svcPostSend.getWrMod(0).getSgeMod(0).setLength(4);
            svcPostSend.execute();
            IbvWC wc = wcEvents.take();
            if (wc.getWr_id() != 98L) {
                throw new IOException("expected sendIdsAck complete");
            }
        }

    }

}
