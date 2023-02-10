/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import static com.swirlds.common.system.InitTrigger.GENESIS;
import static com.swirlds.common.system.InitTrigger.RECONNECT;
import static com.swirlds.common.system.InitTrigger.RESTART;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.PropertyNames;
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
import com.hedera.node.app.service.mono.state.migration.RecordsStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder;
import com.hedera.node.app.service.mono.state.migration.StateChildIndices;
import com.hedera.node.app.service.mono.state.migration.ToDiskMigrations;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.state.migration.VirtualMapDataAccess;
import com.hedera.node.app.service.mono.state.org.StateMetadata;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
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
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.threading.manager.AdHocThreadManager;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.jasperdb.VirtualDataSourceJasperDB;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.gui.SwirldsGui;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** The Merkle tree root of the Hedera Services world state. */
public class ServicesState extends PartialNaryMerkleInternal
        implements MerkleInternal, SwirldState2 {
    private static final VirtualMapDataAccess VIRTUAL_MAP_DATA_ACCESS =
            VirtualMapMigration::extractVirtualMapData;
    private static final Logger log = LogManager.getLogger(ServicesState.class);

    private static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
    public static final ImmutableHash EMPTY_HASH =
            new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

    // Only over-written when Platform deserializes a legacy version of the state
    private int deserializedStateVersion = CURRENT_VERSION;
    // All of the state that is not itself hashed or serialized, but only derived from such state
    private StateMetadata metadata;
    /* Set to true if virtual NFTs are enabled. */
    private boolean enabledVirtualNft;
    private boolean enableVirtualAccounts;
    private boolean enableVirtualTokenRels;
    private Platform platform;
    private final BootstrapProperties bootstrapProperties;

    public ServicesState() {
        // RuntimeConstructable
        bootstrapProperties = null;
    }

    @VisibleForTesting
    ServicesState(final BootstrapProperties bootstrapProperties) {
        this.bootstrapProperties = bootstrapProperties;
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
        this.enableVirtualAccounts = that.enableVirtualAccounts;
        this.enableVirtualTokenRels = that.enableVirtualTokenRels;
        this.platform = that.platform;
    }

    /** Log out the sizes the state children. */
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
        log.info("  (@ {}) # blobs              = {}", StateChildIndices.STORAGE, storage().size());
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
        final boolean enabledJasperdbToMerkleDb =
                getBootstrapProperties()
                        .getBooleanProperty(PropertyNames.VIRTUALDATASOURCE_JASPERDB_TO_MERKLEDB);
        if (enabledJasperdbToMerkleDb) {
            migrateVirtualMapsToMerkleDb(this);
        }
        return MerkleInternal.super.migrate(version);
    }

    // --- SwirldState ---
    @Override
    public void init(
            final Platform platform,
            final SwirldDualState dualState,
            final InitTrigger trigger,
            final SoftwareVersion deserializedVersion) {
        // first store a reference to the platform
        this.platform = platform;

        if (trigger == GENESIS) {
            genesisInit(platform, dualState);
        } else {
            if (deserializedVersion == null) {
                throw new IllegalStateException(
                        "No software version for deserialized state version "
                                + deserializedStateVersion);
            }
            // Note this returns the app in case we need to do something with it after making
            // final changes to state (e.g. after migrating something from memory to disk)
            deserializedInit(platform, dualState, trigger, deserializedVersion);
            final var isUpgrade =
                    SEMANTIC_VERSIONS.deployedSoftwareVersion().isAfter(deserializedVersion);
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
                        this,
                        new ToDiskMigrations(enableVirtualAccounts, enableVirtualTokenRels),
                        vmFactory.get(),
                        accountMigrator,
                        tokenRelMigrator);
            }
        }
    }

    @Override
    public void handleConsensusRound(final Round round, final SwirldDualState dualState) {
        throwIfImmutable();
        final var app = metadata.app();
        app.dualStateAccessor().setDualState(dualState);
        app.logic().incorporateConsensus(round);
    }

    @Override
    public void preHandle(final Event event) {
        metadata.app().eventExpansion().expandAllSigs(event, this);
    }

    private ServicesApp deserializedInit(
            final Platform platform,
            final SwirldDualState dualState,
            final InitTrigger trigger,
            @NonNull final SoftwareVersion deserializedVersion) {
        log.info("Init called on Services node {} WITH Merkle saved state", platform.getSelfId());

        final var bootstrapProps = getBootstrapProperties();
        enableVirtualAccounts =
                bootstrapProps.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK);
        enableVirtualTokenRels =
                bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK);
        enabledVirtualNft =
                bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE);
        return internalInit(platform, bootstrapProps, dualState, trigger, deserializedVersion);
    }

    private void genesisInit(final Platform platform, final SwirldDualState dualState) {
        log.info(
                "Init called on Services node {} WITHOUT Merkle saved state", platform.getSelfId());

        // Create the top-level children in the Merkle tree
        final var bootstrapProps = getBootstrapProperties();
        final var seqStart = bootstrapProps.getLongProperty(PropertyNames.HEDERA_FIRST_USER_ENTITY);
        enableVirtualAccounts =
                bootstrapProps.getBooleanProperty(PropertyNames.ACCOUNTS_STORE_ON_DISK);
        enableVirtualTokenRels =
                bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_STORE_RELS_ON_DISK);
        enabledVirtualNft =
                bootstrapProps.getBooleanProperty(PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE);
        createGenesisChildren(platform.getAddressBook(), seqStart, bootstrapProps);

        internalInit(platform, bootstrapProps, dualState, GENESIS, null);
        networkCtx().markPostUpgradeScanStatus();
    }

    private ServicesApp internalInit(
            final Platform platform,
            final BootstrapProperties bootstrapProps,
            SwirldDualState dualState,
            final InitTrigger trigger,
            @Nullable final SoftwareVersion deserializedVersion) {
        this.platform = platform;
        final var selfId = platform.getSelfId().getId();

        final ServicesApp app;
        if (APPS.includes(selfId)) {
            app = APPS.get(selfId);
        } else {
            final var nodeAddress = addressBook().getAddress(selfId);
            final var initialHash = runningHashLeaf().getRunningHash().getHash();
            app =
                    appBuilder
                            .get()
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

            metadata = new StateMetadata(app, new FCHashMap<>());
            // Log state before migration.
            logStateChildrenSizes();
            // This updates the working state accessor with our children
            app.initializationFlow().runWith(this, bootstrapProps);
            if (trigger == RESTART && isUpgrade) {
                app.stakeStartupHelper()
                        .doUpgradeHousekeeping(networkCtx(), accounts(), stakingInfo());
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
            if (trigger != RECONNECT) {
                // Once we have a dynamic address book, this will run unconditionally
                app.sysFilesManager().updateStakeDetails();
            }
        }
        return app;
    }

    /* --- FastCopyable --- */
    @Override
    public synchronized ServicesState copy() {
        setImmutable(true);

        final var that = new ServicesState(this);
        this.platform = that.platform;
        if (metadata != null) {
            metadata.app().workingState().updateFrom(that);
        }

        return that;
    }

    /* --- Archivable --- */
    @Override
    public synchronized void archive() {
        if (metadata != null) {
            metadata.release();
        }

        topics().archive();
        tokens().archive();
        accounts().archive();
        uniqueTokens().archive();
        tokenAssociations().archive();
        stakingInfo().archive();
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
        final var address = addressBook().getAddress(nodeId.getId());
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
            ctxSummary = networkCtx().summarizedWith(app.dualStateAccessor());
        } else {
            ctxSummary = networkCtx().summarized();
        }
        log.info(ctxSummary);
    }

    public Map<ByteString, EntityNum> aliases() {
        Objects.requireNonNull(metadata, "Cannot get aliases from an uninitialized state");
        return metadata.aliases();
    }

    @SuppressWarnings("unchecked")
    public AccountStorageAdapter accounts() {
        final var accountsStorage = getChild(StateChildIndices.ACCOUNTS);
        return (accountsStorage instanceof VirtualMap)
                ? AccountStorageAdapter.fromOnDisk(
                        VIRTUAL_MAP_DATA_ACCESS,
                        getChild(StateChildIndices.PAYER_RECORDS),
                        (VirtualMap<EntityNumVirtualKey, OnDiskAccount>) accountsStorage)
                : AccountStorageAdapter.fromInMemory(
                        (MerkleMap<EntityNum, MerkleAccount>) accountsStorage);
    }

    public VirtualMap<VirtualBlobKey, VirtualBlobValue> storage() {
        return getChild(StateChildIndices.STORAGE);
    }

    public MerkleMap<EntityNum, MerkleTopic> topics() {
        return getChild(StateChildIndices.TOPICS);
    }

    public MerkleMap<EntityNum, MerkleToken> tokens() {
        return getChild(StateChildIndices.TOKENS);
    }

    @SuppressWarnings("unchecked")
    public TokenRelStorageAdapter tokenAssociations() {
        final var relsStorage = getChild(StateChildIndices.TOKEN_ASSOCIATIONS);
        return (relsStorage instanceof VirtualMap)
                ? TokenRelStorageAdapter.fromOnDisk(
                        (VirtualMap<EntityNumVirtualKey, OnDiskTokenRel>) relsStorage)
                : TokenRelStorageAdapter.fromInMemory(
                        (MerkleMap<EntityNumPair, MerkleTokenRelStatus>) relsStorage);
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
                ? UniqueTokenMapAdapter.wrap(
                        (MerkleMap<EntityNumPair, MerkleUniqueToken>) tokensMap)
                : UniqueTokenMapAdapter.wrap(
                        (VirtualMap<UniqueTokenKey, UniqueTokenValue>) tokensMap);
    }

    public RecordsStorageAdapter payerRecords() {
        return getNumberOfChildren() == StateChildIndices.NUM_032X_CHILDREN
                ? RecordsStorageAdapter.fromDedicated(getChild(StateChildIndices.PAYER_RECORDS))
                : RecordsStorageAdapter.fromLegacy(getChild(StateChildIndices.ACCOUNTS));
    }

    public VirtualMap<ContractKey, IterableContractValue> contractStorage() {
        return getChild(StateChildIndices.CONTRACT_STORAGE);
    }

    record NftStats(
            Map<EntityNum, Integer> totalOwned,
            Map<EntityNum, Integer> totalSupply,
            Map<EntityNumPair, Integer> totalOwnedByType) {}

    static NftStats countByOwnershipIn(
            final UniqueTokenMapAdapter uniqueTokens,
            final TokenRelStorageAdapter associations,
            final MerkleMap<EntityNum, MerkleToken> tokens) {
        final var stats = new NftStats(new HashMap<>(), new HashMap<>(), new HashMap<>());
        Objects.requireNonNull(uniqueTokens.getMerkleMap())
                .forEach(
                        (key, nft) -> {
                            var owner = nft.getOwner();
                            final var token = tokens.get(key.getHiOrderAsNum());
                            if (token == null) {
                                log.warn(
                                        "Failed to find token 0.0.{} for NFT serial no {}",
                                        key.getHiOrderAsLong(),
                                        key.getLowOrderAsLong());
                                return;
                            }
                            if (owner.num() == 0L) {
                                owner = token.treasury();
                            }
                            if (token.isDeleted()) {
                                // We only want to count ownership of NFT's from deleted
                                // tokens *IF* they are still associated to the owner account
                                final var association =
                                        associations.get(
                                                EntityNumPair.fromLongs(
                                                        owner.num(), key.getHiOrderAsLong()));
                                if (association == null) {
                                    // Owner isn't associated, so we don't count this NFT
                                    return;
                                }
                            }
                            stats.totalOwned().merge(owner.asNum(), 1, Integer::sum);
                            final var tokenNum = key.getHiOrderAsNum();
                            final var numPair =
                                    EntityNumPair.fromLongs(owner.num(), tokenNum.longValue());
                            stats.totalOwnedByType().merge(numPair, 1, Integer::sum);
                            if (!token.isDeleted()) {
                                stats.totalSupply().merge(tokenNum, 1, Integer::sum);
                            }
                        });
        return stats;
    }

    public MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo() {
        return getChild(StateChildIndices.STAKING_INFO);
    }

    int getDeserializedStateVersion() {
        return deserializedStateVersion;
    }

    void createGenesisChildren(
            final AddressBook addressBook,
            final long seqStart,
            final BootstrapProperties bootstrapProperties) {
        final VirtualMapFactory virtualMapFactory = vmFactory.get();
        if (enabledVirtualNft) {
            setChild(
                    StateChildIndices.UNIQUE_TOKENS,
                    virtualMapFactory.newVirtualizedUniqueTokenStorage());
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
        //        setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
        setChild(
                StateChildIndices.CONTRACT_STORAGE,
                virtualMapFactory.newVirtualizedIterableStorage());
        setChild(
                StateChildIndices.STAKING_INFO,
                stakingInfoBuilder.buildStakingInfoMap(addressBook, bootstrapProperties));
        if (enableVirtualAccounts) {
            setChild(StateChildIndices.PAYER_RECORDS, new MerkleMap<>());
        }
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

    private static StakingInfoBuilder stakingInfoBuilder =
            StakingInfoMapBuilder::buildStakingInfoMap;
    private static Supplier<VirtualMapFactory> vmFactory = VirtualMapFactory::new;
    private static Supplier<ServicesApp.Builder> appBuilder = DaggerServicesApp::builder;
    private static MapToDiskMigration mapToDiskMigration =
            MapMigrationToDisk::migrateToDiskAsApropos;
    static final Function<MerkleAccountState, OnDiskAccount> accountMigrator = OnDiskAccount::from;
    static final Function<MerkleTokenRelStatus, OnDiskTokenRel> tokenRelMigrator =
            OnDiskTokenRel::from;

    @VisibleForTesting
    void migrateFrom(@NonNull final SoftwareVersion deserializedVersion) {
        // Keep the MutableStateChildren up-to-date (no harm done if they are already are)
        final var app = getMetadata().app();
        app.workingState().updatePrimitiveChildrenFrom(this);
        log.info("Finished migrations needed for deserialized version {}", deserializedVersion);
        logStateChildrenSizes();
        final var nftStats = countByOwnershipIn(uniqueTokens(), tokenAssociations(), tokens());
        logNftStats(nftStats, accounts(), tokenAssociations(), tokens());
        networkCtx().markPostUpgradeScanStatus();
    }

    static void logNftStats(
            final NftStats stats,
            final AccountStorageAdapter accounts,
            final TokenRelStorageAdapter associations,
            final MerkleMap<EntityNum, MerkleToken> tokens) {
        stats.totalOwned().keySet().stream()
                .sorted()
                .forEach(
                        accountNum -> {
                            final var account = accounts.get(accountNum);
                            final var totalOwned = stats.totalOwned().get(accountNum);
                            if (account != null) {
                                if (account.getNftsOwned() != totalOwned) {
                                    log.info(
                                            "Account 0.0.{} owns {} NFTs (was: {})",
                                            accountNum.longValue(),
                                            totalOwned,
                                            account.getNftsOwned());
                                    accounts.getForModify(accountNum).setNftsOwned(totalOwned);
                                }
                            } else {
                                log.warn(
                                        "Missing account 0.0.{} owns {} NFTs",
                                        accountNum.longValue(),
                                        totalOwned);
                            }
                        });
        stats.totalSupply().keySet().stream()
                .sorted()
                .forEach(
                        tokenNum -> {
                            final var token = tokens.get(tokenNum);
                            final var totalSupply = stats.totalSupply().get(tokenNum);
                            if (token != null) {
                                if (token.totalSupply() != totalSupply) {
                                    log.info(
                                            "Token 0.0.{} has {} total supply (was: {})",
                                            tokenNum.longValue(),
                                            totalSupply,
                                            token.totalSupply());
                                    tokens.getForModify(tokenNum).setTotalSupply(totalSupply);
                                }
                            } else {
                                log.warn(
                                        "Missing token 0.0.{} has total supply {}",
                                        tokenNum.longValue(),
                                        totalSupply);
                            }
                        });
        stats.totalOwnedByType().keySet().stream()
                .sorted()
                .forEach(
                        accountTokenNums -> {
                            final var association = associations.get(accountTokenNums);
                            final var totalOwnedByType =
                                    stats.totalOwnedByType().get(accountTokenNums);
                            if (association != null) {
                                if (association.getBalance() != totalOwnedByType) {
                                    log.info(
                                            "Association 0.0.{}-0.0.{} has balance {} (was: {})",
                                            accountTokenNums.getHiOrderAsLong(),
                                            accountTokenNums.getLowOrderAsLong(),
                                            totalOwnedByType,
                                            association.getBalance());
                                    associations
                                            .getForModify(accountTokenNums)
                                            .setBalance(totalOwnedByType);
                                }
                            } else {
                                log.warn(
                                        "Missing association 0.0.{}-0.0.{} has balance {}",
                                        accountTokenNums.getHiOrderAsLong(),
                                        accountTokenNums.getLowOrderAsLong(),
                                        totalOwnedByType);
                            }
                        });
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
        return enableVirtualTokenRels
                && getChild(StateChildIndices.TOKEN_ASSOCIATIONS) instanceof MerkleMap<?, ?>;
    }

    private static void migrateVirtualMapsToMerkleDb(final ServicesState state) {
        final VirtualMapFactory virtualMapFactory = vmFactory.get();

        // virtualized blobs
        final VirtualMap<VirtualBlobKey, VirtualBlobValue> storageMap =
                state.getChild(StateChildIndices.STORAGE);
        if (jasperDbBacked(storageMap)) {
            VirtualMap<VirtualBlobKey, VirtualBlobValue> merkleDbBackedMap =
                    virtualMapFactory.newVirtualizedBlobs();
            merkleDbBackedMap = migrateVirtualMap(storageMap, merkleDbBackedMap);
            state.setChild(StateChildIndices.STORAGE, merkleDbBackedMap);
        }

        // virtualized iterable storage
        final VirtualMap<ContractKey, IterableContractValue> contractStorageMap =
                state.getChild(StateChildIndices.CONTRACT_STORAGE);
        if (jasperDbBacked(contractStorageMap)) {
            VirtualMap<ContractKey, IterableContractValue> merkleDbBackedMap =
                    virtualMapFactory.newVirtualizedIterableStorage();
            merkleDbBackedMap = migrateVirtualMap(contractStorageMap, merkleDbBackedMap);
            state.setChild(StateChildIndices.CONTRACT_STORAGE, merkleDbBackedMap);
        }

        // virtualized accounts, if enabled
        if (state.enableVirtualAccounts) {
            final VirtualMap<EntityNumVirtualKey, OnDiskAccount> accountsMap =
                    state.getChild(StateChildIndices.ACCOUNTS);
            if (jasperDbBacked(accountsMap)) {
                VirtualMap<EntityNumVirtualKey, OnDiskAccount> merkleDbBackedMap =
                        virtualMapFactory.newOnDiskAccountStorage();
                merkleDbBackedMap = migrateVirtualMap(accountsMap, merkleDbBackedMap);
                state.setChild(StateChildIndices.ACCOUNTS, merkleDbBackedMap);
            }
        }

        // virtualized token associations, if enabled
        if (state.enableVirtualTokenRels) {
            final VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> tokenAssociationsMap =
                    state.getChild(StateChildIndices.TOKEN_ASSOCIATIONS);
            if (jasperDbBacked(tokenAssociationsMap)) {
                VirtualMap<EntityNumVirtualKey, OnDiskTokenRel> merkleDbBackedMap =
                        virtualMapFactory.newOnDiskTokenRels();
                merkleDbBackedMap = migrateVirtualMap(tokenAssociationsMap, merkleDbBackedMap);
                state.setChild(StateChildIndices.TOKEN_ASSOCIATIONS, merkleDbBackedMap);
            }
        }

        // virtualized unique token storage, if enabled
        if (state.enabledVirtualNft) {
            final VirtualMap<UniqueTokenKey, UniqueTokenValue> uniqueTokensMap =
                    state.getChild(StateChildIndices.UNIQUE_TOKENS);
            if (jasperDbBacked(uniqueTokensMap)) {
                VirtualMap<UniqueTokenKey, UniqueTokenValue> merkleDbBackedMap =
                        virtualMapFactory.newVirtualizedUniqueTokenStorage();
                merkleDbBackedMap = migrateVirtualMap(uniqueTokensMap, merkleDbBackedMap);
                state.setChild(StateChildIndices.UNIQUE_TOKENS, merkleDbBackedMap);
            }
        }
    }

    private static boolean jasperDbBacked(final VirtualMap<?, ?> map) {
        final VirtualRootNode<?, ?> virtualRootNode = map.getRight();
        return virtualRootNode.getDataSource() instanceof VirtualDataSourceJasperDB;
    }

    private static <K extends VirtualKey<? super K>, V extends VirtualValue>
            VirtualMap<K, V> migrateVirtualMap(
                    final VirtualMap<K, V> source, final VirtualMap<K, V> target) {
        final int copyTargetMapEveryPuts = 10_000;
        final AtomicInteger count = new AtomicInteger(copyTargetMapEveryPuts);
        final AtomicReference<VirtualMap<K, V>> targetMapRef = new AtomicReference<>(target);
        MiscUtils.withLoggedDuration(
                () -> {
                    try {
                        VirtualMapMigration.extractVirtualMapData(
                                AdHocThreadManager.getStaticThreadManager(),
                                source,
                                kvPair -> {
                                    final K key = kvPair.getKey();
                                    final V value = kvPair.getValue();
                                    final VirtualMap<K, V> curCopy = targetMapRef.get();
                                    curCopy.put(key, value);
                                    // Make a map copy every X rounds to flush map cache to disk
                                    if (count.decrementAndGet() == 0) {
                                        targetMapRef.set(curCopy.copy());
                                        curCopy.release();
                                        count.set(copyTargetMapEveryPuts);
                                    }
                                },
                                4);
                    } catch (final InterruptedException z) {
                        log.error("Interrupted VirtualMap migration", z);
                        throw new RuntimeException(z);
                    }
                },
                log,
                "VirtualMap migration: " + source.getLabel());
        return targetMapRef.get();
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
                final int insertionsPerCopy,
                final ServicesState mutableState,
                final ToDiskMigrations toDiskMigrations,
                final VirtualMapFactory virtualMapFactory,
                final Function<MerkleAccountState, OnDiskAccount> accountMigrator,
                final Function<MerkleTokenRelStatus, OnDiskTokenRel> tokenRelMigrator);
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

    @VisibleForTesting
    public static void setVmFactory(final Supplier<VirtualMapFactory> vmFactory) {
        ServicesState.vmFactory = vmFactory;
    }

    @VisibleForTesting
    public static void setMapToDiskMigration(final MapToDiskMigration mapToDiskMigration) {
        ServicesState.mapToDiskMigration = mapToDiskMigration;
    }
}
