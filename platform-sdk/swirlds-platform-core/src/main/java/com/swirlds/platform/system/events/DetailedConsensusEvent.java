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

package com.swirlds.platform.system.events;

import com.hedera.hapi.platform.event.EventConsensusData;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.StreamAligned;
import com.swirlds.common.stream.Timestamped;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;

/**
 * An event that may or may not have reached consensus. If it has reached consensus, provides detailed consensus
 * information.
 */
public class DetailedConsensusEvent extends AbstractSerializableHashable
        implements RunningHashable, StreamAligned, Timestamped, ConsensusEvent {
    /** Value used to indicate that it is undefined*/
    public static final long UNDEFINED = -1;

    public static final long CLASS_ID = 0xe250a9fbdcc4b1baL;
    public static final int CLASS_VERSION = 1;

    /** the pre-consensus event */
    private GossipEvent gossipEvent;
    /** the running hash of this event */
    private final RunningHash runningHash = new RunningHash();
    /** the round in which this event received a consensus order and timestamp */
    private long roundReceived = UNDEFINED;
    /** true if this event is the last in consensus order of all those with the same received round */
    private boolean lastInRoundReceived = false;

    /**
     * Creates an empty instance
     */
    public DetailedConsensusEvent() {}

    /**
     * Create a new instance.
     *
     * @param event the event to copy the data from
     */
    public DetailedConsensusEvent(@NonNull final EventImpl event) { // TODO remove
        Objects.requireNonNull(event);
        this.gossipEvent = event.getBaseEvent();
        this.roundReceived = event.getRoundReceived();
        this.lastInRoundReceived = event.isLastInRoundReceived();
    }

    /**
     * Create a new instance with the provided data.
     *
     * @param gossipEvent         the pre-consensus event
     * @param roundReceived       the round in which this event received a consensus order and timestamp
     * @param lastInRoundReceived true if this event is the last in consensus order of all those with the same received
     *                            round
     */
    public DetailedConsensusEvent(
            @NonNull final GossipEvent gossipEvent, final long roundReceived, final boolean lastInRoundReceived) {
        Objects.requireNonNull(gossipEvent);
        this.gossipEvent = gossipEvent;
        this.roundReceived = roundReceived;
        this.lastInRoundReceived = lastInRoundReceived;
    }

    public static void serialize(
            @NonNull final SerializableDataOutputStream out,
            @NonNull final GossipEvent gossipEvent,
            final long roundReceived,
            final boolean lastInRoundReceived)
            throws IOException {
        Objects.requireNonNull(out);
        Objects.requireNonNull(gossipEvent);

        gossipEvent.serialize(out);

        // some fields used to be part of the stream but are no longer used
        // in order to maintain compatibility with older versions of the stream, we write a constant in their place

        out.writeInt(ConsensusData.CLASS_VERSION);
        out.writeLong(UNDEFINED); // ConsensusData.generation
        out.writeLong(UNDEFINED); // ConsensusData.roundCreated
        out.writeBoolean(false); // ConsensusData.stale
        out.writeBoolean(lastInRoundReceived);
        out.writeInstant(gossipEvent.getConsensusTimestamp());
        out.writeLong(roundReceived);
        out.writeLong(gossipEvent.getConsensusOrder());
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        serialize(out, gossipEvent, roundReceived, lastInRoundReceived);
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        this.gossipEvent = new GossipEvent();
        this.gossipEvent.deserialize(in, gossipEvent.getVersion());

        in.readInt(); // ConsensusData.version
        in.readLong(); // ConsensusData.generation
        in.readLong(); // ConsensusData.roundCreated
        in.readBoolean(); // ConsensusData.stale
        lastInRoundReceived = in.readBoolean();
        final Instant consensusTimestamp = in.readInstant();
        roundReceived = in.readLong();
        final long consensusOrder = in.readLong();

        final EventConsensusData eventConsensusData = EventConsensusData.newBuilder()
                .consensusTimestamp(HapiUtils.asTimestamp(consensusTimestamp))
                .consensusOrder(consensusOrder)
                .build();
        gossipEvent.setConsensusData(eventConsensusData);
    }

    @Override
    public RunningHash getRunningHash() {
        return runningHash;
    }

    /**
     * @return the pre-consensus event
     */
    public GossipEvent getGossipEvent() {
        return gossipEvent;
    }

    @Override
    public Iterator<ConsensusTransaction> consensusTransactionIterator() {
        return gossipEvent.consensusTransactionIterator();
    }

    @Override
    public long getConsensusOrder() {
        return gossipEvent.getConsensusOrder();
    }

    @Override
    public Instant getConsensusTimestamp() {
        return gossipEvent.getConsensusTimestamp();
    }

    @Override
    public Iterator<Transaction> transactionIterator() {
        return gossipEvent.transactionIterator();
    }

    @Override
    public Instant getTimeCreated() {
        return gossipEvent.getTimeCreated();
    }

    @NonNull
    @Override
    public NodeId getCreatorId() {
        return gossipEvent.getCreatorId();
    }

    @Nullable
    @Override
    public SoftwareVersion getSoftwareVersion() {
        return null;
    }

    /**
     * @return the signature for the event
     */
    public Bytes getSignature() {
        return gossipEvent.getSignature();
    }

    /**
     * @return the round in which this event received a consensus order and timestamp
     */
    public long getRoundReceived() {
        return roundReceived;
    }

    /**
     * @return true if this event is the last in consensus order of all those with the same received round
     */
    public boolean isLastInRoundReceived() {
        return lastInRoundReceived;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    //
    // Timestamped
    //
    @Override
    public Instant getTimestamp() {
        return gossipEvent.getConsensusTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(gossipEvent, roundReceived, lastInRoundReceived);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final DetailedConsensusEvent that = (DetailedConsensusEvent) other;
        return Objects.equals(gossipEvent, that.gossipEvent)
                && roundReceived == that.roundReceived
                && lastInRoundReceived == that.lastInRoundReceived;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("gossipEvent", gossipEvent)
                .append("roundReceived", roundReceived)
                .append("lastInRoundReceived", lastInRoundReceived)
                .toString();
    }
}
