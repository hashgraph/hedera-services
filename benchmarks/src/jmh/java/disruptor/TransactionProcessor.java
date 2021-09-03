package disruptor;

import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class TransactionProcessor<K extends VirtualKey, V extends VirtualValue> {
    public static final int BUFFER_SIZE = 131072;   // MUST be 2^n
    public static final int NUM_PRE_FETCH_HANDLERS = 16; // MUST be 2^n

    Disruptor<Transaction> disruptor;
    TransactionPublisher publisher;
    Supplier<VirtualMap<K, V>> mapSupplier;

    public TransactionProcessor(Consumer<Transaction> preFetchLogic, Consumer<Transaction> txLogic) {
        disruptor = new Disruptor<>(
                Transaction::new,
                BUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        // Workaround to create generic array in java
        PreFetchHandler preFetchHandlers[] = new PreFetchHandler[NUM_PRE_FETCH_HANDLERS];
        for (int i = 0; i < NUM_PRE_FETCH_HANDLERS; i++) {
            preFetchHandlers[i] = new PreFetchHandler(i, NUM_PRE_FETCH_HANDLERS, preFetchLogic);
        }

        Latch latch = new Latch();
        disruptor.handleEventsWith(preFetchHandlers)
                        .then(new TransactionHandler(latch, txLogic));
        disruptor.start();

        publisher = new TransactionPublisher(disruptor.getRingBuffer(), latch);
    }

    public TransactionPublisher getPublisher() {
        return publisher;
    }
}
