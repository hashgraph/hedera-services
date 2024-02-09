package com.hedera.node.app.records.streams.impl.producers;

import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

public record QueuedStateSignatureTransaction(@NonNull StateSignatureTransaction sig, boolean poisonPill) {
    // Ensure that the signature is not null
    public QueuedStateSignatureTransaction {
        requireNonNull(sig, "StateSignatureTransaction must not be null");
    }
}
