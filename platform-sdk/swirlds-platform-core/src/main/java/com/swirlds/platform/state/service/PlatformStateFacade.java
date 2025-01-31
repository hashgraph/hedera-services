/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.util.HapiUtils.asInstant;
import static com.swirlds.platform.state.MerkleStateUtils.createInfoString;
import static com.swirlds.platform.state.PlatformStateAccessor.GENESIS_ROUND;
import static com.swirlds.platform.state.service.PlatformStateService.NAME;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.ConsensusSnapshot;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Function;

public class PlatformStateFacade {

    public static final PlatformStateFacade DEFAULT_PLATFORM_STATE_FACADE =
            new PlatformStateFacade(v -> SoftwareVersion.NO_VERSION);

    private final Function<SemanticVersion, SoftwareVersion> versionFactory;

    public PlatformStateFacade(Function<SemanticVersion, SoftwareVersion> versionFactory) {
        this.versionFactory = versionFactory;
    }

    /**
     * Given a {@link State}, returns the creation version of the platform state if it exists.
     * @param root the root to extract the creation version from
     * @return the creation version of the platform state, or null if the state is a genesis state
     */
    public SemanticVersion creationSemanticVersionOf(@NonNull final State root) {
        requireNonNull(root);
        final var state = platformStateOf(root);
        return state == null ? null : state.creationSoftwareVersionOrThrow();
    }

    public boolean isFreezeRound(@NonNull final State state, @NonNull final Round round) {
        final var platformState = platformStateOf(state);
        return isInFreezePeriod(
                round.getConsensusTimestamp(),
                platformState == null || platformState.freezeTime() == null
                        ? null
                        : asInstant(platformState.freezeTime()),
                platformState == null || platformState.lastFrozenTime() == null
                        ? null
                        : asInstant(platformState.lastFrozenTime()));
    }

    public boolean isGenesisStateOf(@NonNull final State state) {
        return getReadablePlatformStateOf(state).getRound() == GENESIS_ROUND;
    }

    /**
     * Determines if a {@code timestamp} is in a freeze period according to the provided timestamps.
     *
     * @param consensusTime  the consensus time to check
     * @param freezeTime     the freeze time
     * @param lastFrozenTime the last frozen time
     * @return true is the {@code timestamp} is in a freeze period
     */
    public static boolean isInFreezePeriod(
            @NonNull final Instant consensusTime,
            @Nullable final Instant freezeTime,
            @Nullable final Instant lastFrozenTime) {

        // if freezeTime is not set, or consensusTime is before freezeTime, we are not in a freeze period
        // if lastFrozenTime is equal to or after freezeTime, which means the nodes have been frozen once at/after the
        // freezeTime, we are not in a freeze period
        if (freezeTime == null || consensusTime.isBefore(freezeTime)) {
            return false;
        }
        // Now we should check whether the nodes have been frozen at the freezeTime.
        // when consensusTime is equal to or after freezeTime,
        // and lastFrozenTime is before freezeTime, we are in a freeze period.
        return lastFrozenTime == null || lastFrozenTime.isBefore(freezeTime);
    }

    /**
     * Given a {@link State}, returns the creation version of the state if it was deserialized, or null otherwise.
     * @param state the state
     * @return the version of the state if it was deserialized, otherwise null
     */
    @Nullable
    public SoftwareVersion creationSoftwareVersionOf(@NonNull final State state) {
        requireNonNull(state);
        return readablePlatformStateOf(state).getCreationSoftwareVersion();
    }

    /**
     * Given a {@link State}, returns the round number of the platform state if it exists.
     * @param root the root to extract the round number from
     * @return the round number of the platform state, or zero if the state is a genesis state
     */
    public long roundOf(@NonNull final State root) {
        requireNonNull(root);
        final var platformState = platformStateOf(root);
        return platformState == null
                ? PlatformStateAccessor.GENESIS_ROUND
                : platformState
                        .consensusSnapshotOrElse(ConsensusSnapshot.DEFAULT)
                        .round();
    }

    public @Nullable PlatformState platformStateOf(@NonNull final State root) {
        ReadableStates readableStates = root.getReadableStates(NAME);
        return readableStates.isEmpty()
                ? null
                : (PlatformState)
                        readableStates.getSingleton(PLATFORM_STATE_KEY).get();
    }

    @Nullable
    public Hash legacyRunningEventHashOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getLegacyRunningEventHash();
    }

    public long ancientThresholdOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getAncientThreshold();
    }

    @Nullable
    public com.swirlds.platform.consensus.ConsensusSnapshot consensusSnapshotOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getSnapshot();
    }

    public PlatformStateAccessor getReadablePlatformStateOf(@NonNull final State root) {
        return new ReadablePlatformStateStore(root.getReadableStates(NAME), versionFactory);
    }

    @Nullable
    public SoftwareVersion firstVersionInBirthRoundModeOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getFirstVersionInBirthRoundMode();
    }

    public long lastRoundBeforeBirthRoundModeOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getLastRoundBeforeBirthRoundMode();
    }

    public long lowestJudgeGenerationBeforeBirthRoundModeOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getLowestJudgeGenerationBeforeBirthRoundMode();
    }

    @Nullable
    public Instant consensusTimestampOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getConsensusTimestamp();
    }

    public Instant freezeTimeOf(@NonNull final State root) {
        return getReadablePlatformStateOf(root).getFreezeTime();
    }

    public void updateLastFrozenTime(@NonNull final State root) {
        getWritablePlatformStateOf(root).setLastFrozenTime(freezeTimeOf(root));
    }

    /**
     * Get writable platform state. Works only on mutable {@link State}.
     * Call this method only if you need to modify the platform state.
     *
     * @return mutable platform state
     */
    @NonNull
    public PlatformStateModifier getWritablePlatformStateOf(@NonNull final State state) {
        if (state.isImmutable()) {
            throw new IllegalStateException("Cannot get writable platform state when state is immutable");
        }
        return writablePlatformStateStore(state);
    }

    /**
     * Updates the platform state with the values from the provided instance of {@link PlatformStateModifier}
     *
     * @param accessor a source of values
     */
    public void updatePlatformState(@NonNull final State state, @NonNull final PlatformStateModifier accessor) {
        writablePlatformStateStore(state).setAllFrom(accessor);
    }

    /**
     * Get readable platform state.
     * Works on both - mutable and immutable {@link State} and, therefore, this method should be preferred.
     *
     * @return immutable platform state
     */
    @NonNull
    public PlatformStateAccessor readablePlatformStateOf(@NonNull final State state) {
        return readablePlatformStateStore(state);
    }

    private ReadablePlatformStateStore readablePlatformStateStore(@NonNull final State state) {
        return new ReadablePlatformStateStore(state.getReadableStates(NAME), versionFactory);
    }

    private WritablePlatformStateStore writablePlatformStateStore(@NonNull final State state) {
        return new WritablePlatformStateStore(state.getWritableStates(NAME), versionFactory);
    }

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    @NonNull
    public String getInfoString(@NonNull final State state, final int hashDepth) {
        return createInfoString(hashDepth, readablePlatformStateOf(state), state.getHash(), state);
    }
}
