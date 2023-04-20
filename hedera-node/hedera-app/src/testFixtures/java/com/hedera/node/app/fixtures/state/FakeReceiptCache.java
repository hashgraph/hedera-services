/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.fixtures.state;

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.node.app.state.ReceiptCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.HashSet;

/** A useful test double for {@link ReceiptCache}. This implementation DOES NOT time out any receipts. */
public class FakeReceiptCache implements ReceiptCache {
    private HashMap<TransactionID, CacheItem> cache = new HashMap<>();

    @Override
    public void record(@NonNull final TransactionID transactionID, @NonNull final AccountID nodeAccountID) {
        final var item = cache.computeIfAbsent(
                transactionID,
                k -> new CacheItem(
                        transactionID,
                        new HashSet<>(),
                        TransactionReceipt.newBuilder().status(UNKNOWN).build()));
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
