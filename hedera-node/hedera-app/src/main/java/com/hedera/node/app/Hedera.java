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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.config.IsEmbeddedTest.NO;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static com.hedera.node.app.statedumpers.DumpCheckpoint.MOD_POST_EVENT_STREAM_REPLAY;
import static com.hedera.node.app.statedumpers.DumpCheckpoint.selectedDumpCheckpoints;
import static com.hedera.node.app.statedumpers.StateDumper.dumpModChildrenFrom;
import static com.hedera.node.app.util.FileUtilities.observePropertiesAndPermissions;
import static com.hedera.node.app.util.HederaAsciiArt.HEDERA;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static com.swirlds.platform.system.status.PlatformStatus.STARTING_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.config.IsEmbeddedTest;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.CurrentPlatformStatusImpl;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.node.app.info.UnavailableLedgerIdNetworkInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.HederaLifecyclesImpl;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.MerkleStateChild;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.ingest.IngestWorkflow;
import com.hedera.node.app.workflows.query.QueryWorkflow;
import com.hedera.node.config.Utils;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.status.PlatformStatus;
import com.swirlds.platform.system.transaction.Transaction;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
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
 * <p>This is the main entry point for the Hedera Consensus Node. It contains initialization logic for the
 * node, including its state. It constructs some artifacts for gluing the mono-service with the modular service
 * infrastructure. It constructs the Dagger dependency tree, and manages the gRPC server, and in all other ways,
 * controls execution of the node. If you want to understand our system, this is a great place to start!
 */
public final class Hedera implements SwirldMain {
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
     * The current version of the software; it is not for a node's version to change
     * without restarting the process, so final.
     */
    private final HederaSoftwareVersion version;

    private final IsEmbeddedTest isEmbeddedTest;

