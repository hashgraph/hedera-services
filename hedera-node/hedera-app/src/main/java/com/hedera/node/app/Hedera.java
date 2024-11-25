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

package com.hedera.node.app;

import static com.hedera.hapi.block.stream.output.StateIdentifier.STATE_ID_BLOCK_STREAM_INFO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static com.hedera.node.app.blocks.impl.BlockImplUtils.combine;
import static com.hedera.node.app.blocks.impl.ConcurrentStreamingTreeHasher.rootHashFrom;
import static com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema.BLOCK_STREAM_INFO_KEY;
import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.records.impl.BlockRecordInfoUtils.blockHashByBlockNumber;
import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.spi.workflows.record.StreamBuilder.nodeTransactionWith;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static com.hedera.node.app.statedumpers.DumpCheckpoint.MOD_POST_EVENT_STREAM_REPLAY;
import static com.hedera.node.app.statedumpers.DumpCheckpoint.selectedDumpCheckpoints;
import static com.hedera.node.app.statedumpers.StateDumper.dumpModChildrenFrom;
import static com.hedera.node.app.util.HederaAsciiArt.HEDERA;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.STARTING_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.SingletonUpdateChange;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockstream.BlockStreamInfo;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.blocks.BlockStreamManager;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.InitialStateHash;
import com.hedera.node.app.blocks.StreamingTreeHasher;
import com.hedera.node.app.blocks.impl.BlockStreamManagerImpl;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.CurrentPlatformStatusImpl;
import com.hedera.node.app.info.GenesisNetworkInfo;
import com.hedera.node.app.info.StateNetworkInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.MerkleStateLifecyclesImpl;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.MerkleStateChild;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.AppThrottleFactory;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.Utils;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.ReadableRosterStoreImpl;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.state.notifications.StateHashedListener;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.spi.WritableSingletonStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/*
 ****************        ****************************************************************************************
 ************                ************                                                                       *
 *********                      *********                                                                       *
 ******                            ******                                                                       *
 ****                                ****      ___           ___           ___           ___           ___      *
 ***        ĦĦĦĦ          ĦĦĦĦ        ***     /\  \         /\  \         /\  \         /\  \         /\  \     *
 **         ĦĦĦĦ          ĦĦĦĦ         **    /::\  \       /::\  \       /::\  \       /::\  \       /::\  \    *
 *          ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ          *   /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \   *
            ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ             /::\~\:\  \   /:/  \:\__\   /::\~\:\  \   /::\~\:\  \   /::\~\:\  \  *
            ĦĦĦĦ          ĦĦĦĦ            /:/\:\ \:\__\ /:/__/ \:|__| /:/\:\ \:\__\ /:/\:\ \:\__\ /:/\:\ \:\__\ *
            ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ            \:\~\:\ \/__/ \:\  \ /:/  / \:\~\:\ \/__/ \/_|::\/:/  / \/__\:\/:/  / *
 *          ĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦĦ          *  \:\ \:\__\    \:\  /:/  /   \:\ \:\__\      |:|::/  /       \::/  /  *
 **         ĦĦĦĦ          ĦĦĦĦ         **   \:\ \/__/     \:\/:/  /     \:\ \/__/      |:|\/__/        /:/  /   *
 ***        ĦĦĦĦ          ĦĦĦĦ        ***    \:\__\        \::/__/       \:\__\        |:|  |         /:/  /    *
 ****                                ****     \/__/         ~~            \/__/         \|__|         \/__/     *
 ******                            ******                                                                       *
 *********                      *********                                                                       *
 ************                ************                                                                       *
 ****************        ****************************************************************************************
*/

/**
 * Represents the Hedera Consensus Node.
 *
 * <p>This is the main entry point for the Hedera Consensus Node. It contains initialization logic for the node,
 * including its state. It constructs the Dagger dependency tree, and manages the gRPC server, and in all other ways,
 * controls execution of the node. If you want to understand our system, this is a great place to start!
 */
public final class Hedera implements SwirldMain, PlatformStatusChangeListener, AppContext.Gossip {
    private static final Logger logger = LogManager.getLogger(Hedera.class);

    // FUTURE: This should come from configuration, not be hardcoded.
    public static final int MAX_SIGNED_TXN_SIZE = 6144;

    /**
     * The application name from the platform's perspective. This is currently locked in at the old main class name and
     * requires data migration to change.
     */
    public static final String APP_NAME = "com.hedera.services.ServicesMain";

    /**
     * The swirld name. Currently, there is only one swirld.
     */
    public static final String SWIRLD_NAME = "123";
    /**
     * The registry to use.
     */
    private final ServicesRegistry servicesRegistry;
    /**
     * The services migrator to use.
     */
    private final ServiceMigrator serviceMigrator;
    /**
     * The current version of the software; it is not possible for a node's version to change
     * without restarting the process, so final.
     */
    private final ServicesSoftwareVersion version;
    /**
     * The current version of the HAPI protobufs.
     */
    private final SemanticVersion hapiVersion;

    /**
     * The application context for the node.
     */
    private final AppContext appContext;

