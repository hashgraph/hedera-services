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
import com.hedera.pbj.runtime.OneOf;
import com.swirlds.platform.util.PayloadUtils;
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

    /**
     * Constructs a new payload wrapper
     *
     * @param payload the hapi payload
     *
     * @throws NullPointerException if payload is null
     */
    public PayloadWrapper(@NonNull final OneOf<PayloadOneOfType> payload) {
        this.payload = Objects.requireNonNull(payload, "payload should not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PayloadWrapper that = (PayloadWrapper) o;
        return Objects.equals(getPayload(), that.getPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getPayload());
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

    /**
     * Returns the payload as a PBJ record
     *
     * @return the payload
     */
    @NonNull
    @Override
    public OneOf<PayloadOneOfType> getPayload() {
        return payload;
    }

    /**
     * Get the serialized size of the transaction. This method returns the same value as
     * {@code SwirldsTransaction.getSerializedLength()} and {@code StateSignatureTransaction.getSerializedLength()}.
     *
     * @return the size of the transaction in the unit of byte
     */
    @Override
    public int getSize() {
        return PayloadUtils.getLegacyPayloadSize(payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
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
