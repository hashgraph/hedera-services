/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusGetter;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class transmits this node's signature on a signed state (via transactions).
 */
public final class SignatureTransmitter {

    private static final Logger logger = LogManager.getLogger(SignatureTransmitter.class);

    private final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter;
    private final PlatformStatusGetter platformStatusGetter;

    /**
     * Create a new SignatureTransmitter.
     *
     * @param prioritySystemTransactionSubmitter used to submit system transactions at high priority
     * @param platformStatusGetter               provides the current platform status
     */
    public SignatureTransmitter(
            @NonNull final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter,
            @NonNull final PlatformStatusGetter platformStatusGetter) {

        this.prioritySystemTransactionSubmitter = Objects.requireNonNull(prioritySystemTransactionSubmitter);
        this.platformStatusGetter = Objects.requireNonNull(platformStatusGetter);
    }

    /**
     * Transmit this node's signature to other nodes for a signed state. Signatures from zero weight nodes are
     * transmitted and valuable for the purpose of detecting ISSes.
     *
     * @param round     the round of the state that was signed
     * @param signature the self signature on the state
     * @param stateHash the hash of the state that was signed
     */
    public void transmitSignature(final long round, @NonNull final Signature signature, @NonNull final Hash stateHash) {

        Objects.requireNonNull(signature);
        Objects.requireNonNull(stateHash);

        if (platformStatusGetter.getCurrentStatus() == PlatformStatus.REPLAYING_EVENTS) {
            // the only time we don't want to submit signatures is during PCES replay
            return;
        }

        final SystemTransaction signatureTransaction = new StateSignatureTransaction(round, signature, stateHash);
        final boolean success = prioritySystemTransactionSubmitter.submit(signatureTransaction);

        if (!success) {
            logger.error(EXCEPTION.getMarker(), "failed to create signed state transaction");
        }
    }
}
