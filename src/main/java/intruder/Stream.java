package intruder;

public abstract class Stream {
    public static final long ROOTMARKER = 0xCAFECAFE;
    protected Endpoint ep;
    protected int connectionId = -1;

    public Stream(Endpoint ep) {
        this.ep = ep;
        this.connectionId = ep.getConnectionId();
    }

    public Endpoint getEp() {
        return ep;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public abstract void close();

    static class Handle {
        int index;
        public Handle(int index) {
            this.index = index;
        }
    }
}
