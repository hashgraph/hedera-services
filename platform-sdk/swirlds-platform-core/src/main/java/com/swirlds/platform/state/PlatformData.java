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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.events.EventSerializationOptions;
import com.swirlds.platform.internal.EventImpl;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * A collection of miscellaneous platform data.
 */
public class PlatformData extends PartialMerkleLeaf implements MerkleLeaf {

    private static final long CLASS_ID = 0x1f89d0c43a8c08bdL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int EPOCH_HASH = 2;
        /** events and mingen are no longer part of the state, restart/reconnect now uses a snapshot */
        public static final int CONSENSUS_SNAPSHOT = 3;
    }

    /**
     * The round of this state. This state represents the handling of all transactions that have reached consensus
     * in all previous rounds. All transactions from this round will eventually be applied to this state.
     * The first state (genesis state) has a round of 0 because the first round is round defined as round 1,
     * and the genesis state is before any transactions are handled.
     */
    private long round;

    /**
     * how many consensus events have there been throughout all of history, up through the round received
     * that this SignedState represents.
     */
    private long numEventsCons;

    /**
     * running hash of the hashes of all consensus events have there been throughout all of history, up
     * through the round received that this SignedState represents.
     */
    private Hash hashEventsCons;

    /**
     * contains events for the round that is being signed and the preceding rounds
     */
    private EventImpl[] events;

    /**
     * the consensus timestamp for this signed state
     */
    private Instant consensusTimestamp;

    /**
     * the minimum generation of famous witnesses per round
     */
    private List<MinGenInfo> minGenInfo;

    /**
     * the timestamp of the last transactions handled by this state
     */
    private Instant lastTransactionTimestamp;

    /**
     * The version of the application software that was responsible for creating this state.
     */
    private SoftwareVersion creationSoftwareVersion;

    /**
     * The epoch hash of this state. Updated every time emergency recovery is performed.
     */
    private Hash epochHash;

    /**
     * The next epoch hash, used to update the epoch hash at the next round boundary. This field is not part of the
     * hash and is not serialized.
     */
    private Hash nextEpochHash;

    //TODO add snapshot

    public PlatformData() {}

    /**
     * Copy constructor.
     *
     * @param that
     * 		the object to copy
     */
    private PlatformData(final PlatformData that) {
        super(that);
        this.round = that.round;
        this.numEventsCons = that.numEventsCons;
        this.hashEventsCons = that.hashEventsCons;
        if (that.events != null) {
            this.events = Arrays.copyOf(that.events, that.events.length);
        }
        this.consensusTimestamp = that.consensusTimestamp;
        if (that.minGenInfo != null) {
            this.minGenInfo = new ArrayList<>(that.minGenInfo);
        }
        this.lastTransactionTimestamp = that.lastTransactionTimestamp;
        this.epochHash = that.epochHash;
        this.nextEpochHash = that.nextEpochHash;
    }

    /**
     * Update the epoch hash if the next epoch hash is non-null and different from the current epoch hash.
     */
    public void updateEpochHash() {
        throwIfImmutable();
        if (nextEpochHash != null && !nextEpochHash.equals(epochHash)) {
            // This is the first round after an emergency recovery round
            // Set the epoch hash to the next value
            epochHash = nextEpochHash;

            // set this to null so the value is consistent with a
            // state loaded from disk or received via reconnect
            nextEpochHash = null;
        }
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(round);
        out.writeLong(numEventsCons);
        out.writeSerializable(hashEventsCons, false);

        out.writeInstant(consensusTimestamp);

        out.writeInstant(lastTransactionTimestamp);
        out.writeSerializable(creationSoftwareVersion, true);
        out.writeSerializable(epochHash, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        round = in.readLong();
        numEventsCons = in.readLong();

        hashEventsCons = in.readSerializable(false, Hash::new);

        if (version < ClassVersion.CONSENSUS_SNAPSHOT) {
            int eventNum = in.readInt();
            events = new EventImpl[eventNum];
            for (int i = 0; i < eventNum; i++) {
                events[i] = in.readSerializable(false, EventImpl::new);
                events[i].getBaseEventHashedData().setHash(in.readSerializable(false, Hash::new));
                events[i].markAsSignedStateEvent();
            }
            State.linkParents(events);
        }

        consensusTimestamp = in.readInstant();

        if (version < ClassVersion.CONSENSUS_SNAPSHOT) {
            final int minGenInfoSize = in.readInt();
            minGenInfo = new LinkedList<>();
            for (int i = 0; i < minGenInfoSize; i++) {
                minGenInfo.add(new MinGenInfo(in.readLong(), in.readLong()));
            }
        }

        lastTransactionTimestamp = in.readInstant();
        creationSoftwareVersion = in.readSerializable();

        if (version >= ClassVersion.EPOCH_HASH) {
            epochHash = in.readSerializable(false, Hash::new);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.CONSENSUS_SNAPSHOT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformData copy() {
        return new PlatformData(this);
    }

    /**
     * Get the software version of the application that created this state.
     *
     * @return the creation version
     */
    public SoftwareVersion getCreationSoftwareVersion() {
        return creationSoftwareVersion;
    }

    /**
     * Set the software version of the application that created this state.
     *
     * @param creationVersion
     * 		the creation version
     * @return this object
     */
    public PlatformData setCreationSoftwareVersion(final SoftwareVersion creationVersion) {
        this.creationSoftwareVersion = creationVersion;
        return this;
    }

    /**
     * Get the round when this state was generated.
     *
     * @return a round number
     */
    public long getRound() {
        return round;
    }

    /**
     * Set the round when this state was generated.
     *
     * @param round
     * 		a round number
     * @return this object
     */
    public PlatformData setRound(final long round) {
        this.round = round;
        return this;
    }

    /**
     * Get the number of consensus events that have been applied to this state since the beginning of time.
     *
     * @return the number of handled consensus events
     */
    public long getNumEventsCons() {
        return numEventsCons;
    }

    /**
     * Set the number of consensus events that have been applied to this state since the beginning of time.
     *
     * @param numEventsCons
     * 		the number of handled consensus events
     * @return this object
     */
    public PlatformData setNumEventsCons(final long numEventsCons) {
        this.numEventsCons = numEventsCons;
        return this;
    }

    /**
     * Get the running hash of all events that have been applied to this state since the begining of time.
     *
     * @return a running hash of events
     */
    public Hash getHashEventsCons() {
        return hashEventsCons;
    }

    /**
     * Set the running hash of all events that have been applied to this state since the begining of time.
     *
     * @param hashEventsCons
     * 		a running hash of events
     * @return this object
     */
    public PlatformData setHashEventsCons(final Hash hashEventsCons) {
        this.hashEventsCons = hashEventsCons;
        return this;
    }

    /**
     * Get the events stored in this state.
     *
     * @return an array of events
     */
    public EventImpl[] getEvents() {
        return events;
    }

    /**
     * Set the events stored in this state.
     *
     * @param events
     * 		an array of events
     * @return this object
     */
    public PlatformData setEvents(final EventImpl[] events) {
        this.events = events;
        if (events != null && events.length > 0) {
            setLastTransactionTimestamp(events[events.length - 1].getLastTransTime());
        }
        return this;
    }

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was
     * applied in the round that created the state.
     *
     * @return a consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was
     * applied in the round that created the state.
     *
     * @param consensusTimestamp
     * 		a consensus timestamp
     * @return this object
     */
    public PlatformData setConsensusTimestamp(final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        return this;
    }

    /**
     * Get the minimum event generation for each node within this state.
     *
     * @return minimum generation info list
     */
    public List<MinGenInfo> getMinGenInfo() {
        return minGenInfo;
    }

    /**
     * Get the minimum event generation for each node within this state.
     *
     * @param minGenInfo
     * 		minimum generation info list
     * @return this object
     */
    public PlatformData setMinGenInfo(final List<MinGenInfo> minGenInfo) {
        this.minGenInfo = minGenInfo;
        return this;
    }

    /**
     * Get the timestamp of the last transaction that was applied during this round.
     *
     * @return a timestamp
     */
    public Instant getLastTransactionTimestamp() {
        return lastTransactionTimestamp;
    }

    /**
     * Set the timestamp of the last transaction that was applied during this round.
     *
     * @param lastTransactionTimestamp
     * 		a timestamp
     * @return this object
     */
    public PlatformData setLastTransactionTimestamp(final Instant lastTransactionTimestamp) {
        this.lastTransactionTimestamp = lastTransactionTimestamp;
        return this;
    }

    /**
     * Sets the epoch hash of this state.
     *
     * @param epochHash
     * 		the epoch hash of this state
     * @return this object
     */
    public PlatformData setEpochHash(final Hash epochHash) {
        this.epochHash = epochHash;
        return this;
    }

    /**
     * Gets the epoch hash of this state.
     *
     * @return the epoch hash of this state
     */
    public Hash getEpochHash() {
        return epochHash;
    }

    /**
     * Sets the next epoch hash of this state.
     *
     * @param nextEpochHash
     * 		the next epoch hash of this state
     * @return this object
     */
    public PlatformData setNextEpochHash(final Hash nextEpochHash) {
        this.nextEpochHash = nextEpochHash;
        return this;
    }

    /**
     * Gets the next epoch hash of this state.
     *
     * @return the next epoch hash of this state
     */
    public Hash getNextEpochHash() {
        return nextEpochHash;
    }

    /**
     * Informational method used in reconnect diagnostics.
     * This method constructs a {@link String} containing the critical attributes of this data object.
     * The original use is during reconnect to produce useful information sent to diagnostic event output.
     *
     * @param addressBookHash
     * 		A {@link Hash} of the current Address Book; helpful to validate that the addresses
     * 		used to validate signatures match the expected set of valid addresses.
     * @return a {@link String} containing the core data from this object, in human-readable form.
     * @see PlatformState#getInfoString()
     */
    public String getInfoString(final Hash addressBookHash) {
        return new StringBuilder()
                .append("Round = ")
                .append(getRound())
                .append(", number of consensus events = ")
                .append(getNumEventsCons())
                .append(", consensus timestamp = ")
                .append(getConsensusTimestamp())
                .append(", last timestamp = ")
                .append(getLastTransactionTimestamp())
                .append(", consensus Events running hash = ")
                .append(getHashEventsCons())
                .append(", address book hash = ")
                .append(addressBookHash != null ? addressBookHash : "not provided")
                .toString();
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

        final PlatformData that = (PlatformData) o;

        return new EqualsBuilder()
                .append(round, that.round)
                .append(numEventsCons, that.numEventsCons)
                .append(hashEventsCons, that.hashEventsCons)
                .append(events, that.events)
                .append(consensusTimestamp, that.consensusTimestamp)
                .append(minGenInfo, that.minGenInfo)
                .append(epochHash, that.epochHash)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(round)
                .append(numEventsCons)
                .append(hashEventsCons)
                .append(events)
                .append(consensusTimestamp)
                .append(minGenInfo)
                .append(epochHash)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("round", round)
                .append("numEventsCons", numEventsCons)
                .append("hashEventsCons", hashEventsCons)
                .append("events", events)
                .append("consensusTimestamp", consensusTimestamp)
                .append("minGenInfo", minGenInfo)
                .append("epochHash", epochHash)
                .toString();
    }
}
