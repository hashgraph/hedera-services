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

package com.swirlds.platform.event;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndLogIfInterrupted;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.EventConsensusData;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.system.events.EventMetadata;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A class used to hold information about an event throughout its lifecycle.
 */
public class PlatformEvent implements ConsensusEvent, Hashable {
    private static final EventConsensusData NO_CONSENSUS =
            new EventConsensusData(null, ConsensusConstants.NO_CONSENSUS_ORDER);
    /** The gossip event */
    private final GossipEvent gossipEvent;
    /** Metadata for an event that can be derived from a GossipEvent */
    private final EventMetadata metadata;
    /** The time this event was received via gossip */
    private Instant timeReceived;

    /**
     * The sequence number of an event before it is added to the write queue.
     */
    public static final long NO_STREAM_SEQUENCE_NUMBER = -1;

    /**
     * Each event is assigned a sequence number as it is written to the preconsensus event stream. This is used to
     * signal when events have been made durable.
     */
    private long streamSequenceNumber = NO_STREAM_SEQUENCE_NUMBER;

    /**
     * The id of the node which sent us this event
     * <p>
     * The sender ID of an event should not be serialized when an event is serialized, and it should not affect the hash
     * of the event in any way.
     */
    private NodeId senderId;

    /** The consensus data for this event */
    private EventConsensusData consensusData;
    /**
     * The consensus timestamp of this event (if it has reached consensus). This is the same timestamp that is stored in
     * {@link #consensusData}, but converted to an {@link Instant}.
     */
    private Instant consensusTimestamp = null;

    /**
     * This latch counts down when prehandle has been called on all application transactions contained in this event.
     */
    private final CountDownLatch prehandleCompleted = new CountDownLatch(1);
    /**
     * The actual birth round to return. May not be the original birth round if this event was created in the software
     * version right before the birth round migration.
     */
    private long birthRound;

    /**
     * Construct a new instance from an unsigned event and a signature.
     *
     * @param unsignedEvent the unsigned event
     * @param signature     the signature for the event
     */
    public PlatformEvent(@NonNull final UnsignedEvent unsignedEvent, @NonNull final byte[] signature) {
        this(unsignedEvent, Bytes.wrap(signature));
    }

    /**
     * Construct a new instance from an unsigned event and a signature.
     *
     * @param unsignedEvent the unsigned event
     * @param signature     the signature for the event
     */
    public PlatformEvent(@NonNull final UnsignedEvent unsignedEvent, @NonNull final Bytes signature) {
        this(
                new GossipEvent(
                        Objects.requireNonNull(unsignedEvent, "The unsignedEvent must not be null")
                                .getEventCore(),
                        Objects.requireNonNull(signature, "The signature must not be null"),
                        unsignedEvent.getEventTransactions()),
                unsignedEvent.getMetadata());
    }

    /**
     * Construct a new instance from a gossip event.
     *
     * @param gossipEvent the gossip event
     * @throws NullPointerException if gossipEvent or any of its fields are null
     */
    public PlatformEvent(@NonNull final GossipEvent gossipEvent) {
        this(Objects.requireNonNull(gossipEvent, "The gossipEvent must not be null"), new EventMetadata(gossipEvent));
    }

    private PlatformEvent(@NonNull final GossipEvent gossipEvent, @NonNull final EventMetadata metadata) {
        this.gossipEvent = gossipEvent;
        this.metadata = metadata;
        this.timeReceived = Instant.now();
        this.senderId = null;
        this.consensusData = NO_CONSENSUS;
        Objects.requireNonNull(gossipEvent.eventCore(), "The eventCore must not be null");
        this.birthRound = gossipEvent.eventCore().birthRound();
    }

    /**
     * Create a copy of this event while populating only the data received via gossip. Consensus data will not be
     * copied.
     *
     * @return a copy of this event
     */
    public PlatformEvent copyGossipedData() {
        final PlatformEvent platformEvent = new PlatformEvent(gossipEvent);
        platformEvent.setHash(getHash());
        return platformEvent;
    }