    /**
     * The Hashgraph Platform. This is set during state initialization.
     */
    private Platform platform;
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
     * The latest platform status we have received via notification.
     */
    private PlatformStatus platformStatus = STARTING_UP;

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
     * steps that try to create or deserialize a {@link MerkleHederaState}.
     *
     * @param constructableRegistry the registry to register {@link RuntimeConstructable} factories with
     * @param registryFactory the factory to use for creating the services registry
     * @param migrator the migrator to use with the services
     */
    public Hedera(
            @NonNull final ConstructableRegistry constructableRegistry,
            @NonNull final ServicesRegistry.Factory registryFactory,
            @NonNull final ServiceMigrator migrator,
            @NonNull final IsEmbeddedTest isEmbeddedTest) {
        requireNonNull(registryFactory);
        requireNonNull(constructableRegistry);
        this.isEmbeddedTest = requireNonNull(isEmbeddedTest);
        this.serviceMigrator = requireNonNull(migrator);
        logger.info(
                """

                        {}

                        Welcome to Hedera! Developed with ❤\uFE0F by the Open Source Community.
                        https://github.com/hashgraph/hedera-services

                        """,
                HEDERA);
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        version = getNodeStartupVersion(bootstrapConfig);
        servicesRegistry = registryFactory.create(constructableRegistry, bootstrapConfig);
        logger.info(
                "Creating Hedera Consensus Node {} with HAPI {}",
                () -> HapiUtils.toString(version.getServicesVersion()),
                () -> HapiUtils.toString(version.getHapiVersion()));
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        CONTRACT_SERVICE,
                        new FileServiceImpl(),
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl())
                .forEach(servicesRegistry::register);
        try {
            // And the factory for the MerkleHederaState class id must be our newState() method
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(MerkleHederaState.class, this::newState));
        } catch (final ConstructableRegistryException e) {
            logger.error("Failed to register MerkleHederaState factory with ConstructableRegistry", e);
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
     * <p>Called by the platform to either build a genesis state, or to deserialize a Merkle node in a saved state
     * with the stable class id of the Services Merkle tree root during genesis (c.f. our constructor, in which
     * we register this method as the factory for the {@literal 0x8e300b0dfdafbb1a} class id).
     *
     * @return a Services state object
     */
    @Override
    @NonNull
    public SwirldState newState() {
        return new MerkleHederaState(new HederaLifecyclesImpl(this));
    }

    /*==================================================================================================================
    *
    * Initialization Step 2: Initialize the state. Either genesis or restart or reconnect or some other trigger.
    * Includes migration when needed.
    *
    =================================================================================================================*/

    /**
     * Invoked by the platform when the state should be initialized. This happens <b>BEFORE</b>
     * {@link #init(Platform, NodeId)} and after {@link #newState()}.
     */
    @SuppressWarnings("java:S1181") // catching Throwable instead of Exception when we do a direct System.exit()
    public void onStateInitialized(
            @NonNull final HederaState state,
            @NonNull final Platform platform,
            @NonNull final PlatformState platformState,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousVersion) {
        // A Hedera object can receive multiple onStateInitialized() calls throughout its lifetime if
        // the platform needs to initialize a learned state after reconnect; however, it cannot be
        // used by multiple platform instances
        if (this.platform != null && this.platform != platform) {
            logger.fatal("Fatal error, platform should never change once set");
            throw new IllegalStateException("Platform should never change once set");
        }
        this.platform = requireNonNull(platform);
        final var metrics = platform.getContext().getMetrics();
        this.configProvider = new ConfigProviderImpl(trigger == GENESIS, metrics);
        logger.info(
                "Initializing Hedera state version {} in {} mode with trigger {} and previous version {}",
                version,
                trigger,
                configProvider
                        .getConfiguration()
                        .getConfigData(HederaConfig.class)
                        .activeProfile(),
                previousVersion == null ? "<NONE>" : previousVersion);
        logger.info(
                "Platform state includes freeze time={} and last frozen={}",
                platformState.getFreezeTime(),
                platformState.getLastFrozenTime());

        HederaSoftwareVersion deserializedVersion = null;
        // We do not support downgrading from one version to an older version.
        if (previousVersion instanceof HederaSoftwareVersion hederaSoftwareVersion) {
            deserializedVersion = hederaSoftwareVersion;
            if (version.isBefore(deserializedVersion)) {
                logger.fatal(
                        "Fatal error, state source version {} is higher than node software version {}",
                        deserializedVersion,
                        version);
                throw new IllegalStateException("Cannot downgrade from " + deserializedVersion + " to " + version);
            }
        } else {
            if (previousVersion != null) {
                logger.fatal("Deserialized state not created with Hedera software");
                throw new IllegalStateException("Deserialized state not created with Hedera software");
            }
        }
        try {
            migrateAndInitialize(state, deserializedVersion, trigger, platformState, metrics);
        } catch (final Throwable t) {
            logger.fatal("Critical failure during initialization", t);
            throw new IllegalStateException("Critical failure during initialization", t);
        }
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
     * @param state current state
     * @param deserializedVersion version deserialized
     * @param trigger trigger that is calling migration
     */
    private void onMigrate(
            @NonNull final HederaState state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics) {
        final var previousVersion = deserializedVersion == null ? null : deserializedVersion.getServicesVersion();
        final var currentVersion = version.getServicesVersion();
        logger.info(
                "Migrating from Services version {} to {} with trigger {}",
                () -> previousVersion == null ? "<NONE>" : HapiUtils.toString(previousVersion),
                () -> HapiUtils.toString(currentVersion),
                () -> trigger);
        final var selfNodeInfo = extractSelfNodeInfo(platform, version);
        final var networkInfo = new UnavailableLedgerIdNetworkInfo(selfNodeInfo, platform);
        // (FUTURE) In principle, the FileService could actually change the active configuration during a
        // migration, which implies we should be passing the config provider and not a static configuration
        // here; but this is a currently unneeded affordance
        serviceMigrator.doMigrations(
                state,
                servicesRegistry,
                previousVersion,
                currentVersion,
                configProvider.getConfiguration(),
                networkInfo,
                metrics);
        final var isUpgrade = isSoOrdered(previousVersion, currentVersion);
        if (isUpgrade && !trigger.equals(RECONNECT)) {
            // (FUTURE) We should probably remove this mono-service vestige, as it not currently used anywhere and
            // is hard to reconcile with a form of migration compatible with block streams and zero-downtime
            // upgrades, where we would want very much to avoid changing state in a way that does not look like
            // dispatching a transaction in the context of a block
            unmarkMigrationRecordsStreamed(state);
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
     * {@link #newState()} or an instance of {@link MerkleHederaState} created by the platform and loaded from the saved
     * state).
     *
     * <p>(FUTURE) Consider moving this initialization into {@link #onStateInitialized(HederaState, Platform, PlatformState, InitTrigger, SoftwareVersion)}
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
        // The Hashgraph platform has a "platform state", and a notification service to indicate when those
        // states change; we use these state changes for various purposes, such as turning off the gRPC
        // server when we ISS or freeze, or turning it back on when we are active
        final var notifications = platform.getNotificationEngine();
        notifications.register(PlatformStatusChangeListener.class, notification -> {
            platformStatus = notification.getNewStatus();
            logger.info("HederaNode#{} is {}", nodeId, platformStatus.name());
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
        });
        // This reconnect listener checks if there is any upgrade-related side effect it missed while offline,
        // and catches up with that side effect (e.g., writing a marker file, or unzipping the upgrade file)
        notifications.register(ReconnectCompleteListener.class, daggerApp.reconnectListener());
        // This notifaction is needed for freeze / upgrade.
        notifications.register(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());
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
            final var state = daggerApp.workingStateAccessor().getHederaState();
            if (state instanceof MerkleHederaState mhs) {
                mhs.close();
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
    public void onPreHandle(@NonNull final Event event, @NonNull final HederaState state) {
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var creator =
                daggerApp.networkInfo().nodeInfo(event.getCreatorId().id());
        if (creator == null) {
            // We were given an event for a node that *does not exist in the address book*. This will be logged as
            // a warning, as this should never happen, and we will skip the event, which may well result in an ISS.
            logger.warn("Received event from node {} which is not in the address book", event.getCreatorId());
            return;
        }

        final var transactions = new ArrayList<Transaction>(1000);
        event.forEachTransaction(transactions::add);
        daggerApp.preHandleWorkflow().preHandle(readableStoreFactory, creator.accountId(), transactions.stream());
    }

    public void onNewRecoveredState(@NonNull final MerkleHederaState recoveredState) {
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
    public void onHandleConsensusRound(
            @NonNull final Round round, @NonNull final PlatformState platformState, @NonNull final HederaState state) {
        daggerApp.workingStateAccessor().setHederaState(state);
        daggerApp.platformStateAccessor().setPlatformState(platformState);
        daggerApp.handleWorkflow().handleRound(state, platformState, round);
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
        if (isEmbeddedTest == NO && !daggerApp.grpcServerManager().isRunning()) {
            daggerApp.grpcServerManager().start();
        }
    }

    /**
     * Called to perform orderly shutdown of the gRPC servers.
     */
    public void shutdownGrpcServer() {
        if (isEmbeddedTest == NO) {
            daggerApp.grpcServerManager().stop();
        }
    }

    /*==================================================================================================================
    *
    * Workflows for use by embedded Hedera
    *
    =================================================================================================================*/
    public IngestWorkflow ingestWorkflow() {
        return daggerApp.ingestWorkflow();
    }

    public QueryWorkflow queryWorkflow() {
        return daggerApp.queryWorkflow();
    }

    public HandleWorkflow handleWorkflow() {
        return daggerApp.handleWorkflow();
    }

    /*==================================================================================================================
    *
    * Genesis Initialization
    *
    =================================================================================================================*/

    private void migrateAndInitialize(
            @NonNull final HederaState state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final PlatformState platformState,
            @NonNull final Metrics metrics) {
        if (trigger != GENESIS) {
            requireNonNull(deserializedVersion, "Deserialized version cannot be null for trigger " + trigger);
        }
        // Until all service schemas are migrated, MerkleHederaState will not be able to implement
        // the States API, even if it already has all its children in the Merkle tree, as it will lack
        // state definitions for those children. (Note also that services may require migrations for
        // those children to be usable with the current version of the software.)
        onMigrate(state, deserializedVersion, trigger, metrics);
        // With the States API grounded in the working state, we can create the object graph from it
        initializeDagger(state, trigger, platformState);
        // (FUTURE) If there were services dedicated to the functions below, they would likely do this
        // initialization work in their restart() hooks, which gets called after the States API is fully
        // initialized on the working state
        initializeExchangeRateManager(state);
        initializeFeeManager(state);
        observePropertiesAndPermissions(state, configProvider.getConfiguration(), configProvider::update);
        logConfiguration();
        daggerApp.throttleServiceManager().initFrom(state);
    }

    /*==================================================================================================================
    *
    * Random private helper methods
    *
    =================================================================================================================*/

    private void initializeDagger(
            @NonNull final HederaState state, @NonNull final InitTrigger trigger, final PlatformState platformState) {
        // The Dagger component should be constructed every time we reach this point, even if
        // it exists (this avoids any problems with mutable singleton state by reconstructing
        // everything); but we must ensure the gRPC server in the old component is fully stopped
        if (daggerApp != null) {
            shutdownGrpcServer();
        }
        // Fully qualified so as to not confuse javadoc
        daggerApp = com.hedera.node.app.DaggerHederaInjectionComponent.builder()
                .initTrigger(trigger)
                .softwareVersion(version)
                .configProvider(configProvider)
                .configProviderImpl(configProvider)
                .self(extractSelfNodeInfo(platform, version))
                .platform(platform)
                .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
                .crypto(CryptographyHolder.get())
                .currentPlatformStatus(new CurrentPlatformStatusImpl(platform))
                .servicesRegistry(servicesRegistry)
                .instantSource(InstantSource.system())
                .build();
        daggerApp.workingStateAccessor().setHederaState(state);
        daggerApp.platformStateAccessor().setPlatformState(platformState);
    }

    private static HederaSoftwareVersion getNodeStartupVersion(@NonNull final Configuration startupConfig) {
        final var versionConfig = startupConfig.getConfigData(VersionConfig.class);
        return new HederaSoftwareVersion(
                versionConfig.hapiVersion(),
                versionConfig.servicesVersion(),
                startupConfig.getConfigData(HederaConfig.class).configVersion());
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

    private void initializeFeeManager(@NonNull final HederaState state) {
        logger.info("Initializing fee schedules");
        final var filesConfig = configProvider.getConfiguration().getConfigData(FilesConfig.class);
        final var fileNum = filesConfig.feeSchedules();
        final var file = requireNonNull(
                getFileFromStorage(state, fileNum), "The initialized state had no fee schedule file 0.0." + fileNum);
        final var status = daggerApp.feeManager().update(file.contents());
        if (status != SUCCESS) {
            // (FUTURE) Ideally this would be a fatal error, but unlike the exchange rates file, it
            // is possible with the current design for state to include a partial fee schedules file,
            // so we cannot fail hard here
            logger.error("State file 0.0.{} did not contain parseable fee schedules ({})", fileNum, status);
        }
    }

    private void initializeExchangeRateManager(@NonNull final HederaState state) {
        final var filesConfig = configProvider.getConfiguration().getConfigData(FilesConfig.class);
        final var fileNum = filesConfig.exchangeRates();
        final var file = requireNonNull(
                getFileFromStorage(state, fileNum), "The initialized state had no exchange rates file 0.0." + fileNum);
        daggerApp.exchangeRateManager().init(state, file.contents());
    }

    private @Nullable File getFileFromStorage(@NonNull final HederaState state, final long fileNum) {
        final var readableFileStore = new ReadableStoreFactory(state).getStore(ReadableFileStore.class);
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        final var fileId = FileID.newBuilder()
                .fileNum(fileNum)
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .build();
        return readableFileStore.getFileLeaf(fileId);
    }

    private void unmarkMigrationRecordsStreamed(HederaState state) {
        final var blockServiceState = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState =
                blockServiceState.<BlockInfo>getSingleton(V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY);
        final var currentBlockInfo = requireNonNull(blockInfoState.get());
        final var nextBlockInfo =
                currentBlockInfo.copyBuilder().migrationRecordsStreamed(false).build();
        blockInfoState.put(nextBlockInfo);
        logger.info("Unmarked migration records streamed");
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

    private SelfNodeInfo extractSelfNodeInfo(
            @NonNull final Platform platform, @NonNull final HederaSoftwareVersion version) {
        final var selfId = platform.getSelfId();
        final var nodeAddress = platform.getAddressBook().getAddress(selfId);
        return SelfNodeInfoImpl.of(nodeAddress, version);
    }
}
