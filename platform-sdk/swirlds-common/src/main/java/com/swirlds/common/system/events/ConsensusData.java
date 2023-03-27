/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A class used to store consensus data about an event.
 * <p>
 * This data is available for an event only after consensus has been determined for it. When an event is initially
 * created, there is no consensus data for it.
 */
public class ConsensusData implements SelfSerializable {
    private static final long CLASS_ID = 0xddf20b7ce114a711L;
    private static final int CLASS_VERSION_ORIGINAL = 1;
    private static final int CLASS_VERSION_REMOVED_WITNESS_FAMOUS = 2;
    private static final int CLASS_VERSION = CLASS_VERSION_REMOVED_WITNESS_FAMOUS;

    /** Value used to indicate that consensus has not been reached */
    public static final long NO_CONSENSUS = -1;

    /** @deprecated generation (which is 1 plus max of parents' generations) */
    @Deprecated(forRemoval = true) // there is no need to store the events generation inside consensusData
    private long generation;
    /** the created round of this event (max of parents', plus either 0 or 1. 0 if not parents. -1 if neg infinity) */
    private long roundCreated;
    /** is there a consensus that this event is stale (no order, transactions ignored) */
    private boolean stale;
    /** the community's consensus timestamp for this event (or an estimate, if isConsensus==false) */
    private Instant consensusTimestamp;
    /* if isConsensus, round where >=1/2 famous see me */
    private long roundReceived;
    /** if isConsensus, then my order in history (0 first), else -1 */
    private long consensusOrder;
    /** is this event the last in consensus order of all those with the same received round? */
    private boolean lastInRoundReceived = false;

    public ConsensusData() {
        generation = NO_CONSENSUS;
        roundCreated = NO_CONSENSUS;
        roundReceived = NO_CONSENSUS;
        consensusOrder = NO_CONSENSUS;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(generation);
        out.writeLong(roundCreated);
        out.writeBoolean(stale);
        out.writeBoolean(lastInRoundReceived);
        out.writeInstant(consensusTimestamp);
        out.writeLong(roundReceived);
        out.writeLong(consensusOrder);
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        generation = in.readLong();
        roundCreated = in.readLong();
        if (version == CLASS_VERSION_ORIGINAL) {
            // read isWitness & isFamous
            in.readBoolean();
            in.readBoolean();
        }
        stale = in.readBoolean();
        lastInRoundReceived = in.readBoolean();
        consensusTimestamp = in.readInstant();
        roundReceived = in.readLong();
        consensusOrder = in.readLong();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        final ConsensusData that = (ConsensusData) o;

        return (generation == that.generation)
                && (roundCreated == that.roundCreated)
                && (stale == that.stale)
                && (roundReceived == that.roundReceived)
                && (consensusOrder == that.consensusOrder)
                && Objects.equals(consensusTimestamp, that.consensusTimestamp);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(generation)
                .append(roundCreated)
                .append(stale)
                .append(roundReceived)
                .append(consensusOrder)
                .append(consensusTimestamp)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("generation", generation)
                .append("roundCreated", roundCreated)
                .append("stale", stale)
                .append("consensusTimestamp", consensusTimestamp)
                .append("roundReceived", roundReceived)
                .append("consensusOrder", consensusOrder)
                .append("lastInRoundReceived", lastInRoundReceived)
                .toString();
    }

    /**
     * @param generation
     * 		the generation of the event
     * @deprecated
     */
    @Deprecated(forRemoval = true) // there is no need to store the events generation inside consensusData
    public void setGeneration(long generation) {
        this.generation = generation;
    }

    public long getRoundCreated() {
        return roundCreated;
    }

    public void setRoundCreated(long roundCreated) {
        this.roundCreated = roundCreated;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    public void setConsensusTimestamp(Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
    }

    public long getRoundReceived() {
        return roundReceived;
    }

    public void setRoundReceived(long roundReceived) {
        this.roundReceived = roundReceived;
    }

    public long getConsensusOrder() {
        return consensusOrder;
    }

    public void setConsensusOrder(long consensusOrder) {
        this.consensusOrder = consensusOrder;
    }

    public boolean isLastInRoundReceived() {
        return lastInRoundReceived;
    }

    public void setLastInRoundReceived(boolean lastInRoundReceived) {
        this.lastInRoundReceived = lastInRoundReceived;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }
}
