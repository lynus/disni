package intruder;

public abstract class Stream {
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
}
