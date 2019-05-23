package intruder;

import com.ibm.disni.verbs.IbvMr;
import org.vmmagic.unboxed.Offset;

import java.io.IOException;

public class LocalBuffer extends Buffer {
    private int rkey, lkey, limit;
    private LocalBuffer nextBuffer;
    public void register(Endpoint ep) throws IOException {
        IbvMr mr = ep.registerMemory(start.toLong(), length.toInt()).execute().free().getMr();
        rkey = mr.getRkey();
        lkey = mr.getLkey();
    }
    public int getLkey() {
        return lkey;
    }
    public int getRkey() {
        return rkey;
    }
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public LocalBuffer getNextBuffer() {
        return nextBuffer;
    }

    public void setNextBuffer(LocalBuffer buffer) {
        this.nextBuffer = buffer;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public void peekBytes(int offset, int size) {
        Utils.log("=========peek local buffer bytes==========");
        Utils.log("buffer address:" + Long.toHexString(start.toLong()) +
                "size: " + (length.toInt() >> 10) + " KB");
        Utils.log("peek offset:length  " + Integer.toHexString(offset) + ":" + Integer.toHexString(size));
        String contents = new String();
        for (int i = 0; i < size; i++) {
            byte v = start.loadByte(Offset.fromIntZeroExtend(offset + i));
            contents += "0x" + hexArray[(v >> 4) & 0xf] + hexArray[v & 0x0F] + " ";
        }
        Utils.log(contents);
        Utils.log("===========================================");
    }
}
