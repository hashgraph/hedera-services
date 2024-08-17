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

import static com.swirlds.platform.state.PlatformStateAccessor.GENESIS_ROUND;
import static com.swirlds.platform.state.service.impl.PbjConverter.fromPbjAddressBook;
import static com.swirlds.platform.state.service.impl.PbjConverter.fromPbjConsensusSnapshot;
import static com.swirlds.platform.state.service.impl.PbjConverter.fromPbjTimestamp;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.AncientMode;
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
public class ReadablePlatformStateStore {
    public static final Function<SemanticVersion, SoftwareVersion> UNKNOWN_VERSION_FACTORY = version -> {
        throw new IllegalStateException("State store was not initialized with a version factory");
    };

    private final ReadableSingletonState<PlatformState> state;
    private final Function<SemanticVersion, SoftwareVersion> versionFactory;

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

    /**
     * Get the software version of the application that created this state.
     * @return the creation version
     */
    @NonNull
    public SoftwareVersion getCreationSoftwareVersion() {
        return versionFactory.apply(stateOrThrow().creationSoftwareVersionOrThrow());
    }

    /**
     * Get the address book, if available.
     * @return the address book, or null if not available
     */
    @Nullable
    public AddressBook getAddressBook() {
        return fromPbjAddressBook(stateOrThrow().addressBook());
    }

    /**
     * Get the previous address book, if available.
     * @return the previous address book, or null if not available
     */
    @Nullable
    public AddressBook getPreviousAddressBook() {
        return fromPbjAddressBook(stateOrThrow().previousAddressBook());
    }

    /**
     * Get the round when this state was generated.
     * @return a round number
     */
    public long getRound() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        if (consensusSnapshot == null) {
            return GENESIS_ROUND;
        } else {
            return consensusSnapshot.round();
        }
    }

    /**
     * Get the legacy running event hash, if available. Used by the consensus event stream.
     * @return a running hash of events or null if not available
     */
    @Nullable
    public Hash getLegacyRunningEventHash() {
        final var hash = stateOrThrow().legacyRunningEventHash();
        return hash.length() == 0 ? null : new Hash(hash.toByteArray());
    }

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state. Returns null if no rounds have been created.
     *
     * @return a consensus timestamp
     */
    @Nullable
    public Instant getConsensusTimestamp() {
        final var consensusSnapshot = stateOrThrow().consensusSnapshot();
        if (consensusSnapshot == null) {
            return null;
        }
        return fromPbjTimestamp(consensusSnapshot.consensusTimestamp());
    }

    /**
     * For the oldest non-ancient round, get the lowest ancient indicator out of all of those round's judges. This is
     * the ancient threshold at the moment after this state's round reached consensus. All events with an ancient
     * indicator that is greater than or equal to this value are non-ancient. All events with an ancient indicator less
     * than this value are ancient.
     * <p>
     * When running in {@link AncientMode#GENERATION_THRESHOLD}, this value is the minimum generation non-ancient. When
     * running in {@link AncientMode#BIRTH_ROUND_THRESHOLD}, this value is the minimum birth round non-ancient.
     * </p>
     * @return the ancient threshold after this round has reached consensus
     * @throws IllegalStateException if no minimum judge info is found in the state
     */
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
     * Gets the number of non-ancient rounds.
     *
     * @return the number of non-ancient rounds
     */
    public int getRoundsNonAncient() {
        return stateOrThrow().roundsNonAncient();
    }

    /**
     * @return the consensus snapshot for this round
     */
    @Nullable
    public ConsensusSnapshot getSnapshot() {
        return fromPbjConsensusSnapshot(stateOrThrow().consensusSnapshot());
    }

    /**
     * Gets the time when the next freeze is scheduled to start. If null then there is no freeze scheduled.
     * @return the time when the freeze starts
     */
    @Nullable
    public Instant getFreezeTime() {
        return fromPbjTimestamp(stateOrThrow().freezeTime());
    }

    /**
     * Gets the last freezeTime based on which the nodes were frozen. If null then there has never been a freeze.
     *
     * @return the last freezeTime based on which the nodes were frozen
     */
    @Nullable
    public Instant getLastFrozenTime() {
        return fromPbjTimestamp(stateOrThrow().lastFrozenTime());
    }

    /**
     * Get the first software version where the birth round migration happened, or null if birth round migration has not
     * yet happened.
     *
     * @return the first software version where the birth round migration happened
     */
    @Nullable
    public SoftwareVersion getFirstVersionInBirthRoundMode() {
        final var version = stateOrThrow().firstVersionInBirthRoundMode();
        return version == null ? null : versionFactory.apply(version);
    }

    /**
     * Get the last round before the birth round mode was enabled, or -1 if birth round mode has not yet been enabled.
     * @return the last round before the birth round mode was enabled
     */
    public long getLastRoundBeforeBirthRoundMode() {
        return stateOrThrow().lastRoundBeforeBirthRoundMode();
    }

    /**
     * Get the lowest judge generation before the birth round mode was enabled, or -1 if birth round mode has not yet
     * been enabled.
     * @return the lowest judge generation before the birth round mode was enabled
     */
    public long getLowestJudgeGenerationBeforeBirthRoundMode() {
        return stateOrThrow().lowestJudgeGenerationBeforeBirthRoundMode();
    }

    private @NonNull PlatformState stateOrThrow() {
        return requireNonNull(state.get());
    }
}
