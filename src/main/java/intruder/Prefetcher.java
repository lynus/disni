package intruder;

import com.ibm.disni.verbs.IbvMr;
import org.jikesrvm.runtime.Callbacks;
import org.vmmagic.unboxed.Address;

import java.io.IOException;

public class Prefetcher extends Callbacks.RdmaSpaceGrowMonitor {
    private IbvMr mr;

    public Prefetcher(IbvMr mr) {
        this.mr = mr;
    }
    @Override
    public void notifyRdmaSpaceGrow(Address start, int pages) {
        try {
            mr.expPrefetchMr(start.toLong(), pages << 12);
        } catch (IOException e) {
            System.out.println("Prefetch failed: " + e.getMessage());
        }
    }
}
