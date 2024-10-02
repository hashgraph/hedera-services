/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.recordcache;

import static com.hedera.node.app.spi.records.RecordCache.isChild;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.records.RecordSource;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.types.StreamMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link RecordSource} that uses a list of precomputed {@link TransactionRecord}s. Used in some tests and when
 * {@link BlockStreamConfig#streamMode()} is {@link StreamMode#BLOCKS} to  support queryable partial records after
 * reconnect or restart.
 */
public class ListRecordSource implements RecordSource {
    private final List<TransactionRecord> precomputedRecords;

    public ListRecordSource() {
        this.precomputedRecords = new ArrayList<>();
    }

    public ListRecordSource(@NonNull final TransactionRecord precomputedRecord) {
        this(List.of(precomputedRecord));
    }

    public ListRecordSource(@NonNull final List<TransactionRecord> precomputedRecords) {
        requireNonNull(precomputedRecords);
        this.precomputedRecords = requireNonNull(precomputedRecords);
    }

    public void incorporate(@NonNull final TransactionRecord precomputedRecord) {
        requireNonNull(precomputedRecord);
        precomputedRecords.add(precomputedRecord);
    }

    @Override
    public void forEachTxnRecord(@NonNull final Consumer<TransactionRecord> action) {
        requireNonNull(action);
        precomputedRecords.forEach(action);
    }

    @Override
    public void forEachTxnOutcome(@NonNull final BiConsumer<TransactionID, ResponseCodeEnum> action) {
        requireNonNull(action);
        precomputedRecords.forEach(
                r -> action.accept(r.transactionIDOrThrow(), r.receiptOrThrow().status()));
    }

    @Override
    public TransactionReceipt receiptOf(@NonNull final TransactionID txnId) {
        requireNonNull(txnId);
        for (final var precomputed : precomputedRecords) {
            if (txnId.equals(precomputed.transactionIDOrThrow())) {
                return precomputed.receiptOrThrow();
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public List<TransactionReceipt> childReceiptsOf(@NonNull TransactionID txnId) {
        requireNonNull(txnId);
        return precomputedRecords.stream()
                .filter(r -> isChild(txnId, r.transactionIDOrThrow()))
                .map(TransactionRecord::receiptOrThrow)
                .toList();
    }
}
