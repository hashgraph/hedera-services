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

package com.swirlds.platform.internal;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.EventMetadata;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.events.ConsensusData;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.util.iterator.SkippingIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * An internal platform event. It holds all the event data relevant to the platform. It implements the Event interface
 * which is a public-facing form of an event.
 */
public class EventImpl extends EventMetadata implements Comparable<EventImpl>, ConsensusEvent {

    /** The base event information, including some gossip specific information */
    private PlatformEvent baseEvent;
    /** Consensus data calculated for an event */
    private ConsensusData consensusData;

    /**
     * An unmodifiable ordered set of system transaction indices in the array of all transactions, from lowest to
     * highest.
     */
    private Set<Integer> systemTransactionIndices;

    /** The number of application transactions in this round */
    private int numAppTransactions = 0;

    public EventImpl() {}

    public EventImpl(final PlatformEvent platformEvent, final EventImpl selfParent, final EventImpl otherParent) {
        this(platformEvent, new ConsensusData(), selfParent, otherParent);
    }

    public EventImpl(@NonNull final PlatformEvent platformEvent) {
        this(platformEvent, new ConsensusData(), null, null);
    }

    private EventImpl(
            final PlatformEvent baseEvent,
            final ConsensusData consensusData,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        super(selfParent, otherParent);
        Objects.requireNonNull(baseEvent, "baseEvent");
        Objects.requireNonNull(baseEvent.getSignature(), "signature");
        Objects.requireNonNull(consensusData, "consensusData");

        this.baseEvent = baseEvent;
        this.consensusData = consensusData;

        findSystemTransactions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final EventImpl event = (EventImpl) o;

        return Objects.equals(baseEvent, event.baseEvent) && Objects.equals(consensusData, event.consensusData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(baseEvent, consensusData);
    }

    /**
     * Events compare by generation. So sorting is always a topological sort. Returns -1 if this.generation is less than
     * other.generation, 1 if greater, 0 if equal.
     *
     * @param other {@inheritDoc}
     * @return {@inheritDoc}
     */
    @Override
    public synchronized int compareTo(final EventImpl other) {
        return Long.compare(getGeneration(), other.getGeneration());
    }

    /**
     * Iterates through all the transactions and stores the indices of the system transactions.
     */
    private void findSystemTransactions() {
        final ConsensusTransactionImpl[] transactions = getTransactions();
        if (transactions == null || transactions.length == 0) {
            systemTransactionIndices = Collections.emptySet();
            return;
        }

        final Set<Integer> indices = new TreeSet<>();
        for (int i = 0; i < transactions.length; i++) {
            if (transactions[i].isSystem()) {
                indices.add(i);
            } else {
                numAppTransactions++;
            }
        }
        this.systemTransactionIndices = Collections.unmodifiableSet(indices);
    }

    /**
     * Returns the number of application transactions in this event
     *
     * @return the number of application transactions
     */
    public int getNumAppTransactions() {
        return numAppTransactions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<Transaction> transactionIterator() {
        if (getTransactions() == null) {
            return Collections.emptyIterator();
        }
        return new SkippingIterator<>(getTransactions(), systemTransactionIndices);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<ConsensusTransaction> consensusTransactionIterator() {
        if (getTransactions() == null) {
            return Collections.emptyIterator();
        }
        return new SkippingIterator<>(getTransactions(), systemTransactionIndices);
    }

    //////////////////////////////////////////
    // Getters for the objects contained
    //////////////////////////////////////////

    /**
     * @return the base event
     */
    public PlatformEvent getBaseEvent() {
        return baseEvent;
    }

    /**
     * Check if the event has a self parent.
     *
     * @return true if the event has a self parent
     */
    public boolean hasSelfParent() {
        return baseEvent.getSelfParent() != null;
    }

    /**
     * Check if the event has other parents.
     *
     * @return true if the event has other parents
     */
    public boolean hasOtherParent() {
        return !baseEvent.getOtherParents().isEmpty();
    }

    //////////////////////////////////////////
    // Convenience methods for nested objects
    //////////////////////////////////////////

    //////////////////////////////////////////
    // Unsigned Event
    //////////////////////////////////////////

    public Instant getTimeCreated() {
        return baseEvent.getTimeCreated();
    }

    public Hash getBaseHash() {
        return baseEvent.getHash();
    }

    /**
     * @return array of transactions inside this event instance
     */
    public ConsensusTransactionImpl[] getTransactions() {
        return baseEvent.getUnsignedEvent().getTransactions();
    }

    public boolean isCreatedBy(final NodeId id) {
        return Objects.equals(getCreatorId(), id);
    }

    //////////////////////////////////////////
    // ConsensusData
    //////////////////////////////////////////

    public void setRoundReceived(final long roundReceived) {
        consensusData.setRoundReceived(roundReceived);
    }

    //////////////////////////////////////////
    //	Event interface methods
    //////////////////////////////////////////

    /**
     * Get the consensus timestamp of this event
     *
     * @return the consensus timestamp of this event
     */
    public Instant getConsensusTimestamp() {
        return baseEvent.getConsensusTimestamp();
    }

    /**
     * Get the generation of this event
     *
     * @return the generation of this event
     */
    public long getGeneration() {
        return baseEvent.getGeneration();
    }

    /**
     * Get the birth round of this event
     *
     * @return the birth round of this event
     */
    public long getBirthRound() {
        return baseEvent.getBirthRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SemanticVersion getSoftwareVersion() {
        return baseEvent.getSoftwareVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public NodeId getCreatorId() {
        return baseEvent.getCreatorId();
    }

    /**
     * Get the round received of this event
     *
     * @return the round received of this event
     */
    public long getRoundReceived() {
        return consensusData.getRoundReceived();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getConsensusOrder() {
        return baseEvent.getConsensusOrder();
    }

    /**
     * check whether this event doesn't contain any transactions
     *
     * @return true iff this event has no transactions
     */
    public boolean isEmpty() {
        return baseEvent.getPayloadCount() == 0;
    }

    @Override
    public String toString() {
        return baseEvent.toString();
    }
}
