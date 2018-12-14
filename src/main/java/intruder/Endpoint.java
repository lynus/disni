package intruder;

import com.ibm.disni.RdmaActiveEndpoint;
import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.verbs.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

import org.jikesrvm.classloader.RVMArray;
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

import static intruder.RdmaClassIdManager.SCALARTYPEMASK;
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
        if (IbvWC.IbvWcStatus.valueOf(ibvWC.getStatus()) != IbvWC.IbvWcStatus.IBV_WC_SUCCESS) {
            System.out.println("WC error!!!!!!");
            System.out.println(IbvWC.IbvWcStatus.valueOf(ibvWC.getStatus()).toString());
        }
        if (ibvWC.getWr_id() == 2323)
            System.out.println("WC got array receive!");
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
    public void readyToReiveId() throws IOException {
        idBuf.execRecv();
    }
    public void sendIds(Class cls) throws IOException, InterruptedException {
        idBuf.sendIds(cls);
    }
    public void sendArrayId(Class cls, int len) throws IOException, InterruptedException {
        idBuf.sendArrayId(cls, len);
    }

    public int waitIds() throws IOException, InterruptedException {
        return idBuf.waitIds();
    }

    public int getArrayLength() {
        return idBuf.getArrayLength();
    }

    public void waitIdsAck() throws IOException, InterruptedException {
        idBuf.waitIdsAck();
    }

    public void sendIdsAck() throws IOException, InterruptedException {
        idBuf.sendIdsAck();
    }

    public static class AllocBuf{
        Address start;
        Object object;
        int len;
    }

    protected AllocBuf allocateScalar(RVMClass cls) {
        //see MemoryManager.allocateScalar()
        int allocator = Plan.ALLOC_NON_MOVING;
        int site = MemoryManager.getAllocationSite(false);
        int align = 8;
        int offset = ObjectModel.getOffsetForAlignment(cls, false);
        int size = cls.getInstanceSize();
        AllocBuf allocBuf = new AllocBuf();
        Selected.Mutator mutator = Selected.Mutator.get();
        int bytes = org.jikesrvm.runtime.Memory.alignUp(cls.getInstanceSize(), MIN_ALIGNMENT);
        Address region = mutator.alloc(bytes, align, offset, allocator, site);
        Object obj = ObjectModel.initializeScalar(region, cls.getTypeInformationBlock(), size);
        mutator.postAlloc(ObjectReference.fromObject(obj), ObjectReference.fromObject(cls.getTypeInformationBlock()),
                size, allocator);

        if (cls.hasFinalizer())
            MemoryManager.addFinalizer(obj);
        allocBuf.object =obj;
        allocBuf.start = region.plus(JavaHeader.minimumObjectSize());
        allocBuf.len = size - JavaHeader.minimumObjectSize();
        return allocBuf;
    }

    protected AllocBuf allocateScalar(RVMArray array, int len) throws  IOException{
        if (!array.isInitialized()) {
            array.resolve();
            array.instantiate();
            array.initialize();
        }
        int allocator = Plan.ALLOC_NON_MOVING;
        int site = MemoryManager.getAllocationSite(false);
        int align = ObjectModel.getAlignment(array);
        int offset = ObjectModel.getOffsetForAlignment(array, false);
        int headersize = ObjectModel.computeArrayHeaderSize(array);
        int size = (len << array.getLogElementSize()) + headersize;

        Selected.Mutator mutator = Selected.Mutator.get();
        int bytes = org.jikesrvm.runtime.Memory.alignUp(size, MIN_ALIGNMENT);
        Address region = mutator.alloc(bytes, align, offset, allocator, site);
        Object obj = ObjectModel.initializeArray(region, array.getTypeInformationBlock(), len, size);
        mutator.postAlloc(ObjectReference.fromObject(obj), ObjectReference.fromObject(array.getTypeInformationBlock()),
                size, allocator);
        AllocBuf allocBuf = new AllocBuf();
        allocBuf.object = obj;
        allocBuf.start = region.plus(JavaHeader.OBJECT_REF_OFFSET);
        allocBuf.len = size - headersize;
        return allocBuf;
    }

    public Object prepareObject(int id) throws IOException {
        RVMType type = Utils.ensureIdValid(id);
        AllocBuf allocBuf = allocateScalar(type.asClass());
        Utils.postReceiveObject(allocBuf.start, allocBuf.len, this);
        //note obj is empty
        return allocBuf.object;
    }

    public  Object prepareArray(int id, int len) throws IOException {
        RVMType type = Utils.ensureIdValid(id);
        AllocBuf allocBuf = allocateScalar(type.getArrayTypeForElementType(), len);
        Object[] array = (Object[])allocBuf.object;
        AllocBuf[] elemAllocBuf = new AllocBuf[len];
        for (int i = 0; i < len; i++) {
            elemAllocBuf[i] = allocateScalar(type.asClass());
            array[i] = elemAllocBuf[i].object;
        }
        Utils.postReceiveMultiObjects(elemAllocBuf, this);
        return allocBuf.object;
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

    public void sendArray(Object[] array) throws IOException, InterruptedException {
        RVMType type = java.lang.JikesRVMSupport.getTypeForClass(array.getClass().getComponentType());
        if (type == null || !type.isInitialized()) {
            throw new IOException(("component class not found or not initialized!"));
        }
        RVMClass cls = type.asClass();
        AllocBuf[] allocBufs = new AllocBuf[array.length];
        for (int i = 0; i < allocBufs.length; i++) {
            allocBufs[i] = new AllocBuf();
            allocBufs[i].start = Magic.objectAsAddress(array[i]).minus(JavaHeaderConstants.ARRAY_LENGTH_BYTES);
            allocBufs[i].len = cls.getInstanceSize() - JavaHeader.minimumObjectSize();
        }
        Utils.postSendMultiObjects(allocBufs, this);
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
            buf.clear();
        }

        public int waitIds() throws InterruptedException, IOException {
            IbvWC wc = wcEvents.take();
            if (wc.getWr_id() != 99L) {
                throw new IOException("expected readid complete");
            }
            int num = buf.getInt();
            Class cls = Factory.query(num & SCALARTYPEMASK);
            System.out.println("get num of id: " + Integer.toHexString(num) + " class: " + cls.getCanonicalName());
            return num;
        }

        public int getArrayLength() {
            return buf.getInt();
        }
        public void sendIds(Class cls) throws  IOException, InterruptedException {
            int id = Factory.query(cls);
            if (id == -1)
                throw new IOException("type not registered: " + cls.getCanonicalName());
            buf.clear();
            buf.putInt(id);
            svcPostSend.execute();
            IbvWC wc = wcEvents.take();
            if (wc.getWr_id() != 98L) {
                throw new IOException("expected sendIds complete");
            }
        }

        public void sendArrayId(Class<?> cls, int len) throws IOException, InterruptedException {
            Class elemCls = cls.getComponentType();
            int id = Factory.query(elemCls);
            if (id == -1)
                throw new IOException("type not registered: " + cls.getCanonicalName());
            id = id | RdmaClassIdManager.ARRAYTYPE;
            buf.clear();
            buf.putInt(id).putInt(len);
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
