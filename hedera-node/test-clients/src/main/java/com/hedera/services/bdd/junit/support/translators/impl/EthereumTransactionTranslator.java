package com.hedera.services.bdd.junit.support.translators.impl;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Optional;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static java.util.Objects.requireNonNull;

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
            Optional.ofNullable(
                            parts.transactionOutput())
                    .map(TransactionOutput::ethereumCallOrThrow)
                    .ifPresent(ethTxOutput -> {
                        final var result = ethTxOutput.();
                        recordBuilder.contractCallResult(result);
                        receiptBuilder.contractID(result.contractID());
                        sidecarRecords.addAll(ethTxOutput.sidecars());
                    })
        });
    }
}
