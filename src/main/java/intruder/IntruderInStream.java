package intruder;

import intruder.RPC.RPCService;
import org.vmmagic.unboxed.Address;

import java.io.IOException;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer, currentBuffer;

    public IntruderInStream(Endpoint ep) throws IOException {
        super(ep);
        RPCService.setHost(ep.serverHost);
        RPCService.register(this);
    }

    public Object readObject() throws IOException {
        while(currentBuffer == null) {}
        LocalBuffer.AddrBufferRet ret = LocalBuffer.getNextAddr(currentBuffer);
        currentBuffer = ret.getLocalBuffer();
        Address header = ret.getAddr();
        Utils.log("get header address: 0x" + Long.toHexString(header.toLong()));
        /*设定header
          VM 部分 TIB status（lock，hash status)
          GC 部分
        */
        Object obj = ObjectModel.initializeHeader(header);
        return obj;
    }
    @Override
    public void close() {
    }

    public LocalBuffer getLocalBuffer() {
        LocalBuffer _buffer = new LocalBuffer();
        if (firstBuffer == null) {
            firstBuffer = _buffer;
            lastBuffer = _buffer;
            currentBuffer = _buffer;
        }
            lastBuffer.setNextBuffer(_buffer);
            lastBuffer = _buffer;
        return _buffer;
    }
    public LocalBuffer getLastBuffer() {
        return lastBuffer;
    }
}
