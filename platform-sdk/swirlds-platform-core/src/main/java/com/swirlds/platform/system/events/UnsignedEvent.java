/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.events;

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.util.HapiUtils;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An event that has not yet been signed
 */
public class UnsignedEvent implements Hashable {
    /**
     * The core event data.
     */
    private final EventCore eventCore;

    /**
     * The transactions of the event.
     */
    private final List<EventTransaction> eventTransactions;

    /**
     * The metadata of the event.
     */
    private final EventMetadata metadata;

    /**
     * Create a UnsignedEvent object
     *
     * @param softwareVersion the software version of the node that created this event.
     * @param creatorId       ID of this event's creator
     * @param selfParent      self parent event descriptor
     * @param otherParents    other parent event descriptors
     * @param birthRound      the round in which this event was created.
     * @param timeCreated     creation time, as claimed by its creator
     * @param transactions    list of transactions included in this event instance
     */
    public UnsignedEvent(
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final NodeId creatorId,
            @Nullable final EventDescriptorWrapper selfParent,
            @NonNull final List<EventDescriptorWrapper> otherParents,
            final long birthRound,
            @NonNull final Instant timeCreated,
            @NonNull final List<EventTransaction> transactions) {
        Objects.requireNonNull(transactions, "The transactions must not be null");
        this.eventTransactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.metadata = new EventMetadata(creatorId, selfParent, otherParents, timeCreated, transactions);
        this.eventCore = new EventCore(
                creatorId.id(),
                birthRound,
                HapiUtils.asTimestamp(timeCreated),
                this.metadata.getAllParents().stream()
                        .map(EventDescriptorWrapper::eventDescriptor)
                        .toList(),
                softwareVersion.getPbjSemanticVersion());
    }

    /**
     * @return the metadata of the event
     */
    public EventMetadata getMetadata() {
        return metadata;
    }

    @NonNull
    public Instant getTimeCreated() {
        return metadata.getTimeCreated();
    }

    /**
     * @return array of transactions inside this event instance
     */
    @NonNull
    public List<TransactionWrapper> getTransactions() {
        return metadata.getTransactions();
    }

    /**
     * Get the event descriptor for this event, creating one if it hasn't yet been created. If called more than once
     * then return the same instance.
     *
     * @return an event descriptor for this event
     * @throws IllegalStateException if called prior to this event being hashed
     */
    @NonNull
    public EventDescriptorWrapper getDescriptor() {
        return metadata.getDescriptor(eventCore.birthRound());
    }

    /**
     * Get the core event data.
     *
     * @return the core event data
     */
    @NonNull
    public EventCore getEventCore() {
        return eventCore;
    }

    /**
     * Get the transactions of the event.
     *
     * @return list of transactions
     */
    @NonNull
    public List<EventTransaction> getEventTransactions() {
        return eventTransactions;
    }

    @Override
    public @Nullable Hash getHash() {
        return metadata.getHash();
    }

    @Override
    public void setHash(final Hash hash) {
        metadata.setHash(hash);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final UnsignedEvent that = (UnsignedEvent) o;

        return (Objects.equals(eventCore, that.eventCore)) && Objects.equals(eventTransactions, that.eventTransactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventCore, eventTransactions);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append(eventCore)
                .append(eventTransactions)
                .append("hash", getHash() == null ? "null" : getHash().toHex(5))
                .toString();
    }
}
