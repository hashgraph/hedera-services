/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.DaggerServicesApp;
import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.SerializableSemVers;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerklePayerRecords;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.migration.VirtualMapDataAccess;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.EmptyReadableStates;
import com.hedera.node.app.spi.state.EmptyWritableStates;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableSingletonStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.merkle.adapters.MerkleMapLikeFactory;
import com.hedera.node.app.state.merkle.disk.OnDiskReadableKVState;
import com.hedera.node.app.state.merkle.disk.OnDiskWritableKVState;
import com.hedera.node.app.state.merkle.memory.InMemoryKey;
import com.hedera.node.app.state.merkle.memory.InMemoryReadableKVState;
import com.hedera.node.app.state.merkle.memory.InMemoryValue;
import com.hedera.node.app.state.merkle.memory.InMemoryWritableKVState;
import com.hedera.node.app.state.merkle.singleton.ReadableSingletonStateImpl;
import com.hedera.node.app.state.merkle.singleton.SingletonNode;
import com.hedera.node.app.state.merkle.singleton.WritableSingletonStateImpl;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.utility.Labeled;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.gui.SwirldsGui;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.hedera.node.app.service.mono.ServicesState.EMPTY_HASH;
import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_FIRST_USER_ENTITY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.swirlds.common.system.InitTrigger.GENESIS;
import static com.swirlds.common.system.InitTrigger.RECONNECT;
import static com.swirlds.common.system.InitTrigger.RESTART;

