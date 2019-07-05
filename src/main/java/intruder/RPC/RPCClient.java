package intruder.RPC;

import com.ibm.darpc.*;
import intruder.RemoteBuffer;
import intruder.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class RPCClient {
    private DaRPCClientGroup<Request, Response> rpcClientGroup;
    private DaRPCClientEndpoint<Request,Response> rpcEp;
    private DaRPCStream<Request, Response> stream;
    private int connectId;
    private int notifyTimes = 0, reserveTimes = 0;
    private boolean startCount;
    public void startCount() {
        this.startCount = true;
    }
    public RPCClient(int connectId) {
        System.err.println("new rpcclient id "+connectId);
        this.connectId = connectId;
    }
    public void connect(InetAddress address) throws IOException {
        try {
            rpcClientGroup = DaRPCClientGroup.createClientGroup(new Protocol(), 10, 0, 10, 10);
        } catch (Exception ex) {
            throw new IOException("failed to create rpc client group");
        }
        rpcEp = rpcClientGroup.createEndpoint();
        InetSocketAddress remote = new InetSocketAddress(address, RPCService.PORT);
        try {

            rpcEp.connect(remote, 10);
        } catch (Exception ex) {
            System.err.println("connect error: " + ex.getMessage());
            ex.printStackTrace();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException _ex) {}
            try {
                rpcEp.close();
                rpcEp = rpcClientGroup.createEndpoint();
                rpcEp.connect(remote, 10);
            } catch (Exception _ex) {
                System.err.println("reconnect error: " + ex.getMessage());
                throw new IOException("rpc client connect failed");
            }
        }
        stream = rpcEp.createStream();
    }
    public void reserveBuffer(RemoteBuffer buffer) throws IOException{
        if (startCount)
            reserveTimes++;
        Request request = new Request(connectId, new Request.ReserveBufferREQ());
        Response response = new Response();
        long start = System.nanoTime();
        DaRPCFuture<Request, Response> future = stream.request(request, response, false);
        while(!future.isDone()) {}
        if (response.status != Response.SUCCESS)
            throw new IOException("reserveBuffer rpc failed");
        long end = System.nanoTime();
        Response.ReserveBufferRES msg = response.reserveBufferRES;
        buffer.setup(msg.rkey, msg.start, msg.size, this);
        if (Utils.enableLog)
            Utils.log("reserveBuffer start: " + Long.toHexString(msg.start) + " rpc time: " + (end - start));
    }

    public void notifyBufferLimit(long bufferStart, int limit, boolean needGap) throws IOException{
        if (startCount)
            notifyTimes++;
        Request request = new Request(connectId, new Request.NotifyBufferLimitREQ(bufferStart, limit, needGap));
        Response response = new Response();
        long start = System.nanoTime();
        DaRPCFuture<Request, Response> future = stream.request(request, response, false);
        while (!future.isDone()) {}
        if (response.status != Response.SUCCESS)
            throw new IOException("notify buffer limit rpc failed");
        long during = System.nanoTime() - start;
        if (Utils.enableLog)
            Utils.log("notify buffer limit success rpc time: " + during);
    }

    public void releaseAndReserve(RemoteBuffer buffer) throws IOException {
        Request request = new Request(connectId, new Request.ReleaseAndReserveREQ(buffer.getStart().toLong()));
        Response response = new Response();
        DaRPCFuture<Request, Response> future = stream.request(request, response, false);
        while (!future.isDone()) {}
        if (response.status != Response.SUCCESS)
            throw new IOException("release_and_reserve rpc failed");
        Response.ReleaseAndReserveRES msg = response.releaseAndReserveRES;
        buffer.setup(msg.rkey, msg.start, msg.size, this);
        if (Utils.enableLog)
            Utils.log("release_and_reserve success");
    }
    public void waitRemoteFinish() throws IOException {
        Request request = new Request(connectId, new Request.WaitFinishREQ());
        Response response = new Response();
        DaRPCFuture<Request, Response> future = stream.request(request, response, false);
        while (!future.isDone()) {}
        if (response.status != Response.SUCCESS)
            throw new IOException("wait finish rpc failed");
    }

    public int getNotifyTimes() {
        return notifyTimes;
    }
    public int getReserveTimes() {
        return reserveTimes;
    }

}
