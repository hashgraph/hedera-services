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
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.GossipEvent;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.events.EventCoreUtils;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A class used to hold information about an event transferred through gossip
 */
public class PlatformEvent extends AbstractSerializableHashable implements ConsensusEvent {
    private static final EventConsensusData NO_CONSENSUS =
            new EventConsensusData(null, ConsensusConstants.NO_CONSENSUS_ORDER);
    private static final long CLASS_ID = 0xfe16b46795bfb8dcL;

    private static final class ClassVersion {
        /**
         * Event serialization changes
         *
         * @since 0.46.0
         */
        public static final int BIRTH_ROUND = 3;
    }

    /** The gossip event */
    private GossipEvent gossipEvent;
    /** List of wrapped transaction */
    private List<TransactionWrapper> transactions;
    /** Self descriptor */
    private EventDescriptorWrapper descriptor;

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
    private EventConsensusData consensusData = NO_CONSENSUS;
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

    @SuppressWarnings("unused") // needed for RuntimeConstructable
    public PlatformEvent() {}

    /**
     * @param unsignedEvent   the hashed data for the event
     * @param signature the signature for the event
     */
    public PlatformEvent(final UnsignedEvent unsignedEvent, final byte[] signature) {
        this(new GossipEvent(
                unsignedEvent.getEventCore(), Bytes.wrap(signature), unsignedEvent.getEventTransactions()));
    }

    /**
     * @param unsignedEvent   the hashed data for the event
     * @param signature the signature for the event
     */
    public PlatformEvent(final UnsignedEvent unsignedEvent, final Bytes signature) {
        this(new GossipEvent(unsignedEvent.getEventCore(), signature, unsignedEvent.getEventTransactions()));
    }

    /**
     * @param gossipEvent the gossip event
     */
    public PlatformEvent(final GossipEvent gossipEvent) {
        this.gossipEvent = gossipEvent;
        this.timeReceived = Instant.now();
        this.senderId = null;
        this.consensusData = NO_CONSENSUS;
        if (gossipEvent.eventCore() != null) {
            this.birthRound = gossipEvent.eventCore().birthRound();
        }
        transactions = gossipEvent.eventTransaction().stream()
                .map(TransactionWrapper::new)
                .toList();
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

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {

        EventSerializationUtils.serializeEvent(
                out,
                new BasicSoftwareVersion(getSoftwareVersion().major()),
                getEventCore(),
                getSelfParent(),
                getOtherParents(),
                gossipEvent.eventTransaction());
        out.writeInt((int) gossipEvent.signature().length());
        gossipEvent.signature().writeTo(out);
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final UnsignedEvent unsignedEvent = UnsignedEvent.deserialize(in);
        final Bytes signature = Bytes.wrap(in.readByteArray(SignatureType.RSA.signatureLength()));

        this.gossipEvent =
                new GossipEvent(unsignedEvent.getEventCore(), signature, unsignedEvent.getEventTransactions());
        this.timeReceived = Instant.now();
        this.senderId = null;
        this.consensusData = NO_CONSENSUS;
        if (gossipEvent.eventCore() != null) {
            this.birthRound = gossipEvent.eventCore().birthRound();
        }
        transactions = gossipEvent.eventTransaction().stream()
                .map(TransactionWrapper::new)
                .toList();
    }

    /**
     * Get the hashed data for the event.
     */
    public GossipEvent getGossipEvent() {
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
     * Get the descriptor for the event.
     *
     * @return the descriptor for the event
     */
    public EventDescriptorWrapper getDescriptor() {
        if (descriptor == null) {
            if (getHash() == null) {
                throw new IllegalStateException("The hash of the event must be set before creating the descriptor");
            }

            descriptor = new EventDescriptorWrapper(new EventDescriptor(
                    getHash().getBytes(), getEventCore().creatorNodeId(), getBirthRound(), getGeneration()));
        }

        return descriptor;
    }

    @Override
    public Iterator<Transaction> transactionIterator() {
        return new TypedIterator<>(transactions.iterator());
    }

    @Override
    public Instant getTimeCreated() {
        if (gossipEvent.eventCore() != null && gossipEvent.eventCore().timeCreated() != null) {
            return CommonUtils.pbjTimestampToInstant(gossipEvent.eventCore().timeCreated());
        }

        return null;
    }

    @NonNull
    @Override
    public SemanticVersion getSoftwareVersion() {
        if (getEventCore().version() != null) {
            return getEventCore().version();
        }
        throw new IllegalStateException("Software version not set");
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
        return new NodeId(getEventCore().creatorNodeId());
    }

    /**
     * Get the generation of the event.
     *
     * @return the generation of the event
     */
    public long getGeneration() {
        return EventCoreUtils.getGeneration(getEventCore());
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
        return transactions.size();
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
        return new TypedIterator<>(transactions.iterator());
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

    /**
     * Set the consensus timestamp on the transaction wrappers for this event. This must be done after the consensus time is
     * set for this event.
     */
    public void setConsensusTimestampsOnTransactions() {
        if (this.consensusData == NO_CONSENSUS) {
            throw new IllegalStateException("Consensus data must be set");
        }

        for (int i = 0; i < transactions.size(); i++) {
            transactions.get(i).setConsensusTimestamp(EventUtils.getTransactionTime(this, i));
        }
    }

    public List<TransactionWrapper> getTransactions() {
        return transactions;
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

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.BIRTH_ROUND;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.BIRTH_ROUND;
    }

    /**
     * Get the event descriptor for the self parent.
     *
     * @return the event descriptor for the self parent
     */
    @Nullable
    public EventDescriptorWrapper getSelfParent() {
        if (!getEventCore().parents().isEmpty()) {
            return new EventDescriptorWrapper(getEventCore().parents().getFirst());
        }
        return null;
    }

    /**
     * Get the event descriptors for the other parents.
     *
     * @return the event descriptors for the other parents
     */
    @NonNull
    public List<EventDescriptorWrapper> getOtherParents() {
        if (getEventCore().parents().size() > 1) {
            return getEventCore().parents().subList(1, getEventCore().parents().size()).stream()
                    .map(EventDescriptorWrapper::new)
                    .toList();
        }
        return Collections.emptyList();
    }

    /** @return a list of all parents, self parent (if any), + all other parents */
    @NonNull
    public List<EventDescriptorWrapper> getAllParents() {
        if (gossipEvent.eventCore() == null) {
            return Collections.emptyList();
        }

        return getEventCore().parents().stream()
                .map(EventDescriptorWrapper::new)
                .toList();
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
        return Objects.equals(getEventCore(), that.getEventCore()) && Objects.equals(consensusData, that.consensusData);
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

    @Override
    public void setHash(final Hash hash) {
        super.setHash(hash);
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
