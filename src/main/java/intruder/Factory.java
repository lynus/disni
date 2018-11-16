package intruder;

import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.RdmaEndpointFactory;
import com.ibm.disni.verbs.RdmaCmId;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Factory implements RdmaEndpointFactory<Endpoint> {
    static private RdmaActiveEndpointGroup<Endpoint> group;
    static private Factory self;
    private Factory() {}
    static {
        try {
            self = new Factory();
            group = new RdmaActiveEndpointGroup<Endpoint>(10, false, 64, 16, 64);
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
}
