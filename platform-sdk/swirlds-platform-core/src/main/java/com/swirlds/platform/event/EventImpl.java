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

package com.swirlds.platform.event;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.common.stream.Timestamped;
import com.swirlds.common.utility.Clearable;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.consensus.CandidateWitness;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.ReachedConsensus;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.ConsensusData;
import com.swirlds.platform.system.events.DetailedConsensusEvent;
import com.swirlds.platform.system.events.EventSerializationOptions;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SystemTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.util.iterator.SkippingIterator;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * An internal platform event. It holds all the event data relevant to the platform. It implements the Event interface
 * which is a public-facing form of an event.
 */
@ConstructableIgnored
public class EventImpl
        implements Comparable<EventImpl>,
                SerializableHashable,
                OptionalSelfSerializable<EventSerializationOptions>,
                RunningHashable,
                StreamAligned,
                Timestamped,
                Clearable,
                ReachedConsensus {

    /**
     * the consensus timestamp of a transaction is guaranteed to be at least this many nanoseconds later than that of
     * the transaction immediately before it in consensus order, and to be a multiple of this (must be positive and a
     * multiple of 10)
     */
    public static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000;

    /**
     * The base event information, including some gossip specific information
     */
    private GossipEvent baseEvent;

    /**
     * Consensus data calculated for an event
     */
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

    /**
     * A list of references to the system transactions in the array of all transactions
     */
    private List<SystemTransaction> systemTransactions;

    /**
     * The number of application transactions in this round
     */
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

    /**
     * the self parent of this
     */
    private EventImpl selfParent;

    /**
     * the other parent of this
     */
    private EventImpl otherParent;

    /**
     * an estimate of what the consensus timestamp will be (could be a very bad guess)
     */
    private Instant estimatedTime;

    /**
     * has this event been cleared (because it was old and should be discarded)?
     */
    private boolean cleared = false;

    /**
     * is this a witness? (is round > selfParent's round, or there is no self parent?)
     */
    private boolean isWitness;

    /**
     * has this witness decided as famous?
     */
    private boolean isFamous;

    /**
     * is this both a witness and the fame election is over?
     */
    private boolean isFameDecided;

    /**
     * is this event a judge?
     */
    private boolean isJudge;

    /**
     * is this part of the consensus order yet?
     */
    private boolean isConsensus;

    /**
     * the local time (not consensus time) at which the event reached consensus
     */
    private Instant reachedConsTimestamp;

    /**
     * lastSee[m] is the last ancestor created by m (memoizes function from Swirlds-TR-2020-01)
     */
    private EventImpl[] lastSee;

    /**
     * stronglySeeP[m] is strongly-seen witness in parent round by m (memoizes function from Swirlds-TR-2020-01)
     */
    private EventImpl[] stronglySeeP;

    /**
     * The first witness that's a self-ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
     */
    private EventImpl firstSelfWitnessS;

    /**
     * the first witness that's an ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
     */
    private EventImpl firstWitnessS;

    /**
     * temporarily used during any graph algorithm that needs to mark vertices (events) already visited
     */
    private int mark;

    /**
     * the time at which each unique famous witness in the received round first received this event
     */
    private List<Instant> recTimes;

    /**
     * the created round of this event (max of parents', plus either 0 or 1. 1 if no parents. 0 if neg infinity)
     */
    private long roundCreated = ConsensusConstants.ROUND_UNDEFINED;

    /** is there a consensus that this event is stale (no order, transactions ignored) */
    private boolean stale;

    /**
     * an array that holds votes for witness elections. the index for each vote matches the index of the witness in the
     * current election
     */
    private boolean[] votes;

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

        this.estimatedTime = Instant.now(); // until a better estimate is found, just guess the time it is now
        // ConsensusImpl.currMark starts at 1 and counts up, so all events initially count as
        // unmarked
        this.mark = ConsensusConstants.EVENT_UNMARKED;
        this.selfParent = selfParent;
        this.otherParent = otherParent;

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
     * Set the consensusTimestamp to an estimate of what it will be when consensus is reached even if it has already
     * reached consensus. Callers are responsible for checking the consensus systemIndicesStatus of this event and using
     * the consensus time or estimated time appropriately.
     *
     * <p>Estimated consensus times are predicted only here and in Platform.estimateTime().
     *
     * @param selfId                    the ID of this platform
     * @param avgSelfCreatedTimestamp   self event consensus timestamp minus time created
     * @param avgOtherReceivedTimestamp other event consensus timestamp minus time received
     * @deprecated this is only used for SwirldState1 which we no longer support, and it did not do any estimates
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
     * A convenience method that supplies every transaction in this event to a consumer.
     *
     * @param consumer
     * 		a transaction consumer
     */
    public void forEachTransaction(@NonNull final Consumer<Transaction> consumer) {
        for (final Iterator<Transaction> transIt = transactionIterator(); transIt.hasNext(); ) {
            consumer.accept(transIt.next());
        }
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
        clear();

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
     * Returns an iterator over the application events in this transaction.
     *
     * @return a transaction iterator
     */
    public Iterator<Transaction> transactionIterator() {
        if (getTransactions() == null) {
            return Collections.emptyIterator();
        }
        return new SkippingIterator<>(getTransactions(), systemTransactionIndices);
    }

    /**
     * Returns an iterator over the application events in this transaction, which have all reached consensus. Each
     * invocation returns a new iterator over the same transactions. This method is thread safe.
     *
     * @return a consensus transaction iterator
     */
    public Iterator<ConsensusTransaction> consensusTransactionIterator() {
        if (getTransactions() == null) {
            return Collections.emptyIterator();
        }
        return new SkippingIterator<>(getTransactions(), systemTransactionIndices);
    }

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

    /**
     * Get the hashed data for the event.
     */
    public BaseEventHashedData getHashedData() {
        return getBaseEventHashedData();
    }

    /**
     * Get the unhashed data for the event.
     */
    public BaseEventUnhashedData getUnhashedData() {
        return getBaseEventUnhashedData();
    }

    /**
     * @return Consensus data calculated for an event
     */
    public ConsensusData getConsensusData() {
        return consensusData;
    }

    /**
     * Returns the time this event was created as claimed by its creator.
     *
     * @return the created time
     */
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

    /**
     * @return The hash instance of the hashed base event data.
     */
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

    public byte[] getSignature() {
        return baseEvent.getUnhashedData().getSignature();
    }

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
     * @deprecated consensus events are part of {@link ConsensusRound}s, whether it's the last one can be determined by
     * looking at its position within the round
     */
    @Deprecated(forRemoval = true)
    public boolean isLastInRoundReceived() {
        return consensusData.isLastInRoundReceived();
    }

    public void setLastInRoundReceived(final boolean lastInRoundReceived) {
        consensusData.setLastInRoundReceived(lastInRoundReceived);
    }

    /**
     * Returns an estimate of what the consensus timestamp will be (could be a very bad guess).
     *
     * @return the estimated consensus timestamp
     */
    public @NonNull Instant getEstimatedTime() {
        // Return the real thing if we have it
        if (getConsensusTimestamp() != null) {
            return getConsensusTimestamp();
        }
        return estimatedTime;
    }

    /**
     * The community's consensus timestamp for this event if a consensus is reached. Otherwise. it will be an estimate.
     *
     * @return the consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return consensusData.getConsensusTimestamp();
    }

    /**
     * Node ID of the otherParent. null if otherParent doesn't exist.
     *
     * @return Other parent event's ID
     */
    @Nullable
    public NodeId getOtherId() {
        return baseEvent.getUnhashedData().getOtherId();
    }

    /**
     * This event's generation, which is 1 plus max of parents' generations.
     *
     * @return This event's generation
     */
    public long getGeneration() {
        return baseEvent.getHashedData().getGeneration();
    }

    /**
     * Returns the software version of the node that created this event.
     *
     * @return the software version
     */
    @Nullable
    public SoftwareVersion getSoftwareVersion() {
        return baseEvent.getHashedData().getSoftwareVersion();
    }

    /**
     * Returns the creator of this event.
     *
     * @return the creator id
     */
    @NonNull
    public NodeId getCreatorId() {
        return baseEvent.getHashedData().getCreatorId();
    }

    /**
     * If isConsensus is true, the round where all unique famous witnesses see this event.
     *
     * @return the round number as described above
     */
    public long getRoundReceived() {
        return consensusData.getRoundReceived();
    }

    /**
     * if isConsensus is true,  the order of this in history (0 first), else -1
     *
     * @return consensusOrder the consensus order sequence number
     */
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

    /**
     * Erase all references to other events within this event. This can be used so other events can be garbage
     * collected, even if this one still has things pointing to it. The numEventsInMemory count is decremented here, and
     * incremented when the event is instantiated, so it is important to ensure that this is eventually called on every
     * event.
     */
    @Override
    public void clear() {
        if (cleared) {
            return;
        }
        cleared = true;
        EventCounter.eventCleared();
        selfParent = null;
        otherParent = null;
        clearMetadata();
    }

    /**
     * This event's parent event. null if none exists.
     *
     * @return The parent event of this event
     */
    @Nullable
    public EventImpl getSelfParent() {
        return selfParent;
    }

    /**
     * @param selfParent the self parent of this
     */
    public void setSelfParent(@Nullable final EventImpl selfParent) {
        this.selfParent = selfParent;
    }

    /**
     * The other parent event of this event. null if other parent doesn't exist.
     *
     * @return The other parent event
     */
    @Nullable
    public EventImpl getOtherParent() {
        return otherParent;
    }

    /**
     * @param otherParent the other parent of this
     */
    public void setOtherParent(@Nullable final EventImpl otherParent) {
        this.otherParent = otherParent;
    }

    /**
     * @param estimatedTime an estimate of what the consensus timestamp will be (could be a very bad guess)
     */
    public void setEstimatedTime(@NonNull final Instant estimatedTime) {
        this.estimatedTime = estimatedTime;
    }

    /**
     * Whether this is a witness event or not. True if either this event's round &gt; selfParent's round, or there is no
     * self parent.
     *
     * @return boolean value to tell whether this event is a witness or not
     */
    public boolean isWitness() {
        return isWitness;
    }

    public void setWitness(final boolean witness) {
        isWitness = witness;
    }

    /**
     * Is this event both a witness and famous?
     *
     * @return True means this event is both a witness and famous
     */
    public boolean isFamous() {
        return isFamous;
    }

    public void setFamous(final boolean famous) {
        isFamous = famous;
    }

    /**
     * Tells whether this event is a witness and the fame election is over or not.
     *
     * @return True means this event is a witness and the fame election is over
     */
    public boolean isFameDecided() {
        return isFameDecided;
    }

    /**
     * @param fameDecided is this both a witness and the fame election is over?
     */
    public void setFameDecided(final boolean fameDecided) {
        isFameDecided = fameDecided;
    }

    /**
     * @return true if this event is a judge
     */
    public boolean isJudge() {
        return isJudge;
    }

    /** Mark this event as a judge */
    public void setJudgeTrue() {
        isJudge = true;
    }

    /**
     * Is this event part of the consensus order?
     *
     * @return True means this is part of consensus order
     */
    public boolean isConsensus() {
        return isConsensus;
    }

    /**
     * @param consensus is this part of the consensus order yet?
     */
    public void setConsensus(final boolean consensus) {
        isConsensus = consensus;
    }

    /**
     * @return the local time (not consensus time) at which the event reached consensus
     */
    public @Nullable Instant getReachedConsTimestamp() {
        return reachedConsTimestamp;
    }

    /**
     * @param reachedConsTimestamp the local time (not consensus time) at which the event reached consensus
     */
    public void setReachedConsTimestamp(@NonNull final Instant reachedConsTimestamp) {
        this.reachedConsTimestamp = reachedConsTimestamp;
    }

    /**
     * @param m the member ID
     * @return last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getLastSee(final int m) {
        return lastSee[m];
    }

    /**
     * remember event, the last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
     *
     * @param m     the member ID
     * @param event the last seen {@link EventImpl} object created by m
     */
    public void setLastSee(final int m, @Nullable final EventImpl event) {
        lastSee[m] = event;
    }

    /**
     * Initialize the lastSee array to hold n elements (for n &ge; 0) (memoizes lastSee function from
     * Swirlds-TR-2020-01)
     *
     * @param n number of members in the initial address book
     */
    public void initLastSee(final int n) {
        lastSee = n == 0 ? null : new EventImpl[n];
    }

    /**
     * @return the number of elements lastSee holds (memoizes lastSee function from Swirlds-TR-2020-01)
     */
    public int sizeLastSee() {
        return lastSee == null ? 0 : lastSee.length;
    }

    /**
     * @param m the member ID
     * @return strongly-seen witness in parent round by m (memoizes stronglySeeP function from Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getStronglySeeP(final int m) {
        return stronglySeeP[m];
    }

    /**
     * remember event, the strongly-seen witness in parent round by m (memoizes stronglySeeP function from
     * Swirlds-TR-2020-01)
     *
     * @param m     the member ID
     * @param event the strongly-seen witness in parent round created by m
     */
    public void setStronglySeeP(final int m, @Nullable final EventImpl event) {
        stronglySeeP[m] = event;
    }

    /**
     * Initialize the stronglySeeP array to hold n elements (for n &ge; 0) (memoizes stronglySeeP function from
     * Swirlds-TR-2020-01)
     *
     * @param n number of members in AddressBook
     */
    public void initStronglySeeP(final int n) {
        stronglySeeP = n == 0 ? null : new EventImpl[n];
    }

    /**
     * @return the number of elements stronglySeeP holds (memoizes stronglySeeP function from Swirlds-TR-2020-01)
     */
    public int sizeStronglySeeP() {
        return stronglySeeP == null ? 0 : stronglySeeP.length;
    }

    /**
     * @return The first witness that's a self-ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getFirstSelfWitnessS() {
        return firstSelfWitnessS;
    }

    /**
     * @param firstSelfWitnessS The first witness that's a self-ancestor in the self round (memoizes function from
     *                          Swirlds-TR-2020-01)
     */
    public void setFirstSelfWitnessS(@Nullable final EventImpl firstSelfWitnessS) {
        this.firstSelfWitnessS = firstSelfWitnessS;
    }

    /**
     * @return the first witness that's an ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
     */
    public @Nullable EventImpl getFirstWitnessS() {
        return firstWitnessS;
    }

    /**
     * @param firstWitnessS the first witness that's an ancestor in the self round (memoizes function from
     *                      Swirlds-TR-2020-01)
     */
    public void setFirstWitnessS(@Nullable final EventImpl firstWitnessS) {
        this.firstWitnessS = firstWitnessS;
    }

    /**
     * @return temporarily used during any graph algorithm that needs to mark vertices (events) already visited
     */
    public int getMark() {
        return mark;
    }

    /**
     * @param mark temporarily used during any graph algorithm that needs to mark vertices (events) already visited
     */
    public void setMark(final int mark) {
        this.mark = mark;
    }

    /**
     * @return the time at which each unique famous witness in the received round first received this event
     */
    public @Nullable List<Instant> getRecTimes() {
        return recTimes;
    }

    /**
     * @param recTimes the time at which each unique famous witness in the received round first received this event
     */
    public void setRecTimes(@Nullable final List<Instant> recTimes) {
        this.recTimes = recTimes;
    }

    /**
     * The created round of this event, which is the max of parents' created around, plus either 0 or 1.
     *
     * @return The round number this event is created
     */
    public long getRoundCreated() {
        return roundCreated;
    }

    public void setRoundCreated(final long roundCreated) {
        this.roundCreated = roundCreated;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(final boolean stale) {
        this.stale = stale;
    }

    /**
     * Initialize the voting array
     *
     * @param numWitnesses the number of witnesses we are voting on
     */
    public void initVoting(final int numWitnesses) {
        if (votes == null || votes.length < numWitnesses) {
            votes = new boolean[numWitnesses];
            return;
        }
        Arrays.fill(votes, false);
    }

    /**
     * Get this witness' vote on the witness provided
     *
     * @param witness the witness being voted on
     * @return true if it's a YES vote, false if it's a NO vote
     */
    public boolean getVote(@NonNull final CandidateWitness witness) {
        return votes != null && votes.length > witness.getElectionIndex() && votes[witness.getElectionIndex()];
    }

    /**
     * Set this witness' vote on the witness provided
     *
     * @param witness the witness being voted on
     * @param vote    true if it's a YES vote, false if it's a NO vote
     */
    public void setVote(@NonNull final CandidateWitness witness, final boolean vote) {
        this.votes[witness.getElectionIndex()] = vote;
    }

    /** Clear all metadata used to calculate consensus, this metadata changes with every round */
    public void clearMetadata() {
        clearJudgeFlags();
        clearNonJudgeMetadata();
    }

    private void clearJudgeFlags() {
        setWitness(false);
        setFamous(false);
        setFameDecided(false);
        isJudge = false;
    }

    private void clearNonJudgeMetadata() {
        initLastSee(0);
        initStronglySeeP(0);
        setFirstSelfWitnessS(null);
        setFirstWitnessS(null);
        setRecTimes(null);
    }
}
