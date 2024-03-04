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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.uptime.UptimeDataImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * State managed and used by the platform.
 */
public class PlatformState extends PartialMerkleLeaf implements MerkleLeaf {

    public static final long CLASS_ID = 0x52cef730a11cb6dfL;

    /**
     * The round of the genesis state.
     */
    public static final long GENESIS_ROUND = 0;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * The address book for this round.
     */
    private AddressBook addressBook;

    /**
     * The previous address book. A temporary workaround until dynamic address books are supported.
     */
    private AddressBook previousAddressBook;

    /**
     * The round of this state. This state represents the handling of all transactions that have reached consensus in
     * all previous rounds. All transactions from this round will eventually be applied to this state. The first state
     * (genesis state) has a round of 0 because the first round is defined as round 1, and the genesis state is before
     * any transactions are handled.
     */
    private long round = GENESIS_ROUND;

    /**
     * The running hash of the hashes of all events have reached consensus up through the round that this SignedState
     * represents.
     */
    private Hash runningEventHash;

    /**
     * the consensus timestamp for this signed state
     */
    private Instant consensusTimestamp;

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

    /**
     * A snapshot of the consensus state at the end of the round, used for restart/reconnect
     */
    private ConsensusSnapshot snapshot;

    /**
     * the time when the freeze starts
     */
    private Instant freezeTime;

    /**
     * the last time when a freeze was performed
     */
    private Instant lastFrozenTime;

    /**
     * Data on node uptime.
     */
    private UptimeDataImpl uptimeData = new UptimeDataImpl();

    public PlatformState() {}

