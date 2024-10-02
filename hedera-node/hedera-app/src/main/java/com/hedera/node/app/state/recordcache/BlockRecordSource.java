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

import static com.hedera.node.app.blocks.HistoryTranslator.HISTORY_TRANSLATOR;
import static com.hedera.node.app.spi.records.RecordCache.Receipts.isChild;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.blocks.HistoryTranslator;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder.Output.ScopedReceipt;
import com.hedera.node.app.blocks.impl.TranslationContext;
import com.hedera.node.app.spi.records.RecordSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link RecordSource} that lazily computes {@link TransactionRecord} and {@link TransactionReceipt} histories from
 * lists of {@link BlockItem}s and corresponding {@link TranslationContext}s.
 */
public class BlockRecordSource implements RecordSource {
    private final HistoryTranslator historyTranslator;
    private final List<BlockStreamBuilder.Output> outputs;

    @Nullable
    private List<TransactionRecord> computedRecords;

    @Nullable
    private List<ScopedReceipt> computedReceipts;

    public BlockRecordSource(@NonNull final List<BlockStreamBuilder.Output> outputs) {
        this(HISTORY_TRANSLATOR, outputs);
    }

    @VisibleForTesting
    public BlockRecordSource(
            @NonNull final HistoryTranslator historyTranslator,
            @NonNull final List<BlockStreamBuilder.Output> outputs) {
        this.historyTranslator = requireNonNull(historyTranslator);
        this.outputs = requireNonNull(outputs);
    }

    /**
     * For each {@link BlockItem} in the source, apply the given action.
     *
     * @param action the action to apply
     */
    public void forEachItem(@NonNull final Consumer<BlockItem> action) {
        requireNonNull(action);
        outputs.forEach(output -> output.forEachItem(action));
    }

    @Override
    public void forEachTxnRecord(@NonNull final Consumer<TransactionRecord> action) {
        requireNonNull(action);
        computedRecords().forEach(action);
    }

    @Override
    public void forEachTxnOutcome(@NonNull final BiConsumer<TransactionID, ResponseCodeEnum> action) {
        requireNonNull(action);
        computedReceipts().forEach(r -> action.accept(r.txnId(), r.receipt().status()));
    }

    @Override
    public TransactionReceipt receiptOf(@NonNull final TransactionID txnId) {
        requireNonNull(txnId);
        for (final var receipt : computedReceipts()) {
            if (txnId.equals(receipt.txnId())) {
                return receipt.receipt();
            }
        }
        throw new IllegalArgumentException();
    }

    @Override
    public List<TransactionReceipt> childReceiptsOf(@NonNull final TransactionID txnId) {
        requireNonNull(txnId);
        final List<TransactionReceipt> receipts = new ArrayList<>();
        for (final var receipt : computedReceipts()) {
            if (isChild(txnId, receipt.txnId())) {
                receipts.add(receipt.receipt());
            }
        }
        return receipts;
    }

    private List<ScopedReceipt> computedReceipts() {
        if (computedReceipts == null) {
            // Mutate the list of outputs before making it visible to another traversing thread via computedRecords
            final List<ScopedReceipt> computation = new ArrayList<>();
            for (final var output : outputs) {
                computation.add(output.receiptFrom(historyTranslator));
            }
            computedReceipts = computation;
        }
        return computedReceipts;
    }

    private List<TransactionRecord> computedRecords() {
        if (computedRecords == null) {
            // Mutate the list of outputs before making it visible to another traversing thread via computedRecords
            final List<TransactionRecord> computation = new ArrayList<>();
            for (final var output : outputs) {
                computation.add(output.recordFrom(historyTranslator));
            }
            computedRecords = computation;
        }
        return computedRecords;
    }
}
