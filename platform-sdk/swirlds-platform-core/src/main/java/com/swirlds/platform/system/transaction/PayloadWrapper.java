/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.transaction;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Objects;

/**
 * A transaction that may or may not reach consensus.
 */
public non-sealed class PayloadWrapper implements ConsensusTransaction {
    /**
     * The consensus timestamp of this transaction, or null if consensus has not yet been reached.
     * NOT serialized and not part of object equality or hash code
     */
    private Instant consensusTimestamp;
    /** An optional metadata object set by the application */
    private Object metadata;
    /** The protobuf data stored */
    private final OneOf<PayloadOneOfType> payload;

    public PayloadWrapper(@NonNull final OneOf<PayloadOneOfType> payload) {
        this.payload = Objects.requireNonNull(payload, "payload should not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Sets the consensus timestamp of this transaction
     *
     * @param consensusTimestamp
     * 		the consensus timestamp
     */
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }

    @NonNull
    @Override
    public OneOf<PayloadOneOfType> getPayload() {
        return payload;
    }

    @Override
    public int getSize() {
        if (PayloadOneOfType.STATE_SIGNATURE_PAYLOAD.equals(payload.kind())) {
            final StateSignaturePayload stateSignaturePayload = payload.as();
            return Long.BYTES // round
                    + Integer.BYTES // signature array length
                    + (int) stateSignaturePayload.signature().length()
                    + Integer.BYTES // hash array length
                    + (int) stateSignaturePayload.hash().length()
                    + Integer.BYTES; // epochHash, always null, which is SerializableStreamConstants.NULL_VERSION
        } else {
            final Bytes bytes = payload.as();
            return Integer.BYTES // add the the size of array length field
                    + (int) bytes.length(); // add the size of the array
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> @Nullable T getMetadata() {
        return (T) metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void setMetadata(@Nullable final T metadata) {
        this.metadata = metadata;
    }
}
