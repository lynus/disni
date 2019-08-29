package intruder;

import org.vmmagic.unboxed.Address;

import java.io.IOException;

public interface Writable {
    Address write(StageBuffer stBuffer) throws IOException;
}
