package disruptor;

import com.lmax.disruptor.EventHandler;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;

import java.util.function.Consumer;

import static disruptor.Utils.fastModulo;

/**
 * Disruptor event handler that mimics the pre-fetch phase of eventFlow. We ask the
 * DataSource backing the VirtualMap to load the entry corresponding to the keys
 * attached to the transaction. The entry will not incur any IO during the
 * handleTransaction phase of eventFlow.
 */
public class PreFetchHandler<K extends VirtualKey, V extends VirtualValue> implements EventHandler<Transaction> {
    long id;
    int numHandlers;

    Consumer<Transaction> preFetchLogic;

    public PreFetchHandler(
            int id,     // handler id
            int numHandlers,    // total number of handlers in set
            Consumer<Transaction> preFetchLogic
    ) {
        this.id = id;
        this.numHandlers = numHandlers;
        this.preFetchLogic = preFetchLogic;
    }

    public void onEvent(Transaction tx, long sequence, boolean endOfBatch) {
        // Only handle events assigned to this handler
        if (fastModulo(sequence, numHandlers) != id) {
            return;
        }

        if (!tx.isLast()) {
            preFetchLogic.accept(tx);
        }
    }
}
