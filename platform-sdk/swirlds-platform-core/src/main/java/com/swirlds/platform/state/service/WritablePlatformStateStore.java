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
import static com.swirlds.platform.state.service.impl.PbjConverter.toPbjPlatformState;
import static com.swirlds.platform.state.service.impl.PbjConverter.toPbjTimestamp;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Function;

/**
 * Extends the read-only platform state store to provide write access to the platform state.
 */
public class WritablePlatformStateStore extends ReadablePlatformStateStore implements PlatformStateAccessor {
    private final WritableStates writableStates;
    private final WritableSingletonState<PlatformState> state;

    /**
     * Constructor that supports getting full {@link SoftwareVersion} information from the platform state. Must
     * be used from within {@link com.swirlds.platform.state.MerkleStateRoot}.
     * @param writableStates the writable states
     * @param versionFactory a factory to create the current {@link SoftwareVersion} from a {@link SemanticVersion}
     */
    public WritablePlatformStateStore(
            @NonNull final WritableStates writableStates,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(writableStates, versionFactory);
        this.writableStates = writableStates;
        this.state = writableStates.getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY);
    }

    /**
     * Constructor that does not support getting full {@link SoftwareVersion} information from the platform state,
     * but can be used to change and access any part of state that does not require the full {@link SoftwareVersion}.
     * @param writableStates the writable states
     */
    public WritablePlatformStateStore(@NonNull final WritableStates writableStates) {
        super(writableStates);
        this.writableStates = writableStates;
        this.state = writableStates.getSingleton(V0540PlatformStateSchema.PLATFORM_STATE_KEY);
    }

    public void setAllFrom(@NonNull final PlatformStateAccessor accessor) {
        this.update(toPbjPlatformState(accessor));
    }

    @Override
    public void setCreationSoftwareVersion(@NonNull final SoftwareVersion creationVersion) {
        requireNonNull(creationVersion);
        final var previousState = stateOrThrow();
        update(new PlatformState(
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

    @Override
    public void setAddressBook(@Nullable final AddressBook addressBook) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setPreviousAddressBook(@Nullable final AddressBook addressBook) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setRound(final long round) {
        final var previousState = stateOrThrow();
        final var previousSnapshot = previousState.consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT);
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setLegacyRunningEventHash(@Nullable final Hash legacyRunningEventHash) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        requireNonNull(consensusTimestamp);
        final var previousState = stateOrThrow();
        final var previousSnapshot = previousState.consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT);
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setRoundsNonAncient(final int roundsNonAncient) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setSnapshot(@NonNull final com.swirlds.platform.consensus.ConsensusSnapshot snapshot) {
        requireNonNull(snapshot);
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setFreezeTime(@Nullable final Instant freezeTime) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setFirstVersionInBirthRoundMode(@NonNull final SoftwareVersion firstVersionInBirthRoundMode) {
        requireNonNull(firstVersionInBirthRoundMode);
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setLastRoundBeforeBirthRoundMode(final long lastRoundBeforeBirthRoundMode) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    @Override
    public void setLowestJudgeGenerationBeforeBirthRoundMode(final long lowestJudgeGenerationBeforeBirthRoundMode) {
        final var previousState = stateOrThrow();
        update(new PlatformState(
                previousState.creationSoftwareVersion(),
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

    private void update(@NonNull final PlatformState state) {
        this.state.put(state);
        if (writableStates instanceof CommittableWritableStates committableWritableStates) {
            committableWritableStates.commit();
        }
    }
}