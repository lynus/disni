package intruder.RPC;

import com.ibm.darpc.DaRPCMessage;

import java.io.IOException;
import java.nio.ByteBuffer;

import static intruder.RPC.Request.*;

public class Response implements DaRPCMessage {
    public static final int FAILED = -1;
    public static final int SUCCESS = 0;
    public static final int SIZE = 8 + 256;
    public int cmd;
    public int status = FAILED;
    public ReserveBufferRES reserveBufferRES;
    public NotifyBufferLimitRES notifyBufferLimitRES;
    public ReleaseAndReserveRES releaseAndReserveRES;
    public WaitFinishRES waitFinishRES;
    public GetTIBRES getTIBRES;

    public void setReserveBufferRES(ReserveBufferRES reserveBufferRES) {
        this.reserveBufferRES = reserveBufferRES;
        cmd = reserveBufferRES.type();
        status = SUCCESS;
    }
    public void setNotifyBufferLimitRES(NotifyBufferLimitRES notifyBufferLimitRES) {
        this.notifyBufferLimitRES = notifyBufferLimitRES;
        cmd = notifyBufferLimitRES.type();
        status = SUCCESS;
    }

    public void setReleaseAndReserveRES(ReleaseAndReserveRES releaseAndReserveRES) {
        this.releaseAndReserveRES = releaseAndReserveRES;
        cmd = releaseAndReserveRES.type();
        status = SUCCESS;
    }

    public void setWaitFinishRES(WaitFinishRES waitFinishRES) {
        this.waitFinishRES = waitFinishRES;
        cmd = waitFinishRES.type();
        status = SUCCESS;
    }

    public void setGetTIBRES(GetTIBRES getTIBRES) {
        this.getTIBRES = getTIBRES;
        cmd = getTIBRES.type();
        status = SUCCESS;
    }

    @Override
    public int write(ByteBuffer buffer) throws IOException {
        buffer.putInt(cmd);
        buffer.putInt(status);
        int written = 8;
        if (status == SUCCESS) {
            switch (cmd) {
                case RESERVE_BUFFER_CMD:
                    written += reserveBufferRES.write(buffer);
                    break;
                case NOTIFY_BUFFER_LIMIT_CMD:
                    written += notifyBufferLimitRES.write(buffer);
                    break;
                case RELEASE_AND_RESERVE_CMD:
                    written += releaseAndReserveRES.write(buffer);
                    break;
                case WAIT_FINISH_CMD:
                    written += waitFinishRES.write(buffer);
                    break;
                case GET_TIB_CMD:
                    written += getTIBRES.write(buffer);
                    break;
            }
        }
        return written;
    }

    @Override
    public void update(ByteBuffer buffer) throws IOException {
        cmd = buffer.getInt();
        status = buffer.getInt();
        if (status == FAILED)
            return;
        switch (cmd) {
            case RESERVE_BUFFER_CMD:
                reserveBufferRES = new ReserveBufferRES();
                reserveBufferRES.update(buffer);
                break;
            case NOTIFY_BUFFER_LIMIT_CMD:
                notifyBufferLimitRES = new NotifyBufferLimitRES();
                notifyBufferLimitRES.update(buffer);
                break;
            case RELEASE_AND_RESERVE_CMD:
                releaseAndReserveRES = new ReleaseAndReserveRES();
                releaseAndReserveRES.update(buffer);
                break;
            case WAIT_FINISH_CMD:
                waitFinishRES = new WaitFinishRES();
                waitFinishRES.update(buffer);
                break;
            case GET_TIB_CMD:
                getTIBRES = new GetTIBRES();
                getTIBRES.update(buffer);
                break;
        }
    }

    @Override
    public int size() {
        return SIZE;
    }

    public void fail(int cmd) {
        status = FAILED;
        this.cmd = cmd;
    }

    public static interface RES {
        int type();
    }
    public static class ReserveBufferRES implements RES{
        public long start;
        public int size, rkey;
        public int type() {
            return RESERVE_BUFFER_CMD;
        }
        public int size(){
            return 16;
        }
        public ReserveBufferRES(){}
        public ReserveBufferRES(long start, int size, int rkey) {
            this.start = start;
            this.size = size;
            this.rkey = rkey;
        }
        public int write(ByteBuffer buffer) {
            buffer.putLong(start);
            buffer.putInt(size);
            buffer.putInt(rkey);
            return size();
        }

        public void update(ByteBuffer buffer) {
            start = buffer.getLong();
            size = buffer.getInt();
            rkey = buffer.getInt();
        }
    }

    public static class GetTIBRES implements RES {
        public long[] tibs;
        public int length;
        public int type() {
            return GET_TIB_CMD;
        }
        public GetTIBRES() {}

        public GetTIBRES(long[] tibs, int length) {
            this.tibs = tibs;
            this.length = length;
        }

        public int write(ByteBuffer buffer) {
            buffer.putInt(length);
            for (int i = 0; i < length; i++)
                buffer.putLong(tibs[i]);
            return 4 + 8 * tibs.length;
        }
        public void update(ByteBuffer buffer) {
            length = buffer.getInt();
            this.tibs = new long[length];
            for (int i = 0; i < length; i++)
                tibs[i] = buffer.getLong();
        }
    }
    private static abstract class NoPayLoad {
        //no data member
        public int size() {
            return 0;
        }

        public int write(ByteBuffer buffer) {
            return 0;
        }

        public void update(ByteBuffer buffer) {}
    }
    public static class NotifyBufferLimitRES extends NoPayLoad implements RES{
        public int type() {
            return NOTIFY_BUFFER_LIMIT_CMD;
        }
    }

    public static class ReleaseAndReserveRES extends ReserveBufferRES {
        public int type() {
            return RELEASE_AND_RESERVE_CMD;
        }
        public ReleaseAndReserveRES() {
            super();
        }
        public ReleaseAndReserveRES(long start, int size, int rkey) {
            super(start, size, rkey);
        }
    }

    public static class WaitFinishRES extends  NoPayLoad implements RES {
        public int type() {
            return WAIT_FINISH_CMD;
        }
    }
}
