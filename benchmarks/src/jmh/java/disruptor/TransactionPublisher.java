package disruptor;

import com.lmax.disruptor.RingBuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

public class TransactionPublisher {
    RingBuffer<Transaction> ringBuffer;
    Latch latch;

    public TransactionPublisher(RingBuffer<Transaction> ringBuffer, Latch latch) {
        this.ringBuffer = ringBuffer;
    }

    public void publish(Transaction tx) {
        long sequence = ringBuffer.next();
        try
        {
            Transaction event = ringBuffer.get(sequence);
            event.setSender(tx.getSender());
            event.setReceiver(tx.getReceiver());
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
            Transaction event = ringBuffer.get(sequence);
            event.setLast(true);
        }
        finally
        {
            ringBuffer.publish(sequence);
            latch.await();
        }
    }
}
