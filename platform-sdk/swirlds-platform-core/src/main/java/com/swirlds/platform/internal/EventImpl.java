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

package com.swirlds.platform.internal;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.common.stream.Timestamped;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.events.ConsensusData;
import com.swirlds.common.system.events.DetailedConsensusEvent;
import com.swirlds.common.system.events.EventSerializationOptions;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.EventMetadata;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.util.iterator.SkippingIterator;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

/**
 * An internal platform event. It holds all the event data relevant to the platform. It implements the Event interface
 * which is a public-facing form of an event.
 */
@ConstructableIgnored
public class EventImpl extends EventMetadata
        implements BaseEvent,
                Comparable<EventImpl>,
                PlatformEvent,
                SerializableHashable,
                OptionalSelfSerializable<EventSerializationOptions>,
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
     * Tracks if this event was read out of a signed state.
     */
    private boolean fromSignedState;

    /**
     * An unmodifiable ordered set of system transaction indices in the array of all transactions, from lowest to
     * highest.
     */
    private Set<Integer> systemTransactionIndices;

    /** A list of references to the system transactions in the array of all transactions */
    private List<SystemTransaction> systemTransactions;

    /** The number of application transactions in this round */
    private int numAppTransactions = 0;

    /**
     * The sequence number of an event before it is added to the write queue.
     */
    public static final long NO_STREAM_SEQUENCE_NUMBER = -1;

    /**
     * The sequence number of an event that will never be written to disk because it is stale.
     */
    public static final long STALE_EVENT_STREAM_SEQUENCE_NUMBER = -2;

    /**
     * Each event is assigned a sequence number as it is written to the preconsensus event stream. This is used to
     * signal when events have been made durable.
     */
    private long streamSequenceNumber = NO_STREAM_SEQUENCE_NUMBER; // needs to be atomic, thread will mark as stale

    /**
     * This latch counts down when prehandle has been called on all application transactions contained in this event.
     */
    private final CountDownLatch prehandleCompleted = new CountDownLatch(1);

    public EventImpl() {}

    public EventImpl(final BaseEventHashedData baseEventHashedData, final BaseEventUnhashedData baseEventUnhashedData) {
        this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), null, null);
    }

    public EventImpl(
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final ConsensusData consensusData) {
        this(baseEventHashedData, baseEventUnhashedData, consensusData, null, null);
    }

    public EventImpl(
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), selfParent, otherParent);
    }

    public EventImpl(final GossipEvent gossipEvent, final EventImpl selfParent, final EventImpl otherParent) {
        this(gossipEvent, new ConsensusData(), selfParent, otherParent);
    }

    public EventImpl(
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final ConsensusData consensusData,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        this(new GossipEvent(baseEventHashedData, baseEventUnhashedData), consensusData, selfParent, otherParent);
    }

    public EventImpl(
            final GossipEvent baseEvent,
            final ConsensusData consensusData,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        super(selfParent, otherParent);
        Objects.requireNonNull(baseEvent, "baseEvent");
        Objects.requireNonNull(baseEvent.getHashedData(), "baseEventDataHashed");
        Objects.requireNonNull(baseEvent.getUnhashedData(), "baseEventDataNotHashed");
        Objects.requireNonNull(consensusData, "consensusData");

        this.baseEvent = baseEvent;
        this.consensusData = consensusData;

        EventCounter.eventCreated();

        setDefaultValues();

        findSystemTransactions();
    }

    /**
     * Set the sequence number in the preconsenus event stream for this event.
     *
     * @param streamSequenceNumber the sequence number
     */
    public void setStreamSequenceNumber(final long streamSequenceNumber) {
        if (this.streamSequenceNumber != NO_STREAM_SEQUENCE_NUMBER
                && streamSequenceNumber != STALE_EVENT_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("sequence number already set");
        }
        this.streamSequenceNumber = streamSequenceNumber;
    }

    /**
     * Get the sequence number in the preconsensus event stream for this event.
     *
     * @return the sequence number
     */
    public long getStreamSequenceNumber() {
        if (streamSequenceNumber == NO_STREAM_SEQUENCE_NUMBER) {
            throw new IllegalStateException("sequence number not set");
        }
        return streamSequenceNumber;
    }

    /**
     * Signal that all transactions have been prehandled for this event.
     */
    public void signalPrehandleCompletion() {
        prehandleCompleted.countDown();
    }

    /**
     * Wait until all transactions have been prehandled for this event.
     */
    public void awaitPrehandleCompletion() {
        abortAndLogIfInterrupted(prehandleCompleted::await, "interrupted while waiting for prehandle completion");
    }

    /**
     * initialize RunningHash instance
     */
    private void setDefaultValues() {
        runningHash = new RunningHash();
        if (baseEvent.getHashedData().getHash() != null) {
            baseEvent.buildDescriptor();
        }
    }

    /**
     * Set the consensusTimestamp to an estimate of what it will be when consensus is reached even
     * if it has already reached consensus. Callers are responsible for checking the consensus
     * systemIndicesStatus of this event and using the consensus time or estimated time
     * appropriately.
     *
     * <p>Estimated consensus times are predicted only here and in Platform.estimateTime().
     *
     * @param selfId the ID of this platform
     * @param avgSelfCreatedTimestamp self event consensus timestamp minus time created
     * @param avgOtherReceivedTimestamp other event consensus timestamp minus time received
     * @deprecated this is only used for SwirldState1 which we no longer support, and it did not do
     *     any estimates
     */
    @SuppressWarnings("unused")
    @Deprecated(forRemoval = true)
    public synchronized void estimateTime(
            final NodeId selfId, final double avgSelfCreatedTimestamp, final double avgOtherReceivedTimestamp) {
        setEstimatedTime(selfId.equals(getCreatorId()) ? getTimeCreated() : baseEvent.getTimeReceived());
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
        serialize(out, EventSerializationOptions.FULL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out, final EventSerializationOptions option)
            throws IOException {
        DetailedConsensusEvent.serialize(
                out, baseEvent.getHashedData(), baseEvent.getUnhashedData(), consensusData, option);
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
        baseEvent = new GossipEvent(consensusEvent.getBaseEventHashedData(), consensusEvent.getBaseEventUnhashedData());
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
            systemTransactions = Collections.emptyList();
            return;
        }

        final Set<Integer> indices = new TreeSet<>();
        final List<SystemTransaction> sysTrans = new ArrayList<>();
        for (int i = 0; i < transactions.length; i++) {
            if (transactions[i].isSystem()) {
                indices.add(i);
                sysTrans.add((SystemTransaction) getTransactions()[i]);
            } else {
                numAppTransactions++;
            }
        }
        this.systemTransactionIndices = Collections.unmodifiableSet(indices);
        this.systemTransactions = Collections.unmodifiableList(sysTrans);
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
     * An iterator over all system transactions in this event.
     *
     * @return system transaction iterator
     */
    public Iterator<SystemTransaction> systemTransactionIterator() {
        return new TypedIterator<>(systemTransactions.iterator());
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
            transactions[i].setConsensusOrder(getConsensusOrder());
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
    public BaseEventHashedData getBaseEventHashedData() {
        return baseEvent.getHashedData();
    }

    /**
     * @return The part of a base event which is not hashed
     */
    public BaseEventUnhashedData getBaseEventUnhashedData() {
        return baseEvent.getUnhashedData();
    }

    @Override
    public BaseEventHashedData getHashedData() {
        return getBaseEventHashedData();
    }

    @Override
    public BaseEventUnhashedData getUnhashedData() {
        return getBaseEventUnhashedData();
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

    public boolean hasUserTransactions() {
        return baseEvent.getHashedData().hasUserTransactions();
    }

    //////////////////////////////////////////
    // BaseEventUnhashedData
    //////////////////////////////////////////

    public byte[] getSignature() {
        return baseEvent.getUnhashedData().getSignature();
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

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Instant getEstimatedTime() {
        // Return the real thing if we have it
        if (getConsensusTimestamp() != null) {
            return getConsensusTimestamp();
        }
        return super.getEstimatedTime();
    }

    //////////////////////////////////////////
    //	Event interface methods
    //////////////////////////////////////////

    /** {@inheritDoc} */
    @Override
    public Instant getConsensusTimestamp() {
        return consensusData.getConsensusTimestamp();
    }

    /** {@inheritDoc} */
    @Override
    @Nullable
    public NodeId getOtherId() {
        return baseEvent.getUnhashedData().getOtherId();
    }

    /** {@inheritDoc} */
    @Override
    public long getGeneration() {
        return baseEvent.getHashedData().getGeneration();
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
     * {@inheritDoc}
     */
    @Override
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

    /**
     * Check if this event was read from a signed state.
     *
     * @return true iff this event was loaded from a signed state
     */
    public boolean isFromSignedState() {
        return fromSignedState;
    }

    /**
     * Mark this as an event that was read from a signed state.
     */
    public void markAsSignedStateEvent() {
        this.fromSignedState = true;
    }

    //
    // String methods
    //

    /**
     * @see EventStrings#toShortString(EventImpl)
     */
    public String toShortString() {
        return EventStrings.toShortString(this);
    }

    /**
     * @see EventStrings#toMediumString(EventImpl)
     */
    public String toMediumString() {
        return EventStrings.toMediumString(this);
    }

    @Override
    public String toString() {
        return toMediumString();
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
