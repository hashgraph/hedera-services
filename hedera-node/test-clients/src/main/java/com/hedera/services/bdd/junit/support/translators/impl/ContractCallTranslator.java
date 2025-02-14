// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support.translators.impl;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
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
