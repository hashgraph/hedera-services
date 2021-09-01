package disruptor;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;

import java.lang.reflect.Array;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class TransactionProcessor<K extends VirtualKey, V extends VirtualValue> {
    public static final int BUFFER_SIZE = 131072;   // MUST be 2^n
    public static final int NUM_PRE_FETCH_HANDLERS = 4;

    Disruptor<Transaction> disruptor;
    TransactionPublisher publisher;

    public TransactionProcessor(
            int numEntities,
            VirtualMap<K, V> map,
            Function<Long, VirtualKey> idFactory,
            BiConsumer<V, V> txLogic
    ) {
        disruptor = new Disruptor<>(
                Transaction::new,
                                BUFFER_SIZE,
                                DaemonThreadFactory.INSTANCE,
                                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        // Workaround to create generic array in java
        EventHandler<Transaction> preFetchHandlers[] = createArray(Transaction.class, NUM_PRE_FETCH_HANDLERS);
        for (int i = 0; i < NUM_PRE_FETCH_HANDLERS; i++) {
            preFetchHandlers[i] = new PreFetchHandler(i, NUM_PRE_FETCH_HANDLERS, numEntities, idFactory, map);
        }

        Latch latch = new Latch();
        disruptor.handleEventsWith(preFetchHandlers)
                        .then(new TransactionHandler(map, latch, txLogic));
        disruptor.start();

        publisher = new TransactionPublisher(disruptor.getRingBuffer(), latch);
    }

    static EventHandler<Transaction>[] createArray(Class<Transaction> type, int length) {
        return (EventHandler<Transaction>[]) Array.newInstance(type, length);
    }

    public TransactionPublisher getPublisher() {
        return publisher;
    }
}
