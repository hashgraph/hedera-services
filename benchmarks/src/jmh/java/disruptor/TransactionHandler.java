package disruptor;

import com.lmax.disruptor.EventHandler;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.util.function.Consumer;

/**
 * Disruptor event handler that mimics the handle phase of eventFlow. The transaction
 * logic to perform is specified as a lambda passed to the handler on construction.
 * It is expected that any IO required by the VirtualMap will have already been done
 * during the preFetch phase.
 */
public class TransactionHandler<K extends VirtualKey, V extends VirtualValue, T> implements EventHandler<Transaction<T>> {
    Consumer<Transaction<T>> txLogic;
    Latch latch;

    public TransactionHandler(Latch latch, Consumer<Transaction<T>> txLogic) {
        this.latch = latch;
        this.txLogic = txLogic;
    }

    public void onEvent(Transaction<T> tx, long sequence, boolean endOfBatch) {
        try {
            if (!tx.isLast()) {
                txLogic.accept(tx);
            }
        } finally {
            if (tx.isLast())
                latch.countdown();

            tx.clear();     // release hard references for GC
        }
    }
}
