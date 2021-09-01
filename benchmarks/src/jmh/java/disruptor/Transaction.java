package disruptor;

import com.swirlds.virtualmap.VirtualKey;
import virtual.VFCMapBenchBase;

public class Transaction {
    VirtualKey senderId;
    VirtualKey receiverId;
    boolean last;

    public VirtualKey getSender() {
        return senderId;
    }

    public VirtualKey getReceiver() {
        return receiverId;
    }

    public boolean isLast() { return last; }

    public void setSender(VirtualKey senderId) {
        this.senderId = senderId;
    }

    public void setReceiver(VirtualKey receiverId) {
        this.receiverId = receiverId;
    }

    public void setLast(boolean last) { this.last = last; }
}