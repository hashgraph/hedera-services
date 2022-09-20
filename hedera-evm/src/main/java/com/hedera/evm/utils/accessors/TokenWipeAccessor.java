package com.hedera.evm.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.evm.execution.EvmProperties;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;

import static com.hedera.evm.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;

public class TokenWipeAccessor extends SignedTxnAccessor {
    private final TokenWipeAccountTransactionBody body;
    private final boolean areNftsEnabled;
    private final int maxBatchSizeWipe;
    public TokenWipeAccessor(
            final byte[] signedTxnWrapperBytes,
            final Transaction txn,
            final EvmProperties dynamicProperties)
            throws InvalidProtocolBufferException {
        super(signedTxnWrapperBytes, txn);
        this.body = getTxn().getTokenWipe();
        this.areNftsEnabled = dynamicProperties.areNftsEnabled();
        this.maxBatchSizeWipe = dynamicProperties.maxBatchSizeWipe();
        setTokenWipeUsageMeta();
    }

    private void setTokenWipeUsageMeta() {
        final var tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(body);
        getSpanMapAccessor().setTokenWipeMeta(this, tokenWipeMeta);
    }
}
