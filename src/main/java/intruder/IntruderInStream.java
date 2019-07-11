package intruder;

import intruder.RPC.RPCService;
import org.vmmagic.unboxed.Address;

import java.io.IOException;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer, currentBuffer;
    public LocalBuffer retireBuffer;
    private volatile boolean finish = false;
    private boolean useHandle = false;
    public IntruderInStream(Endpoint ep) throws IOException {
        super(ep);
        RPCService.setHost(ep.serverHost);
        RPCService.register(this);
    }

    public Object readObject() throws IOException {
        Object root;
        while(currentBuffer == null) {}
        if (currentBuffer.reachLimit()) {
            while(!currentBuffer.isConsumed() && currentBuffer.reachLimit()) {}
            if (currentBuffer.isConsumed()) {
                LocalBuffer _buffer = currentBuffer.getNextBuffer();
                while (_buffer == null)
                    _buffer = currentBuffer.getNextBuffer();
                currentBuffer = _buffer;
            }
        }
        Address jump = currentBuffer.getJump();
        if (currentBuffer.reachLimit()) {
            currentBuffer = currentBuffer.getNextBuffer();
            assert (currentBuffer != null);
        }
        root = currentBuffer.getRoot();
        assert(root != null);
        assert((jump.toLong() & 7) ==0);
        if (jump.loadLong() != Stream.ROOTMARKER)
            throw new IOException("expedted ROOT MARKER");
        if (currentBuffer.inRange(jump)) {
            currentBuffer.setPointer(jump.plus(8));
        } else {
            currentBuffer = currentBuffer.getNextBuffer();
            assert(currentBuffer != null);
            currentBuffer.setPointer(jump.plus(8));
        }
        return root;
    }
    @Override
    public void close() {
    }

    public LocalBuffer getLocalBuffer() {
        LocalBuffer _buffer = new LocalBuffer();
        if (firstBuffer == null) {
            retireBuffer = null;
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

    public void setFinish() {
        finish = true;
    }
    public void setUnfinish() {
        finish = false;
    }
    public boolean isFinish() {
        return finish;
    }
}
