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

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.event.GossipEvent;
import java.io.IOException;
import java.util.Objects;

/**
 * An event that may or may not have reached consensus. If it has reached consensus, provides detailed consensus
 * information.
 */
public class DetailedConsensusEvent extends AbstractSerializableHashable implements SelfSerializable, RunningHashable {

    public static final long CLASS_ID = 0xe250a9fbdcc4b1baL;
    public static final int CLASS_VERSION = 1;

    /** the pre-consensus event */
    private GossipEvent gossipEvent;
    /** Consensus data calculated for an event */
    private ConsensusData consensusData;
    /** the running hash of this event */
    private final RunningHash runningHash = new RunningHash();

    /**
     * Creates an empty instance
     */
    public DetailedConsensusEvent() {}

    /**
     * Create a new instance with the provided data.
     *
     * @param gossipEvent   the pre-consensus event
     * @param consensusData the consensus data for this event
     */
    public DetailedConsensusEvent(final GossipEvent gossipEvent, final ConsensusData consensusData) {
        this.gossipEvent = gossipEvent;
        this.consensusData = consensusData;
    }

    public static void serialize(
            final SerializableDataOutputStream out, final GossipEvent gossipEvent, final ConsensusData consensusData)
            throws IOException {
        gossipEvent.serialize(out);
        out.writeSerializable(consensusData, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serialize(out, gossipEvent, consensusData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        this.gossipEvent = new GossipEvent();
        this.gossipEvent.deserialize(in, gossipEvent.getVersion());
        consensusData = in.readSerializable(false, ConsensusData::new);
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

    /**
     * @return the signature for the event
     */
    public Bytes getSignature() {
        return gossipEvent.getSignature();
    }

    /**
     * Returns all the consensus data associated with this event.
     */
    public ConsensusData getConsensusData() {
        return consensusData;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(gossipEvent, consensusData);
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
        return Objects.equals(gossipEvent, that.gossipEvent) && Objects.equals(consensusData, that.consensusData);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("gossipEvent", gossipEvent)
                .append("consensusData", consensusData)
                .toString();
    }
}
