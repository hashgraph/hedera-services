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

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint.MOD_POST_EVENT_STREAM_REPLAY;
import static com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint.MOD_POST_MIGRATION;
import static com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint.selectedDumpCheckpoints;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static com.hedera.node.app.statedumpers.StateDumper.dumpModChildrenFrom;
import static com.hedera.node.app.util.FileUtilities.observePropertiesAndPermissions;
import static com.hedera.node.app.util.HederaAsciiArt.HEDERA;
import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.CurrentPlatformStatusImpl;
import com.hedera.node.app.info.NetworkInfoImpl;
import com.hedera.node.app.info.SelfNodeInfoImpl;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.records.schemas.V0490BlockRecordSchema;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.SerializableSemVers;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.token.impl.schemas.SyntheticRecordsGenerator;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.state.HederaLifecyclesImpl;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.util.MonoMigrationUtils;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.config.ConfigProvider;
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
import com.swirlds.state.spi.workflows.record.GenesisRecordsBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.Charset;
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
     * The registry of all known services
     */
    private final ServicesRegistry servicesRegistry;
    /**
     * The current version of THIS software
     */
    private final HederaSoftwareVersion version;
    /**
     * The configuration at the time of bootstrapping the node
     */
    private final ConfigProvider bootstrapConfigProvider;
    /**
     * The Hashgraph Platform. This is set during state initialization.
     */
    private Platform platform;
    /**
     * The configuration for this node
     */
    private ConfigProviderImpl configProvider;
    /** The class responsible for remembering objects created in genesis cases */
    private final GenesisRecordsBuilder genesisRecordsBuilder;
    /**
     * Dependencies managed by Dagger. Set during state initialization. The mono-service requires this object, but none
     * of the rest of the system (and particularly the modular implementation) uses it directly. Rather, it is created
     * and used to initialize the system, and more concrete dependencies are used from there.
     */
    private HederaInjectionComponent daggerApp;
    /**
     * Indicates whether the platform is active
     */
    private PlatformStatus platformStatus = PlatformStatus.STARTING_UP;

    private final SyntheticRecordsGenerator recordsGenerator;

    /**
     * The application name from the platform's perspective. This is currently locked in at the old main class name and
     * requires data migration to change.
     */
    public static final String APP_NAME = "com.hedera.services.ServicesMain";

    /**
     * The swirld name. Currently, there is only one swirld.
     */
    public static final String SWIRLD_NAME = "123";

    /*==================================================================================================================
    *
    * Hedera Object Construction.
    *
    =================================================================================================================*/

    /**
     * Creates a Hedera node that will register its own and its services' {@link RuntimeConstructable} factories
     * with the given {@link ConstructableRegistry}.
     *
     * <p>This constructor instantiates several infrastructure components that would be better injected to
     * improve testability and reuse. (For example, the {@link BootstrapConfigProviderImpl}.)
     *
     * @param constructableRegistry the registry to register {@link RuntimeConstructable} factories with
     * @param registry the registry to register services with. This is optional and can be null.
     *                         If null, a new instance of {@link ServicesRegistry} will be created.
     *                         This is useful for testing.
     */
    public Hedera(
            @NonNull final ConstructableRegistry constructableRegistry, @NonNull final ServicesRegistry registry) {
        requireNonNull(constructableRegistry);
        // FUTURE : We need to extract OrderedServiceMigrator and ServicesRegistry into its own class
        requireNonNull(registry);

        this.servicesRegistry = registry;
        this.genesisRecordsBuilder = registry.getGenesisRecords();

        // Print welcome message
        logger.info(
                "\n{}\n\nWelcome to Hedera! Developed with ❤\uFE0F by the Open Source Community. "
                        + "https://github.com/hashgraph/hedera-services\n",
                HEDERA);

        // Load the bootstrap configuration. These config values are NOT stored in state, so we don't need to have
        // state up and running for getting their values. We use this bootstrap config only in this constructor.
        this.bootstrapConfigProvider = new BootstrapConfigProviderImpl();
        final var bootstrapConfig = bootstrapConfigProvider.getConfiguration();

        // Let the user know which mode they are starting in (DEV vs. TEST vs. PROD).
        // NOTE: This bootstrapConfig is not entirely satisfactory. We probably need an alternative...
        final var hederaConfig = bootstrapConfig.getConfigData(HederaConfig.class);
        final var activeProfile = hederaConfig.activeProfile();
        logger.info("Starting in {} mode", activeProfile);

        // Read the software version. In addition to logging, we will use this software version to determine whether
        // we need to migrate the state to a newer release, and to determine which schemas to execute.
        logger.debug("Loading Software Version");
        final var versionConfig = bootstrapConfig.getConfigData(VersionConfig.class);
        version = new HederaSoftwareVersion(
                versionConfig.hapiVersion(), versionConfig.servicesVersion(), hederaConfig.configVersion());
        logger.info(
                "Creating Hedera Consensus Node {} with HAPI {}",
                () -> HapiUtils.toString(version.getServicesVersion()),
                () -> HapiUtils.toString(version.getHapiVersion()));

        // Create a records generator for any synthetic records that need to be CREATED
        this.recordsGenerator = new SyntheticRecordsGenerator();
        // Create all the service implementations
        // This is done so early and not right before we create OrderedServiceMigrator because we need to register
        // the services with the ConstructableRegistry before we can call OrderedServiceMigrator
        logger.info("Registering services");
        registerServices(servicesRegistry);

        // Register MerkleHederaState with the ConstructableRegistry, so we can use a constructor OTHER THAN the default
        // constructor to make sure it has the config and other info it needs to be created correctly.
        try {
            logger.debug("Register MerkleHederaState with ConstructableRegistry");
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(MerkleHederaState.class, this::newState));
        } catch (final ConstructableRegistryException e) {
            logger.error("Failed to register MerkleHederaState with ConstructableRegistry", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Register all the services with the {@link ServicesRegistry}.
     * @param servicesRegistry the registry to register the services with
     */
    private void registerServices(@NonNull ServicesRegistry servicesRegistry) {
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        CONTRACT_SERVICE,
                        new FileServiceImpl(bootstrapConfigProvider),
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(
                                recordsGenerator::sysAcctRecords,
                                recordsGenerator::stakingAcctRecords,
                                recordsGenerator::treasuryAcctRecords,
                                recordsGenerator::multiUseAcctRecords,
                                recordsGenerator::blocklistAcctRecords),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl())
                .forEach(servicesRegistry::register);
    }

    /**
     * Indicates whether this node is UP and ready for business.
     *
     * @return True if the platform is active and the gRPC server is running.
     */
    public boolean isActive() {
        return platformStatus == PlatformStatus.ACTIVE
                && daggerApp.grpcServerManager().isRunning();
    }

    /**
     * Get the current platform status
     * @return current platform status
     */
    public PlatformStatus getPlatformStatus() {
        return platformStatus;
    }

    /**
     * Indicates whether this node is FROZEN.
     * @return True if the platform is frozen
     */
    public boolean isFrozen() {
        return platformStatus == PlatformStatus.FREEZE_COMPLETE;
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
        // Initialize the configuration from disk. We must do this BEFORE we run migration, because the various
        // migration methods may depend on configuration to do their work. For example, the token service migration code
        // needs to know the token treasury account, which has an account ID specified in config. The initial config
        // file in state, created by the file service migration, will match what we have here, so we don't have to worry
        // about re-loading config after migration.
        logger.info("Initializing configuration with trigger {}", trigger);
        final var metrics = platform.getContext().getMetrics();
        configProvider = new ConfigProviderImpl(trigger == GENESIS, metrics);
        logConfiguration();

        recordsGenerator.createRecords(configProvider.getConfiguration(), genesisRecordsBuilder);
        // This only sets static fields from mono service state in v0490 Schemas
        MonoMigrationUtils.maybeMigrateFrom(state, trigger, metrics);

        // This is the *FIRST* time in the initialization sequence that we have access to the platform. Grab it!
        // This instance should never change on us, once it has been set
        assert this.platform == null || this.platform == platform : "Platform should never change once set";
        this.platform = platform;

        //noinspection ConstantValue
        assert platformState != null : "Platform should never pass a null platform state";
        logger.info(
                "Initializing Hedera state with trigger {} and previous version {} instance of {}",
                () -> trigger,
                () -> previousVersion == null ? "<NONE>" : previousVersion,
                () -> previousVersion == null
                        ? "<NONE>"
                        : previousVersion.getClass().getName());

        // We do not support downgrading from one version to an older version.
        final HederaSoftwareVersion deserializedVersion;
        if (previousVersion instanceof HederaSoftwareVersion) {
            deserializedVersion = (HederaSoftwareVersion) previousVersion;
            if (isDowngrade(version, deserializedVersion)) {
                logger.fatal(
                        "Fatal error, state source version {} is higher than node software version {}",
                        deserializedVersion,
                        version);
                System.exit(1);
            }
        } else if (previousVersion instanceof SerializableSemVers) {
            deserializedVersion = new HederaSoftwareVersion(
                    toPbj(((SerializableSemVers) previousVersion).getProto()),
                    toPbj(((SerializableSemVers) previousVersion).getServices()),
                    0);
        } else {
            deserializedVersion = new HederaSoftwareVersion(SemanticVersion.DEFAULT, SemanticVersion.DEFAULT, 0);
        }
        logger.info("Deserialized version is {}, version {}", deserializedVersion, version);

        // Different paths for different triggers. Every trigger should be handled here. If a new trigger is added,
        // since there is no 'default' case, it will cause a compile error, so you will know you have to deal with it
        // here. This is intentional so as to avoid forgetting to handle a new trigger.

        try {
            switch (trigger) {
                case GENESIS -> genesis(state, platformState, metrics);
                case RECONNECT -> reconnect(state, deserializedVersion, platformState, metrics);
                case RESTART, EVENT_STREAM_RECOVERY -> restart(
                        state, deserializedVersion, trigger, platformState, metrics);
            }
        } catch (final Throwable th) {
            logger.fatal("Critical failure during initialization", th);
            System.exit(1);
        }

        // This field has to be set by the time we get here. It will be set by both the genesis and restart code
        // branches. One of those two is called before a "reconnect" trigger, so we should be fully guaranteed that this
        // assertion will hold true.
        assert configProvider != null : "Config Provider *must* have been set by now!";

        // Some logging on what we found about freeze in the platform state
        logger.info(
                "Platform state includes freeze time={} and last frozen={}",
                platformState.getFreezeTime(),
                platformState.getLastFrozenTime());
    }

    /**
     * Called by this class when we detect it is time to do migration. The {@code deserializedVersion} must not be newer
     * than the current software version. If it is prior to the current version, then each migration between the
     * {@code deserializedVersion} and the current version, including the current version, will be executed, thus
     * bringing the state up to date.
     *
     * <p>If the {@code deserializedVersion} is {@code null}, then this is the first time the node has been started,
     * and thus all schemas will be executed.
     * @param state current state
     * @param deserializedVersion version deserialized
     * @param trigger trigger that is calling migration
     */
    private void onMigrate(
            @NonNull final HederaState state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics) {
        final var currentVersion = version.getServicesVersion();
        final var previousVersion = deserializedVersion == null ? null : deserializedVersion.getServicesVersion();
        logger.info(
                "Migrating from version {} to {} with trigger {}",
                () -> previousVersion == null ? "<NONE>" : HapiUtils.toString(previousVersion),
                () -> HapiUtils.toString(currentVersion),
                () -> trigger);

        final var selfId = platform.getSelfId();
        final var nodeAddress = platform.getAddressBook().getAddress(selfId);
        final var selfNodeInfo = SelfNodeInfoImpl.of(nodeAddress, version);
        final var networkInfo = new NetworkInfoImpl(selfNodeInfo, platform, bootstrapConfigProvider);

        final var migrator = new OrderedServiceMigrator(servicesRegistry);
        logger.info("Migration versions are {} to {}", previousVersion, currentVersion);
        migrator.doMigrations(
                state, currentVersion, previousVersion, configProvider.getConfiguration(), networkInfo, metrics);
        MonoMigrationUtils.maybeReleaseMaps();
        try {
            if (shouldDump(trigger, MOD_POST_MIGRATION)) {
                dumpModChildrenFrom(state, MOD_POST_MIGRATION);
            }
        } catch (Exception t) {
            logger.error("Error dumping state after migration at MOD_POST_MIGRATION", t);
        }

        final var isUpgrade = isSoOrdered(previousVersion, currentVersion);
        if (isUpgrade && !trigger.equals(RECONNECT)) {
            // When we upgrade to a higher version, after migrations are complete, we need to update
            // migrationRecordsStreamed flag to false
            // Now that the migrations have happened, we need to give the node a chance to publish any records that need
            // to
            // be created as a result of the migration. We'll do this by unsetting the `migrationRecordsStreamed` flag.
            // Then, when the handle workflow has its first consensus timestamp, it will handle publishing these records
            // (if
            // needed), and re-set this flag to prevent duplicate publishing.
            unmarkMigrationRecordsStreamed(state);
        }

        logger.info("Migration complete");
    }

    /**
     * Unsets the `migrationRecordsStreamed` flag in state, giving the handle workflow an opportunity
     * to publish any necessary records from the node's startup migration.
     */
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
     */
    @SuppressWarnings("java:S1181") // catching Throwable instead of Exception when we do a direct System.exit()
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        if (this.platform != platform) {
            throw new IllegalArgumentException("Platform must be the same instance");
        }
        logger.info("Initializing Hedera app with HederaNode#{}", nodeId);

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
                    daggerApp.nodeId(),
                    defaultCharset,
                    System.getenv("LC_ALL"),
                    System.getenv("LANG"),
                    System.getProperty("file.encoding"));
            daggerApp.systemExits().fail(1);
        }

        // Check that the digest factory supports SHA-384.
        final var digestFactory = daggerApp.digestFactory();
        if (!sha384DigestIsAvailable(digestFactory)) {
            logger.error(
                    "Fatal precondition violation in HederaNode#{}: digest factory does not support SHA-384",
                    daggerApp.nodeId());
            daggerApp.systemExits().fail(1);
        }

        // Finish initialization
        try {
            Locale.setDefault(Locale.US);
            logger.info("Locale to set to US en");

            // The Hashgraph platform has a "platform state", and a notification service to indicate when those
            // states change. We will use these state changes for various purposes, such as turning off the gRPC
            // server when we fall behind or ISS.
            final var notifications = platform.getNotificationEngine();
            notifications.register(PlatformStatusChangeListener.class, notification -> {
                platformStatus = notification.getNewStatus();
                switch (platformStatus) {
                    case ACTIVE -> {
                        logger.info("Hederanode#{} is ACTIVE", nodeId);
                        startGrpcServer();
                    }

                    case REPLAYING_EVENTS,
                            STARTING_UP,
                            OBSERVING,
                            RECONNECT_COMPLETE,
                            CHECKING,
                            FREEZING,
                            BEHIND -> logger.info("Hederanode#{} is {}", nodeId, platformStatus.name());

                    case CATASTROPHIC_FAILURE -> {
                        logger.info("Hederanode#{} is {}", nodeId, platformStatus.name());
                        shutdownGrpcServer();
                    }
                    case FREEZE_COMPLETE -> {
                        logger.info("Hederanode#{} is {}", nodeId, platformStatus.name());
                        closeRecordStreams();
                        shutdownGrpcServer();
                    }
                }
            });
            // The main job of the reconnect listener (com.hedera.node.app.service.mono.state.logic.ReconnectListener)
            // is to log some output (including hashes from the tree for the main state per service) and then to
            // "catchUpOnMissedSideEffects". This last part worries me, because it looks like it invades into the space
            // filled by the freeze service. How should we coordinate lifecycle like reconnect with the services? I am
            // tempted to say that each service has lifecycle methods we can invoke (optional methods on the Service
            // interface), but I worry about the order of invocation on different services. Which service gets called
            // before which other service? Does it matter?
            // ANSWER: We need to look and see if there is an update to the upgrade file that happened on other nodes
            // that we reconnected with. In that case, we need to save the file to disk. Similar to how we have to hook
            // for all the other special files on restart / genesis / reconnect.
            notifications.register(ReconnectCompleteListener.class, daggerApp.reconnectListener());
            // This notifaction is needed for freeze / upgrade.
            notifications.register(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());

            // TBD: notifications.register(NewSignedStateListener.class, daggerApp.newSignedStateListener());
            // com.hedera.node.app.service.mono.state.exports.NewSignedStateListener
            // Has some relationship to freeze/upgrade, but also with balance exports. This was the trigger that
            // caused us to export balance files on a certain schedule.
        } catch (final Throwable th) {
            logger.error("Fatal precondition violation in HederaNode#{}", daggerApp.nodeId(), th);
            daggerApp.systemExits().fail(1); // TBD: Better exit code?
        }
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
    private boolean sha384DigestIsAvailable(@NonNull final NamedDigestFactory digestFactory) {
        try {
            digestFactory.forName("SHA-384");
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
        daggerApp
                .preHandleWorkflow()
                .preHandle(readableStoreFactory, creator.nodeId(), creator.accountId(), transactions.stream());
    }

    public void onNewRecoveredState(@NonNull final MerkleHederaState recoveredState) {
        // (FUTURE) - dump the semantic contents of the recovered state for
        // comparison with the mirroring mono-service state
        try {
            if (shouldDump(daggerApp.initTrigger(), MOD_POST_EVENT_STREAM_REPLAY)) {
                dumpModChildrenFrom(recoveredState, MOD_POST_EVENT_STREAM_REPLAY);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("Error dumping state after migration at MOD_POST_EVENT_STREAM_REPLAY", e);
        }
        daggerApp.blockRecordManager().close();
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
        if (!daggerApp.grpcServerManager().isRunning()) {
            daggerApp.grpcServerManager().start();
        }
    }

    /**
     * Called to perform orderly shutdown of the gRPC servers.
     */
    public void shutdownGrpcServer() {
        daggerApp.grpcServerManager().stop();
    }

    /*==================================================================================================================
    *
    * Genesis Initialization
    *
    =================================================================================================================*/

    /**
     * Implements the code flow for initializing the state of a new Hedera node with NO SAVED STATE.
     */
    private void genesis(
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final Metrics metrics) {
        logger.debug("Genesis Initialization");
        // Create all the nodes in the merkle tree for all the services
        onMigrate(state, null, GENESIS, metrics);
        // Now that we have the state created, we are ready to create the dependency graph with Dagger
        initializeDagger(state, GENESIS, platformState);
        // And now that the entire dependency graph has been initialized, and we have config, and all migration has
        // been completed, we are prepared to initialize in-memory data structures. These specifically are loaded
        // from information held in state (especially those in special files).
        initializeExchangeRateManager(state);
        initializeFeeManager(state);
        daggerApp.throttleServiceManager().initFrom(state);
    }

    /*==================================================================================================================
    *
    * Restart Initialization
    *
    =================================================================================================================*/
    /**
     * Initialize flow for when a node has been restarted. This means it was started from a saved state.
     */
    private void restart(
            @NonNull final HederaState state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final PlatformState platformState,
            @NonNull final Metrics metrics) {
        initializeForTrigger(state, deserializedVersion, trigger, platformState, metrics);
    }

    /*==================================================================================================================
    *
    * Reconnect Initialization
    *
    =================================================================================================================*/

    /**
     * The initialization needed for reconnect. It constructs all schemas appropriately.
     * These are exactly the same steps done as restart trigger.
     *
     * @param state               The current state
     * @param deserializedVersion version of deserialized state
     * @param platformState       platform state
     */
    private void reconnect(
            @NonNull final HederaState state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final PlatformState platformState,
            @NonNull final Metrics metrics) {
        initializeForTrigger(state, deserializedVersion, RECONNECT, platformState, metrics);
    }

    private void initializeForTrigger(
            @NonNull final HederaState state,
            @Nullable final HederaSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final PlatformState platformState,
            @NonNull final Metrics metrics) {
        logger.info(trigger + " Initialization");

        // The deserialized version can ONLY be null if we are in genesis, otherwise something is wrong with the state
        if (deserializedVersion == null) {
            logger.fatal("Fatal error, previous software version not found in saved state!");
            System.exit(1);
        }

        // Initialize the configuration from disk (restart case). We must do this BEFORE we run migration, because
        // the various migration methods may depend on configuration to do their work
        logger.info("Initializing Reconnect configuration");
        this.configProvider = new ConfigProviderImpl(false, metrics);

        // Create all the nodes in the merkle tree for all the services
        // TODO: Actually, we should reinitialize the config on each step along the migration path, so we should pass
        //       the config provider to the migration code and let it get the right version of config as it goes.
        onMigrate(state, deserializedVersion, trigger, metrics);
        if (trigger == EVENT_STREAM_RECOVERY) {
            // (FUTURE) Dump post-migration mod-service state
        }

        // Now that we have the state created, we are ready to create the dependency graph with Dagger
        initializeDagger(state, trigger, platformState);

        // And now that the entire dependency graph has been initialized, and we have config, and all migration has
        // been completed, we are prepared to initialize in-memory data structures. These specifically are loaded
        // from information held in state (especially those in special files).
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
        logger.debug("Initializing dagger");
        final var selfId = platform.getSelfId();
        final var nodeAddress = platform.getAddressBook().getAddress(selfId);
        // The Dagger component should be constructed every time we reach this point, even if
        // it exists. This avoids any problems with mutable singleton state by reconstructing
        // everything. But we must ensure the gRPC server in the old component is fully stopped.
        if (daggerApp != null) {
            shutdownGrpcServer();
        }
        // Fully qualified so as to not confuse javadoc
        daggerApp = com.hedera.node.app.DaggerHederaInjectionComponent.builder()
                .initTrigger(trigger)
                .softwareVersion(version)
                .configProvider(configProvider)
                .configProviderImpl(configProvider)
                .self(SelfNodeInfoImpl.of(nodeAddress, version))
                .platform(platform)
                .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
                .crypto(CryptographyHolder.get())
                .currentPlatformStatus(new CurrentPlatformStatusImpl(platform))
                .servicesRegistry(servicesRegistry)
                .bootstrapProps(new BootstrapProperties(false)) // TBD REMOVE
                .instantSource(InstantSource.system())
                .genesisRecordsConsensusHook((GenesisRecordsConsensusHook) genesisRecordsBuilder)
                .build();

        daggerApp.workingStateAccessor().setHederaState(state);
        daggerApp.platformStateAccessor().setPlatformState(platformState);
    }

    private boolean isDowngrade(
            final HederaSoftwareVersion deployedVersion, final SoftwareVersion deserializedVersion) {
        return deployedVersion.isBefore(deserializedVersion);
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
        final File file = getFileFromStorage(state, fileNum);
        if (file != null) {
            final var fileData = file.contents();
            daggerApp.feeManager().update(fileData);
        }
        logger.info("Fee schedule initialized");
    }

    private void initializeExchangeRateManager(@NonNull final HederaState state) {
        logger.info("Initializing exchange rates");
        final var filesConfig = configProvider.getConfiguration().getConfigData(FilesConfig.class);
        final var fileNum = filesConfig.exchangeRates();
        final var file = getFileFromStorage(state, fileNum);
        if (file != null) {
            final var fileData = file.contents();
            daggerApp.exchangeRateManager().init(state, fileData);
        }
        logger.info("Exchange rates initialized");
    }

    private File getFileFromStorage(HederaState state, long fileNum) {
        final var readableFileStore = new ReadableStoreFactory(state).getStore(ReadableFileStore.class);
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        final var fileId = FileID.newBuilder()
                .fileNum(fileNum)
                .shardNum(hederaConfig.shard())
                .realmNum(hederaConfig.realm())
                .build();
        return readableFileStore.getFileLeaf(fileId);
    }

    public static boolean shouldDump(@NonNull final InitTrigger trigger, @NonNull final DumpCheckpoint checkpoint) {
        return trigger == EVENT_STREAM_RECOVERY && selectedDumpCheckpoints().contains(checkpoint);
    }
}
