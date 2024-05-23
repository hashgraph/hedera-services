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

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.common.stream.Timestamped;
import com.swirlds.platform.event.EventMetadata;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.ConsensusData;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.DetailedConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.util.iterator.SkippingIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
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
@ConstructableIgnored
public class EventImpl extends EventMetadata
        implements Comparable<EventImpl>,
                ConsensusEvent,
                SerializableHashable,
                SelfSerializable,
                RunningHashable,
                StreamAligned,
                Timestamped {
    /**
     * the consensus timestamp of a transaction is guaranteed to be at least this many nanoseconds later than that of
     * the transaction immediately before it in consensus order, and to be a multiple of this (must be positive and a
     * multiple of 10)
     */
    public static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000;

    /** The base event information, including some gossip specific information */
    private GossipEvent baseEvent;
    /** Consensus data calculated for an event */
    private ConsensusData consensusData;
    /**
     * The consensus hash of this event. This hash includes all information for an event that was a result of it
     * reaching consensus. So the hash includes the consensus timestamp, the consensus order and other consensus info.
     * This hash should not be confused with the hash of the base event which is calculated before consensus is reached,
     * right after the event is created.
     */
    private Hash hash = null;

    private RunningHash runningHash;

    /**
     * An unmodifiable ordered set of system transaction indices in the array of all transactions, from lowest to
     * highest.
     */
    private Set<Integer> systemTransactionIndices;

    /** The number of application transactions in this round */
    private int numAppTransactions = 0;

    public EventImpl() {}

    public EventImpl(final GossipEvent gossipEvent, final EventImpl selfParent, final EventImpl otherParent) {
        this(gossipEvent, new ConsensusData(), selfParent, otherParent);
    }

    public EventImpl(@NonNull final GossipEvent gossipEvent) {
        this(gossipEvent, new ConsensusData(), null, null);
    }

    public EventImpl(
            final GossipEvent baseEvent,
            final ConsensusData consensusData,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        super(selfParent, otherParent);
        Objects.requireNonNull(baseEvent, "baseEvent");
        Objects.requireNonNull(baseEvent.getHashedData(), "baseEventDataHashed");
        Objects.requireNonNull(baseEvent.getSignature(), "signature");
        Objects.requireNonNull(consensusData, "consensusData");

        this.baseEvent = baseEvent;
        this.consensusData = consensusData;

        setDefaultValues();

        findSystemTransactions();
    }

    /**
     * Create an instance based on the given {@link DetailedConsensusEvent}
     * @param detailedConsensusEvent the detailed consensus event to build from
     */
    public EventImpl(final DetailedConsensusEvent detailedConsensusEvent) {
        buildFromConsensusEvent(detailedConsensusEvent);
    }

    /**
     * initialize RunningHash instance
     */
    private void setDefaultValues() {
        runningHash = new RunningHash();
    }

    /**
     * Returns the timestamp of the last transaction in this event. If this event has no transaction, then the timestamp
     * of the event will be returned
     *
     * @return timestamp of the last transaction
     */
    public Instant getLastTransTime() {
        if (getTransactions() == null) {
            return null;
        }
        // this is a special case. if an event has 0 or 1 transactions, the timestamp of the last transaction can be
        // considered to be the same, equivalent to the timestamp of the event
        if (getTransactions().length <= 1) {
            return getConsensusTimestamp();
        }
        return getTransactionTime(getTransactions().length - 1);
    }

    /**
     * Returns the timestamp of the transaction with given index in this event
     *
     * @param transactionIndex index of the transaction in this event
     * @return timestamp of the given index transaction
     */
    public Instant getTransactionTime(final int transactionIndex) {
        if (getConsensusTimestamp() == null || getTransactions() == null) {
            return null;
        }
        if (transactionIndex >= getTransactions().length) {
            throw new IllegalArgumentException("Event does not have a transaction with index:" + transactionIndex);
        }
        return getConsensusTimestamp().plusNanos(transactionIndex * MIN_TRANS_TIMESTAMP_INCR_NANOS);
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

    //////////////////////////////////////////
    // Serialization methods
    // Note: this class serializes itself as a com.swirlds.common.event.ConsensusEvent object
    //////////////////////////////////////////

    /**
     * This class serializes itself as a {@link DetailedConsensusEvent} object
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        DetailedConsensusEvent.serialize(out, baseEvent, consensusData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        final DetailedConsensusEvent consensusEvent = new DetailedConsensusEvent();
        consensusEvent.deserialize(in, version);
        buildFromConsensusEvent(consensusEvent);
    }

    /**
     * build current Event from consensusEvent
     *
     * @param consensusEvent the consensus event to build from
     */
    void buildFromConsensusEvent(final DetailedConsensusEvent consensusEvent) {
        baseEvent = consensusEvent.getGossipEvent();
        consensusData = consensusEvent.getConsensusData();
        // clears metadata in case there is any
        super.clear();

        setDefaultValues();
        findSystemTransactions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return DetailedConsensusEvent.CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return DetailedConsensusEvent.CLASS_VERSION;
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
     * Propagates consensus data to all transactions. Invoked when this event has reached consensus and all consensus
     * data is set.
     */
    public void consensusReached() {
        final ConsensusTransactionImpl[] transactions = getTransactions();
        if (transactions == null) {
            return;
        }

        for (int i = 0; i < transactions.length; i++) {
            final Instant transConsTime = getConsensusTimestamp().plusNanos(i * MIN_TRANS_TIMESTAMP_INCR_NANOS);
            transactions[i].setConsensusTimestamp(transConsTime);
        }
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
    public GossipEvent getBaseEvent() {
        return baseEvent;
    }

    /**
     * @return The hashed part of a base event
     */
    public BaseEventHashedData getHashedData() {
        return baseEvent.getHashedData();
    }

    /**
     * @return Consensus data calculated for an event
     */
    public ConsensusData getConsensusData() {
        return consensusData;
    }

    //////////////////////////////////////////
    // Convenience methods for nested objects
    //////////////////////////////////////////

    //////////////////////////////////////////
    // BaseEventHashedData
    //////////////////////////////////////////

    public Instant getTimeCreated() {
        return baseEvent.getHashedData().getTimeCreated();
    }

    public long getOtherParentGen() {
        return baseEvent.getHashedData().getOtherParentGen();
    }

    public Hash getSelfParentHash() {
        return baseEvent.getHashedData().getSelfParentHash();
    }

    public Hash getOtherParentHash() {
        return baseEvent.getHashedData().getOtherParentHash();
    }

    public Hash getBaseHash() {
        return baseEvent.getHashedData().getHash();
    }

    /**
     * @return array of transactions inside this event instance
     */
    public ConsensusTransactionImpl[] getTransactions() {
        return baseEvent.getHashedData().getTransactions();
    }

    public int getNumTransactions() {
        if (baseEvent.getHashedData().getTransactions() == null) {
            return 0;
        } else {
            return baseEvent.getHashedData().getTransactions().length;
        }
    }

    public boolean isCreatedBy(final NodeId id) {
        return Objects.equals(getCreatorId(), id);
    }

    //////////////////////////////////////////
    // ConsensusData
    //////////////////////////////////////////

    public void setConsensusTimestamp(final Instant consensusTimestamp) {
        consensusData.setConsensusTimestamp(consensusTimestamp);
    }

    public void setRoundReceived(final long roundReceived) {
        consensusData.setRoundReceived(roundReceived);
    }

    public void setConsensusOrder(final long consensusOrder) {
        consensusData.setConsensusOrder(consensusOrder);
    }

    /**
     * is this event the last in consensus order of all those with the same received round
     *
     * @return is this event the last in consensus order of all those with the same received round
     * @deprecated consensus events are part of {@link ConsensusRound}s, whether it's the last one
     *     can be determined by looking at its position within the round
     */
    @Deprecated(forRemoval = true)
    public boolean isLastInRoundReceived() {
        return consensusData.isLastInRoundReceived();
    }

    public void setLastInRoundReceived(final boolean lastInRoundReceived) {
        consensusData.setLastInRoundReceived(lastInRoundReceived);
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
        return consensusData.getConsensusTimestamp();
    }

    /**
     * Get the generation of this event
     *
     * @return the generation of this event
     */
    public long getGeneration() {
        return baseEvent.getHashedData().getGeneration();
    }

    /**
     * Get the birth round of this event
     *
     * @return the birth round of this event
     */
    public long getBirthRound() {
        return baseEvent.getHashedData().getBirthRound();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public SoftwareVersion getSoftwareVersion() {
        return baseEvent.getHashedData().getSoftwareVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public NodeId getCreatorId() {
        return baseEvent.getHashedData().getCreatorId();
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
        return consensusData.getConsensusOrder();
    }

    /**
     * check whether this event doesn't contain any transactions
     *
     * @return true iff this event has no transactions
     */
    public boolean isEmpty() {
        return getTransactions() == null || getTransactions().length == 0;
    }

    @Override
    public String toString() {
        return baseEvent.toString();
    }

    //
    // Timestamped
    //

    @Override
    public Instant getTimestamp() {
        return getConsensusTimestamp();
    }

    //
    // RunningHashable
    //

    @Override
    public RunningHash getRunningHash() {
        return runningHash;
    }

    //
    // Hashable
    //

    @Override
    public Hash getHash() {
        return hash;
    }

    @Override
    public void setHash(final Hash hash) {
        this.hash = hash;
    }

    /**
     * Get a mnemonic string representing this event. Event should be hashed prior to this being called. Useful for
     * debugging.
     */
    public String toMnemonic() {
        if (getHash() == null) {
            return "unhashed-event";
        }
        return getHash().toMnemonic();
    }
}
