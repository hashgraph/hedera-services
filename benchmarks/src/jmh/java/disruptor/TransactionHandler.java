package disruptor;

import com.lmax.disruptor.EventHandler;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

/**
 * Disruptor event handler that mimics the handle phase of eventFlow. The transaction
 * logic to perform is specified as a lambda passed to the handler on construction.
 * It is expected that any IO required by the VirtualMap will have already been done
 * during the preFetch phase.
 */
public class TransactionHandler<K extends VirtualKey, V extends VirtualValue> implements EventHandler<Transaction> {
    VirtualMap<K, V> map;
    BiConsumer<V, V> txLogic;
    Latch latch;
    Random rand;

    public TransactionHandler(VirtualMap<K, V> map, Latch latch, BiConsumer<V, V> txLogic) {
        this.map = map;
        this.txLogic = txLogic;
        this.latch = latch;
        this.rand = new Random();
    }

    public void onEvent(Transaction tx, long sequence, boolean endOfBatch) {
        final var tinyBars = rand.nextInt(10);

        try {
            final var sender = map.get((K) tx.getSender());
            final var receiver = map.get((K) tx.getReceiver());

            txLogic.accept(sender, receiver);
        } finally {
            latch.countdown();
        }
    }
}
