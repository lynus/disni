package intruder;

import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.RdmaEndpointFactory;
import com.ibm.disni.verbs.RdmaCmId;
import org.jikesrvm.classloader.RVMType;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Factory implements RdmaEndpointFactory<Endpoint> {
    static private RdmaActiveEndpointGroup<Endpoint> group;
    static private Factory self;
    static private RdmaClassIdManager idManager = new RdmaClassIdManager();
    private Factory() {}
    static {
        try {
            self = new Factory();
            group = new RdmaActiveEndpointGroup<Endpoint>(100, false, 64, Utils.MAXSGEPERWR, 64);
            group.init(self);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Endpoint createEndpoint(RdmaCmId rdmaCmId, boolean serverSide) throws IOException {
        return new Endpoint(group, rdmaCmId, serverSide);
    }

    static public Listener newListener(InetSocketAddress address) throws Exception {
        return new Listener(address, group);
    }

    static public Endpoint newEndpoint() throws IOException {
        return group.createEndpoint();
    }

    static public int registerRdmaClass(Class cls) {
        return idManager.registerClass(cls);
    }

    //dimension field in the id is strip before call this method.
    static public RVMType query(int id) {
        return idManager.query(id);
    }

    //argument cls can be both array or scalar class
    static public int query(Class cls) {
        RVMType type = ObjectModel.getInnerMostEleType(cls);
        return idManager.query(type);
    }

    static public Enum query(int id, int ordinal) {
        return idManager.queryEnum(id, ordinal);
    }

    static public void close() throws InterruptedException, IOException {
        group.close();
    }
    static public void useODP() {
        group.useODP();
    }
}
