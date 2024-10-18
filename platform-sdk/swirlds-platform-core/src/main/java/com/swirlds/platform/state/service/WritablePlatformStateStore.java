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

import static com.swirlds.platform.state.service.PbjConverter.toPbjAddressBook;
import static com.swirlds.platform.state.service.PbjConverter.toPbjConsensusSnapshot;
import static com.swirlds.platform.state.service.PbjConverter.toPbjPlatformState;
import static com.swirlds.platform.state.service.PbjConverter.toPbjTimestamp;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Extends the read-only platform state store to provide write access to the platform state.
 */
public class WritablePlatformStateStore extends ReadablePlatformStateStore implements PlatformStateModifier {
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

    /**
     * Overwrite the current platform state with the provided state.
     */
    public void setAllFrom(@NonNull final PlatformStateModifier modifier) {
        this.update(toPbjPlatformState(modifier));
    }

    private void setAllFrom(@NonNull final PlatformStateValueAccumulator accumulator) {
        this.update(toPbjPlatformState(stateOrThrow(), accumulator));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCreationSoftwareVersion(@NonNull final SoftwareVersion creationVersion) {
        requireNonNull(creationVersion);
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().creationSoftwareVersion(creationVersion.getPbjSemanticVersion()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAddressBook(@Nullable final AddressBook addressBook) {
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().addressBook(toPbjAddressBook(addressBook)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPreviousAddressBook(@Nullable final AddressBook addressBook) {
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().previousAddressBook(toPbjAddressBook(addressBook)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRound(final long round) {
        final var previousState = stateOrThrow();
        update(previousState
                .copyBuilder()
                .consensusSnapshot(previousState
                        .consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT)
                        .copyBuilder()
                        .round(round)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLegacyRunningEventHash(@Nullable final Hash legacyRunningEventHash) {
        final var previousState = stateOrThrow();
        update(previousState
                .copyBuilder()
                .legacyRunningEventHash(
                        legacyRunningEventHash == null ? Bytes.EMPTY : legacyRunningEventHash.getBytes()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        requireNonNull(consensusTimestamp);
        final var previousState = stateOrThrow();
        update(previousState
                .copyBuilder()
                .consensusSnapshot(previousState
                        .consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT)
                        .copyBuilder()
                        .consensusTimestamp(toPbjTimestamp(consensusTimestamp))));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRoundsNonAncient(final int roundsNonAncient) {
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().roundsNonAncient(roundsNonAncient));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSnapshot(@NonNull final com.swirlds.platform.consensus.ConsensusSnapshot snapshot) {
        requireNonNull(snapshot);
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().consensusSnapshot(toPbjConsensusSnapshot(snapshot)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFreezeTime(@Nullable final Instant freezeTime) {
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().freezeTime(toPbjTimestamp(freezeTime)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        final var previousState = stateOrThrow();
        update(previousState.copyBuilder().lastFrozenTime(toPbjTimestamp(lastFrozenTime)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setFirstVersionInBirthRoundMode(@NonNull final SoftwareVersion firstVersionInBirthRoundMode) {
        requireNonNull(firstVersionInBirthRoundMode);
        final var previousState = stateOrThrow();
        update(previousState
                .copyBuilder()
                .firstVersionInBirthRoundMode(firstVersionInBirthRoundMode.getPbjSemanticVersion())
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLastRoundBeforeBirthRoundMode(final long lastRoundBeforeBirthRoundMode) {
        final var previousState = stateOrThrow();
        update(previousState
                .copyBuilder()
                .lastRoundBeforeBirthRoundMode(lastRoundBeforeBirthRoundMode)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLowestJudgeGenerationBeforeBirthRoundMode(final long lowestJudgeGenerationBeforeBirthRoundMode) {
        final var previousState = stateOrThrow();
        update(previousState
                .copyBuilder()
                .lowestJudgeGenerationBeforeBirthRoundMode(lowestJudgeGenerationBeforeBirthRoundMode));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bulkUpdate(@NonNull final Consumer<PlatformStateModifier> updater) {
        final var accumulator = new PlatformStateValueAccumulator();
        updater.accept(accumulator);
        setAllFrom(accumulator);
    }

    private @NonNull PlatformState stateOrThrow() {
        return requireNonNull(state.get());
    }

    private void update(@NonNull final PlatformState.Builder stateBuilder) {
        update(stateBuilder.build());
    }

    private void update(@NonNull final PlatformState stateBuilder) {
        this.state.put(stateBuilder);
        if (writableStates instanceof CommittableWritableStates committableWritableStates) {
            committableWritableStates.commit();
        }
    }
}
