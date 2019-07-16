package intruder;

import intruder.RPC.RPCService;
import org.vmmagic.unboxed.Address;

import java.io.IOException;

public class IntruderInStream extends Stream {
    private LocalBuffer firstBuffer, lastBuffer, currentBuffer, noBoundryBuffer;
    public LocalBuffer retireBuffer;
    private volatile boolean finish = false;
    private boolean useHandle = false;
    public boolean debug = false;
    private volatile boolean ready = false;
    public IntruderInStream(Endpoint ep) throws IOException {
        super(ep);
        RPCService.setHost(ep.serverHost);
        RPCService.register(this);
    }

    public Object readObject() throws IOException {
        Object root;
        while(currentBuffer == null) {}
        while (true) {
            if (currentBuffer.getBoundry() == 0) {
                if (currentBuffer.reachLimit()) {
                    continue;
                } else
                    break;
            } else {
                if (currentBuffer.reachBoundry()) {
                    currentBuffer = currentBuffer.getNextBuffer();
                    assert(currentBuffer != null);
                    continue;
                } else
                    break;
            }
        }
        Address jump = currentBuffer.getJump();
        while (jump == Address.zero())
            jump = currentBuffer.getJump1();

        if (currentBuffer.reachBoundry()) {
            currentBuffer = currentBuffer.getNextBuffer();
            Utils.log("jump to next buffer 0x" + Long.toHexString(currentBuffer.getStart().toLong()));
            assert (currentBuffer != null);
            while (currentBuffer.reachLimit()) {}
        }
        root = currentBuffer.getRoot();
        assert(root != null);
        assert((jump.toLong() & 7) ==0);
        long tmp = jump.loadLong();
        while (tmp != Stream.ROOTMARKER) {
//            Utils.peekBytes("localbuffer", jump, 0, 48, 3);
//            Utils.log("expected ROOT MARKER addr 0x" + Long.toHexString(jump.toLong()) +
//                    " delta: " + jump.diff(currentBuffer.getStart()).toLong()
//                    + " value " + Long.toHexString(jump.loadLong()) + " tmp " + tmp);
            tmp = jump.loadLong();
        }
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
            noBoundryBuffer = _buffer;
        }
            lastBuffer.setNextBuffer(_buffer);
            lastBuffer = _buffer;
        return _buffer;
    }
    public LocalBuffer getLastBuffer() {
        return lastBuffer;
    }
    public LocalBuffer getNoBoundryBuffer() {
        return noBoundryBuffer;
    }

    public void nextNoBoundryBuffer() {
        noBoundryBuffer = noBoundryBuffer.getNextBuffer();
        assert(noBoundryBuffer != null); }

    public void setFinish() {
        finish = true;
    }
    public void setUnfinish() {
        finish = false;
    }
    public boolean isFinish() {
        return finish;
    }
    public void spin() {
        while (!ready) {}
        ready = false;
    }
    public void notifyReady() {
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }
}
