package com.hedera.evm.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.evm.execution.EvmProperties;
import com.hedera.evm.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Transaction;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;

public class AccessorFactory {
    private final EvmProperties dynamicProperties;

    public AccessorFactory(final EvmProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    public TxnAccessor uncheckedSpecializedAccessor(final Transaction transaction) {
        try {
            return constructSpecializedAccessor(transaction);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Not a valid signed transaction");
        }
    }

    public SignedTxnAccessor constructSpecializedAccessor(final Transaction transaction)
            throws InvalidProtocolBufferException {
        return internalSpecializedConstruction(transaction.toByteArray(), transaction);
    }

    private SignedTxnAccessor internalSpecializedConstruction(
            final byte[] transactionBytes, final Transaction transaction)
            throws InvalidProtocolBufferException {
        final var body = extractTransactionBody(transaction);
        final var function = MiscUtils.FUNCTION_EXTRACTOR.apply(body);
        if (function == TokenAccountWipe) {
            return new TokenWipeAccessor(transactionBytes, transaction, dynamicProperties);
        }
        return SignedTxnAccessor.from(transactionBytes, transaction);
    }
}