    /**
     * Copy constructor.
     *
     * @param that the object to copy
     */
    private PlatformState(final PlatformState that) {
        super(that);
        this.addressBook = that.addressBook == null ? null : that.addressBook.copy();
        this.previousAddressBook = that.previousAddressBook == null ? null : that.previousAddressBook.copy();
        this.round = that.round;
        this.runningEventHash = that.runningEventHash;
        this.consensusTimestamp = that.consensusTimestamp;
        this.creationSoftwareVersion = that.creationSoftwareVersion;
        this.epochHash = that.epochHash;
        this.nextEpochHash = that.nextEpochHash;
        this.roundsNonAncient = that.roundsNonAncient;
        this.snapshot = that.snapshot;
        this.freezeTime = that.freezeTime;
        this.lastFrozenTime = that.lastFrozenTime;
        this.uptimeData = that.uptimeData.copy();
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
        out.writeSerializable(addressBook, false);
        out.writeSerializable(previousAddressBook, true);
        out.writeLong(round);
        out.writeSerializable(runningEventHash, false);
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
        addressBook = in.readSerializable(false, AddressBook::new);
        previousAddressBook = in.readSerializable(true, AddressBook::new);
        round = in.readLong();
        runningEventHash = in.readSerializable(false, Hash::new);
        consensusTimestamp = in.readInstant();
        creationSoftwareVersion = in.readSerializable();
        epochHash = in.readSerializable(false, Hash::new);
        roundsNonAncient = in.readInt();
        snapshot = in.readSerializable(false, ConsensusSnapshot::new);
        freezeTime = in.readInstant();
        lastFrozenTime = in.readInstant();
        uptimeData = in.readSerializable(false, UptimeDataImpl::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformState copy() {
        return new PlatformState(this);
    }

    /**
     * Get the software version of the application that created this state.
     *
     * @return the creation version
     */
    @Nullable
    public SoftwareVersion getCreationSoftwareVersion() {
        return creationSoftwareVersion;
    }

    /**
     * Set the software version of the application that created this state.
     *
     * @param creationVersion the creation version
     */
    public void setCreationSoftwareVersion(@NonNull final SoftwareVersion creationVersion) {
        this.creationSoftwareVersion = Objects.requireNonNull(creationVersion);
    }

    /**
     * Get the address book.
     */
    @Nullable
    public AddressBook getAddressBook() {
        return addressBook;
    }

    /**
     * Set the address book.
     *
     * @param addressBook an address book
     */
    public void setAddressBook(@Nullable final AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    /**
     * Get the previous address book.
     */
    @Nullable
    public AddressBook getPreviousAddressBook() {
        return previousAddressBook;
    }

    /**
     * Set the previous address book.
     *
     * @param addressBook an address book
     */
    public void setPreviousAddressBook(@Nullable final AddressBook addressBook) {
        this.previousAddressBook = addressBook;
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
     */
    public void setRound(final long round) {
        this.round = round;
    }

    /**
     * Get the running hash of all events that have been applied to this state since the beginning of time.
     *
     * @return a running hash of events
     */
    @Nullable
    public Hash getRunningEventHash() {
        return runningEventHash;
    }

    /**
     * Set the running hash of all events that have been applied to this state since the beginning of time.
     *
     * @param runningEventHash a running hash of events
     */
    public void setRunningEventHash(@Nullable final Hash runningEventHash) {
        this.runningEventHash = runningEventHash;
    }

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @return a consensus timestamp
     */
    @Nullable
    public Instant getConsensusTimestamp() {
        return consensusTimestamp;
    }

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @param consensusTimestamp a consensus timestamp
     */
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        this.consensusTimestamp = Objects.requireNonNull(consensusTimestamp);
    }

    /**
     * For the oldest non-ancient round, get the lowest ancient indicator out of all of those round's judges. This is
     * the ancient threshold at the moment after this state's round reached consensus. All events with an ancient
     * indicator that is greater than or equal to this value are non-ancient. All events with an ancient indicator less
     * than this value are ancient.
     *
     * <p>
     * When running in {@link AncientMode#GENERATION_THRESHOLD}, this value is the minimum generation non-ancient. When
     * running in {@link AncientMode#BIRTH_ROUND_THRESHOLD}, this value is the minimum birth round non-ancient.
     * </p>
     *
     * @return the ancient threshold after this round has reached consensus
     */
    public long getAncientThreshold() {
        if (snapshot == null) {
            throw new IllegalStateException(
                    "No minimum judge info found in state for round " + round + ", snapshot is null");
        }

        final List<MinimumJudgeInfo> minimumJudgeInfo = snapshot.getMinimumJudgeInfoList();
        if (minimumJudgeInfo.isEmpty()) {
            throw new IllegalStateException(
                    "No minimum judge info found in state for round " + round + ", list is empty");
        }

        return minimumJudgeInfo.getFirst().minimumJudgeAncientThreshold();
    }

    /**
     * Sets the epoch hash of this state.
     *
     * @param epochHash the epoch hash of this state
     */
    public void setEpochHash(@Nullable final Hash epochHash) {
        this.epochHash = epochHash;
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
     */
    public void setNextEpochHash(@Nullable final Hash nextEpochHash) {
        this.nextEpochHash = nextEpochHash;
    }

    /**
     * Gets the next epoch hash of this state.
     *
     * @return the next epoch hash of this state
     */
    @Nullable
    public Hash getNextEpochHash() {
        return nextEpochHash;
    }

    /**
     * Sets the number of non-ancient rounds.
     *
     * @param roundsNonAncient the number of non-ancient rounds
     */
    public void setRoundsNonAncient(final int roundsNonAncient) {
        this.roundsNonAncient = roundsNonAncient;
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
     * @return the consensus snapshot for this round
     */
    @Nullable
    public ConsensusSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * @param snapshot the consensus snapshot for this round
     */
    public void setSnapshot(@NonNull final ConsensusSnapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot);
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
     */
    public void setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        this.lastFrozenTime = lastFrozenTime;
    }

    /**
     * Gets the uptime data.
     *
     * @return the uptime data
     */
    @Nullable
    public UptimeDataImpl getUptimeData() {
        return uptimeData;
    }

    /**
     * Sets the uptime data.
     *
     * @param uptimeData the uptime data
     */
    public void setUptimeData(@NonNull final UptimeDataImpl uptimeData) {
        this.uptimeData = Objects.requireNonNull(uptimeData);
    }
}