    /**
     * The contract service singleton, kept as a field here to avoid constructing twice
     * (once in constructor to register schemas, again inside Dagger component).
     */
    private final ContractServiceImpl contractServiceImpl;

    /**
     * The schedule service singleton, kept as a field here to avoid constructing twice
     * (once in constructor to register schemas, again inside Dagger component).
     */
    private final ScheduleServiceImpl scheduleServiceImpl;

    /**
     * The TSS base service singleton, kept as a field here to avoid constructing twice
     * (once in constructor to register schemas, again inside Dagger component).
     */
    private final TssBaseService tssBaseService;

    /**
     * The file service singleton, kept as a field here to avoid constructing twice
     * (once in constructor to register schemas, again inside Dagger component).
     */
    private final FileServiceImpl fileServiceImpl;

    /**
     * The block stream service singleton, kept as a field here to reuse information learned
     * during the state migration phase in the later initialization phase.
     */
    private final BlockStreamService blockStreamService;

    /**
     * The bootstrap configuration provider for the network.
     */
    private final BootstrapConfigProviderImpl bootstrapConfigProvider;

    /**
     * The stream mode the node is operating in.
     */
    private final StreamMode streamMode;

    /**
     * The factory for the startup networks.
     */
    private final StartupNetworksFactory startupNetworksFactory;

    /**
     * The id of this node.
     */
    private final NodeId selfNodeId;

    /**
     * The Hashgraph Platform. This is set during state initialization.
     */
    private Platform platform;
    /**
     * The current status of the platform.
     */
    private PlatformStatus platformStatus = STARTING_UP;
    /**
     * The configuration for this node; non-final because its sources depend on whether
     * we are initializing the first consensus state from genesis or a saved state.
     */
    private ConfigProviderImpl configProvider;
    /**
     * DI for all objects needed to implement Hedera node lifecycles; non-final because
     * it is completely recreated every time the platform initializes a new state as the
     * basis for applying consensus transactions.
     */
    private HederaInjectionComponent daggerApp;

    /**
     * The metrics object being used for reporting.
     */
    private Metrics metrics;

    /**
     * A {@link StateChangeListener} that accumulates state changes that are only reported once per block; in the
     * current system, these are the singleton and queue updates. Every {@link MerkleStateRoot} will have this
     * listener registered.
     */
    private final BoundaryStateChangeListener boundaryStateChangeListener = new BoundaryStateChangeListener();

    /**
     * A {@link StateChangeListener} that accumulates state changes that must be immediately reported as they occur,
     * because the exact order of mutations---not just the final values---determines the Merkle root hash.
     */
    private final KVStateChangeListener kvStateChangeListener = new KVStateChangeListener();

    /**
     * The state root supplier to use for creating a new state root.
     */
    private final Supplier<PlatformMerkleStateRoot> stateRootSupplier;

    /**
     * The action to take, if any, when a consensus round is sealed.
     */
    private final BiConsumer<Round, State> onSealConsensusRound;
    /**
     * Once set, a future that resolves to the hash of the state used to initialize the application. This is known
     * immediately at genesis or on restart from a saved state; during reconnect, it is known when reconnect
     * completes. Used to inject the start-of-state hash to the {@link BlockStreamManagerImpl}.
     */
    @Nullable
    private CompletableFuture<Bytes> initialStateHashFuture;

    @Nullable
    private List<StateChanges.Builder> migrationStateChanges;

    @Nullable
    private StartupNetworks startupNetworks;

    @FunctionalInterface
    public interface TssBaseServiceFactory {
        @NonNull
        TssBaseService apply(@NonNull AppContext appContext);
    }

    @FunctionalInterface
    public interface StartupNetworksFactory {
        @NonNull
        StartupNetworks apply(
                long selfNodeId, @NonNull ConfigProvider configProvider, @NonNull TssBaseService tssBaseService);
    }

    /*==================================================================================================================
    *
    * Hedera Object Construction.
    *
    =================================================================================================================*/