    /**
     * Set the sequence number in the preconsensus event stream for this event.
     *
     * @param streamSequenceNumber the sequence number
     */
    public void setStreamSequenceNumber(final long streamSequenceNumber) {
        if (this.streamSequenceNumber != NO_STREAM_SEQUENCE_NUMBER) {
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
     * The immutable gossip event
     */
    public @NonNull GossipEvent getGossipEvent() {
        return gossipEvent;
    }

    /**
     * {{ @inheritDoc }}
     */
    @Override
    @NonNull
    public Bytes getSignature() {
        return gossipEvent.signature();
    }

    /**
     * @return the descriptor for the event
     */
    public @NonNull EventDescriptorWrapper getDescriptor() {
        return metadata.getDescriptor(getBirthRound());
    }

    @Override
    public Iterator<Transaction> transactionIterator() {
        return new TypedIterator<>(metadata.getTransactions().iterator());
    }

    @Override
    public Instant getTimeCreated() {
        return metadata.getTimeCreated();
    }

    @NonNull
    @Override
    public SemanticVersion getSoftwareVersion() {
        return gossipEvent.eventCore().version();
    }

    /**
     * {{@inheritDoc}}
     */
    @NonNull
    @Override
    public EventCore getEventCore() {
        return gossipEvent.eventCoreOrThrow();
    }

    @NonNull
    @Override
    public NodeId getCreatorId() {
        return metadata.getCreatorId();
    }

    /**
     * Get the generation of the event.
     *
     * @return the generation of the event
     */
    public long getGeneration() {
        return metadata.getGeneration();
    }

    /**
     * Get the birth round of the event.
     *
     * @return the birth round of the event
     */
    public long getBirthRound() {
        return birthRound;
    }

    /**
     * @return the number of transactions this event contains
     */
    public int getTransactionCount() {
        return metadata.getTransactions().size();
    }

    /**
     * Get the time this event was received via gossip
     *
     * @return the time this event was received
     */
    public @NonNull Instant getTimeReceived() {
        return timeReceived;
    }

    /**
     * Set the time this event was received
     *
     * @param timeReceived the time this event was received
     */
    public void setTimeReceived(@NonNull final Instant timeReceived) {
        this.timeReceived = timeReceived;
    }

    /**
     * Get the id of the node which sent us this event
     *
     * @return the id of the node which sent us this event
     */
    @Nullable
    public NodeId getSenderId() {
        return senderId;
    }

    /**
     * Set the id of the node which sent us this event
     *
     * @param senderId the id of the node which sent us this event
     */
    public void setSenderId(@NonNull final NodeId senderId) {
        this.senderId = senderId;
    }

    /**
     * @return this event's consensus data, this will be null if the event has not reached consensus
     */
    @Nullable
    public EventConsensusData getConsensusData() {
        return consensusData;
    }

    /**
     * @return the consensus timestamp for this event, this will be null if the event has not reached consensus
     */
    @Nullable
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    @Override
    public @NonNull Iterator<ConsensusTransaction> consensusTransactionIterator() {
        return new TypedIterator<>(metadata.getTransactions().iterator());
    }

    /**
     * @return the consensus order for this event, this will be
     * {@link com.swirlds.platform.consensus.ConsensusConstants#NO_CONSENSUS_ORDER} if the event has not reached
     * consensus
     */
    public long getConsensusOrder() {
        return consensusData.consensusOrder();
    }

    /**
     * Set the consensus data for this event
     *
     * @param consensusData the consensus data for this event
     */
    public void setConsensusData(@NonNull final EventConsensusData consensusData) {
        if (this.consensusData != NO_CONSENSUS) {
            throw new IllegalStateException("Consensus data already set");
        }
        Objects.requireNonNull(consensusData, "consensusData");
        Objects.requireNonNull(consensusData.consensusTimestamp(), "consensusData.consensusTimestamp");
        this.consensusData = consensusData;
        this.consensusTimestamp = HapiUtils.asInstant(consensusData.consensusTimestamp());
    }

    public List<EventTransaction> getEventTransactions() {
        return gossipEvent.eventTransaction();
    }

    /**
     * Set the consensus timestamp on the transaction wrappers for this event. This must be done after the consensus time is
     * set for this event.
     */
    public void setConsensusTimestampsOnTransactions() {
        if (this.consensusData == NO_CONSENSUS) {
            throw new IllegalStateException("Consensus data must be set");
        }

        for (int i = 0; i < metadata.getTransactions().size(); i++) {
            metadata.getTransactions().get(i).setConsensusTimestamp(EventUtils.getTransactionTime(this, i));
        }
    }

    public List<TransactionWrapper> getTransactions() {
        return metadata.getTransactions();
    }

    /**
     * Signal that all transactions have been prehandled for this event.
     */
    public void signalPrehandleCompletion() {
        prehandleCompleted.countDown();
    }

    /**
     * Override the birth round for this event. This will only be called for events created in the software version
     * right before the birth round migration.
     *
     * @param birthRound the birth round that has been assigned to this event
     */
    public void overrideBirthRound(final long birthRound) {
        this.birthRound = birthRound;
    }

    /**
     * Wait until all transactions have been prehandled for this event.
     */
    public void awaitPrehandleCompletion() {
        abortAndLogIfInterrupted(prehandleCompleted::await, "interrupted while waiting for prehandle completion");
    }

    /**
     * Get the event descriptor for the self parent.
     *
     * @return the event descriptor for the self parent
     */
    @Nullable
    public EventDescriptorWrapper getSelfParent() {
        return metadata.getSelfParent();
    }

    /**
     * Get the event descriptors for the other parents.
     *
     * @return the event descriptors for the other parents
     */
    @NonNull
    public List<EventDescriptorWrapper> getOtherParents() {
        return metadata.getOtherParents();
    }

    /** @return a list of all parents, self parent (if any), + all other parents */
    @NonNull
    public List<EventDescriptorWrapper> getAllParents() {
        return metadata.getAllParents();
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(getDescriptor());
        stringBuilder.append("\n");
        stringBuilder.append("    sp: ");

        final EventDescriptorWrapper selfParent = getSelfParent();
        if (selfParent != null) {
            stringBuilder.append(selfParent);
        } else {
            stringBuilder.append("null");
        }
        stringBuilder.append("\n");

        int otherParentCount = 0;
        for (final EventDescriptorWrapper otherParent : getOtherParents()) {
            stringBuilder.append("    op");
            stringBuilder.append(otherParentCount);
            stringBuilder.append(": ");
            stringBuilder.append(otherParent);

            otherParentCount++;
            if (otherParentCount != getOtherParents().size()) {
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        // FUTURE WORK:
        // this method seems to be exclusively used for testing purposes. if that is the case, it would be better to
        // have a separate method for testing equality that is only used in the unit tests.
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final PlatformEvent that = (PlatformEvent) o;
        return Objects.equals(gossipEvent, that.gossipEvent) && Objects.equals(consensusData, that.consensusData);
    }

    /**
     * Check if the gossiped data of this event is equal to the gossiped data of another event. Ignores the consensus
     * data.
     *
     * @param that the other event
     * @return true if the gossiped data of this event is equal to the gossiped data of the other event
     */
    public boolean equalsGossipedData(@NonNull final PlatformEvent that) {
        return Objects.equals(getEventCore(), that.getEventCore())
                && Objects.equals(getSignature(), that.getSignature());
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    @Nullable
    @Override
    public Hash getHash() {
        return metadata.getHash();
    }

    @Override
    public void setHash(final Hash hash) {
        metadata.setHash(hash);
    }

    /**
     * Get the value used to determine if this event is ancient or not. Will be the event's generation prior to
     * migration, and the event's birth round after migration.
     *
     * @return the value used to determine if this event is ancient or not
     */
    public long getAncientIndicator(@NonNull final AncientMode ancientMode) {
        return switch (ancientMode) {
            case GENERATION_THRESHOLD -> getGeneration();
            case BIRTH_ROUND_THRESHOLD -> getBirthRound();
        };
    }
}
