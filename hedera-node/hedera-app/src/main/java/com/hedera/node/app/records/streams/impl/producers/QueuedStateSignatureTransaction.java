package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.streams.v7.BlockStateProof;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.Nullable;

public record QueuedStateSignatureTransaction(
        @Nullable StateSignatureTransaction sig, @Nullable BlockStateProof proof) {
    // Ensure that the signature is not null
    public QueuedStateSignatureTransaction {
        if (sig == null && proof == null) {
            throw new IllegalArgumentException("StateSignatureTransaction and BlockStateProof cannot both be null");
        }
    }
}
