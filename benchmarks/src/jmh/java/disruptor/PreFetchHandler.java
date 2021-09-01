package disruptor;

import com.lmax.disruptor.EventHandler;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;

import java.util.Random;
import java.util.function.Function;

/**
 * Disruptor event handler that mimics the pre-fetch phase of eventFlow. We ask the
 * DataSource backing the VirtualMap to load the entry corresponding to the keys
 * attached to the transaction. The entry will not incur any IO during the
 * handleTransaction phase of eventFlow.
 */
public class PreFetchHandler<K extends VirtualKey, V extends VirtualValue> implements EventHandler<Transaction> {
    long id;
    int numHandlers;
    int numEntities;

    Function<Long, VirtualKey> idFactory;
    VirtualMap<K, V> map;

    public PreFetchHandler(
            int id,     // handler id
            int numHandlers,    // total number of handlers in set
            int numEntities,    // total number of entities in VirtualMap
            Function<Long, VirtualKey> idFactory,   // factory for VirtualKey
            VirtualMap<K, V> map
    ) {
        this.id = id;
        this.numHandlers = numHandlers;
        this.numEntities = numEntities;
        this.idFactory = idFactory;
        this.map = map;
    }

    public void onEvent(Transaction tx, long sequence, boolean endOfBatch) {
        // Only handle events assigned to this handler
        if (sequence % numHandlers != id) {
            return;
        }

        map.get((K) tx.getSender());
        map.get((K) tx.getReceiver());
    }
}
