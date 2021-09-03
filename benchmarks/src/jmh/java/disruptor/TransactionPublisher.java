package disruptor;

import com.lmax.disruptor.RingBuffer;

public class TransactionPublisher {
    RingBuffer<Transaction> ringBuffer;
    Latch latch;

    public TransactionPublisher(RingBuffer<Transaction> ringBuffer, Latch latch) {
        this.ringBuffer = ringBuffer;
        this.latch = latch;
    }

    public void publish(Transaction tx) {
        long sequence = ringBuffer.next();
        try
        {
            Transaction event = ringBuffer.get(sequence);
            event.copy(tx);
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
//            System.out.println("Publishing END message into disruptor, awaiting...");
            latch.await();
//            System.out.println("... finished waiting for END message");
        }
    }
}
