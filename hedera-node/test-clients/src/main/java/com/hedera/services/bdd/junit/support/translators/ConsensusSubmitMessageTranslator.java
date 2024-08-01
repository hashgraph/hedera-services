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

package com.hedera.services.bdd.junit.support.translators;

import static com.hedera.services.bdd.junit.support.translators.BlockStreamTransactionTranslator.BlockTransaction;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ConsensusSubmitMessageTranslator implements TransactionRecordTranslator<BlockTransaction> {
    @Override
    public TransactionRecord translate(@NotNull BlockTransaction transaction) {
        final var receiptBuilder = TransactionReceipt.newBuilder();

        final var txnOutputItem = transaction.transactionOutput();
        if (txnOutputItem != null && txnOutputItem.hasTransactionOutput()) {
            if (txnOutputItem.transactionOutput().hasSubmitMessage()) {
                final var submitMessageOutput =
                        txnOutputItem.transactionOutput().submitMessage();

                // TODO: looks like the protoOrdinal() has values in reverse order i.e. 0 is the most recent version,
                // while services handler uses RUNNING_HASH_VERSION = 3L, is that correct?
                final var version =
                        submitMessageOutput.topicRunningHashVersion().protoOrdinal();
                receiptBuilder.topicRunningHashVersion(version);
            }
        }

        final var stateChangesItem = transaction.stateChanges();
        if (stateChangesItem.hasStateChanges()) {
            final var submitMessageStateChanges = stateChangesItem.stateChanges();
            submitMessageStateChanges.stateChanges().stream()
                    .filter(StateChange::hasMapUpdate)
                    .findFirst()
                    .ifPresent(stateChange -> {
                        final var topic = stateChange.mapUpdate().value().topicValue();
                        receiptBuilder.topicSequenceNumber(topic.sequenceNumber());
                        receiptBuilder.topicRunningHash(topic.runningHash());
                    });
        }

        return TransactionRecord.newBuilder().receipt(receiptBuilder.build()).build();
    }

    @Override
    public List<TransactionRecord> translateAll(List<BlockTransaction> transactions) {
        return transactions.stream().map(this::translate).toList();
    }
}
