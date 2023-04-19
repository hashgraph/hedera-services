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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.internal.StateSignatureTransaction;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class transmits this node's signature on a signed state (via transactions).
 */
public final class SignatureTransmitter {

    private static final Logger logger = LogManager.getLogger(SignatureTransmitter.class);
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter;

    public SignatureTransmitter(
            final AddressBook addressBook,
            final NodeId selfId,
            final PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter) {
        this.addressBook = addressBook;
        this.selfId = selfId;
        this.prioritySystemTransactionSubmitter = prioritySystemTransactionSubmitter;
    }

    /**
     * Transmit this node's signature to other nodes for a signed state.  Signatures from zero weight nodes are
     * transmitted and valuable for the purpose of detecting ISSes.
     *
     * @param round
     * 		the round of the state that was signed
     * @param signature
     * 		the self signature on the state
     * @param stateHash
     * 		the hash of the state that was signed
     */
    public void transmitSignature(final long round, final Signature signature, final Hash stateHash) {
        final SystemTransaction signatureTransaction = new StateSignatureTransaction(round, signature, stateHash);
        final boolean success = prioritySystemTransactionSubmitter.submit(signatureTransaction);

        if (!success) {
            logger.error(EXCEPTION.getMarker(), "failed to create signed state transaction");
        }
    }
}
