/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.status.PlatformStatusNexus;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * This class is responsible for signing states and producing {@link StateSignaturePayload}s.
 */
public class StateSigner {
    /**
     * An object responsible for signing states with this node's key.
     */
    private final PlatformSigner signer;
    /**
     * provides the current platform status
     */
    private final PlatformStatusNexus statusNexus;

    /**
     * Create a new {@link StateSigner} instance.
     *
     * @param signer      an object responsible for signing states with this node's key
     * @param statusNexus provides the current platform status
     */
    public StateSigner(@NonNull final PlatformSigner signer, @NonNull final PlatformStatusNexus statusNexus) {
        this.signer = Objects.requireNonNull(signer);
        this.statusNexus = Objects.requireNonNull(statusNexus);
    }

    /**
     * Sign the given state and produce a {@link StateSignaturePayload} containing the signature. This method
     * assumes that the given {@link ReservedSignedState} is reserved by the caller and will release the state when
     * done.
     *
     * @param reservedSignedState the state to sign
     * @return a {@link StateSignaturePayload} containing the signature, or null if the state should not be signed
     */
    public @Nullable ConsensusTransactionImpl signState(@NonNull final ReservedSignedState reservedSignedState) {
        try (reservedSignedState) {
            if (statusNexus.getCurrentStatus() == PlatformStatus.REPLAYING_EVENTS) {
                // the only time we don't want to submit signatures is during PCES replay
                return null;
            }

            final Hash stateHash =
                    Objects.requireNonNull(reservedSignedState.get().getState().getHash());
            final Bytes signature = signer.signImmutable(stateHash);
            Objects.requireNonNull(signature);

            final StateSignaturePayload payload = StateSignaturePayload.newBuilder()
                    .round(reservedSignedState.get().getRound())
                    .signature(signature)
                    .hash(stateHash.getBytes())
                    .build();
            return new StateSignatureTransaction(payload);
        }
    }
}
