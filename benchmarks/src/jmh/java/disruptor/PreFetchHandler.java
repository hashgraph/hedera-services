package disruptor;

import com.lmax.disruptor.EventHandler;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.util.function.Consumer;

/**
 * Disruptor event handler that mimics the pre-fetch phase of eventFlow. We ask the
 * DataSource backing the VirtualMap to load the entry corresponding to the keys
 * attached to the transaction. The entry will not incur any IO during the
 * handleTransaction phase of eventFlow.
 */
public class PreFetchHandler<K extends VirtualKey, V extends VirtualValue, T> implements EventHandler<Transaction<T>> {
    long id;
    int numHandlers;

    Consumer<Transaction<T>> preFetchLogic;

    public PreFetchHandler(
            int id,     // handler id
            int numHandlers,    // total number of handlers in set
            Consumer<Transaction<T>> preFetchLogic
    ) {
        this.id = id;
        this.numHandlers = numHandlers;
        this.preFetchLogic = preFetchLogic;
    }

    public void onEvent(Transaction<T> tx, long sequence, boolean endOfBatch) {
        // Only handle events assigned to this handler
        if (sequence % numHandlers != id) {
            return;
        }

        if (!tx.isLast()) {
            preFetchLogic.accept(tx);
        }
    }
}
