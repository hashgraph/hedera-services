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

import static com.swirlds.platform.state.service.PbjConverter.fromPbjAddressBook;
import static com.swirlds.platform.state.service.PbjConverter.fromPbjConsensusSnapshot;
import static com.swirlds.platform.state.service.PbjConverter.fromPbjTimestamp;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Function;

/**
 * Gives read-only access to the platform state, encapsulating conversion from PBJ types to the current types
 * in use by the platform.
 */
public class ReadablePlatformStateStore implements PlatformStateAccessor {
    public static final Function<SemanticVersion, SoftwareVersion> UNKNOWN_VERSION_FACTORY = version -> {
        throw new IllegalStateException("State store was not initialized with a version factory");
    };

    private final ReadableSingletonState<PlatformState> state;

    private Function<SemanticVersion, SoftwareVersion> versionFactory;

    /**
     * Constructor that supports getting full {@link SoftwareVersion} information from the platform state. Must
     * be used from within {@link com.swirlds.platform.state.MerkleStateRoot}.
     * @param readableStates the readable states
     * @param versionFactory a factory to create the current {@link SoftwareVersion} from a {@link SemanticVersion}
     */
    public ReadablePlatformStateStore(
            @NonNull final ReadableStates readableStates,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        this.state = requireNonNull(readableStates).getSingleton(PLATFORM_STATE_KEY);
        this.versionFactory = requireNonNull(versionFactory);
    }

    /**
     * Constructor that does not support getting full {@link SoftwareVersion} information from the platform state.
     * @param readableStates the readable states
     */
    public ReadablePlatformStateStore(@NonNull final ReadableStates readableStates) {
        this(readableStates, UNKNOWN_VERSION_FACTORY);
    }

    public void setVersionFactory(@NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        this.versionFactory = requireNonNull(versionFactory);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public SoftwareVersion getCreationSoftwareVersion() {
        return versionFactory.apply(stateOrThrow().creationSoftwareVersionOrThrow());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public AddressBook getAddressBook() {
        return fromPbjAddressBook(stateOrThrow().addressBook());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public AddressBook getPreviousAddressBook() {
        return fromPbjAddressBook(stateOrThrow().previousAddressBook());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRound() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        if (consensusSnapshot == null) {
            return GENESIS_ROUND;
        } else {
            return consensusSnapshot.round();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Hash getLegacyRunningEventHash() {
        final var hash = stateOrThrow().legacyRunningEventHash();
        return hash.length() == 0 ? null : new Hash(hash.toByteArray());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public Instant getConsensusTimestamp() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        if (consensusSnapshot == null) {
            return null;
        }
        return fromPbjTimestamp(consensusSnapshot.consensusTimestamp());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getAncientThreshold() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        requireNonNull(consensusSnapshot, "No minimum judge info found in state for round, snapshot is null");
        final var minimumJudgeInfos = consensusSnapshot.minimumJudgeInfoList();
        if (minimumJudgeInfos.isEmpty()) {
            throw new IllegalStateException(
                    "No minimum judge info found in state for round " + consensusSnapshot.round() + ", list is empty");
        }
        return minimumJudgeInfos.getFirst().minimumJudgeAncientThreshold();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRoundsNonAncient() {
        return stateOrThrow().roundsNonAncient();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public ConsensusSnapshot getSnapshot() {
        return fromPbjConsensusSnapshot(stateOrThrow().consensusSnapshot());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Instant getFreezeTime() {
        return fromPbjTimestamp(stateOrThrow().freezeTime());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Instant getLastFrozenTime() {
        return fromPbjTimestamp(stateOrThrow().lastFrozenTime());
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public SoftwareVersion getFirstVersionInBirthRoundMode() {
        final var version = stateOrThrow().firstVersionInBirthRoundMode();
        return version == null ? null : versionFactory.apply(version);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLastRoundBeforeBirthRoundMode() {
        return stateOrThrow().lastRoundBeforeBirthRoundMode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLowestJudgeGenerationBeforeBirthRoundMode() {
        return stateOrThrow().lowestJudgeGenerationBeforeBirthRoundMode();
    }

    private @NonNull PlatformState stateOrThrow() {
        return requireNonNull(state.get());
    }
}
