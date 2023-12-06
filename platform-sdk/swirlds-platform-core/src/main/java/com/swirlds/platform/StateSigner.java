package com.swirlds.platform;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusGetter;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class StateSigner {
    /**
     * An object responsible for signing states with this node's key.
     */
    private final HashSigner signer;
    /** provides the current platform status */
    private final PlatformStatusGetter platformStatusGetter;

    public StateSigner(final HashSigner signer, final PlatformStatusGetter platformStatusGetter) {
        this.signer = signer;
        this.platformStatusGetter = platformStatusGetter;
    }

    public StateSignatureTransaction signState(@NonNull final ReservedSignedState reservedSignedState) {
        try(reservedSignedState) {
            if (platformStatusGetter.getCurrentStatus() == PlatformStatus.REPLAYING_EVENTS) {
                // the only time we don't want to submit signatures is during PCES replay
                return null;
            }

            final Hash stateHash = Objects.requireNonNull(reservedSignedState.get().getState().getHash());
            final Signature signature = signer.sign(stateHash);
            Objects.requireNonNull(signature);

            return new StateSignatureTransaction(
                    reservedSignedState.get().getRound(),
                    signature,
                    stateHash);
        }
    }
}
