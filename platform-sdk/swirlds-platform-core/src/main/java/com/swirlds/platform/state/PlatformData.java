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

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.RoundCalculationUtils;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.uptime.UptimeDataImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A collection of miscellaneous platform data.
 */
public class PlatformData extends PartialMerkleLeaf implements MerkleLeaf {

    // TODO refactor platform code to not directly call into this class
    // TODO does this deserve to have an interface for getters and setters?

    private static final long CLASS_ID = 0x1f89d0c43a8c08bdL;

    /**
     * The round of the genesis state.
     */
    public static final long GENESIS_ROUND = 0;

    private static final class ClassVersion {
        public static final int EPOCH_HASH = 2;
        public static final int ROUNDS_NON_ANCIENT = 3;
        /**
         * - Events are no longer serialized, the field is kept for migration purposes - Mingen is no longer stored
         * directly, its part of the snapshot - restart/reconnect now uses a snapshot - lastTransactionTimestamp is no
         * longer stored directly, its part of the snapshot - numEventsCons is no longer stored directly, its part of
         * the snapshot
         */
        public static final int CONSENSUS_SNAPSHOT = 4;
        /**
         * Move data from the dual state to this location.
         */
        public static final int ABSORB_DUAL_STATE = 5;
    }

    /**
     * The round of this state. This state represents the handling of all transactions that have reached consensus in
     * all previous rounds. All transactions from this round will eventually be applied to this state. The first state
     * (genesis state) has a round of 0 because the first round is defined as round 1, and the genesis state is before
     * any transactions are handled.
     */
    private long round = GENESIS_ROUND;

    /**
     * running hash of the hashes of all consensus events have there been throughout all of history, up through the
     * round received that this SignedState represents.
     */
    private Hash hashEventsCons;

    /**
     * the consensus timestamp for this signed state
     */
    private Instant consensusTimestamp;

    /**
     * the minimum generation of famous witnesses per round
     */
    private List<MinGenInfo> minGenInfo;

    /**
     * The version of the application software that was responsible for creating this state.
     */
    private SoftwareVersion creationSoftwareVersion;

    /**
     * The epoch hash of this state. Updated every time emergency recovery is performed.
     */
    private Hash epochHash;

    /**
     * The next epoch hash, used to update the epoch hash at the next round boundary. This field is not part of the hash
     * and is not serialized.
     */
    private Hash nextEpochHash;

    /**
     * The number of non-ancient rounds.
     */
    private int roundsNonAncient;

    /** A snapshot of the consensus state at the end of the round, used for restart/reconnect */
    private ConsensusSnapshot snapshot;

    /**
     * the time when the freeze starts
     */
    private Instant freezeTime;

    /**
     * the last freezeTime based on which the nodes were frozen
     */
    private Instant lastFrozenTime;

    /**
     * Data on node uptime.
     */
    private UptimeDataImpl uptimeData = new UptimeDataImpl();

    public PlatformData() {}