    /**
     * Creates a Hedera node and registers its own and its services' {@link RuntimeConstructable} factories
     * with the given {@link ConstructableRegistry}.
     *
     * <p>This registration is a critical side effect that must happen called before any Platform initialization
     * steps that try to create or deserialize a {@link MerkleStateRoot}.
     *
     * @param constructableRegistry the registry to register {@link RuntimeConstructable} factories with
     * @param registryFactory the factory to use for creating the services registry
     * @param migrator the migrator to use with the services
     * @param tssBaseServiceFactory the factory for the TSS base service
     * @param startupNetworksFactory the factory for the startup networks
     * @param selfNodeId the node ID of this node
     */
    public Hedera(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final ServicesRegistry.Factory registryFactory,
            @NonNull final ServiceMigrator migrator,
            @NonNull final InstantSource instantSource,
            @NonNull final TssBaseServiceFactory tssBaseServiceFactory,
            @NonNull final StartupNetworksFactory startupNetworksFactory,
            @NonNull final NodeId selfNodeId) {
        requireNonNull(registryFactory);
        requireNonNull(constructableRegistry);
        this.selfNodeId = requireNonNull(selfNodeId);
        this.serviceMigrator = requireNonNull(migrator);
        this.startupNetworksFactory = requireNonNull(startupNetworksFactory);
        logger.info(
                """

                        {}

                        Welcome to Hedera! Developed with ❤\uFE0F by the Open Source Community.
                        https://github.com/hashgraph/hedera-services

                        """,
                HEDERA);
        bootstrapConfigProvider = new BootstrapConfigProviderImpl();
        final var bootstrapConfig = bootstrapConfigProvider.getConfiguration();
        hapiVersion = bootstrapConfig.getConfigData(VersionConfig.class).hapiVersion();
        version = ServicesSoftwareVersion.from(bootstrapConfig);
        streamMode = bootstrapConfig.getConfigData(BlockStreamConfig.class).streamMode();
        servicesRegistry = registryFactory.create(constructableRegistry, bootstrapConfig);
        logger.info(
                "Creating Hedera Consensus Node {} with HAPI {}",
                () -> HapiUtils.toString(version.getPbjSemanticVersion()),
                () -> HapiUtils.toString(hapiVersion));
        fileServiceImpl = new FileServiceImpl();

        final Supplier<Configuration> configSupplier = () -> configProvider.getConfiguration();
        this.appContext = new AppContextImpl(
                instantSource,
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())),
                this,
                configSupplier,
                () -> daggerApp.networkInfo().selfNodeInfo(),
                new AppThrottleFactory(
                        configSupplier,
                        () -> daggerApp.workingStateAccessor().getState(),
                        () -> daggerApp.throttleServiceManager().activeThrottleDefinitionsOrThrow(),
                        ThrottleAccumulator::new));
        tssBaseService = tssBaseServiceFactory.apply(appContext);
        contractServiceImpl = new ContractServiceImpl(appContext);
        scheduleServiceImpl = new ScheduleServiceImpl();
        blockStreamService = new BlockStreamService();
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        contractServiceImpl,
                        fileServiceImpl,
                        tssBaseService,
                        new FreezeServiceImpl(),
                        scheduleServiceImpl,
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        blockStreamService,
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl(),
                        // FUTURE: a lambda that tests if a ReadableTssStore
                        // constructed from the migration state returns a
                        // RosterKeys with the ledger id for the given roster
                        new RosterService(roster -> true),
                        PLATFORM_STATE_SERVICE)
                .forEach(servicesRegistry::register);
        try {
            final Supplier<PlatformMerkleStateRoot> baseSupplier = () ->
                    new PlatformMerkleStateRoot(new MerkleStateLifecyclesImpl(this), ServicesSoftwareVersion::new);
            final var blockStreamsEnabled = isBlockStreamEnabled();
            stateRootSupplier = blockStreamsEnabled ? () -> withListeners(baseSupplier.get()) : baseSupplier;
            onSealConsensusRound = blockStreamsEnabled ? this::manageBlockEndRound : (round, state) -> {};
            // And the factory for the MerkleStateRoot class id must be our constructor
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(PlatformMerkleStateRoot.class, stateRootSupplier));
        } catch (final ConstructableRegistryException e) {
            logger.error("Failed to register " + MerkleStateRoot.class + " factory with ConstructableRegistry", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Called immediately after the constructor to get the version of this software. In an upgrade scenario, this
     * version will be greater than the one in the saved state.
     *
     * @return The software version.
     */
    @Override
    @NonNull
    public SoftwareVersion getSoftwareVersion() {
        return version;
    }

    /*==================================================================================================================
    *
    * Initialization Step 1: Create a new state (either genesis or restart, once per node).
    *
    =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * <p>Called by the platform to build a genesis state.
     *
     * @return a Services state object
     */
    @Override
    @NonNull
    public MerkleRoot newMerkleStateRoot() {
        return stateRootSupplier.get();
    }

    @Override
    public void notify(@NonNull final PlatformStatusChangeNotification notification) {
        this.platformStatus = notification.getNewStatus();
        logger.info("HederaNode#{} is {}", platform.getSelfId(), platformStatus.name());
        switch (platformStatus) {
            case ACTIVE -> startGrpcServer();
            case CATASTROPHIC_FAILURE -> shutdownGrpcServer();
            case FREEZE_COMPLETE -> {
                closeRecordStreams();
                shutdownGrpcServer();
            }
            case REPLAYING_EVENTS, STARTING_UP, OBSERVING, RECONNECT_COMPLETE, CHECKING, FREEZING, BEHIND -> {
                // Nothing to do here, just enumerate for completeness
            }
        }
    }

    /*==================================================================================================================
    *
    * Initialization Step 2: Initialize the state. Either genesis or restart or reconnect or some other trigger.
    * Includes migration when needed.
    *
    =================================================================================================================*/

    public void initializeStatesApi(
            @NonNull final State state,
            @NonNull final Metrics metrics,
            @NonNull final InitTrigger trigger,
            @Nullable final AddressBook genesisAddressBook,
            @NonNull final Configuration platformConfiguration) {
        requireNonNull(state);
        requireNonNull(platformConfiguration);
        this.metrics = requireNonNull(metrics);
        this.configProvider = new ConfigProviderImpl(trigger == GENESIS, metrics);
        final var deserializedVersion = serviceMigrator.creationVersionOf(state);
        logger.info(
                "Initializing Hedera state version {} in {} mode with trigger {} and previous version {}",
                version,
                configProvider
                        .getConfiguration()
                        .getConfigData(HederaConfig.class)
                        .activeProfile(),
                trigger,
                deserializedVersion == null ? "<NONE>" : deserializedVersion);
        if (trigger != GENESIS) {
            requireNonNull(deserializedVersion, "Deserialized version cannot be null for trigger " + trigger);
        }
        final var savedStateVersion =
                deserializedVersion == null ? null : new ServicesSoftwareVersion(deserializedVersion);
        if (version.compareTo(savedStateVersion) < 0) {
            logger.fatal(
                    "Fatal error, state source version {} is higher than node software version {}",
                    savedStateVersion,
                    version);
            throw new IllegalStateException("Cannot downgrade from " + savedStateVersion + " to " + version);
        }
        try {
            migrateSchemas(state, savedStateVersion, trigger, metrics, genesisAddressBook, platformConfiguration);
            logConfiguration();
        } catch (final Throwable t) {
            logger.fatal("Critical failure during schema migration", t);
            throw new IllegalStateException("Critical failure during migration", t);
        }
        final var readableStore = new ReadablePlatformStateStore(state.getReadableStates(PlatformStateService.NAME));
        logger.info(
                "Platform state includes freeze time={} and last frozen={}",
                readableStore.getFreezeTime(),
                readableStore.getLastFrozenTime());
    }

    /**
     * Invoked by the platform when the state should be initialized. This happens <b>BEFORE</b>
     * {@link SwirldMain#init(Platform, NodeId)} and after {@link #newMerkleStateRoot()}.
     */
    @SuppressWarnings("java:S1181") // catching Throwable instead of Exception when we do a direct System.exit()
    public void onStateInitialized(
            @NonNull final State state, @NonNull final Platform platform, @NonNull final InitTrigger trigger) {
        // A Hedera object can receive multiple onStateInitialized() calls throughout its lifetime if
        // the platform needs to initialize a learned state after reconnect; however, it cannot be
        // used by multiple platform instances
        if (this.platform != null && this.platform != platform) {
            logger.fatal("Fatal error, platform should never change once set");
            throw new IllegalStateException("Platform should never change once set");
        }
        this.platform = requireNonNull(platform);
        if (state.getReadableStates(PlatformStateService.NAME).isEmpty()) {
            initializeStatesApi(
                    state,
                    metrics,
                    trigger,
                    RosterUtils.buildAddressBook(platform.getRoster()),
                    platform.getContext().getConfiguration());
        }
        // With the States API grounded in the working state, we can create the object graph from it
        initializeDagger(state, trigger);
    }

    /**
     * Called by this class when we detect it is time to do migration. The {@code deserializedVersion} must not be newer
     * than the current software version. If it is prior to the current version, then each migration between the
     * {@code deserializedVersion} and the current version, including the current version, will be executed, thus
     * bringing the state up to date.
     *
     * <p>If the {@code deserializedVersion} is {@code null}, then this is the first time the node has been started,
     * and thus all schemas will be executed.
     *
     * @param state                 current state
     * @param deserializedVersion   version deserialized
     * @param trigger               trigger that is calling migration
     * @param genesisAddressBook    the genesis address book, if applicable
     * @param platformConfiguration platform configuration
     */
    private void migrateSchemas(
            @NonNull final State state,
            @Nullable final ServicesSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics,
            @Nullable final AddressBook genesisAddressBook,
            @NonNull final Configuration platformConfiguration) {
        final var previousVersion = deserializedVersion == null ? null : deserializedVersion.getPbjSemanticVersion();
        final var isUpgrade = version.compareTo(deserializedVersion) > 0;
        logger.info(
                "{} from Services version {} @ current {} with trigger {}",
                () -> isUpgrade ? "Upgrading" : (previousVersion == null ? "Starting" : "Restarting"),
                () -> HapiUtils.toString(Optional.ofNullable(deserializedVersion)
                        .map(ServicesSoftwareVersion::getPbjSemanticVersion)
                        .orElse(null)),
                () -> HapiUtils.toString(version.getPbjSemanticVersion()),
                () -> trigger);
        // This is set only when the trigger is genesis. Because, only in those cases
        // the migration code is using the network info values.
        NetworkInfo genesisNetworkInfo = null;
        if (trigger == GENESIS) {
            final var config = configProvider.getConfiguration();
            final var ledgerConfig = config.getConfigData(LedgerConfig.class);
            final var genesisRoster = buildRoster(requireNonNull(genesisAddressBook));
            genesisNetworkInfo = new GenesisNetworkInfo(genesisRoster, ledgerConfig.id());
        }
        blockStreamService.resetMigratedLastBlockHash();
        startupNetworks = startupNetworksFactory.apply(selfNodeId.id(), configProvider, tssBaseService);
        PLATFORM_STATE_SERVICE.setAppVersionFn(() -> version);
        PLATFORM_STATE_SERVICE.setActiveRosterFn(
                () -> new ReadableRosterStoreImpl(state.getReadableStates(RosterService.NAME)).getActiveRoster());
        final var migrationChanges = serviceMigrator.doMigrations(
                state,
                servicesRegistry,
                deserializedVersion,
                version,
                // (FUTURE) In principle, the FileService could change the active configuration during a
                // migration, implying we should pass a config provider; but we don't need this yet
                configProvider.getConfiguration(),
                platformConfiguration,
                genesisNetworkInfo,
                metrics,
                startupNetworks);
        PLATFORM_STATE_SERVICE.clearActiveRosterFn();
        migrationStateChanges = new ArrayList<>(migrationChanges);
        kvStateChangeListener.reset();
        boundaryStateChangeListener.reset();
        // If still using BlockRecordManager state, then for specifically a non-genesis upgrade,
        // set in state that post-upgrade work is pending
        if (streamMode != BLOCKS && isUpgrade && trigger != RECONNECT && trigger != GENESIS) {
            unmarkMigrationRecordsStreamed(state);
            migrationStateChanges.add(
                    StateChanges.newBuilder().stateChanges(boundaryStateChangeListener.allStateChanges()));
            boundaryStateChangeListener.reset();
        }
        logger.info("Migration complete");
    }

    /*==================================================================================================================
    *
    * Initialization Step 3: Initialize the app. Happens once at startup.
    *
    =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * <p>Called <b>AFTER</b> init and migrate have been called on the state (either the new state created from
     * {@link #newMerkleStateRoot()} or an instance of {@link MerkleStateRoot} created by the platform and
     * loaded from the saved state).
     *
     * <p>(FUTURE) Consider moving this initialization into {@link #onStateInitialized(State, Platform, InitTrigger)}
     * instead, as there is no special significance to having it here instead.
     */
    @SuppressWarnings("java:S1181") // catching Throwable instead of Exception when we do a direct System.exit()
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        if (this.platform != platform) {
            throw new IllegalArgumentException("Platform must be the same instance");
        }
        assertEnvSanityChecks(nodeId);
        logger.info("Initializing Hedera app with HederaNode#{}", nodeId);
        Locale.setDefault(Locale.US);
        logger.info("Locale to set to US en");
    }

    @Override
    public void submit(@NonNull final TransactionBody body) {
        requireNonNull(body);
        if (platformStatus != ACTIVE) {
            throw new IllegalStateException("" + PLATFORM_NOT_ACTIVE);
        }
        final HederaFunctionality function;
        try {
            function = functionOf(body);
        } catch (UnknownHederaFunctionality e) {
            throw new IllegalArgumentException("" + UNKNOWN);
        }
        try {
            final var config = configProvider.getConfiguration();
            final var adminConfig = config.getConfigData(NetworkAdminConfig.class);
            final var allowList = adminConfig.nodeTransactionsAllowList().functionalitySet();
            if (!allowList.contains(function)) {
                throw new IllegalArgumentException("" + NOT_SUPPORTED);
            }
            final var payload = com.hedera.hapi.node.base.Transaction.PROTOBUF.toBytes(nodeTransactionWith(body));
            requireNonNull(daggerApp).submissionManager().submit(body, payload);
        } catch (PreCheckException e) {
            final var reason = e.responseCode();
            if (reason == DUPLICATE_TRANSACTION) {
                // In this case the client must not retry with the same transaction, but
                // could retry with a different transaction id if desired.
                throw new IllegalArgumentException("" + DUPLICATE_TRANSACTION);
            }
            throw new IllegalStateException("" + reason);
        }
    }

    @Override
    public Signature sign(final byte[] ledgerId) {
        return platform.sign(ledgerId);
    }

    /**
     * Called to perform orderly close record streams.
     */
    private void closeRecordStreams() {
        daggerApp.blockRecordManager().close();
    }

    /**
     * Gets whether the default charset is UTF-8.
     */
    private boolean isUTF8(@NonNull final Charset defaultCharset) {
        if (!UTF_8.equals(defaultCharset)) {
            logger.error("Default charset is {}, not UTF-8", defaultCharset);
            return false;
        }
        return true;
    }

    /**
     * Gets whether the sha384 digest is available
     */
    private boolean sha384DigestIsAvailable() {
        try {
            MessageDigest.getInstance("SHA-384");
            return true;
        } catch (final NoSuchAlgorithmException e) {
            logger.error(e);
            return false;
        }
    }

    /*==================================================================================================================
    *
    * Other app lifecycle methods
    *
    =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * <p>Called by the platform after <b>ALL</b> initialization to start the gRPC servers and begin operation, or by
     * the notification listener when it is time to restart the gRPC server after it had been stopped (such as during
     * reconnect).
     */
    @Override
    public void run() {
        logger.info("Starting the Hedera node");
    }

    /**
     * Called for an orderly shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down Hedera node");
        shutdownGrpcServer();

        if (daggerApp != null) {
            logger.debug("Shutting down the state");
            final var state = daggerApp.workingStateAccessor().getState();
            if (state instanceof MerkleStateRoot msr) {
                msr.close();
            }

            logger.debug("Shutting down the block manager");
            daggerApp.blockRecordManager().close();
        }

        platform = null;
        daggerApp = null;
    }

    /**
     * Invoked by the platform to handle pre-consensus events. This only happens after {@link #run()} has been called.
     */
    public void onPreHandle(@NonNull final Event event, @NonNull final State state) {
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var creator =
                daggerApp.networkInfo().nodeInfo(event.getCreatorId().id());
        if (creator == null) {
            // It's normal immediately post-upgrade to still see events from a node removed from the address book
            if (!isSoOrdered(event.getSoftwareVersion(), version.getPbjSemanticVersion())) {
                logger.warn(
                        "Received event (version {} vs current {}) from node {} which is not in the address book",
                        com.hedera.hapi.util.HapiUtils.toString(event.getSoftwareVersion()),
                        com.hedera.hapi.util.HapiUtils.toString(version.getPbjSemanticVersion()),
                        event.getCreatorId());
            }
            return;
        }

        final var transactions = new ArrayList<Transaction>(1000);
        event.forEachTransaction(transactions::add);
        daggerApp.preHandleWorkflow().preHandle(readableStoreFactory, creator.accountId(), transactions.stream());
    }

    public void onNewRecoveredState(@NonNull final MerkleStateRoot recoveredState) {
        try {
            if (shouldDump(daggerApp.initTrigger(), MOD_POST_EVENT_STREAM_REPLAY)) {
                dumpModChildrenFrom(recoveredState, MOD_POST_EVENT_STREAM_REPLAY, MerkleStateChild.childrenToDump());
            }
        } catch (Exception e) {
            logger.error("Error dumping state after migration at MOD_POST_EVENT_STREAM_REPLAY", e);
        }
        // Always close the block manager so replay will end with a complete record file
        daggerApp.blockRecordManager().close();
    }

    public static boolean shouldDump(@NonNull final InitTrigger trigger, @NonNull final DumpCheckpoint checkpoint) {
        return trigger == EVENT_STREAM_RECOVERY && selectedDumpCheckpoints().contains(checkpoint);
    }

    /**
     * Invoked by the platform to handle a round of consensus events.  This only happens after {@link #run()} has been
     * called.
     */
    public void onHandleConsensusRound(@NonNull final Round round, @NonNull final State state) {
        daggerApp.workingStateAccessor().setState(state);
        daggerApp.handleWorkflow().handleRound(state, round);
    }

    /**
     * Called by the platform after it has made all its changes to this state for the given round.
     *
     * @param round the round whose platform state changes are completed
     * @param state the state after the platform has made all its changes
     */
    public void onSealConsensusRound(@NonNull final Round round, @NonNull final State state) {
        requireNonNull(state);
        requireNonNull(round);
        onSealConsensusRound.accept(round, state);
    }

    /*==================================================================================================================
    *
    * gRPC Server Lifecycle
    *
    =================================================================================================================*/

    /**
     * Start the gRPC Server if it is not already running.
     */
    void startGrpcServer() {
        if (isNotEmbedded() && !daggerApp.grpcServerManager().isRunning()) {
            daggerApp.grpcServerManager().start();
        }
    }

    /**
     * Called to perform orderly shutdown of the gRPC servers.
     */
    public void shutdownGrpcServer() {
        if (isNotEmbedded()) {
            daggerApp.grpcServerManager().stop();
        }
    }

    /**
     * Called to set the starting state hash after genesis or restart.
     *
     * @param stateHash the starting state hash
     */
    public void setInitialStateHash(@NonNull final Hash stateHash) {
        requireNonNull(stateHash);
        initialStateHashFuture = completedFuture(stateHash.getBytes());
    }

    /*==================================================================================================================
    *
    * Exposed for use by embedded Hedera
    *
    =================================================================================================================*/
    public IngestWorkflow ingestWorkflow() {
        return daggerApp.ingestWorkflow();
    }

    public QueryWorkflow queryWorkflow() {
        return daggerApp.queryWorkflow();
    }

    public QueryWorkflow operatorQueryWorkflow() {
        return daggerApp.operatorQueryWorkflow();
    }

    public HandleWorkflow handleWorkflow() {
        return daggerApp.handleWorkflow();
    }

    public ConfigProvider configProvider() {
        return configProvider;
    }

    public BlockStreamManager blockStreamManager() {
        return daggerApp.blockStreamManager();
    }

    public ThrottleDefinitions activeThrottleDefinitions() {
        return daggerApp.throttleServiceManager().activeThrottleDefinitionsOrThrow();
    }

    public boolean isBlockStreamEnabled() {
        return streamMode != RECORDS;
    }

    public boolean isRosterLifecycleEnabled() {
        return configProvider
                .getConfiguration()
                .getConfigData(AddressBookConfig.class)
                .useRosterLifecycle();
    }

    public KVStateChangeListener kvStateChangeListener() {
        return kvStateChangeListener;
    }

    public BoundaryStateChangeListener boundaryStateChangeListener() {
        return boundaryStateChangeListener;
    }

    /*==================================================================================================================
    *
    * Random private helper methods
    *
    =================================================================================================================*/

    private void initializeDagger(@NonNull final State state, @NonNull final InitTrigger trigger) {
        final var notifications = platform.getNotificationEngine();
        final var blockStreamEnabled = isBlockStreamEnabled();
        // The Dagger component should be constructed every time we reach this point, even if
        // it exists (this avoids any problems with mutable singleton state by reconstructing
        // everything); but we must ensure the gRPC server in the old component is fully stopped,
        // as well as unregister listeners from the last time this method ran
        if (daggerApp != null) {
            shutdownGrpcServer();
            notifications.unregister(PlatformStatusChangeListener.class, this);
            notifications.unregister(ReconnectCompleteListener.class, daggerApp.reconnectListener());
            notifications.unregister(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());
            if (blockStreamEnabled) {
                notifications.unregister(StateHashedListener.class, daggerApp.blockStreamManager());
                daggerApp.tssBaseService().unregisterLedgerSignatureConsumer(daggerApp.blockStreamManager());
            }
        }
        if (trigger == RECONNECT) {
            // During a reconnect, we wait for reconnect to complete successfully and then set the initial hash
            // from the immutable state in the ReconnectCompleteNotification
            initialStateHashFuture = new CompletableFuture<>();
            notifications.register(ReconnectCompleteListener.class, new ReadReconnectStartingStateHash(notifications));
        }
        // For other triggers the initial state hash must have been set already
        requireNonNull(initialStateHashFuture);
        final var roundNum = requireNonNull(state.getReadableStates(PlatformStateService.NAME)
                        .<PlatformState>getSingleton(PLATFORM_STATE_KEY)
                        .get())
                .consensusSnapshotOrThrow()
                .round();
        final var initialStateHash = new InitialStateHash(initialStateHashFuture, roundNum);

        final var activeRoster = tssBaseService.chooseRosterForNetwork(
                state, trigger, serviceMigrator, version, configProvider.getConfiguration(), platform.getRoster());
        final var networkInfo =
                new StateNetworkInfo(state, activeRoster, platform.getSelfId().id(), configProvider);
        // Fully qualified so as to not confuse javadoc
        daggerApp = com.hedera.node.app.DaggerHederaInjectionComponent.builder()
                .configProviderImpl(configProvider)
                .bootstrapConfigProviderImpl(bootstrapConfigProvider)
                .fileServiceImpl(fileServiceImpl)
                .contractServiceImpl(contractServiceImpl)
                .scheduleService(scheduleServiceImpl)
                .tssBaseService(tssBaseService)
                .initTrigger(trigger)
                .softwareVersion(version.getPbjSemanticVersion())
                .self(networkInfo.selfNodeInfo())
                .platform(platform)
                .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
                .crypto(CryptographyHolder.get())
                .currentPlatformStatus(new CurrentPlatformStatusImpl(platform))
                .servicesRegistry(servicesRegistry)
                .instantSource(appContext.instantSource())
                .throttleFactory(appContext.throttleFactory())
                .metrics(metrics)
                .kvStateChangeListener(kvStateChangeListener)
                .boundaryStateChangeListener(boundaryStateChangeListener)
                .migrationStateChanges(migrationStateChanges != null ? migrationStateChanges : new ArrayList<>())
                .initialStateHash(initialStateHash)
                .networkInfo(networkInfo)
                .startupNetworks(startupNetworks)
                .build();
        // Initialize infrastructure for fees, exchange rates, and throttles from the working state
        daggerApp.initializer().accept(state);
        notifications.register(PlatformStatusChangeListener.class, this);
        notifications.register(ReconnectCompleteListener.class, daggerApp.reconnectListener());
        notifications.register(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());
        if (blockStreamEnabled) {
            notifications.register(StateHashedListener.class, daggerApp.blockStreamManager());
            daggerApp
                    .blockStreamManager()
                    .initLastBlockHash(
                            switch (trigger) {
                                case GENESIS -> BlockStreamManager.ZERO_BLOCK_HASH;
                                default -> blockStreamService
                                        .migratedLastBlockHash()
                                        .orElseGet(() -> startBlockHashFrom(state));
                            });
            daggerApp.tssBaseService().registerLedgerSignatureConsumer(daggerApp.blockStreamManager());
            migrationStateChanges = null;
        }
    }

    /**
     * Given the {@link BlockStreamInfo} context from a {@link State}, infers the block hash of the
     * last block that was incorporated in this state.
     *
     * @param state the state to use
     * @return the inferred block hash
     */
    private Bytes startBlockHashFrom(@NonNull final State state) {
        final var blockStreamInfo = state.getReadableStates(BlockStreamService.NAME)
                .<BlockStreamInfo>getSingleton(BLOCK_STREAM_INFO_KEY)
                .get();
        requireNonNull(blockStreamInfo);
        // Three of the four ingredients in the block hash are directly in the BlockStreamInfo; that is,
        // the previous block hash, the input tree root hash, and the start of block state hash
        final var prevBlockHash = blockHashByBlockNumber(
                blockStreamInfo.trailingBlockHashes(),
                blockStreamInfo.blockNumber() - 1,
                blockStreamInfo.blockNumber() - 1);
        requireNonNull(prevBlockHash);
        final var leftParent = combine(prevBlockHash, blockStreamInfo.inputTreeRootHash());
        // The fourth ingredient, the output tree root hash, is not directly in the BlockStreamInfo, but
        // we can recompute it based on the tree hash information and the fact the last output item in
        // the block was devoted to putting the BlockStreamInfo itself into the state
        final var outputTreeRootHash = outputTreeRootHashFrom(blockStreamInfo);
        final var rightParent = combine(outputTreeRootHash, blockStreamInfo.startOfBlockStateHash());
        return combine(leftParent, rightParent);
    }

    /**
     * Given a {@link BlockStreamInfo} context, computes the output tree root hash that must have been
     * computed at the end of the block that the context describes, assuming the final output block item
     * was the state change that put the context into the state.
     *
     * @param blockStreamInfo the context to use
     * @return the inferred output tree root hash
     */
    private @NonNull Bytes outputTreeRootHashFrom(@NonNull final BlockStreamInfo blockStreamInfo) {
        // This was the last state change in the block
        final var blockStreamInfoChange = StateChange.newBuilder()
                .stateId(STATE_ID_BLOCK_STREAM_INFO.protoOrdinal())
                .singletonUpdate(SingletonUpdateChange.newBuilder()
                        .blockStreamInfoValue(blockStreamInfo)
                        .build())
                .build();
        // And this was the last output block item
        final var lastStateChanges = BlockItem.newBuilder()
                .stateChanges(new StateChanges(blockStreamInfo.blockEndTime(), List.of(blockStreamInfoChange)))
                .build();
        // So we can combine this last leaf's has with the size and rightmost hashes
        // store from the pending output tree to recompute its final root hash
        final var penultimateOutputTreeStatus = new StreamingTreeHasher.Status(
                blockStreamInfo.numPrecedingOutputItems(), blockStreamInfo.rightmostPrecedingOutputTreeHashes());
        final var lastLeafHash = noThrowSha384HashOf(BlockItem.PROTOBUF.toBytes(lastStateChanges));
        return rootHashFrom(penultimateOutputTreeStatus, lastLeafHash);
    }

    private void logConfiguration() {
        if (logger.isInfoEnabled()) {
            final var config = configProvider.getConfiguration();
            final var lines = new ArrayList<String>();
            lines.add("Active Configuration:");
            Utils.allProperties(config).forEach((key, value) -> lines.add(key + " = " + value));
            logger.info(String.join("\n", lines));
        }
    }

    private void unmarkMigrationRecordsStreamed(@NonNull final State state) {
        final var blockServiceState = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = blockServiceState.<BlockInfo>getSingleton(BLOCK_INFO_STATE_KEY);
        final var currentBlockInfo = requireNonNull(blockInfoState.get());
        final var nextBlockInfo =
                currentBlockInfo.copyBuilder().migrationRecordsStreamed(false).build();
        blockInfoState.put(nextBlockInfo);
        logger.info("Unmarked post-upgrade work as done");
        ((WritableSingletonStateBase<BlockInfo>) blockInfoState).commit();
    }

    private void assertEnvSanityChecks(@NonNull final NodeId nodeId) {
        // Check that UTF-8 is in use. Otherwise, the node will be subject to subtle bugs in string handling that will
        // lead to ISS.
        final var defaultCharset = daggerApp.nativeCharset().get();
        if (!isUTF8(defaultCharset)) {
            logger.error(
                    """
                            Fatal precondition violation in HederaNode#{}: default charset is {} and not UTF-8
                            LC_ALL={}
                            LANG={}
                            file.encoding={}
                            """,
                    nodeId,
                    defaultCharset,
                    System.getenv("LC_ALL"),
                    System.getenv("LANG"),
                    System.getProperty("file.encoding"));
            System.exit(1);
        }

        // Check that the digest factory supports SHA-384.
        if (!sha384DigestIsAvailable()) {
            logger.error(
                    "Fatal precondition violation in HederaNode#{}: digest factory does not support SHA-384", nodeId);
            System.exit(1);
        }
    }

    private PlatformMerkleStateRoot withListeners(@NonNull final PlatformMerkleStateRoot root) {
        root.registerCommitListener(boundaryStateChangeListener);
        root.registerCommitListener(kvStateChangeListener);
        return root;
    }

    private void manageBlockEndRound(@NonNull final Round round, @NonNull final State state) {
        daggerApp.blockStreamManager().endRound(state, round.getRoundNum());
    }

    /**
     * Returns true if the source of time is the system time. Always true for live networks.
     *
     * @return true if the source of time is the system time
     */
    private boolean isNotEmbedded() {
        return appContext.instantSource() == InstantSource.system();
    }

    private class ReadReconnectStartingStateHash implements ReconnectCompleteListener {
        private final NotificationEngine notifications;

        private ReadReconnectStartingStateHash(@NonNull final NotificationEngine notifications) {
            this.notifications = requireNonNull(notifications);
        }

        @Override
        public void notify(@NonNull final ReconnectCompleteNotification notification) {
            requireNonNull(notification);
            requireNonNull(initialStateHashFuture)
                    .complete(requireNonNull(notification.getState().getHash()).getBytes());
            notifications.unregister(ReconnectCompleteListener.class, this);
        }
    }
}
