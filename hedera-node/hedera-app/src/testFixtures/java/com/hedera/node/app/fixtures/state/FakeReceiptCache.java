package com.hedera.node.app.fixtures.state;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.node.app.state.ReceiptCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.HashSet;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;

public class FakeReceiptCache implements ReceiptCache {
    private HashMap<TransactionID, CacheItem> cache = new HashMap<>();

    @Override
    public void record(@NonNull final TransactionID transactionID, @NonNull final AccountID nodeAccountID) {
        final var item = cache.computeIfAbsent(transactionID, k -> new CacheItem(transactionID, new HashSet<>(), TransactionReceipt.newBuilder().status(UNKNOWN).build()));
        item.nodeAccountIDs().add(nodeAccountID);
    }

    @Override
    public CacheItem get(@NonNull final TransactionID transactionID) {
        return cache.get(transactionID);
    }

    @Override
    public void update(@NonNull final TransactionID transactionID, @NonNull final TransactionReceipt receipt) {
        final var item = get(transactionID);
        assert item != null;
        cache.put(transactionID, new CacheItem(transactionID, item.nodeAccountIDs(), receipt));
    }
}
