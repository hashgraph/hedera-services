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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.records.RecordSource;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.types.StreamMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link RecordSource} that uses a list of precomputed {@link SingleTransactionRecord}s. Used exclusively when
 * {@link BlockStreamConfig#streamMode()} is {@link StreamMode#RECORDS} since in this case the
 * {@link SingleTransactionRecord} objects are already constructed for streaming.
 */
public class LegacyListRecordSource implements RecordSource {
    private final List<SingleTransactionRecord> precomputedRecords;

    public LegacyListRecordSource(@NonNull final List<SingleTransactionRecord> precomputedRecords) {
        requireNonNull(precomputedRecords);
        this.precomputedRecords = requireNonNull(precomputedRecords);
    }

    public @NonNull List<SingleTransactionRecord> precomputedRecords() {
        return precomputedRecords;
    }

    @Override
    public void forEachTxnRecord(@NonNull final Consumer<TransactionRecord> action) {
        requireNonNull(action);
        precomputedRecords.forEach(r -> action.accept(r.transactionRecord()));
    }

    @Override
    public void forEachTxnOutcome(@NonNull BiConsumer<TransactionID, ResponseCodeEnum> action) {
        requireNonNull(action);
        precomputedRecords.forEach(r -> action.accept(
                r.transactionRecord().transactionIDOrThrow(),
                r.transactionRecord().receiptOrThrow().status()));
    }
}
