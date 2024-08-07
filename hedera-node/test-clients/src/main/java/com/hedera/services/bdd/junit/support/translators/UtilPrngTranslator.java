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

import static com.hedera.hapi.block.stream.output.UtilPrngOutput.EntropyOneOfType.PRNG_BYTES;
import static com.hedera.hapi.block.stream.output.UtilPrngOutput.EntropyOneOfType.PRNG_NUMBER;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class UtilPrngTranslator implements TransactionRecordTranslator<SingleTransactionBlockItems> {
    @Override
    public SingleTransactionRecord translate(
            @NotNull SingleTransactionBlockItems transaction, @NotNull StateChanges stateChanges) {
        final var recordBuilder = TransactionRecord.newBuilder();

        if (transaction.output().hasUtilPrng()) {
            final var entropy = transaction.output().utilPrng().entropy();
            if (entropy.kind() == PRNG_BYTES) {
                recordBuilder.prngBytes(entropy.as());
            } else if (entropy.kind() == PRNG_NUMBER) {
                recordBuilder.prngNumber(entropy.as());
            }
        }
        recordBuilder.consensusTimestamp(stateChanges.consensusTimestamp());
        return new SingleTransactionRecord(
                transaction.txn(),
                recordBuilder.build(),
                List.of(),
                new SingleTransactionRecord.TransactionOutputs(null));
    }
}