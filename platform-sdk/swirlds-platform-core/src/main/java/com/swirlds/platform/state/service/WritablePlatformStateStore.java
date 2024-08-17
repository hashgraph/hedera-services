/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.impl.PbjConverter.toPbjAddressBook;
import static com.swirlds.platform.state.service.impl.PbjConverter.toPbjConsensusSnapshot;
import static com.swirlds.platform.state.service.impl.PbjConverter.toPbjTimestamp;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Extends the read-only platform state store to provide write access to the platform state.
 */
public class WritablePlatformStateStore extends ReadablePlatformStateStore {
    private final WritableSingletonState<PlatformState> state;

    public WritablePlatformStateStore(@NonNull final WritableStates writableStates) {
        super(writableStates);
        this.state = requireNonNull(writableStates).getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY);
    }

    /**
     * Set the software version of the application that created this state.
     *
     * @param creationVersion the creation version
     */
    public void setCreationSoftwareVersion(@NonNull SoftwareVersion creationVersion) {
        requireNonNull(creationVersion);
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                creationVersion.getPbjSemanticVersion(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the address book.
     * @param addressBook an address book
     */
    public void setAddressBook(@Nullable final AddressBook addressBook) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                toPbjAddressBook(addressBook),
                previousState.previousAddressBook()));
    }

    /**
     * Set the previous address book.
     *
     * @param addressBook an address book
     */
    public void setPreviousAddressBook(@Nullable final AddressBook addressBook) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                toPbjAddressBook(addressBook)));
    }

    /**
     * Set the round when this state was generated.
     *
     * @param round a round number
     */
    public void setRound(final long round) {
        final var previousState = stateOrThrow();
        final var previousSnapshot = previousState.consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT);
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                new ConsensusSnapshot(
                        round,
                        previousSnapshot.judgeHashes(),
                        previousSnapshot.minimumJudgeInfoList(),
                        previousSnapshot.nextConsensusNumber(),
                        previousSnapshot.consensusTimestamp()),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the legacy running event hash. Used by the consensus event stream.
     *
     * @param legacyRunningEventHash a running hash of events
     */
    public void setLegacyRunningEventHash(@Nullable final Hash legacyRunningEventHash) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                legacyRunningEventHash == null ? Bytes.EMPTY : legacyRunningEventHash.getBytes(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @param consensusTimestamp a consensus timestamp
     */
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        requireNonNull(consensusTimestamp);
        final var previousState = stateOrThrow();
        final var previousSnapshot = previousState.consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT);
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                new ConsensusSnapshot(
                        previousSnapshot.round(),
                        previousSnapshot.judgeHashes(),
                        previousSnapshot.minimumJudgeInfoList(),
                        previousSnapshot.nextConsensusNumber(),
                        toPbjTimestamp(consensusTimestamp)),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Sets the number of non-ancient rounds.
     *
     * @param roundsNonAncient the number of non-ancient rounds
     */
    public void setRoundsNonAncient(final int roundsNonAncient) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                roundsNonAncient,
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the consensus snapshot for this round.
     * @param snapshot the consensus snapshot for this round
     */
    public void setSnapshot(@NonNull com.swirlds.platform.consensus.ConsensusSnapshot snapshot) {
        requireNonNull(snapshot);
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                toPbjConsensusSnapshot(snapshot),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Sets the instant after which the platform will enter FREEZING status. When consensus timestamp of a signed state
     * is after this instant, the platform will stop creating events and accepting transactions. This is used to safely
     * shut down the platform for maintenance.
     *
     * @param freezeTime an Instant in UTC
     */
    public void setFreezeTime(@Nullable final Instant freezeTime) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                toPbjTimestamp(freezeTime),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Sets the last freezeTime based on which the nodes were frozen.
     *
     * @param lastFrozenTime the last freezeTime based on which the nodes were frozen
     */
    public void setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                toPbjTimestamp(lastFrozenTime),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the first software version where the birth round migration happened.
     *
     * @param firstVersionInBirthRoundMode the first software version where the birth round migration happened
     */
    public void setFirstVersionInBirthRoundMode(@NonNull final SoftwareVersion firstVersionInBirthRoundMode) {
        requireNonNull(firstVersionInBirthRoundMode);
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                previousState.lastRoundBeforeBirthRoundMode(),
                firstVersionInBirthRoundMode.getPbjSemanticVersion(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the last round before the birth round mode was enabled.
     *
     * @param lastRoundBeforeBirthRoundMode the last round before the birth round mode was enabled
     */
    public void setLastRoundBeforeBirthRoundMode(final long lastRoundBeforeBirthRoundMode) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                previousState.lowestJudgeGenerationBeforeBirthRoundMode(),
                lastRoundBeforeBirthRoundMode,
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    /**
     * Set the lowest judge generation before the birth round mode was enabled.
     *
     * @param lowestJudgeGenerationBeforeBirthRoundMode the lowest judge generation before the birth round mode was
     *                                                  enabled
     */
    public void setLowestJudgeGenerationBeforeBirthRoundMode(final long lowestJudgeGenerationBeforeBirthRoundMode) {
        final var previousState = stateOrThrow();
        state.put(new PlatformState(
                previousState.creationSoftwareVersionOrThrow(),
                previousState.roundsNonAncient(),
                previousState.consensusSnapshot(),
                previousState.freezeTime(),
                previousState.lastFrozenTime(),
                previousState.legacyRunningEventHash(),
                lowestJudgeGenerationBeforeBirthRoundMode,
                previousState.lastRoundBeforeBirthRoundMode(),
                previousState.firstVersionInBirthRoundMode(),
                previousState.addressBook(),
                previousState.previousAddressBook()));
    }

    private @NonNull PlatformState stateOrThrow() {
        return requireNonNull(state.get());
    }
}
