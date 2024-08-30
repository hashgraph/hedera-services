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

import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;

/**
 * Translates a ethereum transaction into a {@link SingleTransactionRecord}.
 */
public class EthereumTransactionTranslator implements BlockTransactionPartsTranslator {
    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        requireNonNull(parts);
        requireNonNull(baseTranslator);
        requireNonNull(remainingStateChanges);
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder, sidecarRecords, involvedTokenId) -> {
            final var op = parts.body().ethereumTransactionOrThrow();
            final var ethTxData = populateEthTxData(op.ethereumData().toByteArray());
            if (ethTxData != null) {
                recordBuilder.ethereumHash(Bytes.wrap(ethTxData.getEthereumHash()));
            }
            Optional.ofNullable(parts.transactionOutput())
                    .flatMap(output -> Optional.ofNullable(output.ethereumCall()))
                    .ifPresent(ethTxOutput -> {
                        final var result =
                                switch (ethTxOutput.ethResult().kind()) {
                                    case UNSET -> throw new IllegalStateException("Missing result kind");
                                    case ETHEREUM_CALL_RESULT -> {
                                        final var callResult = ethTxOutput.ethereumCallResultOrThrow();
                                        recordBuilder.contractCallResult(callResult);
                                        yield callResult;
                                    }
                                    case ETHEREUM_CREATE_RESULT -> {
                                        final var createResult = ethTxOutput.ethereumCreateResultOrThrow();
                                        recordBuilder.contractCreateResult(createResult);
                                        yield createResult;
                                    }
                                };
                        receiptBuilder.contractID(result.contractID());
                        sidecarRecords.addAll(ethTxOutput.sidecars());
                    });
        });
    }
}
