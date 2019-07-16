package intruder.RPC;

import com.ibm.darpc.DaRPCMessage;

import java.io.IOException;
import java.nio.ByteBuffer;

public class Request implements DaRPCMessage {
    public final static int RESERVE_BUFFER_CMD = 1;
    public final static int NOTIFY_BUFFER_LIMIT_CMD = 2;
    public final static int RELEASE_AND_RESERVE_CMD = 3;
    public final static int WAIT_FINISH_CMD = 4;
    public final static int GET_TIB_CMD = 5;
    public final static int GET_ENUM_CMD = 6;
    public final static int NOTIFY_READY_CMD = 7;
    private final static int SIZE = 8 + 16;
    public int cmd, connectId;
    public ReserveBufferREQ reserveBufferREQ;
    public NotifyBufferLimitREQ notifyBufferLimitREQ;
    public ReleaseAndReserveREQ releaseAndReserveREQ;
    public WaitFinishREQ waitFinishREQ;
    public GetTIBREQ getTIBREQ;
    public GetEnumREQ getEnumREQ;
    public NotifyReadyREQ notifyReadyREQ;
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

    public Request(int connectId, WaitFinishREQ waitFinishREQ) {
        this.connectId = connectId;
        this.cmd = waitFinishREQ.type();
        this.waitFinishREQ = waitFinishREQ;
    }

    public Request(int connectId, GetTIBREQ getTIBREQ) {
        this.connectId = connectId;
        this.cmd = getTIBREQ.type();
        this.getTIBREQ = getTIBREQ;
    }

    public Request(int connectId, GetEnumREQ getEnumREQ) {
        this.connectId = connectId;
        this.cmd = getEnumREQ.type();
        this.getEnumREQ = getEnumREQ;
    }

    public Request(int connectId, NotifyReadyREQ notifyReadyREQ) {
        this.connectId = connectId;
        this.cmd = notifyReadyREQ.type();
        this.notifyReadyREQ = notifyReadyREQ;
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
            case WAIT_FINISH_CMD:
                written += waitFinishREQ.write(buffer);
                break;
            case GET_TIB_CMD:
                written += getTIBREQ.write(buffer);
                break;
            case GET_ENUM_CMD:
                written += getEnumREQ.write(buffer);
                break;
            case NOTIFY_READY_CMD:
                written += notifyReadyREQ.write(buffer);
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
            case WAIT_FINISH_CMD:
                this.waitFinishREQ = new WaitFinishREQ();
                waitFinishREQ.update(buffer);
                break;
            case GET_TIB_CMD:
                this.getTIBREQ = new GetTIBREQ();
                getTIBREQ.update(buffer);
                break;
            case GET_ENUM_CMD:
                this.getEnumREQ = new GetEnumREQ();
                getEnumREQ.update(buffer);
                break;
            case NOTIFY_READY_CMD:
                this.notifyReadyREQ = new NotifyReadyREQ();
                notifyReadyREQ.update(buffer);
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
        private boolean isBoundry;
        public int type() {
            return NOTIFY_BUFFER_LIMIT_CMD;
        }
        public int size() {
            return 13;
        }
        public int write(ByteBuffer buffer) {
            buffer.putInt(limit);
            buffer.putLong(bufferStart);
            buffer.put(isBoundry ? (byte)1:(byte)0);
            return size();
        }

        public void update(ByteBuffer buffer) {
            limit = buffer.getInt();
            bufferStart = buffer.getLong();
            isBoundry = buffer.get() == 1;
        }
        public NotifyBufferLimitREQ(long bufferStart, int limit, boolean isBoundry) {
            this.limit = limit;
            this.bufferStart = bufferStart;
            this.isBoundry = isBoundry;
        }

        public long getBufferStart() {
            return bufferStart;
        }

        public int getLimit() {
            return limit;
        }

        public boolean isBoundry() {
            return isBoundry;
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

    public static class WaitFinishREQ extends NOPayLoad implements REQ{
        public int type() {
            return WAIT_FINISH_CMD;
        }
    }
    public static class GetTIBREQ extends NOPayLoad implements REQ {
        public int type() {
            return GET_TIB_CMD;
        }
    }

    public static class GetEnumREQ extends NOPayLoad implements REQ {
        public int type() {
            return GET_ENUM_CMD;
        }
    }
    public static class NotifyReadyREQ extends NOPayLoad implements REQ {
        public int type() {
            return NOTIFY_READY_CMD;
        }
    }
}

