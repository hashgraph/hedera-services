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

package com.hedera.node.app.blocks.translators.impl;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.node.app.blocks.translators.BaseTranslator;
import com.hedera.node.app.blocks.translators.BlockTransactionParts;
import com.hedera.node.app.blocks.translators.BlockTransactionPartsTranslator;
import com.hedera.node.app.state.SingleTransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Translates a contract call transaction into a {@link SingleTransactionRecord}.
 */
public class ContractCallTranslator implements BlockTransactionPartsTranslator {
    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder) -> parts.outputIfPresent(
                        TransactionOutput.TransactionOneOfType.CONTRACT_CALL)
                .map(TransactionOutput::contractCallOrThrow)
                .ifPresent(callContractOutput -> {
                    final var result = callContractOutput.contractCallResultOrThrow();
                    recordBuilder.contractCallResult(result);
                    if (parts.transactionIdOrThrow().nonce() == 0 && result.gasUsed() > 0L) {
                        receiptBuilder.contractID(result.contractID());
                    }
                }));
    }
}