    /**
     * Copy constructor.
     *
     * @param that the object to copy
     */
    private PlatformData(final PlatformData that) {
        super(that);
        this.round = that.round;
        this.hashEventsCons = that.hashEventsCons;
        this.consensusTimestamp = that.consensusTimestamp;
        if (that.minGenInfo != null) {
            this.minGenInfo = new ArrayList<>(that.minGenInfo);
        }
        this.creationSoftwareVersion = that.creationSoftwareVersion;
        this.epochHash = that.epochHash;
        this.nextEpochHash = that.nextEpochHash;
        this.roundsNonAncient = that.roundsNonAncient;
        this.snapshot = that.snapshot;
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
        out.writeSerializable(hashEventsCons, false);

        out.writeInstant(consensusTimestamp);

        out.writeSerializable(creationSoftwareVersion, true);
        out.writeSerializable(epochHash, false);
        out.writeInt(roundsNonAncient);
        out.writeSerializable(snapshot, false);
        out.writeInstant(freezeTime);
        out.writeInstant(lastFrozenTime);
        out.writeSerializable(uptimeData, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {

        if (version < ClassVersion.CONSENSUS_SNAPSHOT) {
            throw new IOException("Unsupported version " + version);
        }

        round = in.readLong();
        hashEventsCons = in.readSerializable(false, Hash::new);
        consensusTimestamp = in.readInstant();
        creationSoftwareVersion = in.readSerializable();
        epochHash = in.readSerializable(false, Hash::new);
        roundsNonAncient = in.readInt();
        snapshot = in.readSerializable(false, ConsensusSnapshot::new);

        if (version >= ClassVersion.ABSORB_DUAL_STATE) {
            freezeTime = in.readInstant();
            lastFrozenTime = in.readInstant();
            uptimeData = in.readSerializable(false, UptimeDataImpl::new);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ABSORB_DUAL_STATE;
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
     * @param creationVersion the creation version
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
     * @param round a round number
     * @return this object
     */
    public PlatformData setRound(final long round) {
        this.round = round;
        return this;
    }

    /**
     * Get the running hash of all events that have been applied to this state since the beginning of time.
     *
     * @return a running hash of events
     */
    public Hash getHashEventsCons() {
        return hashEventsCons;
    }

    /**
     * Set the running hash of all events that have been applied to this state since the beginning of time.
     *
     * @param hashEventsCons a running hash of events
     * @return this object
     */
    public PlatformData setHashEventsCons(final Hash hashEventsCons) {
        this.hashEventsCons = hashEventsCons;
        return this;
    }

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @return a consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @param consensusTimestamp a consensus timestamp
     * @return this object
     */
    public PlatformData setConsensusTimestamp(final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        return this;
    }

    // TODO remove methods that are not simple getters and setters

    /**
     * Get the minimum event generation for each node within this state.
     *
     * @return minimum generation info list
     */
    public List<MinGenInfo> getMinGenInfo() {
        if (snapshot != null) {
            return snapshot.minGens();
        }
        return minGenInfo;
    }

    /**
     * The minimum generation of famous witnesses for the round specified. This method only looks at non-ancient rounds
     * contained within this state.
     *
     * @param round the round whose minimum generation will be returned
     * @return the minimum generation for the round specified
     * @throws NoSuchElementException if the generation information for this round is not contained withing this state
     */
    public long getMinGen(final long round) {
        for (final MinGenInfo info : getMinGenInfo()) {
            if (info.round() == round) {
                return info.minimumGeneration();
            }
        }
        throw new NoSuchElementException("No minimum generation found for round: " + round);
    }

    /**
     * Return the round generation of the oldest round in this state
     *
     * @return the generation of the oldest round
     */
    public long getMinRoundGeneration() {
        return getMinGenInfo().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No MinGen info found in state"))
                .minimumGeneration();
    }

    /**
     * Sets the epoch hash of this state.
     *
     * @param epochHash the epoch hash of this state
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
    @Nullable
    public Hash getEpochHash() {
        return epochHash;
    }

    /**
     * Sets the next epoch hash of this state.
     *
     * @param nextEpochHash the next epoch hash of this state
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
     * Sets the number of non-ancient rounds.
     *
     * @param roundsNonAncient the number of non-ancient rounds
     * @return this object
     */
    public PlatformData setRoundsNonAncient(final int roundsNonAncient) {
        this.roundsNonAncient = roundsNonAncient;
        return this;
    }

    /**
     * Gets the number of non-ancient rounds.
     *
     * @return the number of non-ancient rounds
     */
    public int getRoundsNonAncient() {
        return roundsNonAncient;
    }

    /**
     * Gets the minimum generation of non-ancient events.
     *
     * @return the minimum generation of non-ancient events
     */
    public long getMinimumGenerationNonAncient() {
        return RoundCalculationUtils.getMinGenNonAncient(roundsNonAncient, round, this::getMinGen);
    }

    /**
     * @return the consensus snapshot for this round
     */
    public ConsensusSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * @param snapshot the consensus snapshot for this round
     * @return this object
     */
    public PlatformData setSnapshot(final ConsensusSnapshot snapshot) {
        this.snapshot = snapshot;
        return this;
    }

    /**
     * Gets the time when the next freeze is scheduled to start. If null then there is no freeze scheduled.
     *
     * @return the time when the freeze starts
     */
    @Nullable
    public Instant getFreezeTime() {
        return freezeTime;
    }

    /**
     * Sets the instant after which the platform will enter FREEZING status. When consensus timestamp of a signed state
     * is after this instant, the platform will stop creating events and accepting transactions. This is used to safely
     * shut down the platform for maintenance.
     *
     * @param freezeTime an Instant in UTC
     */
    public void setFreezeTime(@Nullable final Instant freezeTime) {
        this.freezeTime = freezeTime;
    }

    /**
     * Gets the last freezeTime based on which the nodes were frozen. If null then there has never been a freeze.
     *
     * @return the last freezeTime based on which the nodes were frozen
     */
    @Nullable
    public Instant getLastFrozenTime() {
        return lastFrozenTime;
    }

    /**
     * Sets the last freezeTime based on which the nodes were frozen.
     *
     * @param lastFrozenTime the last freezeTime based on which the nodes were frozen
     * @return this object
     */
    @Nullable
    public PlatformData setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        this.lastFrozenTime = lastFrozenTime;
        return this;
    }

    /**
     * Gets the uptime data.
     *
     * @return the uptime data
     */
    @NonNull
    public UptimeDataImpl getUptimeData() {
        return uptimeData;
    }

    /**
     * Sets the uptime data.
     *
     * @param uptimeData the uptime data
     * @return this object
     */
    public PlatformData setUptimeData(@NonNull final UptimeDataImpl uptimeData) {
        this.uptimeData = Objects.requireNonNull(uptimeData);
        return this;
    }

    // TODO get rid of equals, hashCode, and toString

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
        final PlatformData that = (PlatformData) other;
        return round == that.round
                && Objects.equals(hashEventsCons, that.hashEventsCons)
                && Objects.equals(consensusTimestamp, that.consensusTimestamp)
                && Objects.equals(minGenInfo, that.minGenInfo)
                && Objects.equals(epochHash, that.epochHash)
                && Objects.equals(roundsNonAncient, that.roundsNonAncient)
                && Objects.equals(snapshot, that.snapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return NonCryptographicHashing.hash32(round);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("round", round)
                .append("hashEventsCons", hashEventsCons)
                .append("consensusTimestamp", consensusTimestamp)
                .append("minGenInfo", minGenInfo)
                .append("epochHash", epochHash)
                .append("roundsNonAncient", roundsNonAncient)
                .append("snapshot", snapshot)
                .toString();
    }
}
