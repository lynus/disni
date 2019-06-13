package intruder.RPC;

import com.ibm.darpc.DaRPCMessage;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Request implements DaRPCMessage {
    public final static int RESERVE_BUFFER_CMD = 1;
    public final static int NOTIFY_BUFFER_LIMIT_CMD = 2;
    public final static int RELEASE_AND_RESERVE_CMD = 3;
    private final static int SIZE = 8 + 16;
    public int cmd, connectId;
    public ReserveBufferREQ reserveBufferREQ;
    public NotifyBufferLimitREQ notifyBufferLimitREQ;
    public ReleaseAndReserveREQ releaseAndReserveREQ;

    public Request() {}
    public Request(int connectId, ReserveBufferREQ reserveBufferREQ) {
        this.connectId = connectId;
        this.cmd = reserveBufferREQ.type();
        this.reserveBufferREQ = reserveBufferREQ;
    }

    public Request(int connectId, NotifyBufferLimitREQ notifyBufferLimitREQ) {
        this.connectId = connectId;
        this.cmd = notifyBufferLimitREQ.type();
        this.notifyBufferLimitREQ = notifyBufferLimitREQ;
    }

    public Request(int connectId, ReleaseAndReserveREQ releaseAndReserveREQ) {
        this.connectId = connectId;
        this.cmd = releaseAndReserveREQ.type();
        this.releaseAndReserveREQ = releaseAndReserveREQ;
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        buffer.putInt(connectId);
        buffer.putInt(cmd);
        int written = 8;
        switch (cmd) {
            case RESERVE_BUFFER_CMD:
                written += reserveBufferREQ.write(buffer);
                break;
            case NOTIFY_BUFFER_LIMIT_CMD:
                written += notifyBufferLimitREQ.write(buffer);
                break;
            case RELEASE_AND_RESERVE_CMD:
                written += releaseAndReserveREQ.write(buffer);
                break;
        }
        return written;
    }

    @Override
    public void update(ByteBuffer buffer) throws IOException {
        connectId = buffer.getInt();
        cmd = buffer.getInt();
        switch (cmd) {
            case RESERVE_BUFFER_CMD:
                this.reserveBufferREQ = new ReserveBufferREQ();
                break;
            case NOTIFY_BUFFER_LIMIT_CMD:
                this.notifyBufferLimitREQ = new NotifyBufferLimitREQ();
                notifyBufferLimitREQ.update(buffer);
                break;
            case RELEASE_AND_RESERVE_CMD:
                this.releaseAndReserveREQ = new ReleaseAndReserveREQ();
                releaseAndReserveREQ.update(buffer);
                break;
        }
    }

    @Override
    public int size() {
        return SIZE;
    }

    public static interface REQ {
        int type();
    }

    private static abstract class NOPayLoad {
        public int size() {
            return 0;
        }

        public int write(ByteBuffer buffer) {
            return 0;
        }

        public void update(ByteBuffer buffer) {}
    }

    public static class ReserveBufferREQ extends NOPayLoad implements  REQ {
        public int type() {
            return RESERVE_BUFFER_CMD;
        }
    }

    public static class NotifyBufferLimitREQ implements  REQ{
        private int limit;
        private long bufferStart;
        public int type() {
            return NOTIFY_BUFFER_LIMIT_CMD;
        }
        public int size() {
            return 12;
        }
        public int write(ByteBuffer buffer) {
            buffer.putInt(limit);
            buffer.putLong(bufferStart);
            return size();
        }

        public void update(ByteBuffer buffer) {
            limit = buffer.getInt();
            bufferStart = buffer.getLong();
        }
        public NotifyBufferLimitREQ(long bufferStart, int limit) {
            this.limit = limit;
            this.bufferStart = bufferStart;
        }

        public long getBufferStart() {
            return bufferStart;
        }

        public int getLimit() {
            return limit;
        }

        public NotifyBufferLimitREQ() {}
    }

    public static class ReleaseAndReserveREQ implements REQ {
        private long start;
        public int type() {
            return RELEASE_AND_RESERVE_CMD;
        }
        public int size() {
            return 8;
        }

        public int write(ByteBuffer buffer) {
            buffer.putLong(start);
            return size();
        }

        public void update(ByteBuffer buffer) {
            start = buffer.getLong();
        }

        public ReleaseAndReserveREQ(long start) {
            this.start = start;
        }
        public ReleaseAndReserveREQ() {}
    }
}
