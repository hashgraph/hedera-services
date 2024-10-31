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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.EventConsensusData;
import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.GossipEvent;
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
import com.swirlds.platform.event.EventSerializationUtils;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;

/**
 * A wrapper around a {@link PlatformEvent} that holds additional information required by the
 * {@link com.swirlds.platform.event.stream.ConsensusEventStream}.
 */
public class CesEvent extends AbstractSerializableHashable
        implements RunningHashable, StreamAligned, Timestamped, ConsensusEvent {
    /** Value used to indicate that it is undefined*/
    public static final long UNDEFINED = -1;

    public static final long CLASS_ID = 0xe250a9fbdcc4b1baL;
    private static final int CES_EVENT_VERSION_ORIGINAL = 1;
    private static final int CES_EVENT_VERSION_PBJ_EVENT = 2;
    private static final int CONSENSUS_DATA_CLASS_VERSION = 2;

    /** the pre-consensus event */
    private PlatformEvent platformEvent;
    /** the running hash of this event */
    private final RunningHash runningHash = new RunningHash();
    /** the round in which this event received a consensus order and timestamp */
    private long roundReceived = UNDEFINED;
    /** true if this event is the last in consensus order of all those with the same received round */
    private boolean lastInRoundReceived = false;

    /**
     * Creates an empty instance
     */
    public CesEvent() {}

    /**
     * Create a new instance with the provided data.
     *
     * @param platformEvent         the pre-consensus event
     * @param roundReceived       the round in which this event received a consensus order and timestamp
     * @param lastInRoundReceived true if this event is the last in consensus order of all those with the same received
     *                            round
     */
    public CesEvent(
            @NonNull final PlatformEvent platformEvent, final long roundReceived, final boolean lastInRoundReceived) {
        Objects.requireNonNull(platformEvent);
        this.platformEvent = platformEvent;
        this.roundReceived = roundReceived;
        this.lastInRoundReceived = lastInRoundReceived;
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(out);
        Objects.requireNonNull(platformEvent);

        out.writePbjRecord(platformEvent.getGossipEvent(), GossipEvent.PROTOBUF);

        // some fields used to be part of the stream but are no longer used
        // in order to maintain compatibility with older versions of the stream, we write a constant in their place

        out.writeInt(CONSENSUS_DATA_CLASS_VERSION);
        out.writeLong(UNDEFINED); // ConsensusData.generation
        out.writeLong(UNDEFINED); // ConsensusData.roundCreated
        out.writeBoolean(false); // ConsensusData.stale
        out.writeBoolean(lastInRoundReceived);
        out.writeInstant(platformEvent.getConsensusTimestamp());
        out.writeLong(roundReceived);
        out.writeLong(platformEvent.getConsensusOrder());
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        this.platformEvent = switch (version) {
            case CES_EVENT_VERSION_ORIGINAL -> EventSerializationUtils.deserializePlatformEvent(in, false);
            case CES_EVENT_VERSION_PBJ_EVENT -> new PlatformEvent(in.readPbjRecord(GossipEvent.PROTOBUF));
            default -> throw new IOException("Unsupported version " + version);};

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
        platformEvent.setConsensusData(eventConsensusData);
    }

    @Override
    public RunningHash getRunningHash() {
        return runningHash;
    }

    /**
     * @return the platform event backing this consensus event
     */
    public PlatformEvent getPlatformEvent() {
        return platformEvent;
    }

    @Override
    @NonNull
    public Iterator<ConsensusTransaction> consensusTransactionIterator() {
        return platformEvent.consensusTransactionIterator();
    }

    @Override
    public long getConsensusOrder() {
        return platformEvent.getConsensusOrder();
    }

    @Override
    public Instant getConsensusTimestamp() {
        return platformEvent.getConsensusTimestamp();
    }

    @Override
    public Iterator<Transaction> transactionIterator() {
        return platformEvent.transactionIterator();
    }

    @Override
    public Instant getTimeCreated() {
        return platformEvent.getTimeCreated();
    }

    @NonNull
    @Override
    public NodeId getCreatorId() {
        return platformEvent.getCreatorId();
    }

    @NonNull
    @Override
    public SemanticVersion getSoftwareVersion() {
        return platformEvent.getSoftwareVersion();
    }

    /**
     * {{@inheritDoc}}
     */
    @NonNull
    @Override
    public EventCore getEventCore() {
        return getPlatformEvent().getEventCore();
    }

    /**
     * {{ @inheritDoc }}
     */
    @NonNull
    public Bytes getSignature() {
        return platformEvent.getSignature();
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
    public int getClassVersion() {
        return CES_EVENT_VERSION_PBJ_EVENT;
    }

    //
    // Timestamped
    //
    @Override
    public Instant getTimestamp() {
        return platformEvent.getConsensusTimestamp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(platformEvent, roundReceived, lastInRoundReceived);
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
        final CesEvent that = (CesEvent) other;
        return Objects.equals(platformEvent, that.platformEvent)
                && roundReceived == that.roundReceived
                && lastInRoundReceived == that.lastInRoundReceived;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("platformEvent", platformEvent)
                .append("roundReceived", roundReceived)
                .append("lastInRoundReceived", lastInRoundReceived)
                .toString();
    }
}
