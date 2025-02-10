package com.swirlds.state.merkle.queue;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;

public class QueueState {

    // TODO: check if class id and class version should be added

    // Queue head index. This is the index at which the next element is retrieved from
    // a queue. If equal to tail, the queue is empty
    private long head = 1;

    // Queue tail index. This is the index at which the next element will be added to
    // a queue. Queue size therefore is tail - head
    private long tail = 1;

    public QueueState() {
    }

    public QueueState(final ReadableSequentialData in) {
        this.head = in.readLong();
        this.tail = in.readLong();
    }

    public QueueState(long head, long tail) {
        this.head = head;
        this.tail = tail;
    }

    public void writeTo(final WritableSequentialData out) {
        out.writeLong(head);
        out.writeLong(tail);
    }

    public int getSizeInBytes() {
        return 2 * Long.BYTES;
    }

    /**
     * Gets the head index.
     */
    public long getHead() {
        return head;
    }

    /**
     * Gets the head index and increments it by one. May not be atomic.
     */
    public long getHeadAndIncrement() {
        return head++;
    }

    /**
     * Gets the tail index.
     */
    public long getTail() {
        return tail;
    }

    /**
     * Gets the tail index and increments it by one. May not be atomic.
     */
    public long getTailAndIncrement() {
        return tail++;
    }

    /**
     * Returns if this queue node state is empty, i.e. if the head and tail indexes are equal.
     */
    public boolean isEmpty() {
        return head == tail;
    }
}
