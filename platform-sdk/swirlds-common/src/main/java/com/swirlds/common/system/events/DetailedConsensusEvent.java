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

package com.swirlds.common.system.events;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.crypto.AbstractSerializableHashable;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.RunningHashable;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An event that may or may not have reached consensus. If it has reached consensus, provides detailed consensus
 * information.
 */
public class DetailedConsensusEvent extends AbstractSerializableHashable
        implements OptionalSelfSerializable<EventSerializationOptions>, RunningHashable {

    public static final long CLASS_ID = 0xe250a9fbdcc4b1baL;
    public static final int CLASS_VERSION = 1;

    /** The hashed part of a base event */
    private BaseEventHashedData baseEventHashedData;
    /** The part of a base event which is not hashed */
    private BaseEventUnhashedData baseEventUnhashedData;
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
     * @param baseEventHashedData
     * 		event data that is part of the event's hash
     * @param baseEventUnhashedData
     * 		event data that is not part of the event's hash
     * @param consensusData
     * 		the consensus data for this event
     */
    public DetailedConsensusEvent(
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final ConsensusData consensusData) {
        this.baseEventHashedData = baseEventHashedData;
        this.baseEventUnhashedData = baseEventUnhashedData;
        this.consensusData = consensusData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out, final EventSerializationOptions option)
            throws IOException {
        serialize(out, baseEventHashedData, baseEventUnhashedData, consensusData, option);
    }

    public static void serialize(
            final SerializableDataOutputStream out,
            final BaseEventHashedData baseEventHashedData,
            final BaseEventUnhashedData baseEventUnhashedData,
            final ConsensusData consensusData,
            final EventSerializationOptions option)
            throws IOException {
        out.writeOptionalSerializable(baseEventHashedData, false, option);
        out.writeSerializable(baseEventUnhashedData, false);
        out.writeSerializable(consensusData, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serialize(out, baseEventHashedData, baseEventUnhashedData, consensusData, EventSerializationOptions.FULL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        baseEventHashedData = in.readSerializable(false, BaseEventHashedData::new);
        baseEventUnhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
        consensusData = in.readSerializable(false, ConsensusData::new);
    }

    @Override
    public RunningHash getRunningHash() {
        return runningHash;
    }

    /**
     * Returns the event data that is part of this event's hash.
     */
    public BaseEventHashedData getBaseEventHashedData() {
        return baseEventHashedData;
    }

    /**
     * Returns the event data that is not part of this event's hash.
     */
    public BaseEventUnhashedData getBaseEventUnhashedData() {
        return baseEventUnhashedData;
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
        return new HashCodeBuilder()
                .append(baseEventHashedData.hashCode())
                .append(baseEventUnhashedData.hashCode())
                .append(consensusData.hashCode())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        final DetailedConsensusEvent that = (DetailedConsensusEvent) obj;
        return new EqualsBuilder()
                .append(this.baseEventHashedData, that.baseEventHashedData)
                .append(this.baseEventUnhashedData, that.baseEventUnhashedData)
                .append(this.consensusData, that.consensusData)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("baseEventHashedData", baseEventHashedData)
                .append("baseEventUnhashedData", baseEventUnhashedData)
                .append("consensusData", consensusData)
                .toString();
    }
}
