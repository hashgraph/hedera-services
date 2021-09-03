package disruptor;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

public class Transaction {
    VirtualKey senderId;
    VirtualKey receiverId;
    VirtualValue sender;
    VirtualValue receiver;

    boolean last;

    public Transaction() { }

    public Transaction( VirtualKey senderId,  VirtualKey receiverId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
    }

    public void copy(Transaction tx) {
        this.senderId = tx.senderId;
        this.sender = tx.sender;
        this.receiverId = tx.receiverId;
        this.receiver = tx.receiver;
        this.last = tx.last;
    }

    public VirtualKey getSenderId() {
        return senderId;
    }

    public VirtualKey getReceiverId() {
        return receiverId;
    }

    public VirtualValue getSender() { return sender; }

    public VirtualValue getReceiver() { return receiver; }

    public boolean isLast() { return last; }

    public void setSenderId(VirtualKey senderId) {
        this.senderId = senderId;
    }

    public void setReceiverId(VirtualKey receiverId) {
        this.receiverId = receiverId;
    }

    public void setSender(VirtualValue sender) { this.sender = sender; }

    public void setReceiver(VirtualValue receiver) { this.receiver = receiver; }

    public void setLast(boolean last) { this.last = last; }

    public void clear() {
        this.senderId = null;
        this.sender = null;
        this.receiverId = null;
        this.receiver = null;
        this.last = false;
    }
}