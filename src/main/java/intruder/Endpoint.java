package intruder;

import com.ibm.disni.RdmaActiveEndpoint;
import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.verbs.IbvWC;
import com.ibm.disni.verbs.RdmaCmId;

import java.io.IOException;

import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.ObjectModel;

public class Endpoint extends RdmaActiveEndpoint {
    public Endpoint(RdmaActiveEndpointGroup<? extends RdmaActiveEndpoint> group, RdmaCmId idPriv, boolean serverSide) throws IOException {
        super(group, idPriv, serverSide);
    }

    @Override
    public void dispatchCqEvent(IbvWC ibvWC) throws IOException {

    }

    @Override
    protected synchronized void init() throws IOException {
        super.init();
    }

    public int writeObject(Object obj) throws IOException {
        RVMType type = ObjectModel.getObjectType(obj);
        if (!type.isRDMAObject()) {
            throw new IOException("target object is not of RDMA type.");
        }
        return 0;
    }

    public Object readObject() {
        return null;
    }

    public void registerHeap() throws IOException {

    }
}