/**
 * An implementation of {@link SwirldState2} and {@link HederaState}. The Hashgraph Platform
 * communicates with the application through {@link com.swirlds.common.system.SwirldMain} and {@link
 * SwirldState2}. The Hedera application, after startup, only needs the ability to get {@link
 * ReadableStates} and {@link WritableStates} from this object.
 *
 * <p>Among {@link MerkleHederaState}'s child nodes are the various {@link
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
public class MerkleHederaState extends PartialNaryMerkleInternal
        implements MerkleInternal, SwirldState2, HederaState, StateChildrenProvider {
    private static final Logger log = LogManager.getLogger(MerkleHederaState.class);

    private static final VirtualMapDataAccess VIRTUAL_MAP_DATA_ACCESS =
            VirtualMapMigration::extractVirtualMapData;
    /**
     * Used when asked for a service's readable states that we don't have
     */
    private static final ReadableStates EMPTY_READABLE_STATES = new EmptyReadableStates();
    /**
     * Used when asked for a service's writable states that we don't have
     */
    private static final WritableStates EMPTY_WRITABLE_STATES = new EmptyWritableStates();

    // For serialization
    private static final long CLASS_ID = 0x2de3ead3caf06392L;
    private static final int VERSION_1 = 1;
    private static final int CURRENT_VERSION = VERSION_1;

    /**
     * This callback is invoked whenever the consensus round happens. The Hashgraph Platform, today,
     * only communicates the consensus round through the {@link SwirldState2} interface. In the
     * future it will use a callback on a platform created via a platform builder. Until that
     * happens the only way our application will know of new transactions, will be through this
     * callback. Since this is not serialized and saved to state, it must be restored on application
     * startup. If this is never set, the application will never be able to handle a new round of
     * transactions.
     *
     * <p>This reference is moved forward to the working mutable state.
     */
    private BiConsumer<Round, SwirldDualState> onHandleConsensusRound;

    /**
     * This callback is invoked whenever there is an event to pre-handle.
     *
     * <p>This reference is moved forward to the working mutable state.
     */
    private Consumer<Event> onPreHandle;

    /**
     * This callback is invoked when the platform determines it is time to perform a migration. This
     * is supplied via the constructor, and so a custom entry in the ConstructableRegistry has to be
     * made to create this object.
     *
     * <p>This reference is only on the first, original state. It is not moved or copied forward to
     * later working mutable states.
     */
    private Consumer<MerkleHederaState> onMigrate;

    private SemanticVersion versionFromSavedState;
    private com.hedera.node.app.service.mono.state.org.StateMetadata metadata;

    /**
     * Maintains information about each service, and each state of each service, known by this
     * instance. The key is the "service-name.state-key".
     */
    private final Map<String, Map<String, StateMetadata<?, ?>>> services = new HashMap<>();

    // Default constructor provided for ConstructableRegistry, TO BE REMOVED ASAP
    @Deprecated(forRemoval = true)
    public MerkleHederaState() {
    }

    /**
     * Create a new instance. This constructor must be used for all creations of this class.
     *
     * @param onMigrate              The callback to invoke when the platform deems it time to migrate
     * @param onPreHandle            The callback to invoke when an event is ready for pre-handle
     * @param onHandleConsensusRound The callback invoked when the platform has
     */
    public MerkleHederaState(
            @NonNull final Consumer<MerkleHederaState> onMigrate,
            @NonNull final Consumer<Event> onPreHandle,
            @NonNull final BiConsumer<Round, SwirldDualState> onHandleConsensusRound) {
        this.onMigrate = Objects.requireNonNull(onMigrate);
        this.onPreHandle = Objects.requireNonNull(onPreHandle);
        this.onHandleConsensusRound = Objects.requireNonNull(onHandleConsensusRound);
    }

    @Override
    public void init(
            final Platform platform,
            final AddressBook addressBook,
            final SwirldDualState dualState,
            final InitTrigger trigger,
            final SoftwareVersion deserializedVersion) {
        System.out.println("In init with " + trigger);
        if (trigger == GENESIS) {
            // Create the top-level children in the Merkle tree
            onMigrate.accept(this);
            final var bootstrapProps = new BootstrapProperties(false);
            final var seqStart = bootstrapProps.getLongProperty(HEDERA_FIRST_USER_ENTITY);
            createSpecialGenesisChildren(addressBook, seqStart, bootstrapProps);
            internalInit(platform, bootstrapProps, dualState, GENESIS, null);
            networkCtx().markPostUpgradeScanStatus();
        } else {
            versionFromSavedState = ((SerializableSemVers) deserializedVersion).getServices();
            // Note this returns the app in case we need to do something with it  after making
            // final changes to state (e.g. after migrating something from memory to disk)
            deserializedInit(platform, addressBook, dualState, trigger, deserializedVersion);
        }
    }

    private ServicesApp deserializedInit(
            final Platform platform,
            final AddressBook addressBook,
            final SwirldDualState dualState,
            final InitTrigger trigger,
            @NonNull final SoftwareVersion deserializedVersion) {
        log.info("Init called on Services node {} WITH Merkle saved state", platform.getSelfId());

        // Immediately override the address book from the saved state
        final var writableNetworkStates = createWritableStates("NetworkService");
        writableNetworkStates.getSingleton("ADDRESS_BOOK").put(addressBook);
        ((MerkleWritableStates) writableNetworkStates).commit();
        final var bootstrapProps = new BootstrapProperties(false);
        return internalInit(platform, bootstrapProps, dualState, trigger, deserializedVersion);
    }

    private void createSpecialGenesisChildren(
            final AddressBook addressBook,
            final long seqStart,
            final BootstrapProperties bootstrapProperties) {
        final var writableNetworkStates = createWritableStates("NetworkService");
        writableNetworkStates.getSingleton("CONTEXT")
                .put(genesisNetworkCtxWith(seqStart));
        writableNetworkStates.getSingleton("RUNNING_HASHES")
                .put(genesisRunningHashLeaf());
        writableNetworkStates.getSingleton("ADDRESS_BOOK")
                .put(addressBook);
        writableNetworkStates.getSingleton("SPECIAL_FILES")
                .put(new MerkleSpecialFiles());
        final var writableStakingInfos = writableNetworkStates.
                <EntityNum, MerkleStakingInfo>get("STAKING");
        buildStakingInfoMap(addressBook, bootstrapProperties, writableStakingInfos);
        ((MerkleWritableStates) writableNetworkStates).commit();
    }

    private void buildStakingInfoMap(
            final AddressBook addressBook,
            final BootstrapProperties bootstrapProperties,
            final WritableKVState<EntityNum, MerkleStakingInfo> stakingInfos) {
        final var numberOfNodes = addressBook.getSize();
        long maxStakePerNode =
                bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT) / numberOfNodes;
        long minStakePerNode = maxStakePerNode / 2;
        for (int i = 0; i < numberOfNodes; i++) {
            final var nodeNum = EntityNum.fromLong(addressBook.getAddress(i).getId());
            final var info = new MerkleStakingInfo(bootstrapProperties);
            info.setMinStake(minStakePerNode);
            info.setMaxStake(maxStakePerNode);
            stakingInfos.put(nodeNum, info);
        }
    }

    private ServicesApp internalInit(
            final Platform platform,
            final BootstrapProperties bootstrapProps,
            SwirldDualState dualState,
            final InitTrigger trigger,
            @Nullable final SoftwareVersion deserializedVersion) {
        final var selfId = platform.getSelfId().getId();

        final ServicesApp app;
        if (APPS.includes(selfId)) {
            app = APPS.get(selfId);
        } else {
            final var nodeAddress = addressBook().getAddress(selfId);
            final var initialHash = runningHashLeaf().getRunningHash().getHash();
            app =
                    DaggerServicesApp.builder()
                            .staticAccountMemo(nodeAddress.getMemo())
                            .bootstrapProps(bootstrapProps)
                            .initialHash(initialHash)
                            .platform(platform)
                            .consoleCreator(SwirldsGui::createConsole)
                            .crypto(CryptographyHolder.get())
                            .selfId(selfId)
                            .build();
            APPS.save(selfId, app);
        }

        if (dualState == null) {
            dualState = new DualStateImpl();
        }
        app.dualStateAccessor().setDualState(dualState);
        log.info(
                "Dual state includes freeze time={} and last frozen={}",
                dualState.getFreezeTime(),
                dualState.getLastFrozenTime());

        final var deployedVersion = SEMANTIC_VERSIONS.deployedSoftwareVersion();
        if (deployedVersion.isBefore(deserializedVersion)) {
            log.error(
                    "Fatal error, state source version {} is after node software version {}",
                    deserializedVersion,
                    deployedVersion);
            app.systemExits().fail(1);
        } else {
            final var isUpgrade = deployedVersion.isAfter(deserializedVersion);
            if (trigger == RESTART) {
                // We may still want to change the address book without an upgrade. But note
                // that without a dynamic address book, this MUST be a no-op during reconnect.
                app.stakeStartupHelper().doRestartHousekeeping(addressBook(), stakingInfo());
                if (isUpgrade) {
                    dualState.setFreezeTime(null);
                    networkCtx().discardPreparedUpgradeMeta();
                    if (deployedVersion.hasMigrationRecordsFrom(deserializedVersion)) {
                        networkCtx().markMigrationRecordsNotYetStreamed();
                    }
                }
            }
            networkCtx().setStateVersion(CURRENT_VERSION);

            metadata = new com.hedera.node.app.service.mono.state.org.StateMetadata(app, new FCHashMap<>());
            // This updates the working state accessor with our children
            app.initializationFlow().runWith(this, bootstrapProps);
            if (trigger == RESTART && isUpgrade) {
                app.stakeStartupHelper()
                        .doUpgradeHousekeeping(networkCtx(), accounts(), stakingInfo());
            }

            // Ensure the prefetch queue is created and thread pool is active instead of waiting
            // for lazy-initialization to take place
            app.prefetchProcessor();

            log.info("  --> Context initialized accordingly on Services node {}", selfId);

            if (trigger == GENESIS) {
                app.sysAccountsCreator()
                        .ensureSystemAccounts(
                                app.backingAccounts(), app.workingState().addressBook());
                app.sysFilesManager().createManagedFilesIfMissing();
                app.stakeStartupHelper().doGenesisHousekeeping(addressBook());
            }
            if (trigger != RECONNECT) {
                // Once we have a dynamic address book, this will run unconditionally
                app.sysFilesManager().updateStakeDetails();
            }
        }
        return app;
    }

    @Nullable
    public SemanticVersion deserializedVersion() {
        return versionFromSavedState;
    }

    /**
     * Private constructor for fast-copy.
     *
     * @param from The other state to fast-copy from. Cannot be null.
     */
    private MerkleHederaState(@NonNull final MerkleHederaState from) {
        // Copy the Merkle route from the source instance
        super(from);

        // Copy over the metadata
        for (final var entry : from.services.entrySet()) {
            this.services.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        this.metadata = (from.metadata == null) ? null : from.metadata.copy();
        this.versionFromSavedState = from.versionFromSavedState;

        // Copy the non-null Merkle children from the source (should also be handled by super, TBH).
        // Note we don't "compress" -- null children remain in here unless we manually remove them
        // (which would cause massive re-hashing).
        for (int childIndex = 0, n = from.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = from.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }

        // **MOVE** over the handle listener. Don't leave it on the old copy anymore.
        this.onHandleConsensusRound = from.onHandleConsensusRound;
        from.onHandleConsensusRound = null;

        // **MOVE** over the pre-handle. Don't leave it on the old copy anymore.
        this.onPreHandle = from.onPreHandle;
        from.onPreHandle = null;

        // **DO NOT** move over the onMigrate handler. We don't need it in subsequent
        // copies of the state
        this.onMigrate = null;
        from.onMigrate = null;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public ReadableStates createReadableStates(@NonNull final String serviceName) {
        final var stateMetadata = services.get(serviceName);
        return stateMetadata == null
                ? EMPTY_READABLE_STATES
                : new MerkleReadableStates(stateMetadata);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public WritableStates createWritableStates(@NonNull final String serviceName) {
        throwIfImmutable();
        final var stateMetadata = services.get(serviceName);
        return stateMetadata == null
                ? EMPTY_WRITABLE_STATES
                : new MerkleWritableStates(stateMetadata);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleHederaState copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        System.out.println("in copy");
        final var that = new MerkleHederaState(this);
        if (metadata != null) {
            metadata.app().workingState().updateFrom(that);
        }
        return that;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getAddressBookCopy() {
        // To be implemented by Issue #4200
//        throw new RuntimeException("Not yet implemented");
        return addressBook().copy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(
            @NonNull final Round round, @NonNull final SwirldDualState swirldDualState) {
//        if (onHandleConsensusRound != null) {
//            onHandleConsensusRound.accept(round, swirldDualState);
//        }
        throwIfImmutable();
        final var app = metadata.app();
        app.dualStateAccessor().setDualState(swirldDualState);
        app.logic().incorporateConsensus(round);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(Event event) {
//        if (onPreHandle != null) {
//            onPreHandle.accept(event);
//        }
        metadata.app().eventExpansion().expandAllSigs(event, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleNode migrate(int ignored) {
        if (onMigrate != null) {
            onMigrate.accept(this);
        }

        // Always return this node, we never want to replace MerkleHederaState node in the tree
        return this;
    }

    <K extends Comparable<K>, V> void putServiceStateIfAbsent(
            @NonNull final StateMetadata<K, V> md) {
        throwIfImmutable();
        Objects.requireNonNull(md);
        final var stateMetadata = services.computeIfAbsent(md.serviceName(), k -> new HashMap<>());
        stateMetadata.put(md.stateDefinition().stateKey(), md);
    }

    /**
     * Puts the defined service state and its associated node into the merkle tree. The precondition
     * for calling this method is that node MUST be a {@link MerkleMap} or {@link VirtualMap} and
     * MUST have a correct label applied.
     *
     * @param md   The metadata associated with the state
     * @param node The node to add. Cannot be null.
     * @throws IllegalArgumentException if the node is neither a merkle map nor virtual map, or if
     *                                  it doesn't have a label, or if the label isn't right.
     */
    <K extends Comparable<K>, V> void putServiceStateIfAbsent(
            @NonNull final StateMetadata<K, V> md, @NonNull final MerkleNode node) {

        // Validate the inputs
        throwIfImmutable();
        Objects.requireNonNull(md);
        Objects.requireNonNull(node);

        final var label = node instanceof Labeled labeled ? labeled.getLabel() : null;
        if (label == null) {
            throw new IllegalArgumentException("`node` must be a Labeled and have a label");
        }

        final var def = md.stateDefinition();
        if (def.onDisk() && !(node instanceof VirtualMap<?, ?>)) {
            throw new IllegalArgumentException(
                    "Mismatch: state definition claims on-disk, but "
                            + "the merkle node is not a VirtualMap");
        }

        if (label.isEmpty()) {
            // It looks like both MerkleMap and VirtualMap do not allow for a null label.
            // But I want to leave this check in here anyway, in case that is ever changed.
            throw new IllegalArgumentException("A label must be specified on the node");
        }

        if (!label.equals(StateUtils.computeLabel(md.serviceName(), def.stateKey()))) {
            throw new IllegalArgumentException(
                    "A label must be computed based on the same "
                            + "service name and state key in the metadata!");
        }

        // Put this metadata into the map
        final var stateMetadata = services.computeIfAbsent(md.serviceName(), k -> new HashMap<>());
        stateMetadata.put(def.stateKey(), md);

        // Look for a node, and if we don't find it, then insert the one we were given
        // If there is not a node there, then set it. I don't want to overwrite the existing node,
        // because it may have been loaded from state on disk, and the node provided here in this
        // call is always for genesis. So we may just ignore it.
        if (findNodeIndex(md.serviceName(), def.stateKey()) == -1) {
            System.out.println("Setting child " + getNumberOfChildren() + " to " + node);
            setChild(getNumberOfChildren(), node);
        }
    }

    /**
     * Removes the node and metadata from the state merkle tree.
     *
     * @param serviceName The service name. Cannot be null.
     * @param stateKey    The state key
     */
    void removeServiceState(@NonNull final String serviceName, @NonNull final String stateKey) {
        throwIfImmutable();
        Objects.requireNonNull(serviceName);
        Objects.requireNonNull(stateKey);

        // Remove the metadata entry
        final var stateMetadata = services.get(serviceName);
        if (stateMetadata != null) {
            stateMetadata.remove(stateKey);
        }

        // Remove the node
        final var index = findNodeIndex(serviceName, stateKey);
        if (index != -1) {
            setChild(index, null);
        }
    }

    /**
     * Simple utility method that finds the state node index.
     *
     * @param serviceName the service name
     * @param stateKey    the state key
     * @return -1 if not found, otherwise the index into the children
     */
    private int findNodeIndex(@NonNull final String serviceName, @NonNull final String stateKey) {
        final var label = StateUtils.computeLabel(serviceName, stateKey);
        System.out.println("Looking for label " + label);
        for (int i = 0, n = getNumberOfChildren(); i < n; i++) {
            final var node = getChild(i);
            if (node instanceof Labeled labeled && label.equals(labeled.getLabel())) {
                System.out.println("Found at index " + i);
                return i;
            }
        }

        System.out.println("Not found");
        return -1;
    }

    @Override
    public AccountStorageAdapter accounts() {
        return AccountStorageAdapter.fromOnDisk(
                VIRTUAL_MAP_DATA_ACCESS,
                mapLikePayerRecords(),
                getChild(findNodeIndex("TokenService", "ACCOUNTS")));
    }

    private MerkleMapLike<EntityNum, MerklePayerRecords> mapLikePayerRecords() {
        return MerkleMapLikeFactory.unwrapping(
                (StateMetadata<EntityNum, MerklePayerRecords>) services.get("TokenService").get("PAYER_RECORDS"),
                getChild(findNodeIndex("TokenService", "PAYER_RECORDS")));
    }

    @Override
    @SuppressWarnings("java:S5961")
    public MerkleMapLike<EntityNum, MerkleTopic> topics() {
        return MerkleMapLikeFactory.unwrapping(
                (StateMetadata<EntityNum, MerkleTopic>) services.get("ConsensusService").get("TOPICS"),
                getChild(findNodeIndex("ConsensusService", "TOPICS")));
    }

    @Override
    public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
        return getChild(findNodeIndex("FileService", "BLOBS"));
    }

    @Override
    public VirtualMap<ContractKey, IterableContractValue> contractStorage() {
        return getChild(findNodeIndex("ContractService", "STORAGE"));
    }

    @Override
    public MerkleMapLike<EntityNum, MerkleToken> tokens() {
        return MerkleMapLikeFactory.unwrapping(
                (StateMetadata<EntityNum, MerkleToken>) services.get("TokenService").get("TOKENS"),
                getChild(findNodeIndex("TokenService", "TOKENS")));
    }

    @Override
    public TokenRelStorageAdapter tokenAssociations() {
        return TokenRelStorageAdapter.fromOnDisk(
                getChild(findNodeIndex("TokenService", "TOKEN_RELS")));
    }

    @Override
    public MerkleScheduledTransactions scheduleTxs() {
        // No good way to support this at the moment
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MerkleNetworkContext networkCtx() {
        return ((SingletonNode<MerkleNetworkContext>) getChild(
                findNodeIndex("NetworkService", "CONTEXT"))).getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public AddressBook addressBook() {
        return ((SingletonNode<AddressBook>) getChild(
                findNodeIndex("NetworkService", "ADDRESS_BOOK"))).getValue();
    }

    @Override
    public MerkleSpecialFiles specialFiles() {
        return ((SingletonNode<MerkleSpecialFiles>) getChild(
                findNodeIndex("NetworkService", "SPECIAL_FILES"))).getValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public UniqueTokenMapAdapter uniqueTokens() {
        return UniqueTokenMapAdapter.wrap(
                (VirtualMap<UniqueTokenKey, UniqueTokenValue>)
                        getChild(findNodeIndex("TokenService", "NFTS")));
    }

    @Override
    public RecordsStorageAdapter payerRecords() {
        return RecordsStorageAdapter.fromDedicated(mapLikePayerRecords());
    }

    @Override
    public RecordsRunningHashLeaf runningHashLeaf() {
        return ((SingletonNode<RecordsRunningHashLeaf>) getChild(
                findNodeIndex("NetworkService", "RUNNING_HASHES"))).getValue();
    }

    @Override
    public Map<ByteString, EntityNum> aliases() {
        Objects.requireNonNull(metadata, "Cannot get aliases from an uninitialized state");
        return metadata.aliases();
    }

    @Override
    public MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo() {
        return MerkleMapLikeFactory.unwrapping(
                (StateMetadata<EntityNum, MerkleStakingInfo>) services.get("NetworkService").get("STAKING"),
                getChild(findNodeIndex("NetworkService", "STAKING")));
    }

    @Override
    public boolean isInitialized() {
        return metadata != null;
    }

    @Override
    public Instant getTimeOfLastHandledTxn() {
        return networkCtx().consensusTimeOfLastHandledTxn();
    }

    @Override
    public int getStateVersion() {
        return networkCtx().getStateVersion();
    }

    private RecordsRunningHashLeaf genesisRunningHashLeaf() {
        final var genesisRunningHash = new RunningHash();
        genesisRunningHash.setHash(EMPTY_HASH);
        return new RecordsRunningHashLeaf(genesisRunningHash);
    }

    private MerkleNetworkContext genesisNetworkCtxWith(final long seqStart) {
        return new MerkleNetworkContext(
                null, new SequenceNumber(seqStart), seqStart - 1, new ExchangeRates());
    }

    /**
     * Base class implementation for states based on MerkleTree
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private abstract class MerkleStates implements ReadableStates {
        private final Map<String, StateMetadata<?, ?>> stateMetadata;
        protected final Map<String, ReadableKVState<?, ?>> kvInstances;
        protected final Map<String, ReadableSingletonState<?>> singletonInstances;
        private final Set<String> stateKeys;

        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            this.stateMetadata = Objects.requireNonNull(stateMetadata);
            this.stateKeys = Collections.unmodifiableSet(stateMetadata.keySet());
            this.kvInstances = new HashMap<>();
            this.singletonInstances = new HashMap<>();
        }

        @NonNull
        @Override
        public <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
            final ReadableKVState<K, V> instance =
                    (ReadableKVState<K, V>) kvInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || md.stateDefinition().singleton()) {
                throw new IllegalArgumentException("Unknown k/v state key '" + stateKey + ";");
            }

            final var node = findNode(md);
            if (node instanceof VirtualMap v) {
                final var ret = createReadableKVState(md, v);
                kvInstances.put(stateKey, ret);
                return ret;
            } else if (node instanceof MerkleMap m) {
                final var ret = createReadableKVState(md, m);
                kvInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for k/v state " + stateKey);
            }
        }

        @NonNull
        @Override
        public <T> ReadableSingletonState<T> getSingleton(@NonNull String stateKey) {
            final ReadableSingletonState<T> instance =
                    (ReadableSingletonState<T>) singletonInstances.get(stateKey);
            if (instance != null) {
                return instance;
            }

            final var md = stateMetadata.get(stateKey);
            if (md == null || !md.stateDefinition().singleton()) {
                throw new IllegalArgumentException(
                        "Unknown singleton state key '" + stateKey + "'");
            }

            final var node = findNode(md);
            if (node instanceof SingletonNode s) {
                final var ret = createReadableSingletonState(md, s);
                singletonInstances.put(stateKey, ret);
                return ret;
            } else {
                // This exception should never be thrown. Only if "findNode" found the wrong node!
                throw new IllegalStateException("Unexpected type for singleton state " + stateKey);
            }
        }

        @Override
        public boolean contains(@NonNull final String stateKey) {
            return stateMetadata.containsKey(stateKey);
        }

        @NonNull
        @Override
        public Set<String> stateKeys() {
            return stateKeys;
        }

        @NonNull
        protected abstract ReadableKVState createReadableKVState(
                @NonNull StateMetadata md, @NonNull VirtualMap v);

        @NonNull
        protected abstract ReadableKVState createReadableKVState(
                @NonNull StateMetadata md, @NonNull MerkleMap m);

        @NonNull
        protected abstract ReadableSingletonState createReadableSingletonState(
                @NonNull StateMetadata md, @NonNull SingletonNode<?> s);

        /**
         * Utility method for finding and returning the given node. Will throw an ISE if such a node
         * cannot be found!
         *
         * @param md The metadata
         * @return The found node
         */
        @NonNull
        private MerkleNode findNode(@NonNull final StateMetadata<?, ?> md) {
            final var index = findNodeIndex(md.serviceName(), md.stateDefinition().stateKey());
            if (index == -1) {
                // This can only happen if there WAS a node here, and it was removed!
                throw new IllegalStateException(
                        "State '"
                                + md.stateDefinition().stateKey()
                                + "' for service '"
                                + md.serviceName()
                                + "' is missing from the merkle tree!");
            }

            return getChild(index);
        }
    }

    /**
     * An implementation of {@link ReadableStates} based on the merkle tree.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private final class MerkleReadableStates extends MerkleStates {
        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleReadableStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            super(stateMetadata);
        }

        @Override
        @NonNull
        protected ReadableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final VirtualMap v) {
            return new OnDiskReadableKVState<>(md, v);
        }

        @Override
        @NonNull
        protected ReadableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final MerkleMap m) {
            return new InMemoryReadableKVState<>(md, m);
        }

        @Override
        @NonNull
        protected ReadableSingletonState<?> createReadableSingletonState(
                @NonNull final StateMetadata md, @NonNull final SingletonNode<?> s) {
            return new ReadableSingletonStateImpl<>(md, s);
        }
    }

    /**
     * An implementation of {@link WritableStates} based on the merkle tree.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    final class MerkleWritableStates extends MerkleStates implements WritableStates {
        /**
         * Create a new instance
         *
         * @param stateMetadata cannot be null
         */
        MerkleWritableStates(@NonNull final Map<String, StateMetadata<?, ?>> stateMetadata) {
            super(stateMetadata);
        }

        @NonNull
        @Override
        public <K extends Comparable<K>, V> WritableKVState<K, V> get(@NonNull String stateKey) {
            return (WritableKVState<K, V>) super.get(stateKey);
        }

        @NonNull
        @Override
        public <T> WritableSingletonState<T> getSingleton(@NonNull String stateKey) {
            return (WritableSingletonState<T>) super.getSingleton(stateKey);
        }

        @Override
        @NonNull
        protected WritableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final VirtualMap v) {
            return new OnDiskWritableKVState<>(md, v);
        }

        @Override
        @NonNull
        protected WritableKVState<?, ?> createReadableKVState(
                @NonNull final StateMetadata md, @NonNull final MerkleMap m) {
            return new InMemoryWritableKVState<>(md, m);
        }

        @Override
        @NonNull
        protected WritableSingletonState<?> createReadableSingletonState(
                @NonNull final StateMetadata md, @NonNull final SingletonNode<?> s) {
            return new WritableSingletonStateImpl<>(md, s);
        }

        void commit() {
            for (final ReadableKVState kv : kvInstances.values()) {
                ((WritableKVStateBase) kv).commit();
            }
            for (final ReadableSingletonState s : singletonInstances.values()) {
                ((WritableSingletonStateBase) s).commit();
            }
        }
    }
}
