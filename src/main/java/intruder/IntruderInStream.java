package intruder;

import intruder.RPC.RPCService;

import java.io.IOException;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer;

    public IntruderInStream(Endpoint ep) throws IOException{
        super(ep);
        RPCService.setHost(ep.serverHost);
        RPCService.register(this);
    }

    public Object readObject() throws IOException {
        return null;
    }
    @Override
    public void close() {

    }

    public LocalBuffer getLocalBuffer() {
        LocalBuffer _buffer = new LocalBuffer();
        if (firstBuffer == null) {
            firstBuffer = _buffer;
            lastBuffer = _buffer;
        }
            lastBuffer.setNextBuffer(_buffer);
            lastBuffer = _buffer;
        return _buffer;
    }
    public LocalBuffer getLastBuffer() {
        return lastBuffer;
    }
}
