/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.state.MerkleStateUtils.createInfoString;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.SnapshotPlatformStateAccessor;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.state.State;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

/**
 * An implementation of {@link State}. The Hashgraph Platform
 * communicates with the application through {@link SwirldMain} and {@link
 * StateLifecycles}. The Hedera application, after startup, only needs the ability to get {@link
 * ReadableStates} and {@link WritableStates} from this object.
 *
 * <p>Among {@link MerkleStateRoot}'s child nodes are the various {@link
 * com.swirlds.merkle.map.MerkleMap}'s and {@link com.swirlds.virtualmap.VirtualMap}'s that make up
 * the service's states. Each such child node has a label specified that is computed from the
 * metadata for that state. Since both service names and state keys are restricted to characters
 * that do not include the period, we can use it to separate service name from state key. When we
 * need to find all states for a service, we can do so by iteration and string comparison.
 *
 * <p>NOTE: The implementation of this class must change before we can support state proofs
 * properly. In particular, a wide n-ary number of children is less than ideal, since the hash of
 * each child must be part of the state proof. It would be better to have a binary tree. We should
 * consider nesting service nodes in a MerkleMap, or some other such approach to get a binary tree.
 */
@ConstructableIgnored
public class PlatformMerkleStateRoot extends MerkleStateRoot<PlatformMerkleStateRoot> {

    private static final long CLASS_ID = 0x8e300b0dfdafbb1aL;

    private final Function<SemanticVersion, SoftwareVersion> versionFactory;

    /**
     * Create a new instance. This constructor must be used for all creations of this class.
     *
     * @param versionFactory a factory for creating {@link SoftwareVersion} based on provided {@link SemanticVersion}
     */
    public PlatformMerkleStateRoot(@NonNull Function<SemanticVersion, SoftwareVersion> versionFactory) {
        this.versionFactory = requireNonNull(versionFactory);
    }

    protected PlatformMerkleStateRoot(@NonNull PlatformMerkleStateRoot from) {
        super(from);
        this.versionFactory = from.versionFactory;
    }

    @Override
    protected PlatformMerkleStateRoot copyingConstructor() {
        return new PlatformMerkleStateRoot(this);
    }

    /**
     * Returns a factory constructing instances of {@link SoftwareVersion} based on provided {@link SemanticVersion}.
     */
    @NonNull
    public Function<SemanticVersion, SoftwareVersion> getVersionFactory() {
        return versionFactory;
    }

    /**
     * Get writable platform state. Works only on mutable {@link PlatformMerkleStateRoot}.
     * Call this method only if you need to modify the platform state.
     *
     * @return mutable platform state
     */
    @NonNull
    public PlatformStateModifier getWritablePlatformState() {
        if (isImmutable()) {
            throw new IllegalStateException("Cannot get writable platform state when state is immutable");
        }
        return writablePlatformStateStore();
    }

    /**
     * Updates the platform state with the values from the provided instance of {@link PlatformStateModifier}
     *
     * @param accessor a source of values
     */
    public void updatePlatformState(@NonNull final PlatformStateModifier accessor) {
        writablePlatformStateStore().setAllFrom(accessor);
    }

    /**
     * Get readable platform state.
     * Works on both - mutable and immutable {@link PlatformMerkleStateRoot} and, therefore, this method should be preferred.
     *
     * @return immutable platform state
     */
    @NonNull
    public PlatformStateAccessor getReadablePlatformState() {
        return getServices().isEmpty()
                ? new SnapshotPlatformStateAccessor(getPlatformState(), versionFactory)
                : readablePlatformStateStore();
    }

    private ReadablePlatformStateStore readablePlatformStateStore() {
        return new ReadablePlatformStateStore(getReadableStates(PlatformStateService.NAME), versionFactory);
    }

    private WritablePlatformStateStore writablePlatformStateStore() {
        return new WritablePlatformStateStore(getWritableStates(PlatformStateService.NAME), versionFactory);
    }

    private com.hedera.hapi.platform.state.PlatformState getPlatformState() {
        final var index = findNodeIndex(PlatformStateService.NAME, PLATFORM_STATE_KEY);
        return index == -1
                ? V0540PlatformStateSchema.UNINITIALIZED_PLATFORM_STATE
                : ((SingletonNode<PlatformState>) getChild(index)).getValue();
    }

    /**
     * Returns the round number from the consensus snapshot, or the genesis round if there is no consensus snapshot.
     */
    @Override
    public long getCurrentRound() {
        return getPlatformState().consensusSnapshot() == null
                ? PlatformStateAccessor.GENESIS_ROUND
                : getPlatformState().consensusSnapshot().round();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    @NonNull
    public String getInfoString(final int hashDepth) {
        return createInfoString(hashDepth, getReadablePlatformState(), getHash(), this);
    }
}
