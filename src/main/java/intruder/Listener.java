package intruder;

import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.RdmaServerEndpoint;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Listener {
    private InetSocketAddress bindAddress;
    private RdmaServerEndpoint<Endpoint> serverEndpoint;
    private RdmaActiveEndpointGroup<Endpoint> group;

    public Listener(InetSocketAddress address, RdmaActiveEndpointGroup group) throws Exception {
        bindAddress = address;
        this.group = group;
        serverEndpoint = group.createServerEndpoint();
        serverEndpoint.bind(bindAddress, 10);
    }

    public Endpoint accept() throws IOException {
        Endpoint ep = serverEndpoint.accept();
        ep.serverHost = bindAddress.getAddress();
        return ep;
    }

    public void close() throws IOException, InterruptedException {
        serverEndpoint.close();
    }

}
