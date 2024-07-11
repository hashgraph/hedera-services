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

package com.swirlds.platform.state;

import static com.swirlds.platform.state.PlatformStateConverter.convert;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.platform.state.MinimumJudgeInfo;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.hapi.platform.state.PlatformState.Builder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.state.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.merkle.StateMetadata;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class is a drop-in replacement for {@link PlatformState} that uses singleton object under the hood.`
 * It assumes that the instance of {@link MerkleRoot} passed in the constructor is a {@link MerkleStateRoot} instance.
 * It registers the singleton object for the platform state in the {@link MerkleStateRoot} if it is not already present.
 */
public class PlatformStateAccessorSingleton implements PlatformStateAccessor {

    private final MerkleStateRoot root;
    private final StateMetadata<String, PlatformState> md;
    private final Function<SemanticVersion, SoftwareVersion> softwareVersionSupplier;
    private final Supplier<SingletonNode<PlatformState>> platformSingletonSupplier;

    /**
     * Create a new instance of the platform state accessor.
     *
     * @param root Merkle state root
     */
    public PlatformStateAccessorSingleton(@NonNull MerkleStateRoot root) {
        this.root = root;
        final Function<SemanticVersion, SoftwareVersion> softwareVersionSupplier = root.getSoftwareVersionSupplier();
        this.softwareVersionSupplier = requireNonNull(softwareVersionSupplier);
        V0540PlatformStateSchema schema = new V0540PlatformStateSchema();
        md = new StateMetadata<>(
                PlatformStateAccessor.PLATFORM_NAME,
                schema,
                schema.statesToCreate().iterator().next());
        platformSingletonSupplier = () -> new SingletonNode<>(
                md.serviceName(),
                md.stateDefinition().stateKey(),
                md.singletonClassId(),
                md.stateDefinition().valueCodec(),
                PlatformState.newBuilder().build());

        root.putServiceStateIfAbsent(md, platformSingletonSupplier);
    }

    /**
     * Get the software version of the application that created this state.
     *
     * @return the creation version
     */
    @NonNull
    public SoftwareVersion getCreationSoftwareVersion() {
        return softwareVersionSupplier.apply(getPlatformState().creationSoftwareVersion());
    }

    /**
     * Set the software version of the application that created this state.
     *
     * @param creationVersion the creation version
     */
    public void setCreationSoftwareVersion(@NonNull final SoftwareVersion creationVersion) {
        updatePlatformState((p, b) -> b.creationSoftwareVersion(creationVersion.getPbjSemanticVersion()));
    }

    /**
     * Get the address book.
     */
    @Nullable
    public AddressBook getAddressBook() {
        return getPlatformState().addressBook() == null
                ? null
                : convert(getPlatformState().addressBook());
    }

    /**
     * Set the address book.
     *
     * @param addressBook an address book
     */
    public void setAddressBook(@Nullable final AddressBook addressBook) {
        updatePlatformState((p, b) -> b.addressBook(convert(addressBook)));
    }

    /**
     * Get the previous address book.
     */
    @Nullable
    public AddressBook getPreviousAddressBook() {
        return convert(getPlatformState().previousAddressBook());
    }
    /**
     * Set the previous address book.
     *
     * @param addressBook an address book
     */
    public void setPreviousAddressBook(@Nullable final AddressBook addressBook) {
        updatePlatformState((p, b) -> b.previousAddressBook(convert(addressBook)));
    }

    /**
     * Get the round when this state was generated.
     *
     * @return a round number
     */
    public long getRound() {
        com.hedera.hapi.platform.state.ConsensusSnapshot consensusSnapshot =
                getPlatformState().consensusSnapshot();
        if (consensusSnapshot == null) {
            return GENESIS_ROUND;
        } else {
            return consensusSnapshot.round();
        }
    }

    /**
     * Set the round when this state was generated.
     *
     * @param round a round number
     */
    public void setRound(final long round) {
        updatePlatformState((p, b) ->
                b.consensusSnapshot(getConsensusSnapshot(p).round(round).build()));
    }

    private static com.hedera.hapi.platform.state.ConsensusSnapshot.Builder getConsensusSnapshot(PlatformState p) {
        com.hedera.hapi.platform.state.ConsensusSnapshot.Builder snapshotBuilder;
        if (p.consensusSnapshot() == null) {
            snapshotBuilder = new com.hedera.hapi.platform.state.ConsensusSnapshot.Builder();
        } else {
            snapshotBuilder = requireNonNull(p.consensusSnapshot()).copyBuilder();
        }
        return snapshotBuilder;
    }

    /**
     * Get the legacy running event hash. Used by the consensus event stream.
     *
     * @return a running hash of events
     */
    @Nullable
    public Hash getLegacyRunningEventHash() {
        return getPlatformState().legacyRunningEventHash().length() == 0
                ? null
                : new Hash(getPlatformState().legacyRunningEventHash().toByteArray());
    }

    /**
     * Set the legacy running event hash. Used by the consensus event stream.
     *
     * @param legacyRunningEventHash a running hash of events
     */
    public void setLegacyRunningEventHash(@Nullable final Hash legacyRunningEventHash) {
        if (legacyRunningEventHash != null) {
            updatePlatformState((p, b) -> b.legacyRunningEventHash(legacyRunningEventHash.getBytes()));
        } else {
            updatePlatformState((p, b) -> b.legacyRunningEventHash(Bytes.EMPTY));
        }
    }

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @return a consensus timestamp
     */
    @Nullable
    public Instant getConsensusTimestamp() {
        com.hedera.hapi.platform.state.ConsensusSnapshot consensusSnapshot =
                getPlatformState().consensusSnapshot();
        if (consensusSnapshot == null) {
            return null;
        }
        Timestamp timestamp = consensusSnapshot.consensusTimestamp();
        if (timestamp == null) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.seconds(), timestamp.nanos());
    }

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @param consensusTimestamp a consensus timestamp
     */
    public void setConsensusTimestamp(@NonNull final Instant consensusTimestamp) {
        updatePlatformState((p, b) -> b.consensusSnapshot(getConsensusSnapshot(p)
                .consensusTimestamp(Timestamp.newBuilder()
                        .seconds(consensusTimestamp.getEpochSecond())
                        .nanos(consensusTimestamp.getNano())
                        .build())
                .build()));
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
        com.hedera.hapi.platform.state.ConsensusSnapshot consensusSnapshot =
                getPlatformState().consensusSnapshot();
        if (consensusSnapshot == null) {
            throw new IllegalStateException("No minimum judge info found in state for round, snapshot is null");
        }

        List<MinimumJudgeInfo> minimumJudgeInfos = consensusSnapshot.minimumJudgeInfoList();
        if (minimumJudgeInfos.isEmpty()) {
            throw new IllegalStateException(
                    "No minimum judge info found in state for round " + consensusSnapshot.round() + ", list is empty");
        }

        return minimumJudgeInfos.getFirst().minimumJudgeAncientThreshold();
    }

    /**
     * Sets the number of non-ancient rounds.
     *
     * @param roundsNonAncient the number of non-ancient rounds
     */
    public void setRoundsNonAncient(final int roundsNonAncient) {
        updatePlatformState((p, b) -> b.roundsNonAncient(roundsNonAncient));
    }

    /**
     * Gets the number of non-ancient rounds.
     *
     * @return the number of non-ancient rounds
     */
    public int getRoundsNonAncient() {
        return getPlatformState().roundsNonAncient();
    }

    /**
     * @return the consensus snapshot for this round
     */
    @Nullable
    public ConsensusSnapshot getSnapshot() {
        return convert(getPlatformState().consensusSnapshot());
    }

    /**
     * @param snapshot the consensus snapshot for this round
     */
    public void setSnapshot(@NonNull final ConsensusSnapshot snapshot) {
        updatePlatformState((p, b) -> b.consensusSnapshot(convert(snapshot)));
    }

    /**
     * Gets the time when the next freeze is scheduled to start. If null then there is no freeze scheduled.
     *
     * @return the time when the freeze starts
     */
    @Nullable
    public Instant getFreezeTime() {
        return convert(getPlatformState().freezeTime());
    }

    /**
     * Sets the instant after which the platform will enter FREEZING status. When consensus timestamp of a signed state
     * is after this instant, the platform will stop creating events and accepting transactions. This is used to safely
     * shut down the platform for maintenance.
     *
     * @param freezeTime an Instant in UTC
     */
    public void setFreezeTime(@Nullable final Instant freezeTime) {
        updatePlatformState((p, b) -> b.freezeTime(convert(freezeTime)));
    }

    /**
     * Gets the last freezeTime based on which the nodes were frozen. If null then there has never been a freeze.
     *
     * @return the last freezeTime based on which the nodes were frozen
     */
    @Nullable
    public Instant getLastFrozenTime() {
        return convert(getPlatformState().lastFrozenTime());
    }

    /**
     * Sets the last freezeTime based on which the nodes were frozen.
     *
     * @param lastFrozenTime the last freezeTime based on which the nodes were frozen
     */
    public void setLastFrozenTime(@Nullable final Instant lastFrozenTime) {
        updatePlatformState((p, b) -> b.lastFrozenTime(convert(lastFrozenTime)));
    }

    /**
     * Get the first software version where the birth round migration happened, or null if birth round migration has not
     * yet happened.
     *
     * @return the first software version where the birth round migration happened
     */
    @Nullable
    public SoftwareVersion getFirstVersionInBirthRoundMode() {
        SemanticVersion version = getPlatformState().firstVersionInBirthRoundMode();
        return version == null ? null : softwareVersionSupplier.apply(version);
    }

    /**
     * Set the first software version where the birth round migration happened.
     *
     * @param firstVersionInBirthRoundMode the first software version where the birth round migration happened
     */
    public void setFirstVersionInBirthRoundMode(final SoftwareVersion firstVersionInBirthRoundMode) {
        updatePlatformState(
                (p, b) -> b.firstVersionInBirthRoundMode(firstVersionInBirthRoundMode.getPbjSemanticVersion()));
    }

    /**
     * Get the last round before the birth round mode was enabled, o r -1 if birth round mode has not yet been enabled.
     *
     * @return the last round before the birth round mode was enabled
     */
    public long getLastRoundBeforeBirthRoundMode() {
        return getPlatformState().lastRoundBeforeBirthRoundMode();
    }

    /**
     * Set the last round before the birth round mode was enabled.
     *
     * @param lastRoundBeforeBirthRoundMode the last round before the birth round mode was enabled
     */
    public void setLastRoundBeforeBirthRoundMode(final long lastRoundBeforeBirthRoundMode) {
        updatePlatformState((p, b) -> b.lastRoundBeforeBirthRoundMode(lastRoundBeforeBirthRoundMode));
    }

    /**
     * Get the lowest judge generation before the birth round mode was enabled, or -1 if birth round mode has not yet
     * been enabled.
     *
     * @return the lowest judge generation before the birth round mode was enabled
     */
    public long getLowestJudgeGenerationBeforeBirthRoundMode() {
        return getPlatformState().lowestJudgeGenerationBeforeBirthRoundMode();
    }

    /**
     * Set the lowest judge generation before the birth round mode was enabled.
     *
     * @param lowestJudgeGenerationBeforeBirthRoundMode the lowest judge generation before the birth round mode was
     *                                                  enabled
     */
    public void setLowestJudgeGenerationBeforeBirthRoundMode(final long lowestJudgeGenerationBeforeBirthRoundMode) {
        updatePlatformState(
                (p, b) -> b.lowestJudgeGenerationBeforeBirthRoundMode(lowestJudgeGenerationBeforeBirthRoundMode));
    }

    void migrateToPlatformStateAccessorSingleton() {
        MerkleNode child = root.getChild(0);
        // Handling special migration case.
        // This check will be true in  case of loaded state of version 0.53.
        if (child instanceof com.swirlds.platform.state.PlatformState platformState) {
            root.setChild(0, platformSingletonSupplier.get());
            updatePlatformState(platformState);
        }
    }

    /**
     * Updates values of existing platform state with the values from the source.
     * @param source source of the new values
     */
    void updatePlatformState(PlatformStateAccessor source) {
        updatePlatformState((p, b) -> {
            if (source == this) {
                return ((PlatformStateAccessorSingleton) source)
                        .getPlatformState()
                        .copyBuilder();
            } else {
                return convert(source).copyBuilder();
            }
        });
    }

    private void updatePlatformState(BiFunction<PlatformState, PlatformState.Builder, PlatformState.Builder> updater) {
        WritableStates writableStates = root.getWritableStates(PLATFORM_NAME);
        WritableSingletonState<PlatformState> writablePlatformState = writableStates.getSingleton(PLATFORM_STATE_KEY);
        final PlatformState platformState = writablePlatformState.get();
        final Builder builder = platformState == null ? PlatformState.newBuilder() : platformState.copyBuilder();
        final Builder updatedBuilder = updater.apply(platformState, builder);
        writablePlatformState.put(updatedBuilder.build());
        ((CommittableWritableStates) writableStates).commit();
    }

    private com.hedera.hapi.platform.state.PlatformState getPlatformState() {
        ReadableSingletonState<PlatformState> platformState =
                root.getReadableStates(PLATFORM_NAME).getSingleton(PLATFORM_STATE_KEY);
        return requireNonNull(platformState.get());
    }

    @Override
    public String toString() {
        return getPlatformState().toString();
    }
}
