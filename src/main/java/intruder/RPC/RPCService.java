package intruder.RPC;

import com.ibm.darpc.DaRPCServerEndpoint;
import com.ibm.darpc.DaRPCServerEvent;
import com.ibm.darpc.DaRPCServerGroup;
import com.ibm.darpc.DaRPCService;
import com.ibm.disni.RdmaServerEndpoint;
import intruder.Buffer;
import intruder.IntruderInStream;
import intruder.LocalBuffer;
import intruder.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

public class RPCService extends Protocol implements DaRPCService<Request, Response> {
    private static RPCService instance;
    public static final int PORT = 9090;
    private DaRPCServerGroup<Request, Response> serverGroup;
    private RdmaServerEndpoint<DaRPCServerEndpoint<Request, Response>> serverEp;
    private ConcurrentHashMap<Integer, IntruderInStream> hashMap = new ConcurrentHashMap<Integer, IntruderInStream>();
    private static InetAddress host;
    public static void setHost(InetAddress _host) {
        host = _host;
    }
    private RPCService() throws Exception {
        super();
        if (host == null)
            throw new IOException("RPCService host not specified");
        long[] affinity = new long[]{1};
        serverGroup = DaRPCServerGroup.createServerGroup(this, affinity, 10,
                0, true, 10,10,10,32);
        serverEp = serverGroup.createServerEndpoint();
        serverEp.bind(new InetSocketAddress(host, PORT),10);
        Acceptor acceptor = new Acceptor();
        acceptor.start();
    }

    public static synchronized  RPCService getInstance() throws IOException{
       if (instance == null)
           try {
               instance = new RPCService();
           } catch (Exception ex) {
               throw new IOException(ex.getMessage());
           }
       return instance;
    }

    public static void register(IntruderInStream stream) throws IOException {
        getInstance().hashMap.put(stream.getConnectionId(), stream);
    }
    @Override
    public void processServerEvent(DaRPCServerEvent<Request, Response> event) throws IOException {
        Request request = event.getReceiveMessage();
        Response response = event.getSendMessage();
        IntruderInStream inStream = hashMap.get(request.connectId);
        LocalBuffer buffer;
        if (inStream == null) {
            response.fail(request.cmd);
            System.err.println("inStream not found, requested connectId: " + request.connectId + " hash id: " + hashMap.hashCode());
        } else {
            switch (request.cmd) {
            case Request.RESERVE_BUFFER_CMD:
                buffer = inStream.getLastBuffer();
                if (buffer != null)
                    buffer.markConsumed();
                buffer = inStream.getLocalBuffer();
                try {
                    Buffer.allocate(buffer);
                    buffer.register(inStream.getEp());
                    response.setReserveBufferRES(new Response.ReserveBufferRES(buffer.getStart().toLong(),
                            buffer.getLength().toInt(), buffer.getRkey()));
                } catch (IOException ex) {
                    ex.printStackTrace();
                    response.fail(Request.RESERVE_BUFFER_CMD);
                }
                break;
            case Request.NOTIFY_BUFFER_LIMIT_CMD:
                int limit = request.notifyBufferLimitREQ.getLimit();
                long bufferStart = request.notifyBufferLimitREQ.getBufferStart();
                boolean needGap = request.notifyBufferLimitREQ.needGap();
                buffer = inStream.getLastBuffer();
                if (buffer.getStart().toLong() != bufferStart) {
                    response.fail(Request.NOTIFY_BUFFER_LIMIT_CMD);
                    break;
                }
                buffer.setLimit(limit, needGap);
                response.setNotifyBufferLimitRES(new Response.NotifyBufferLimitRES());
                if (Utils.enableLog) {
                    if (needGap)
                        Utils.log("rpc limit: " + limit + " gap filled");
                    else
                        Utils.log("rpc limit: " + limit);
                }
                //buffer.peekBytes(0, 64, 3);
                break;
            case Request.RELEASE_AND_RESERVE_CMD:
                break;
            case Request.WAIT_FINISH_CMD:
                response.setWaitFinishRES(new Response.WaitFinishRES());
                while(true) {
                    if (!inStream.isFinish()) {}
                    else {
                        inStream.setUnfinish();
                        break;
                    }
                }
            }
        }
        event.triggerResponse();
    }


    @Override
    public void open(DaRPCServerEndpoint<Request, Response> rpcClientEndpoint) {
    }

    @Override
    public void close(DaRPCServerEndpoint<Request, Response> rpcClientEndpoint) {


    }

    private class Acceptor extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    serverEp.accept();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    System.exit(-1);
                }
            }
        }
    }
}
