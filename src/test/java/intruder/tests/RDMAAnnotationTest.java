package intruder.tests;

import intruder.Endpoint;
import intruder.Factory;
import org.junit.Test;
import static org.junit.Assert.*;
import org.vmmagic.pragma.RDMA;

import java.io.IOException;

public class RDMAAnnotationTest {

    @Test(expected = IOException.class)
    public void putNONRDMAObject() throws IOException{
        NonRDMAObj obj = new NonRDMAObj();
        Endpoint ep = Factory.newEndpoint();
        ep.writeObject(obj);
    }

    @Test(expected = Test.None.class)
    public void putRDMAObject() throws IOException{
        RDMAObj obj = new RDMAObj();
        Endpoint ep = Factory.newEndpoint();
        ep.writeObject(obj);
    }
    @RDMA
    static class RDMAObj {
        public int ia,ib;
        private long la,lb;
        public RDMAObj(){
            la = 1L;
            lb = 2L;
            ia = 99;
            ib = 55;
        }
    }

    static class NonRDMAObj {
        public int ia,ib;
        private long la,lb;
        public NonRDMAObj(){
            la = 1L;
            lb = 2L;
            ia = 99;
            ib = 55;
        }
    }

}
