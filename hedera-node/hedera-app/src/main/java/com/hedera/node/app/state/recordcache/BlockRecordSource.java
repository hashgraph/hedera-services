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

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.blocks.RecordTranslator;
import com.hedera.node.app.blocks.impl.BlockStreamBuilder;
import com.hedera.node.app.blocks.impl.RecordTranslationContext;
import com.hedera.node.app.spi.records.RecordSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A {@link RecordSource} that lazily computes {@link TransactionRecord}s
 * from lists of {@link BlockItem}s and corresponding {@link RecordTranslationContext}s.
 */
public class BlockRecordSource implements RecordSource {
    private final RecordTranslator recordTranslator;
    private final List<BlockStreamBuilder.Output> outputs;

    @Nullable
    private List<TransactionRecord> computedRecords;

    public BlockRecordSource(
            @NonNull final RecordTranslator recordTranslator, @NonNull final List<BlockStreamBuilder.Output> outputs) {
        this.recordTranslator = requireNonNull(recordTranslator);
        this.outputs = requireNonNull(outputs);
    }

    /**
     * For each {@link BlockItem} in the source, apply the given action.
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
        computedRecords()
                .forEach(r -> action.accept(
                        r.transactionIDOrThrow(), r.receiptOrThrow().status()));
    }

    private List<TransactionRecord> computedRecords() {
        if (computedRecords == null) {
            computedRecords = new ArrayList<>();
            for (final var output : outputs) {
                computedRecords.add(output.translatedWith(recordTranslator));
            }
        }
        return computedRecords;
    }
}
