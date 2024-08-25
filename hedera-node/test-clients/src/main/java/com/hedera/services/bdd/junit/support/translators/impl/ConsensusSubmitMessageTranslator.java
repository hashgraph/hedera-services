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

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.SingleTransactionBlockItems;
import com.hedera.services.bdd.junit.support.translators.TransactionRecordTranslator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public class ConsensusSubmitMessageTranslator implements TransactionRecordTranslator<SingleTransactionBlockItems> {
    // For explanation about this constant value, see
    // https://github.com/hashgraph/hedera-protobufs/blob/pbj-storage-spec-review/block/stream/output/consensus_service.proto#L6
    private static final long RUNNING_HASH_VERSION = 3L;

    @Override
    public SingleTransactionRecord translate(
            @NonNull SingleTransactionBlockItems transaction, @Nullable StateChanges stateChanges) {
        final var receiptBuilder = TransactionReceipt.newBuilder();
        if (transaction.result().status().equals(SUCCESS)) {
            receiptBuilder.topicRunningHashVersion(RUNNING_HASH_VERSION);
        } else {
            receiptBuilder.status(transaction.result().status());
        }

        if (stateChanges != null) {
            stateChanges.stateChanges().stream()
                    .filter(StateChange::hasMapUpdate)
                    .findFirst()
                    .ifPresent(stateChange -> {
                        final var topic = stateChange.mapUpdate().value().topicValue();
                        if (topic != null) {
                            receiptBuilder.topicSequenceNumber(topic.sequenceNumber());
                            receiptBuilder.topicRunningHash(topic.runningHash());
                        }
                    });
        }

        return new SingleTransactionRecord(
                transaction.txn(),
                TransactionRecord.newBuilder().receipt(receiptBuilder.build()).build(),
                List.of(),
                new SingleTransactionRecord.TransactionOutputs(null));
    }
}
