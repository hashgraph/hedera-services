/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.demo.migration.MigrationTestingToolMain.PREVIOUS_SOFTWARE_VERSION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.merkle.test.fixtures.map.util.MerkleMapTestUtil.configuration;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.platform.NodeId;
import com.swirlds.demo.migration.virtual.AccountVirtualMapKey;
import com.swirlds.demo.migration.virtual.AccountVirtualMapKeySerializer;
import com.swirlds.demo.migration.virtual.AccountVirtualMapValue;
import com.swirlds.demo.migration.virtual.AccountVirtualMapValueSerializer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Iterator;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MigrationTestingToolState extends PartialNaryMerkleInternal implements MerkleInternal, SwirldState {
    private static final Logger logger = LogManager.getLogger(MigrationTestingToolState.class);

    /**
     * The version history of this class. Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        /**
         * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
         * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
         * specially by the platform.
         */
        public static final int ORIGINAL = 1;
        /**
         * In this version, serialization was performed by serialize/deserialize.
         */
        public static final int MIGRATE_TO_SERIALIZABLE = 2;
        /**
         * Migrate from the old FCMap to the new MerkleMap
         */
        public static final int MERKLE_MAP_REFACTOR = 3;
        /**
         * Add a virtual map and remove all blobs.
         */
        public static final int VIRTUAL_MAP = 4;
    }

    private static final long CLASS_ID = 0x1a0daec64a09f6a4L;

    /**
     * A record of the positions of each child within this node.
     */
    private static class ChildIndices {
        public static final int UNUSED = 0;
        public static final int MERKLE_MAP = 1;
        public static final int VIRTUAL_MAP = 2;

        public static final int CHILD_COUNT = 3;
    }

    public NodeId selfId;

    public MigrationTestingToolState() {
        super(ChildIndices.CHILD_COUNT);
    }

    private MigrationTestingToolState(final MigrationTestingToolState that) {
        super(that);
        if (that.getMerkleMap() != null) {
            setMerkleMap(that.getMerkleMap().copy());
        }
        if (that.getVirtualMap() != null) {
            setVirtualMap(that.getVirtualMap().copy());
        }
        that.setImmutable(true);
        this.setImmutable(false);
        this.selfId = that.selfId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfChildren() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumChildCount() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaximumChildCount() {
        return ChildIndices.CHILD_COUNT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean childHasExpectedType(final int index, final long childClassId) {
        switch (index) {
            case ChildIndices.UNUSED:
                // We used to use this for an address book, but now we don't use this index.
                // Ignore whatever is found at this index.
                return true;
            case ChildIndices.MERKLE_MAP:
                return childClassId == MerkleMap.CLASS_ID;
            case ChildIndices.VIRTUAL_MAP:
                return childClassId == VirtualMap.CLASS_ID;
            default:
                return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
        if (!children.isEmpty() && children.get(0) instanceof AddressBook) {
            // We used to store an address book here, but we can ignore it now.
            children.set(0, null);
        }

        super.addDeserializedChildren(children, version);
    }

    /**
     * Get a {@link MerkleMap} that contains various data.
     */
    protected MerkleMap<AccountID, MapValue> getMerkleMap() {
        return getChild(ChildIndices.MERKLE_MAP);
    }

    /**
     * Set a {@link MerkleMap} that contains various data.
     */
    protected void setMerkleMap(final MerkleMap<AccountID, MapValue> map) {
        throwIfImmutable();
        setChild(ChildIndices.MERKLE_MAP, map);
    }

    /**
     * Get a {@link VirtualMap} that contains various data.
     */
    protected VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> getVirtualMap() {
        return getChild(ChildIndices.VIRTUAL_MAP);
    }

    /**
     * Set a {@link VirtualMap} that contains various data.
     */
    protected void setVirtualMap(final VirtualMap<AccountVirtualMapKey, AccountVirtualMapValue> map) {
        setChild(ChildIndices.VIRTUAL_MAP, map);
    }

    /**
     * Do genesis initialization.
     */
    private void genesisInit(final Platform platform) {
        setMerkleMap(new MerkleMap<>());
        final MerkleDbTableConfig tableConfig = new MerkleDbTableConfig((short) 1, DigestType.SHA_384, configuration().getConfigData(MerkleDbConfig.class));
        // to make it work for the multiple node in one JVM case, we need reset the default instance path every time
        // we create another instance of MerkleDB.
        MerkleDb.resetDefaultInstancePath();
        final VirtualDataSourceBuilder dsBuilder = new MerkleDbDataSourceBuilder(tableConfig, configuration());
        setVirtualMap(new VirtualMap<>(
                "virtualMap", new AccountVirtualMapKeySerializer(), new AccountVirtualMapValueSerializer(), dsBuilder, configuration()));
        selfId = platform.getSelfId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        final MerkleMap<AccountID, MapValue> merkleMap = getMerkleMap();
        if (merkleMap != null) {
            logger.info(STARTUP.getMarker(), "MerkleMap initialized with {} values", merkleMap.size());
        }
        final VirtualMap<?, ?> virtualMap = getVirtualMap();
        if (virtualMap != null) {
            logger.info(STARTUP.getMarker(), "VirtualMap initialized with {} values", virtualMap.size());
        }
        selfId = platform.getSelfId();

        if (trigger == InitTrigger.GENESIS) {
            logger.warn(STARTUP.getMarker(), "InitTrigger was {} when expecting RESTART or RECONNECT", trigger);
        }

        if (previousSoftwareVersion == null || previousSoftwareVersion.compareTo(PREVIOUS_SOFTWARE_VERSION) != 0) {
            logger.warn(
                    STARTUP.getMarker(),
                    "previousSoftwareVersion was {} when expecting it to be {}",
                    previousSoftwareVersion,
                    PREVIOUS_SOFTWARE_VERSION);
        }

        if (trigger == InitTrigger.GENESIS) {
            logger.info(STARTUP.getMarker(), "Doing genesis initialization");
            genesisInit(platform);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(final Round round, final PlatformStateAccessor platformState) {
        throwIfImmutable();
        for (final Iterator<ConsensusEvent> eventIt = round.iterator(); eventIt.hasNext(); ) {
            final ConsensusEvent event = eventIt.next();
            for (final Iterator<ConsensusTransaction> transIt = event.consensusTransactionIterator();
                    transIt.hasNext(); ) {
                final ConsensusTransaction trans = transIt.next();
                if (trans.isSystem()) {
                    continue;
                }
                final MigrationTestingToolTransaction mTrans =
                        TransactionUtils.parseTransaction(trans.getApplicationTransaction());
                mTrans.applyTo(this);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MigrationTestingToolState copy() {
        throwIfImmutable();
        return new MigrationTestingToolState(this);
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
        return ClassVersion.VIRTUAL_MAP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.VIRTUAL_MAP;
    }
}
