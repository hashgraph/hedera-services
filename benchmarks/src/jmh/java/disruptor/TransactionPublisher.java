package disruptor;

import com.lmax.disruptor.RingBuffer;

public class TransactionPublisher<T> {
    RingBuffer<Transaction<T>> ringBuffer;
    Latch latch;

    public TransactionPublisher(RingBuffer<Transaction<T>> ringBuffer, Latch latch) {
        this.ringBuffer = ringBuffer;
        this.latch = latch;
    }

    public void publish(T data) {
        long sequence = ringBuffer.next();
        try
        {
            Transaction<T> event = ringBuffer.get(sequence);
            event.setData(data);
            event.setLast(false);
        }
        finally
        {
            ringBuffer.publish(sequence);
        }
    }

    public void end() throws InterruptedException {
        long sequence = ringBuffer.next();
        try
        {
            Transaction<T> event = ringBuffer.get(sequence);
            event.setLast(true);
        }
        finally
        {
            ringBuffer.publish(sequence);
            latch.await();
        }
    }
}
