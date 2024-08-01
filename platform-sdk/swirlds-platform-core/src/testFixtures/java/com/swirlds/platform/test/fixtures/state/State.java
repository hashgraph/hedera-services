/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.state;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * The root of the merkle tree holding the state of the Swirlds ledger. Contains two children: the state used by the
 * application and the state used by the platform.
 *
 */
public class State extends PartialNaryMerkleInternal implements MerkleRoot {

    private static final long CLASS_ID = 0x2971b4ba7dd84402L;

    public static class ClassVersion {
        public static final int MIGRATE_PLATFORM_STATE = 7;
    }

    private static class ChildIndices {
        /**
         * The state written and used by the application. It is the state resulting from all transactions in consensus
         * order from all events with received rounds up through the round this State represents.
         */
        public static final int SWIRLD_STATE = 0;
    }

    /**
     * Used to track the lifespan of this state.
     */
    private final RuntimeObjectRecord registryRecord;

    /**
     * Constructs a new state.
     */
    public State() {
        registryRecord = RuntimeObjectRegistry.createRecord(getClass());
    }

    private State(final State that) {
        super(that);

        registryRecord = RuntimeObjectRegistry.createRecord(getClass());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode migrate(final int version) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.MIGRATE_PLATFORM_STATE;
    }

    /**
     * Get the application state.
     *
     * @return the application state
     */
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
     * Get the platform state.
     *
     * @return the platform state
     */
    @NonNull
    @Override
    public PlatformState getPlatformState() {
        throw new UnsupportedOperationException("PlatformState is not supported in this implementation of MerkleRoot");
    }

    /**
     * Set the platform state.
     *
     * @param platformState the platform state
     */
    @Override
    public void setPlatformState(@NonNull final PlatformState platformState) {
        throw new UnsupportedOperationException("PlatformState is not supported in this implementation of MerkleRoot");
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
    public State copy() {
        throwIfImmutable();
        throwIfDestroyed();
        return new State(this);
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
        return Objects.equals(getSwirldState(), state.getSwirldState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getSwirldState());
    }

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    @NonNull
    @Override
    public String getInfoString(final int hashDepth) {
        return "<State>";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this).append("swirldState", getSwirldState()).toString();
    }
}
