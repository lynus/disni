package intruder;

import org.mmtk.plan.Plan;
import org.vmmagic.pragma.Inline;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;


public class Queue {
    private Address start;
    private Offset head ,tail;
    public Queue(int capacity) {
        int pages = 8* capacity / 4096;
        if (pages == 0) pages = 1;
        //XXX use ByteBuffer.allocateDirect() cause seg fault, have no idea why
        start = Plan.smallCodeSpace.acquire(pages);
        Utils.log("queue start addr: 0x" + Long.toHexString(start.toLong()));
        head = Offset.zero();
        tail = Offset.zero();
    }
    @Inline
    public void add(Object obj) {
        start.store(ObjectReference.fromObject(obj), head);
        head = head.plus(8);
    }
    @Inline
    public Object remove() {
        if (tail.sLT(head)) {
            ObjectReference ref = start.loadObjectReference(tail);
            if (tail.plus(8).EQ(head)) {
                tail = Offset.zero();
                head = Offset.zero();
            } else
                tail = tail.plus(8);
            return ref.toObject();
        }
        return null;
    }
    @Inline
    public int size() {
        return (head.minus(tail).toInt()) >> 3;
    }
    @Inline
    public void add(Address addr) {
        start.store(addr, head);
        head = head.plus(8);
    }
    @Inline
    public Address removeAddress() {
        if (tail.sLT(head)) {
            Address ret = start.loadAddress(tail);
            if (tail.plus(8).EQ(head)) {
                tail = Offset.zero();
                head = Offset.zero();
            } else
                tail = tail.plus(8);
            return ret;
        }
        return Address.zero();
    }
}
