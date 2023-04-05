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

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.common.stream.Timestamped;
import com.swirlds.common.system.NodeId;
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
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.RoundInfo;
import com.swirlds.platform.StreamEventParser;
import com.swirlds.platform.event.EventCounter;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.InternalEventData;
import com.swirlds.platform.util.iterator.SkippingIterator;
import com.swirlds.platform.util.iterator.TypedIterator;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * An internal platform event. It holds all the event data relevant to the platform. It implements the Event interface
 * which is a public-facing form of an event.
 */
@ConstructableIgnored
public class EventImpl extends AbstractSerializableHashable
        implements BaseEvent,
                Comparable<EventImpl>,
                PlatformEvent,
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
    /** Internal data used for calculating consensus */
    private InternalEventData internalEventData;

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

    public EventImpl() {}

    public EventImpl(final BaseEventHashedData baseEventHashedData, final BaseEventUnhashedData baseEventUnhashedData) {
        this(baseEventHashedData, baseEventUnhashedData, new ConsensusData(), null, null);
        updateConsensusDataGeneration();
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
        updateConsensusDataGeneration();
    }

    public EventImpl(final GossipEvent gossipEvent, final EventImpl selfParent, final EventImpl otherParent) {
        this(gossipEvent, new ConsensusData(), selfParent, otherParent);
        updateConsensusDataGeneration();
    }

    /**
     * This constructor is used in {@link StreamEventParser} when parsing events from stream
     *
     * @param consensusEvent
     */
    public EventImpl(final DetailedConsensusEvent consensusEvent) {
        buildFromConsensusEvent(consensusEvent);
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
        CommonUtils.throwArgNull(baseEvent, "baseEvent");
        CommonUtils.throwArgNull(baseEvent.getHashedData(), "baseEventDataHashed");
        CommonUtils.throwArgNull(baseEvent.getUnhashedData(), "baseEventDataNotHashed");
        CommonUtils.throwArgNull(consensusData, "consensusData");

        this.baseEvent = baseEvent;
        this.consensusData = consensusData;
        this.internalEventData = new InternalEventData(selfParent, otherParent);

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
     * initialize RunningHash instance
     */
    private void setDefaultValues() {
        runningHash = new RunningHash();
        if (baseEvent.getHashedData().getHash() != null) {
            baseEvent.buildDescriptor();
        }
    }

    /**
     * Set this event's generation to be 1 more than the max of its parents.
     *
     * @deprecated
     */
    @Deprecated(forRemoval = true) // there is no need to store the events generation inside consensusData
    public void updateConsensusDataGeneration() {
        consensusData.setGeneration(baseEvent.getHashedData().getGeneration());
    }

    /**
     * Calls {@link InternalEventData#clear()}
     */
    public void clear() {
        internalEventData.clear();
    }

    /**
     * Set the consensusTimestamp to an estimate of what it will be when consensus is reached even if it has already
     * reached consensus. Callers are responsible for checking the consensus systemIndicesStatus of this event and using
     * the consensus time or estimated time appropriately.
     * <p>
     * Estimated consensus times are predicted only here and in Platform.estimateTime().
     *
     * @param selfId                    the ID of this platform
     * @param avgSelfCreatedTimestamp   self event consensus timestamp minus time created
     * @param avgOtherReceivedTimestamp other event consensus timestamp minus time received
     */
    public synchronized void estimateTime(
            final NodeId selfId, final double avgSelfCreatedTimestamp, final double avgOtherReceivedTimestamp) {
        /* a base time */
        final Instant t;
        /* number of seconds to add to the base time */
        double sec;

        if (selfId.equalsMain(getCreatorId())) {
            // event by self
            t = getTimeCreated();
            // seconds from self creating an event to the consensus timestamp that event receives
            sec = avgSelfCreatedTimestamp; // secSC2T
        } else {
            // event by other
            t = getTimeReceived();
            // seconds from receiving an event (not by self) to the timestamp that event receives
            sec = avgOtherReceivedTimestamp; // secOR2T
        }

        sec = 0; // this will be changed to give a better estimate than 0 or those above

        setEstimatedTime(t.plus((long) (sec * 1_000_000_000.0), ChronoUnit.NANOS));
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
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        final EventImpl event = (EventImpl) o;

        return Objects.equals(baseEvent, event.baseEvent) && Objects.equals(consensusData, event.consensusData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(baseEvent)
                .append(consensusData)
                .toHashCode();
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
     * @param consensusEvent
     */
    void buildFromConsensusEvent(final DetailedConsensusEvent consensusEvent) {
        baseEvent = new GossipEvent(consensusEvent.getBaseEventHashedData(), consensusEvent.getBaseEventUnhashedData());
        consensusData = consensusEvent.getConsensusData();
        internalEventData = new InternalEventData();

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

    /**
     * @return Internal data used for calculating consensus
     */
    public InternalEventData getInternalEventData() {
        return internalEventData;
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

    public long getSelfParentGen() {
        return baseEvent.getHashedData().getSelfParentGen();
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
        return getCreatorId() == id.getId();
    }

    public boolean isCreatedBy(final long id) {
        return getCreatorId() == id;
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

    public void setRoundCreated(final long roundCreated) {
        consensusData.setRoundCreated(roundCreated);
    }

    public void setWitness(final boolean witness) {
        internalEventData.setWitness(witness);
    }

    public void setFamous(final boolean famous) {
        internalEventData.setFamous(famous);
    }

    public boolean isStale() {
        return consensusData.isStale();
    }

    public void setStale(final boolean stale) {
        consensusData.setStale(stale);
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
     */
    public boolean isLastInRoundReceived() {
        return consensusData.isLastInRoundReceived();
    }

    public void setLastInRoundReceived(final boolean lastInRoundReceived) {
        consensusData.setLastInRoundReceived(lastInRoundReceived);
    }

    //////////////////////////////////////////
    // InternalEventData
    //////////////////////////////////////////

    /**
     * @param selfParent the self parent of this
     */
    public void setSelfParent(final EventImpl selfParent) {
        internalEventData.setSelfParent(selfParent);
    }

    /**
     * @param otherParent the other parent of this
     */
    public void setOtherParent(final EventImpl otherParent) {
        internalEventData.setOtherParent(otherParent);
    }

    /**
     * @param fameDecided is this both a witness and the fame election is over?
     */
    public void setFameDecided(final boolean fameDecided) {
        internalEventData.setFameDecided(fameDecided);
    }

    /**
     * @param consensus is this part of the consensus order yet?
     */
    public void setConsensus(final boolean consensus) {
        internalEventData.setConsensus(consensus);
    }

    /**
     * @return the time this event was first received locally
     */
    public Instant getTimeReceived() {
        return internalEventData.getTimeReceived();
    }

    /**
     * @param timeReceived the time this event was first received locally
     */
    public void setTimeReceived(final Instant timeReceived) {
        internalEventData.setTimeReceived(timeReceived);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant getEstimatedTime() {
        // Return the real thing if we have it
        if (getConsensusTimestamp() != null) {
            return getConsensusTimestamp();
        }
        return internalEventData.getEstimatedTime();
    }

    /**
     * @param estimatedTime an estimate of what the consensus timestamp will be (could be a very bad guess)
     */
    public void setEstimatedTime(final Instant estimatedTime) {
        internalEventData.setEstimatedTime(estimatedTime);
    }

    /**
     * @return the local time (not consensus time) at which the event reached consensus
     */
    public Instant getReachedConsTimestamp() {
        return internalEventData.getReachedConsTimestamp();
    }

    /**
     * @return has this event been cleared (because it was old and should be discarded)?
     */
    public boolean isCleared() {
        return internalEventData.isCleared();
    }

    /**
     * @return the Election associated with the earliest round involved in the election for this event's fame
     */
    public RoundInfo.ElectionRound getFirstElection() {
        return internalEventData.getFirstElection();
    }

    /**
     * @param firstElection the Election associated with the earliest round involved in the election for this event's
     *                      fame
     */
    public void setFirstElection(final RoundInfo.ElectionRound firstElection) {
        internalEventData.setFirstElection(firstElection);
    }

    /**
     * @return temporarily used during any graph algorithm that needs to mark vertices (events) already visited
     */
    public int getMark() {
        return internalEventData.getMark();
    }

    /**
     * @param mark temporarily used during any graph algorithm that needs to mark vertices (events) already visited
     */
    public void setMark(final int mark) {
        internalEventData.setMark(mark);
    }

    /**
     * @return the time at which each unique famous witness in the received round first received this event
     */
    public ArrayList<Instant> getRecTimes() {
        return internalEventData.getRecTimes();
    }

    /**
     * @param recTimes the time at which each unique famous witness in the received round first received this event
     */
    public void setRecTimes(final ArrayList<Instant> recTimes) {
        internalEventData.setRecTimes(recTimes);
    }

    /**
     * @return is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous witness
     */
    public boolean isFrozen() {
        return internalEventData.isFrozen();
    }

    /**
     * @param frozen is roundCreated frozen (won't change with address book changes)? True if an ancestor of a famous
     *               witness
     */
    public void setFrozen(final boolean frozen) {
        internalEventData.setFrozen(frozen);
    }

    /**
     * @param reachedConsTimestamp the local time (not consensus time) at which the event reached consensus
     */
    public void setReachedConsTimestamp(final Instant reachedConsTimestamp) {
        internalEventData.setReachedConsTimestamp(reachedConsTimestamp);
    }

    /**
     * @param m the member ID
     * @return last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
     */
    public EventImpl getLastSee(final int m) {
        return internalEventData.getLastSee(m);
    }

    /**
     * remember event, the last ancestor created by m (memoizes lastSee function from Swirlds-TR-2020-01)
     *
     * @param m     the member ID of the creator
     * @param event the last seen {@link EventImpl} object created by m
     */
    public void setLastSee(final int m, final EventImpl event) {
        internalEventData.setLastSee(m, event);
    }

    /**
     * Initialize the lastSee array to hold n elements (for n &ge; 0) (memoizes lastSee function from
     * Swirlds-TR-2020-01)
     *
     * @param n number of members in AddressBook
     */
    public void initLastSee(final int n) {
        internalEventData.initLastSee(n);
    }

    /**
     * @return the number of elements lastSee holds (memoizes lastSee function from Swirlds-TR-2020-01)
     */
    public int sizeLastSee() {
        return internalEventData.sizeLastSee();
    }

    /**
     * @param m the member ID
     * @return strongly-seen witness in parent round by m (memoizes stronglySeeP function from Swirlds-TR-2020-01)
     */
    public EventImpl getStronglySeeP(final int m) {
        return internalEventData.getStronglySeeP(m);
    }

    /**
     * remember event, the strongly-seen witness in parent round by m (memoizes stronglySeeP function from
     * Swirlds-TR-2020-01)
     *
     * @param m     the member ID of the creator
     * @param event the strongly-seen witness in parent round created by m
     */
    public void setStronglySeeP(final int m, final EventImpl event) {
        internalEventData.setStronglySeeP(m, event);
    }

    /**
     * Initialize the stronglySeeP array to hold n elements (for n &ge; 0) (memoizes stronglySeeP function from
     * Swirlds-TR-2020-01)
     *
     * @param n
     * 		number of members in AddressBook
     */
    public void initStronglySeeP(final int n) {
        internalEventData.initStronglySeeP(n);
    }

    /**
     * @return the number of elements stronglySeeP holds (memoizes stronglySeeP function from Swirlds-TR-2020-01)
     */
    public int sizeStronglySeeP() {
        return internalEventData.sizeStronglySeeP();
    }

    /**
     * @return The first witness that's a self-ancestor in the self round (memoizes function from
     * 		Swirlds-TR-2020-01)
     */
    public EventImpl getFirstSelfWitnessS() {
        return internalEventData.getFirstSelfWitnessS();
    }

    /**
     * @param firstSelfWitnessS
     * 		The first witness that's a self-ancestor in the self round (memoizes function from Swirlds-TR-2020-01)
     */
    public void setFirstSelfWitnessS(final EventImpl firstSelfWitnessS) {
        internalEventData.setFirstSelfWitnessS(firstSelfWitnessS);
    }

    /**
     * @return the first witness that's an ancestor in the the self round (memoizes function from
     * 		Swirlds-TR-2020-01)
     */
    public EventImpl getFirstWitnessS() {
        return internalEventData.getFirstWitnessS();
    }

    /**
     * @param firstWitnessS
     * 		the first witness that's an ancestor in the the self round (memoizes function from Swirlds-TR-2020-01)
     */
    public void setFirstWitnessS(final EventImpl firstWitnessS) {
        internalEventData.setFirstWitnessS(firstWitnessS);
    }

    //////////////////////////////////////////
    //	Event interface methods
    //////////////////////////////////////////

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isWitness() {
        return internalEventData.isWitness();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFameDecided() {
        return internalEventData.isFameDecided();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isFamous() {
        return internalEventData.isFamous();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConsensus() {
        return internalEventData.isConsensus();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant getConsensusTimestamp() {
        return consensusData.getConsensusTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getOtherId() {
        return baseEvent.getUnhashedData().getOtherId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventImpl getSelfParent() {
        return internalEventData.getSelfParent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventImpl getOtherParent() {
        return internalEventData.getOtherParent();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getGeneration() {
        return baseEvent.getHashedData().getGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRoundCreated() {
        return consensusData.getRoundCreated();
    }

    public long getMaxRoundCreated() {
        final long selfParentRound =
                this.getSelfParent() == null ? 0 : this.getSelfParent().getRoundCreated();
        final long otherParentRound =
                this.getOtherParent() == null ? 0 : this.getOtherParent().getRoundCreated();
        return Math.max(selfParentRound, otherParentRound);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCreatorId() {
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
     * {@inheritDoc}
     */
    @Override
    public Instant getTimestamp() {
        return getConsensusTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RunningHash getRunningHash() {
        return runningHash;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toMediumString();
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
