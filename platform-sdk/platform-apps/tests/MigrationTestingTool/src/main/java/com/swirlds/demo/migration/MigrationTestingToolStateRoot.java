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

package com.swirlds.demo.migration;

import static com.swirlds.platform.state.MerkleStateUtils.createInfoString;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.route.MerkleRouteFactory;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.state.merkle.MerkleTreeSnapshotWriter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Objects;

/**
 * This is a temporary resurrection of {@code com.swirlds.platform.state.State} from the oblivion. This class is necessary
 * to load existing state which still has {@code com.swirlds.platform.state.State} as a root of the tree.
 * Without it the state cannot be deserialized.
 *
 * @deprecated This class should be removed after 0.57 release and once the source migration state is updated to 0.57.
 */
@Deprecated(forRemoval = true)
public class MigrationTestingToolStateRoot extends PartialNaryMerkleInternal implements MerkleRoot {

    private static final long CLASS_ID = 0x2971b4ba7dd84402L;

    public static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int REMOVE_DUAL_STATE = 6;
        public static final int MIGRATE_PLATFORM_STATE = 7;
    }

    private static class ChildIndices {
        /**
         * The state written and used by the application. It is the state resulting from all transactions in consensus
         * order from all events with received rounds up through the round this State represents.
         */
        public static final int SWIRLD_STATE = 0;
        /**
         * The state written and used by the platform.
         */
        public static final int PLATFORM_STATE = 1;
    }

    /**
     * Used to track the lifespan of this state.
     */
    private final RuntimeObjectRecord registryRecord;

    public MigrationTestingToolStateRoot() {
        registryRecord = RuntimeObjectRegistry.createRecord(getClass());
        updatePlatformState(new PlatformState());
    }

    private MigrationTestingToolStateRoot(final MigrationTestingToolStateRoot that) {
        super(that);

        registryRecord = RuntimeObjectRegistry.createRecord(getClass());

        if (that.getSwirldState() != null) {
            this.setSwirldState(that.getSwirldState().copy());
        }
        if (that.getWritablePlatformState() != null) {
            this.updatePlatformState(that.getWritablePlatformState().copy());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode migrate(final int version) {
        if (version < ClassVersion.REMOVE_DUAL_STATE) {
            throw new UnsupportedOperationException("State migration from version " + version + " is not supported."
                    + " The minimum supported version is " + getMinimumSupportedVersion());
        }

        if (version < ClassVersion.MIGRATE_PLATFORM_STATE
                && getSwirldState() instanceof MigrationTestingToolState merkleStateRoot) {
            PlatformState platformState = getWritablePlatformState().copy();
            setChild(ChildIndices.PLATFORM_STATE, null);
            merkleStateRoot.updatePlatformState(platformState);
            merkleStateRoot.setRoute(MerkleRouteFactory.getEmptyRoute());
            return merkleStateRoot.copy();
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.REMOVE_DUAL_STATE;
    }

    /**
     * Get the application state.
     *
     * @return the application state
     */
    @Override
    @NonNull
    public SwirldState getSwirldState() {
        return getChild(ChildIndices.SWIRLD_STATE);
    }

    /**
     * Set the application state.
     *
     * @param state the application state
     */
    public void setSwirldState(final SwirldState state) {
        setChild(ChildIndices.SWIRLD_STATE, state);
    }

    /**
     * Immutable platform state is not supported by this class.
     */
    @NonNull
    @Override
    public PlatformStateAccessor getReadablePlatformState() {
        return getChild(ChildIndices.PLATFORM_STATE);
    }

    /**
     * Get the platform state.
     * @return the platform state
     */
    @NonNull
    @Override
    public PlatformState getWritablePlatformState() {
        return getChild(ChildIndices.PLATFORM_STATE);
    }

    /**
     * Updates the platform state.
     *
     * @param modifier the platform state
     */
    @Override
    public void updatePlatformState(@NonNull final PlatformStateModifier modifier) {
        if (modifier instanceof PlatformState platformState) {
            setChild(ChildIndices.PLATFORM_STATE, platformState);
        } else {
            throw new UnsupportedOperationException("%s implementation of %s is not supported"
                    .formatted(modifier.getClass().getSimpleName(), PlatformStateModifier.class.getSimpleName()));
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
        return ClassVersion.MIGRATE_PLATFORM_STATE;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public MerkleRoot copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new MigrationTestingToolStateRoot(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void destroyNode() {
        registryRecord.release();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        final MerkleRoot state = (MerkleRoot) other;
        return Objects.equals(getReadablePlatformState(), state.getReadablePlatformState())
                && Objects.equals(getSwirldState(), state.getSwirldState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getReadablePlatformState(), getSwirldState());
    }

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    @NonNull
    @Override
    public String getInfoString(final int hashDepth) {
        final PlatformStateAccessor platformState = getReadablePlatformState();
        return createInfoString(hashDepth, platformState, getHash(), this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createSnapshot(@NonNull final Path targetPath) {
        throwIfMutable();
        throwIfDestroyed();
        MerkleTreeSnapshotWriter.createSnapshot(
                this, targetPath, getReadablePlatformState().getRound());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("platformState", getReadablePlatformState())
                .append("swirldState", getSwirldState())
                .toString();
    }
}