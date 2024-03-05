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

package com.swirlds.platform.state;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.utility.RuntimeObjectRecord;
import com.swirlds.common.utility.RuntimeObjectRegistry;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.SwirldState;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The root of the merkle tree holding the state of the Swirlds ledger. Contains two children: the state used by the
 * application and the state used by the platform.
 */
public class State extends PartialNaryMerkleInternal implements MerkleInternal {

    private static final Logger logger = LogManager.getLogger(State.class);

    private static final long CLASS_ID = 0x2971b4ba7dd84402L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
        public static final int ADD_MIN_GEN = 2;
        public static final int EVENT_REFACTOR = 3;
        public static final int MIGRATE_TO_SERIALIZABLE = 4;
        public static final int ADD_DUAL_STATE = 5;
        public static final int REMOVE_DUAL_STATE = 6;
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
        /**
         * Not used after migration.
         */
        public static final int DUAL_STATE = 2;
    }

    /**
     * Used to track the lifespan of this state.
     */
    private final RuntimeObjectRecord registryRecord;

    public State() {
        registryRecord = RuntimeObjectRegistry.createRecord(getClass());
    }

    private State(final State that) {
        super(that);

        registryRecord = RuntimeObjectRegistry.createRecord(getClass());

        if (that.getSwirldState() != null) {
            this.setSwirldState(that.getSwirldState().copy());
        }
        if (that.getPlatformState() != null) {
            this.setPlatformState(that.getPlatformState().copy());
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
    public PlatformState getPlatformState() {
        return getChild(ChildIndices.PLATFORM_STATE);
    }

    /**
     * Set the platform state.
     *
     * @param platformState the platform state
     */
    public void setPlatformState(final PlatformState platformState) {
        setChild(ChildIndices.PLATFORM_STATE, platformState);
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
        return ClassVersion.REMOVE_DUAL_STATE;
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
        final State state = (State) other;
        return Objects.equals(getPlatformState(), state.getPlatformState())
                && Objects.equals(getSwirldState(), state.getSwirldState());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(getPlatformState(), getSwirldState());
    }

    /**
     * Generate a string that describes this state.
     *
     * @param hashDepth the depth of the tree to visit and print
     */
    public String getInfoString(final int hashDepth) {
        final PlatformState platformState = getPlatformState();
        final Hash epochHash = platformState.getNextEpochHash();
        final Hash hashEventsCons = platformState.getRunningEventHash();

        final ConsensusSnapshot snapshot = platformState.getSnapshot();
        final List<MinimumJudgeInfo> minimumJudgeInfo = snapshot == null ? null : snapshot.getMinimumJudgeInfoList();

        final StringBuilder sb = new StringBuilder();

        new TextTable()
                .setBordersEnabled(false)
                .addRow("Round:", platformState.getRound())
                .addRow("Timestamp:", platformState.getConsensusTimestamp())
                .addRow("Next consensus number:", snapshot == null ? "null" : snapshot.nextConsensusNumber())
                .addRow("Running event hash:", hashEventsCons)
                .addRow("Running event mnemonic:", hashEventsCons == null ? "null" : hashEventsCons.toMnemonic())
                .addRow("Rounds non-ancient:", platformState.getRoundsNonAncient())
                .addRow("Creation version:", platformState.getCreationSoftwareVersion())
                .addRow("Epoch mnemonic:", epochHash == null ? "null" : epochHash.toMnemonic())
                .addRow("Epoch hash:", epochHash)
                .addRow("Minimum judge hash code:", minimumJudgeInfo == null ? "null" : minimumJudgeInfo.hashCode())
                .addRow("Root hash:", getHash())
                .render(sb);

        sb.append("\n");
        new MerkleTreeVisualizer(this).setDepth(hashDepth).render(sb);
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("platformState", getPlatformState())
                .append("swirldState", getSwirldState())
                .toString();
    }
}
