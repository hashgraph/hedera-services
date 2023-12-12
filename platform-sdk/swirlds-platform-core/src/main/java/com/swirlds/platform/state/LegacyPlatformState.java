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
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.uptime.UptimeDataImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

// TODO revert changes in this class

/**
 * This subtree contains state data which is managed and used exclusively by the platform.
 *
 * @deprecated use {@link PlatformState} instead, this class is kept for migration
 */
@Deprecated(forRemoval = true)
public class LegacyPlatformState extends PartialNaryMerkleInternal implements MerkleInternal {

    public static final long CLASS_ID = 0x483ae5404ad0d0bfL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int ADDED_PREVIOUS_ADDRESS_BOOK = 2;
    }

    private static final class ChildIndices {
        public static final int PLATFORM_DATA = 0;
        public static final int ADDRESS_BOOK = 1;
        public static final int PREVIOUS_ADDRESS_BOOK = 2;
    }

    public LegacyPlatformState() {}

    /**
     * Copy constructor.
     *
     * @param that the node to copy
     */
    private LegacyPlatformState(final LegacyPlatformState that) {
        super(that);
        if (that.getPlatformData() != null) {
            this.setPlatformData(that.getPlatformData().copy());
        }
        if (that.getAddressBook() != null) {
            this.setAddressBook(that.getAddressBook().copy());
        }
        if (that.getPreviousAddressBook() != null) {
            this.setPreviousAddressBook(that.getPreviousAddressBook().copy());
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
    public int getVersion() {
        return ClassVersion.ADDED_PREVIOUS_ADDRESS_BOOK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LegacyPlatformState copy() {
        return new LegacyPlatformState(this);
    }

    /**
     * Get the address book.
     */
    public AddressBook getAddressBook() {
        return getChild(ChildIndices.ADDRESS_BOOK);
    }

    /**
     * Set the address book.
     *
     * @param addressBook an address book
     */
    public void setAddressBook(final AddressBook addressBook) {
        setChild(ChildIndices.ADDRESS_BOOK, addressBook);
    }

    /**
     * Get the object containing miscellaneous round information.
     *
     * @return round data
     */
    public PlatformData getPlatformData() {
        PlatformData platformData = getChild(ChildIndices.PLATFORM_DATA);
        if (platformData == null) {
            platformData = new PlatformData();
            setPlatformData(platformData);
        }
        return platformData;
    }

    /**
     * Set the object containing miscellaneous platform information.
     *
     * @param round round data
     */
    public void setPlatformData(final PlatformData round) {
        setChild(ChildIndices.PLATFORM_DATA, round);
    }

    /**
     * Get the previous address book.
     */
    public AddressBook getPreviousAddressBook() {
        return getChild(ChildIndices.PREVIOUS_ADDRESS_BOOK);
    }

    /**
     * Set the previous address book.
     *
     * @param addressBook an address book
     */
    public void setPreviousAddressBook(final AddressBook addressBook) {
        setChild(ChildIndices.PREVIOUS_ADDRESS_BOOK, addressBook);
    }

    /**
     * Get the software version of the application that created this state.
     *
     * @return the creation version
     */
    public SoftwareVersion getCreationSoftwareVersion() {
        return getPlatformData().getCreationSoftwareVersion();
    }

    /**
     * Set the software version of the application that created this state.
     *
     * @param creationVersion the creation version
     */
    public void setCreationSoftwareVersion(final SoftwareVersion creationVersion) {
        getPlatformData().setCreationSoftwareVersion(creationVersion);
    }

    /**
     * Get the round when this state was generated.
     *
     * @return a round number
     */
    public long getRound() {
        return getPlatformData().getRound();
    }

    /**
     * Set the round when this state was generated.
     *
     * @param round a round number
     */
    public void setRound(final long round) {
        getPlatformData().setRound(round);
    }

    /**
     * Get the running hash of all events that have been applied to this state since the beginning of time.
     *
     * @return a running hash of events
     */
    public Hash getHashEventsCons() {
        return getPlatformData().getHashEventsCons();
    }

    /**
     * Set the running hash of all events that have been applied to this state since the beginning of time.
     *
     * @param hashEventsCons a running hash of events
     */
    public void setHashEventsCons(final Hash hashEventsCons) {
        getPlatformData().setHashEventsCons(hashEventsCons);
    }

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @return a consensus timestamp
     */
    public Instant getConsensusTimestamp() {
        return getPlatformData().getConsensusTimestamp();
    }

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @param consensusTimestamp a consensus timestamp
     */
    public void setConsensusTimestamp(final Instant consensusTimestamp) {
        getPlatformData().setConsensusTimestamp(consensusTimestamp);
    }

    /**
     * Sets the epoch hash of this state.
     *
     * @param epochHash the epoch hash of this state
     */
    public void setEpochHash(final Hash epochHash) {
        getPlatformData().setEpochHash(epochHash);
    }

    /**
     * Gets the epoch hash of this state.
     *
     * @return the epoch hash of this state
     */
    @Nullable
    public Hash getEpochHash() {
        return getPlatformData().getEpochHash();
    }

    /**
     * Sets the next epoch hash of this state.
     *
     * @param nextEpochHash the next epoch hash of this state
     */
    public void setNextEpochHash(final Hash nextEpochHash) {
        getPlatformData().setNextEpochHash(nextEpochHash);
    }

    /**
     * Gets the next epoch hash of this state.
     *
     * @return the next epoch hash of this state
     */
    public Hash getNextEpochHash() {
        return getPlatformData().getNextEpochHash();
    }

    /**
     * Sets the number of non-ancient rounds.
     *
     * @param roundsNonAncient the number of non-ancient rounds
     */
    public void setRoundsNonAncient(final int roundsNonAncient) {
        getPlatformData().setRoundsNonAncient(roundsNonAncient);
    }

    /**
     * Gets the number of non-ancient rounds.
     *
     * @return the number of non-ancient rounds
     */
    public int getRoundsNonAncient() {
        return getPlatformData().getRoundsNonAncient();
    }

    /**
     * @return the consensus snapshot for this round
     */
    public ConsensusSnapshot getSnapshot() {
        return getPlatformData().getSnapshot();
    }

    /**
     * @param snapshot the consensus snapshot for this round
     */
    public void setSnapshot(final ConsensusSnapshot snapshot) {
        getPlatformData().setSnapshot(snapshot);
    }

    /**
     * Gets the time when the next freeze is scheduled to start. If null then there is no freeze scheduled.
     *
     * @return the time when the freeze starts
     */
    @Nullable
    public Instant getFreezeTime() {
        return getPlatformData().getFreezeTime();
    }

    /**
     * Sets the instant after which the platform will enter FREEZING status. When consensus timestamp of a signed state
     * is after this instant, the platform will stop creating events and accepting transactions. This is used to safely
     * shut down the platform for maintenance.
     *
     * @param freezeTime an Instant in UTC
     */
    public void setFreezeTime(@Nullable final Instant freezeTime) {
        getPlatformData().setFreezeTime(freezeTime);
    }

    /**
     * Gets the last freezeTime based on which the nodes were frozen. If null then there has never been a freeze.
     *
     * @return the last freezeTime based on which the nodes were frozen
     */
    @Nullable
    public Instant getLastFrozenTime() {
        return getPlatformData().getLastFrozenTime();
    }

    /**
     * Sets the last freezeTime based on which the nodes were frozen.
     *
     * @param lastFrozenTime the last freezeTime based on which the nodes were frozen
     */
    public void setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        getPlatformData().setLastFrozenTime(lastFrozenTime);
    }

    /**
     * Gets the uptime data.
     *
     * @return the uptime data
     */
    @NonNull
    public UptimeDataImpl getUptimeData() {
        return getPlatformData().getUptimeData();
    }

    /**
     * Sets the uptime data.
     *
     * @param uptimeData the uptime data
     */
    public void setUptimeData(@NonNull final UptimeDataImpl uptimeData) {
        getPlatformData().setUptimeData(uptimeData);
    }

    /**
     * Copy data from a dual state object into this object. Needed for the migration between states that had a dual
     * state and states that no longer have a dual state.
     *
     * @param dualState the dual state object to copy data from
     */
    public void absorbDualState(@NonNull final DualStateImpl dualState) {
        getPlatformData().setFreezeTime(dualState.getFreezeTime());
        getPlatformData().setLastFrozenTime(dualState.getLastFrozenTime());
        getPlatformData().setUptimeData(dualState.getUptimeData());
    }
}
