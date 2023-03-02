/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.exceptions.IllegalChildIndexException;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.internal.EventImpl;
import java.util.HashMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * The root of the merkle tree holding the state of the Swirlds ledger.
 * Contains three children:
 * the state used by the application;
 * the state used by the platform;
 * and the state used by both application and platform.
 */
public class State extends PartialNaryMerkleInternal implements MerkleInternal {

    private static final long CLASS_ID = 0x2971b4ba7dd84402L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int ADD_MIN_GEN = 2;
        public static final int EVENT_REFACTOR = 3;
        public static final int MIGRATE_TO_SERIALIZABLE = 4;
        public static final int ADD_DUAL_STATE = 5;
    }

    private static class ChildIndices {
        /**
         * The state written and used by the application. It is the state resulting from all transactions in
         * consensus order from all events with received rounds up through the round this State represents.
         */
        public static final int SWIRLD_STATE = 0;
        /**
         * The state written and used by the platform.
         */
        public static final int PLATFORM_STATE = 1;
        /**
         * The state either read or written by the platform and the application
         */
        public static final int DUAL_STATE = 2;
    }

    /**
     * Used to track the lifespan of this state.
     */
    private final RuntimeObjectRecord registryRecord;

    private boolean initialized = false;

    public State() {
        registryRecord = RuntimeObjectRegistry.createRecord(getClass());
    }

    private State(final State that) {
        super(that);

        registryRecord = RuntimeObjectRegistry.createRecord(getClass());
        this.initialized = that.initialized;

        if (that.getSwirldState() != null) {
            this.setSwirldState(that.getSwirldState().copy());
        }
        if (that.getPlatformState() != null) {
            this.setPlatformState(that.getPlatformState().copy());
        }
        if (that.getDualState() != null) {
            this.setDualState(that.getDualState().copy());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean childHasExpectedType(final int index, final long childClassId) {
        switch (index) {
            case ChildIndices.SWIRLD_STATE:
                return true;
            case ChildIndices.PLATFORM_STATE:
                return childClassId == PlatformState.CLASS_ID || childClassId == LegacyPlatformState.CLASS_ID;
            case ChildIndices.DUAL_STATE:
                return childClassId == DualStateImpl.CLASS_ID;
            default:
                throw new IllegalChildIndexException(getMinimumChildCount(), getMaximumChildCount(), index);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ADD_MIN_GEN;
    }

    /**
     * Get the application state.
     *
     * @return the application state
     */
    public SwirldState getSwirldState() {
        return getChild(ChildIndices.SWIRLD_STATE);
    }

    /**
     * Set the application state.
     *
     * @param state
     * 		the application state
     */
    public void setSwirldState(final SwirldState state) {
        setChild(ChildIndices.SWIRLD_STATE, state);
    }

    /**
     * Get the platform state.
     *
     * @return the platform state
     */
    public PlatformState getPlatformState() {
        return getChild(ChildIndices.PLATFORM_STATE);
    }

    /**
     * Set the platform state.
     *
     * @param platformState
     * 		the platform state
     */
    public void setPlatformState(final PlatformState platformState) {
        setChild(ChildIndices.PLATFORM_STATE, platformState);
    }

    /**
     * Get the dualState.
     *
     * @return the dualState
     */
    private DualStateImpl getDualState() {
        return getChild(ChildIndices.DUAL_STATE);
    }

    /**
     * Get current dualState object which can be read/written by the platform
     *
     * @return current dualState object which can be read/written by the platform
     */
    public PlatformDualState getPlatformDualState() {
        return getDualState();
    }

    /**
     * Get current dualState object which can be read/written by the application
     *
     * @return current dualState object which can be read/written by the application
     */
    public SwirldDualState getSwirldDualState() {
        return getDualState();
    }

    /**
     * Set the dual state.
     *
     * @param dualState
     * 		the dual state
     */
    public void setDualState(final DualStateImpl dualState) {
        setChild(ChildIndices.DUAL_STATE, dualState);
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
        return ClassVersion.ADD_DUAL_STATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public State copy() {
        throwIfImmutable();
        throwIfDestroyed();
        return new State(this);
    }

    // Perhaps this belongs in a different file
    public static void linkParents(final EventImpl[] events) {
        final HashMap<Hash, EventImpl> eventsByHash = new HashMap<>();
        for (final EventImpl event : events) {
            eventsByHash.put(event.getBaseHash(), event);
            event.setSelfParent(eventsByHash.get(event.getSelfParentHash()));
            event.setOtherParent(eventsByHash.get(event.getOtherParentHash()));
        }
    }

    /**
     * Mark this state as having been initialized.
     */
    public void markAsInitialized() {
        initialized = true;
    }

    /**
     * Has this state been initialized?
     */
    public boolean isInitialized() {
        return initialized;
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final State that = (State) o;

        return new EqualsBuilder()
                .append(getPlatformState(), that.getPlatformState())
                .append(getSwirldState(), that.getSwirldState())
                .append(getPlatformDualState(), that.getPlatformDualState())
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(getPlatformState())
                .append(getSwirldState())
                .append(getPlatformDualState())
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("platformState", getPlatformState())
                .append("swirldState", getSwirldState())
                .append("dualState", getPlatformDualState())
                .toString();
    }
}
