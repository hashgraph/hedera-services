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

package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.config.types.EntityType.TOPIC;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.SingleTransactionBlockItems;
import com.hedera.services.bdd.junit.support.translators.TransactionRecordTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Translates a consensus topic create transaction into a {@link SingleTransactionRecord}.
 */
public class ConsensusTopicCreateTranslator
        implements BlockTransactionPartsTranslator, TransactionRecordTranslator<SingleTransactionBlockItems> {
    private static final Logger log = LogManager.getLogger(ConsensusTopicCreateTranslator.class);

    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder, sidecarRecords, involvedTokenId) -> {
            if (parts.status() == SUCCESS) {
                final var createdNum = baseTranslator.nextCreatedNum(TOPIC);
                final var iter = remainingStateChanges.listIterator();
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasTopicIdKey()) {
                        final var topicId =
                                stateChange.mapUpdateOrThrow().keyOrThrow().topicIdKeyOrThrow();
                        if (topicId.topicNum() == createdNum) {
                            iter.remove();
                            return;
                        }
                    }
                }
                log.error(
                        "No matching state change found for successful topic create with id {}",
                        parts.transactionIdOrThrow());
            }
        });
    }

    @Override
    public SingleTransactionRecord translate(
            @NonNull final SingleTransactionBlockItems transaction, @Nullable final StateChanges stateChanges) {
        final var receiptBuilder = TransactionReceipt.newBuilder();
        final var recordBuilder = TransactionRecord.newBuilder();

        if (stateChanges != null) {
            maybeAssignTopicID(stateChanges, receiptBuilder);
        }

        return new SingleTransactionRecord(
                transaction.txn(),
                recordBuilder.receipt(receiptBuilder.build()).build(),
                List.of(),
                new SingleTransactionRecord.TransactionOutputs(null));
    }

    private void maybeAssignTopicID(final StateChanges stateChanges, final TransactionReceipt.Builder receiptBuilder) {
        stateChanges.stateChanges().stream()
                .filter(StateChange::hasMapUpdate)
                .findFirst()
                .ifPresent(stateChange -> {
                    if (stateChange.mapUpdate().hasKey()
                            && stateChange.mapUpdate().key().hasTopicIdKey()) {
                        receiptBuilder.topicID(stateChange.mapUpdate().key().topicIdKey());
                    }
                });
    }
}
