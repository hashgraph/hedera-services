/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono;

import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk.INSERTIONS_PER_COPY;
import static com.hedera.node.app.service.mono.state.migration.StateVersions.CURRENT_VERSION;
import static com.hedera.node.app.service.mono.state.migration.StateVersions.MINIMUM_SUPPORTED_VERSION;
import static com.hedera.node.app.service.mono.state.migration.UniqueTokensMigrator.migrateFromUniqueTokenMerkleMap;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.parseAccount;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertyNames;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccountState;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactions;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.MapMigrationToDisk;
import com.hedera.node.app.service.mono.state.migration.RecordConsolidation;
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder;
import com.hedera.node.app.service.mono.state.migration.StateChildIndices;
import com.hedera.node.app.service.mono.state.migration.ToDiskMigrations;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.org.StateMetadata;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.platform.NodeId;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.merkledb.MerkleDb;
import com.swirlds.platform.gui.SwirldsGui;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Merkle tree root of the Hedera Services world state.
 */
public class ServicesState extends PartialNaryMerkleInternal
        implements MerkleInternal, SwirldState, StateChildrenProvider {
    private static final Logger log = LogManager.getLogger(ServicesState.class);

    private static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
    public static final ImmutableHash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

    // Only over-written when Platform deserializes a legacy version of the state
    private int deserializedStateVersion = CURRENT_VERSION;
    // All of the state that is not itself hashed or serialized, but only derived from such state
    private StateMetadata metadata;
    // Virtual map factory. If multiple states are in a single JVM, each has its own factory
    private VirtualMapFactory vmFactory = null;
    /* Set to true if virtual NFTs are enabled. */
    private boolean enabledVirtualNft;
    private boolean enableVirtualAccounts;
    private boolean enableVirtualTokenRels;
    private boolean consolidateRecordStorage;
    private Platform platform;
    private final BootstrapProperties bootstrapProperties;
    // Will only be used when the record storage strategy is IN_SINGLE_FCQ
    private @Nullable Map<EntityNum, Queue<ExpirableTxnRecord>> queryableRecords;

    public ServicesState() {
        // RuntimeConstructable
        bootstrapProperties = null;

        MerkleDb.resetDefaultInstancePath();
    }

    @VisibleForTesting
    ServicesState(final BootstrapProperties bootstrapProperties) {
        this.bootstrapProperties = bootstrapProperties;

        MerkleDb.resetDefaultInstancePath();
    }

    private ServicesState(final ServicesState that) {
        // Copy the Merkle route from the source instance
        super(that);
        // Copy the non-null Merkle children from the source
        for (int childIndex = 0, n = that.getNumberOfChildren(); childIndex < n; childIndex++) {
            final var childToCopy = that.getChild(childIndex);
            if (childToCopy != null) {
                setChild(childIndex, childToCopy.copy());
            }
        }
        // Copy the non-Merkle state from the source
        this.deserializedStateVersion = that.deserializedStateVersion;
        this.metadata = (that.metadata == null) ? null : that.metadata.copy();
        this.bootstrapProperties = that.bootstrapProperties;
        this.enabledVirtualNft = that.enabledVirtualNft;
        this.enableVirtualAccounts = that.enableVirtualAccounts;
        this.enableVirtualTokenRels = that.enableVirtualTokenRels;
        this.consolidateRecordStorage = that.consolidateRecordStorage;
        this.queryableRecords = that.queryableRecords;
        this.platform = that.platform;
    }

    /**
     * Log out the sizes the state children.
     */
    private void logStateChildrenSizes() {
        log.info(
                "  (@ {}) # NFTs               = {}",
                StateChildIndices.UNIQUE_TOKENS,
                uniqueTokens().size());
        log.info(
                "  (@ {}) # token associations = {}",
                StateChildIndices.TOKEN_ASSOCIATIONS,
                tokenAssociations().size());
        log.info("  (@ {}) # topics             = {}", StateChildIndices.TOPICS, topics().size());
        log.info(
                "  (@ {}) # blobs              = {}",
                StateChildIndices.STORAGE,
                storage().size());
        log.info(
                "  (@ {}) # accounts/contracts = {}",
                StateChildIndices.ACCOUNTS,
                accounts().size());
        log.info("  (@ {}) # tokens             = {}", StateChildIndices.TOKENS, tokens().size());
        log.info(
                "  (@ {}) # scheduled txns     = {}",
                StateChildIndices.SCHEDULE_TXS,
                scheduleTxs().getNumSchedules());
        log.info(
                "  (@ {}) # contract K/V pairs = {}",
                StateChildIndices.CONTRACT_STORAGE,
                contractStorage().size());
    }

    // --- MerkleInternal ---
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int getMinimumChildCount() {
        return StateChildIndices.NUM_025X_CHILDREN;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return MINIMUM_SUPPORTED_VERSION;
    }

    @Override
    public void addDeserializedChildren(final List<MerkleNode> children, final int version) {
        super.addDeserializedChildren(children, version);
        deserializedStateVersion = version;
    }

    @Override
    public MerkleNode migrate(final int version) {
        return MerkleInternal.super.migrate(version);
    }

    // --- SwirldState ---
    @Override
    public void init(
            final Platform platform,
            final PlatformState platformState,
            final InitTrigger trigger,
            final SoftwareVersion deserializedVersion) {
        // first store a reference to the platform
        this.platform = platform;

        if (trigger == GENESIS) {
            genesisInit(platform, platformState);
        } else {
            if (deserializedVersion == null) {
                throw new IllegalStateException(
                        "No software version for deserialized state version " + deserializedStateVersion);
            }

            // Make sure the initializing state uses our desired record storage strategy before
            // triggering the downstream flow that rebuilds auxiliary data structures
            final var bootstrapProps = getBootstrapProperties();
            consolidateRecordStorage = bootstrapProps.getBooleanProperty(PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ);
            if (consolidateRecordStorage) {
                queryableRecords = new HashMap<>();
            }
            if (recordConsolidationRequiresMigration()) {
                recordConsolidator.consolidateRecordsToSingleFcq(this);
            }

            // Note this returns the app in case we need to do something with it after making
            // final changes to state (e.g. after migrating something from memory to disk)
            deserializedInit(platform, platformState, trigger, deserializedVersion);
            final var isUpgrade = SEMANTIC_VERSIONS.deployedSoftwareVersion().isNonConfigUpgrade(deserializedVersion);
            if (isUpgrade) {
                migrateFrom(deserializedVersion);
            }

            // Because the flags below can be toggled without a software upgrade, we need to
            // check for migration regardless of versioning. This should be done after any
            // other migrations are complete.
            if (shouldMigrateNfts()) {
                migrateFromUniqueTokenMerkleMap(this);
            }
            if (shouldMigrateSomethingToDisk()) {
                mapToDiskMigration.migrateToDiskAsApropos(
                        INSERTIONS_PER_COPY,
                        consolidateRecordStorage,
                        this,
                        new ToDiskMigrations(enableVirtualAccounts, enableVirtualTokenRels),
                        getVirtualMapFactory(),
                        accountMigrator,
                        tokenRelMigrator);
            }
        }
    }

    @Override
    public void handleConsensusRound(final Round round, final PlatformState platformState) {
        throwIfImmutable();

        final var app = metadata.app();
        app.mapWarmer().warmCache(round);

        app.platformStateAccessor().setPlatformState(platformState);
        app.logic().incorporateConsensus(round);
    }

    @Override
    public void preHandle(final Event event) {
        metadata.app().eventExpansion().expandAllSigs(event, this);
    }

    @Override
    public AddressBook updateWeight(@NonNull AddressBook configAddressBook, @NonNull PlatformContext context) {
        throwIfImmutable();
        // Get all nodeIds added in the config.txt
        Set<NodeId> configNodeIds = configAddressBook.getNodeIdSet();
        stakingInfo().forEach((nodeNum, stakingInfo) -> {
            NodeId nodeId = new NodeId(nodeNum.longValue());
            // ste weight for the nodes that exist in state and remove from
            // nodes given in config.txt. This is needed to recognize newly added nodes
            configAddressBook.updateWeight(nodeId, stakingInfo.getWeight());
            configNodeIds.remove(nodeId);
        });
        // for any newly added nodes that doesn't exist in state, weight should be set to 0
        // irrespective of the weight provided in config.txt
        configNodeIds.forEach(nodeId -> configAddressBook.updateWeight(nodeId, 0));
        return configAddressBook;
    }

    private ServicesApp deserializedInit(
            final Platform platform,
            final PlatformState platformState,
            final InitTrigger trigger,
            @NonNull final SoftwareVersion deserializedVersion) {
        log.info("Init called on Services node {} WITH Merkle saved state", platform.getSelfId());

        final var bootstrapProps = getBootstrapProperties();
        enableVirtualAccounts = bootstrapProps.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK);
        enableVirtualTokenRels = bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK);
        enabledVirtualNft = bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE);
        return internalInit(platform, bootstrapProps, platformState, trigger, deserializedVersion);
    }

    private void genesisInit(final Platform platform, final PlatformState platformState) {
        log.info("Init called on Services node {} WITHOUT Merkle saved state", platform.getSelfId());

        // Create the top-level children in the Merkle tree
        final var bootstrapProps = getBootstrapProperties();
        final var seqStart = bootstrapProps.getLongProperty(PropertyNames.HEDERA_FIRST_USER_ENTITY);
        enableVirtualAccounts = bootstrapProps.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK);
        enableVirtualTokenRels = bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK);
        enabledVirtualNft = bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE);
        consolidateRecordStorage = bootstrapProps.getBooleanProperty(PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ);
        createGenesisChildren(platform.getAddressBook(), seqStart, bootstrapProps);
        internalInit(platform, bootstrapProps, platformState, GENESIS, null);
        networkCtx().markPostUpgradeScanStatus();
    }

    private ServicesApp internalInit(
            final Platform platform,
            final BootstrapProperties bootstrapProps,
            PlatformState platformState,
            final InitTrigger trigger,
            @Nullable final SoftwareVersion deserializedVersion) {
        this.platform = platform;
        final var selfId = platform.getSelfId();

        final ServicesApp app;
        if (APPS.includes(selfId)) {
            app = APPS.get(selfId);
        } else {
            final var nodeAddress = addressBook().getAddress(selfId);
            final var initialHash = runningHashLeaf().getRunningHash().getHash();
            app = appBuilder
                    .get()
                    .initTrigger(trigger)
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
        app.maybeNewRecoveredStateListener().ifPresent(listener -> platform.getNotificationEngine()
                .register(NewRecoveredStateListener.class, listener));

        if (platformState == null) {
            log.error("Platform state is null, this is highly unusual.");
            platformState = new PlatformState();
        }
        app.platformStateAccessor().setPlatformState(platformState);
        log.info(
                "Platform state includes freeze time={} and last frozen={}",
                platformState.getFreezeTime(),
                platformState.getLastFrozenTime());

        final var deployedVersion = SEMANTIC_VERSIONS.deployedSoftwareVersion();
        if (deployedVersion.isBefore(deserializedVersion)) {
            log.error(
                    "Fatal error, state source version {} is after node software version {}",
                    deserializedVersion,
                    deployedVersion);
            app.systemExits().fail(1);
        } else {
            final var isUpgrade = deployedVersion.isNonConfigUpgrade(deserializedVersion);
            if (trigger == RESTART) {
                // We may still want to change the address book without an upgrade. But note
                // that without a dynamic address book, this MUST be a no-op during reconnect.
                app.stakeStartupHelper().doRestartHousekeeping(addressBook(), stakingInfo());
                if (isUpgrade) {
                    networkCtx().discardPreparedUpgradeMeta();
                    if (deployedVersion.hasMigrationRecordsFrom(deserializedVersion)) {
                        networkCtx().markMigrationRecordsNotYetStreamed();
                    }
                }
            }
            networkCtx().setStateVersion(CURRENT_VERSION);

            metadata = new StateMetadata(app, new FCHashMap<>());
            // Log state before migration.
            logStateChildrenSizes();
            // This updates the working state accessor with our children
            app.initializationFlow().runWith(this, bootstrapProps);
            if (trigger == RESTART && isUpgrade) {
                app.stakeStartupHelper().doUpgradeHousekeeping(networkCtx(), accounts(), stakingInfo());
            }

            // Ensure the prefetch queue is created and thread pool is active instead of waiting
            // for lazy-initialization to take place
            app.prefetchProcessor();
            log.info("Created prefetch processor");

            logSummary();
            log.info("  --> Context initialized accordingly on Services node {}", selfId);

            if (trigger == GENESIS) {
                app.sysAccountsCreator()
                        .ensureSystemAccounts(
                                app.backingAccounts(), app.workingState().addressBook());
                app.sysFilesManager().createManagedFilesIfMissing();
                app.stakeStartupHelper().doGenesisHousekeeping(addressBook());
            }
        }
        return app;
    }

    /* --- FastCopyable --- */
    @Override
    public synchronized ServicesState copy() {
        setImmutable(true);

        final var that = new ServicesState(this);
        if (metadata != null) {
            metadata.app().workingState().updateFrom(that);
        }

        return that;
    }

    /* --- MerkleNode --- */
    @Override
    public synchronized void destroyNode() {
        if (metadata != null) {
            metadata.release();
        }
    }

    /* -- Getters and helpers -- */
    public AccountID getAccountFromNodeId(final NodeId nodeId) {
        final var address = addressBook().getAddress(nodeId);
        final var memo = address.getMemo();
        return parseAccount(memo);
    }

    public boolean isInitialized() {
        return metadata != null;
    }

    public Instant getTimeOfLastHandledTxn() {
        return networkCtx().consensusTimeOfLastHandledTxn();
    }

    public int getStateVersion() {
        return networkCtx().getStateVersion();
    }

    public void logSummary() {
        final String ctxSummary;
        if (metadata != null) {
            final var app = metadata.app();
            app.hashLogger().logHashesFor(this);
            ctxSummary = networkCtx().summarizedWith(app.platformStateAccessor());
        } else {
            ctxSummary = networkCtx().summarized();
        }
        log.info(ctxSummary);
    }

    public Map<ByteString, EntityNum> aliases() {
        requireNonNull(metadata, "Cannot get aliases from an uninitialized state");
        return metadata.aliases();
    }

    @SuppressWarnings("unchecked")
    public AccountStorageAdapter accounts() {
        final var accountsStorage = getChild(StateChildIndices.ACCOUNTS);
        return (accountsStorage instanceof VirtualMap)
                ? AccountStorageAdapter.fromOnDisk(
                        VirtualMapLike.from((VirtualMap<EntityNumVirtualKey, OnDiskAccount>) accountsStorage))
                : AccountStorageAdapter.fromInMemory(
                        MerkleMapLike.from((MerkleMap<EntityNum, MerkleAccount>) accountsStorage));
    }

    public VirtualMapLike<VirtualBlobKey, VirtualBlobValue> storage() {
        return VirtualMapLike.from(getChild(StateChildIndices.STORAGE));
    }

    public MerkleMapLike<EntityNum, MerkleTopic> topics() {
        return MerkleMapLike.from(getChild(StateChildIndices.TOPICS));
    }

    public MerkleMapLike<EntityNum, MerkleToken> tokens() {
        return MerkleMapLike.from(getChild(StateChildIndices.TOKENS));
    }

    @SuppressWarnings("unchecked")
    public TokenRelStorageAdapter tokenAssociations() {
        final var relsStorage = getChild(StateChildIndices.TOKEN_ASSOCIATIONS);
        return (relsStorage instanceof VirtualMap)
                ? TokenRelStorageAdapter.fromOnDisk(
                        VirtualMapLike.from((VirtualMap<EntityNumVirtualKey, OnDiskTokenRel>) relsStorage))
                : TokenRelStorageAdapter.fromInMemory((MerkleMap<EntityNumPair, MerkleTokenRelStatus>) relsStorage);
    }

    public MerkleScheduledTransactions scheduleTxs() {
        return getChild(StateChildIndices.SCHEDULE_TXS);
    }

    public MerkleNetworkContext networkCtx() {
        return getChild(StateChildIndices.NETWORK_CTX);
    }

    public AddressBook addressBook() {
        return platform.getAddressBook();
    }

    public MerkleSpecialFiles specialFiles() {
        return getChild(StateChildIndices.SPECIAL_FILES);
    }

    public RecordsRunningHashLeaf runningHashLeaf() {
        return getChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH);
    }

    @SuppressWarnings("unchecked")
    public UniqueTokenMapAdapter uniqueTokens() {
        final var tokensMap = getChild(StateChildIndices.UNIQUE_TOKENS);
        return tokensMap.getClass() == MerkleMap.class
                ? UniqueTokenMapAdapter.wrap((MerkleMap<EntityNumPair, MerkleUniqueToken>) tokensMap)
                : UniqueTokenMapAdapter.wrap(
                        VirtualMapLike.from((VirtualMap<UniqueTokenKey, UniqueTokenValue>) tokensMap));
    }

    public RecordsStorageAdapter payerRecords() {
        if (getNumberOfChildren() == StateChildIndices.NUM_032X_CHILDREN) {
            if (getChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ) instanceof MerkleMap) {
                return RecordsStorageAdapter.fromDedicated(
                        MerkleMapLike.from(getChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ)));
            } else {
                return RecordsStorageAdapter.fromConsolidated(
                        getChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ),
                        requireNonNull(queryableRecords));
            }
        } else {
            return RecordsStorageAdapter.fromLegacy(MerkleMapLike.from(getChild(StateChildIndices.ACCOUNTS)));
        }
    }

    public VirtualMapLike<ContractKey, IterableContractValue> contractStorage() {
        return VirtualMapLike.from(getChild(StateChildIndices.CONTRACT_STORAGE));
    }

    public MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfo() {
        return MerkleMapLike.from(getChild(StateChildIndices.STAKING_INFO));
    }

    int getDeserializedStateVersion() {
        return deserializedStateVersion;
    }

    void createGenesisChildren(
            final AddressBook addressBook, final long seqStart, final BootstrapProperties bootstrapProperties) {
        final VirtualMapFactory virtualMapFactory = getVirtualMapFactory();
        if (enabledVirtualNft) {
            setChild(StateChildIndices.UNIQUE_TOKENS, virtualMapFactory.newVirtualizedUniqueTokenStorage());
        } else {
            setChild(StateChildIndices.UNIQUE_TOKENS, new MerkleMap<>());
        }
        if (enableVirtualTokenRels) {
            setChild(StateChildIndices.TOKEN_ASSOCIATIONS, virtualMapFactory.newOnDiskTokenRels());
        } else {
            setChild(StateChildIndices.TOKEN_ASSOCIATIONS, new MerkleMap<>());
        }
        setChild(StateChildIndices.TOPICS, new MerkleMap<>());
        setChild(StateChildIndices.STORAGE, virtualMapFactory.newVirtualizedBlobs());
        if (enableVirtualAccounts) {
            setChild(StateChildIndices.ACCOUNTS, virtualMapFactory.newOnDiskAccountStorage());
        } else {
            setChild(StateChildIndices.ACCOUNTS, new MerkleMap<>());
        }
        setChild(StateChildIndices.TOKENS, new MerkleMap<>());
        setChild(StateChildIndices.NETWORK_CTX, genesisNetworkCtxWith(seqStart));
        setChild(StateChildIndices.SPECIAL_FILES, new MerkleSpecialFiles());
        setChild(StateChildIndices.SCHEDULE_TXS, new MerkleScheduledTransactions());
        setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, genesisRunningHashLeaf());
        setChild(StateChildIndices.CONTRACT_STORAGE, virtualMapFactory.newVirtualizedIterableStorage());
        setChild(
                StateChildIndices.STAKING_INFO,
                stakingInfoBuilder.buildStakingInfoMap(addressBook, bootstrapProperties));
        if (consolidateRecordStorage) {
            setChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ, new FCQueue<>());
            queryableRecords = new HashMap<>();
        } else if (enableVirtualAccounts) {
            setChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ, new MerkleMap<>());
        }
    }

    private RecordsRunningHashLeaf genesisRunningHashLeaf() {
        final var genesisRunningHash = new RunningHash();
        genesisRunningHash.setHash(EMPTY_HASH);
        return new RecordsRunningHashLeaf(genesisRunningHash);
    }

    private MerkleNetworkContext genesisNetworkCtxWith(final long seqStart) {
        return new MerkleNetworkContext(null, new SequenceNumber(seqStart), seqStart - 1, new ExchangeRates());
    }

    private static StakingInfoBuilder stakingInfoBuilder = StakingInfoMapBuilder::buildStakingInfoMap;
    private static Supplier<VirtualMapFactory> vmFactorySupplier = null; // for testing purposes
    private static Supplier<ServicesApp.Builder> appBuilder = DaggerServicesApp::builder;
    private static MapToDiskMigration mapToDiskMigration = MapMigrationToDisk::migrateToDiskAsApropos;
    private static RecordConsolidator recordConsolidator = RecordConsolidation::toSingleFcq;
    static final Function<MerkleAccountState, OnDiskAccount> accountMigrator = OnDiskAccount::from;
    static final Function<MerkleTokenRelStatus, OnDiskTokenRel> tokenRelMigrator = OnDiskTokenRel::from;

    @VisibleForTesting
    void migrateFrom(@NonNull final SoftwareVersion deserializedVersion) {
        // Keep the MutableStateChildren up-to-date (no harm done if they are already are)
        final var app = getMetadata().app();
        app.workingState().updatePrimitiveChildrenFrom(this);
        log.info("Finished migrations needed for deserialized version {}", deserializedVersion);
        logStateChildrenSizes();
        networkCtx().markPostUpgradeScanStatus();
    }

    boolean shouldMigrateNfts() {
        return enabledVirtualNft && !uniqueTokens().isVirtual();
    }

    boolean shouldMigrateSomethingToDisk() {
        return shouldMigrateAccountsToDisk() || shouldMigrateTokenRelsToDisk();
    }

    boolean shouldMigrateAccountsToDisk() {
        return enableVirtualAccounts && getNumberOfChildren() < StateChildIndices.NUM_032X_CHILDREN;
    }

    boolean shouldMigrateTokenRelsToDisk() {
        return enableVirtualTokenRels && getChild(StateChildIndices.TOKEN_ASSOCIATIONS) instanceof MerkleMap<?, ?>;
    }

    boolean recordConsolidationRequiresMigration() {
        return consolidateRecordStorage
                && (getNumberOfChildren() < StateChildIndices.NUM_032X_CHILDREN
                        || getChild(StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ) instanceof MerkleMap<?, ?>);
    }

    private BootstrapProperties getBootstrapProperties() {
        return bootstrapProperties == null ? new BootstrapProperties() : bootstrapProperties;
    }

    @FunctionalInterface
    interface StakingInfoBuilder {

        MerkleMap<EntityNum, MerkleStakingInfo> buildStakingInfoMap(
                AddressBook addressBook, BootstrapProperties bootstrapProperties);
    }

    @FunctionalInterface
    interface MapToDiskMigration {

        void migrateToDiskAsApropos(
                int insertionsPerCopy,
                boolean useConsolidatedFcq,
                ServicesState mutableState,
                ToDiskMigrations toDiskMigrations,
                VirtualMapFactory virtualMapFactory,
                Function<MerkleAccountState, OnDiskAccount> accountMigrator,
                Function<MerkleTokenRelStatus, OnDiskTokenRel> tokenRelMigrator);
    }

    @FunctionalInterface
    interface RecordConsolidator {
        void consolidateRecordsToSingleFcq(@NonNull ServicesState mutableState);
    }

    @VisibleForTesting
    StateMetadata getMetadata() {
        return metadata;
    }

    @VisibleForTesting
    void setMetadata(final StateMetadata metadata) {
        this.metadata = metadata;
    }

    @VisibleForTesting
    void setPlatform(final Platform platform) {
        this.platform = platform;
    }

    @VisibleForTesting
    void setDeserializedStateVersion(final int deserializedStateVersion) {
        this.deserializedStateVersion = deserializedStateVersion;
    }

    @VisibleForTesting
    static void setAppBuilder(final Supplier<ServicesApp.Builder> appBuilder) {
        ServicesState.appBuilder = appBuilder;
    }

    @VisibleForTesting
    static void setStakingInfoBuilder(final StakingInfoBuilder stakingInfoBuilder) {
        ServicesState.stakingInfoBuilder = stakingInfoBuilder;
    }

    private VirtualMapFactory getVirtualMapFactory() {
        if (vmFactorySupplier != null) {
            return vmFactorySupplier.get();
        }
        if (vmFactory == null) {
            vmFactory = new VirtualMapFactory();
        }
        return vmFactory;
    }

    @VisibleForTesting
    public static void setVmFactory(final Supplier<VirtualMapFactory> vmFactorySupplier) {
        ServicesState.vmFactorySupplier = vmFactorySupplier;
    }

    @VisibleForTesting
    public static void setMapToDiskMigration(final MapToDiskMigration mapToDiskMigration) {
        ServicesState.mapToDiskMigration = mapToDiskMigration;
    }

    @VisibleForTesting
    public static void setRecordConsolidator(@NonNull final RecordConsolidator recordConsolidator) {
        ServicesState.recordConsolidator = recordConsolidator;
    }
}
