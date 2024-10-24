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

package com.hedera.node.app;

import static com.hedera.node.app.records.schemas.V0490BlockRecordSchema.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_CONFIG_FILE_NAME;
import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_SETTINGS_FILE_NAME;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.setupGlobalMetrics;
import static com.swirlds.platform.config.internal.PlatformConfigUtils.checkConfiguration;
import static com.swirlds.platform.crypto.CryptoStatic.initNodeSecurity;
import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static com.swirlds.platform.system.InitTrigger.RECONNECT;
import static com.swirlds.platform.system.InitTrigger.RESTART;
import static com.swirlds.platform.system.SoftwareVersion.NO_VERSION;
import static com.swirlds.platform.system.SystemExitCode.CONFIGURATION_ERROR;
import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static com.swirlds.platform.system.SystemExitUtils.exitSystem;
import static com.swirlds.platform.system.address.AddressBookUtils.createRoster;
import static com.swirlds.platform.system.address.AddressBookUtils.initializeAddressBook;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.StateChanges.Builder;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.blocks.BlockStreamService;
import com.hedera.node.app.blocks.impl.BoundaryStateChangeListener;
import com.hedera.node.app.blocks.impl.KVStateChangeListener;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.GenesisNetworkInfo;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.OrderedServiceMigrator;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistry.Factory;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.tss.PlaceholderTssLibrary;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.types.StreamMode;
import com.swirlds.base.time.Time;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.RuntimeConstructable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.SystemEnvironmentConfigSource;
import com.swirlds.config.extensions.sources.SystemPropertiesConfigSource;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.Browser;
import com.swirlds.platform.CommandLineArgs;
import com.swirlds.platform.ParameterProvider;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.state.State;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point.
 *
 * <p>This class simply delegates to {@link Hedera}.
 */
public class ServicesMain implements SwirldMain {

    private static final Logger logger = LogManager.getLogger(ServicesMain.class);

    /**
     * The {@link SwirldMain} to actually use, depending on whether workflows are enabled.
     */
    private final SwirldMain delegate;

    /**
     * Create a new instance
     */
    public ServicesMain() {
        delegate = newHedera();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SoftwareVersion getSoftwareVersion() {
        return delegate.getSoftwareVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(@NonNull final Platform ignored, @NonNull final NodeId nodeId) {
        delegate.init(ignored, nodeId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MerkleRoot newMerkleStateRoot() {
        return delegate.newMerkleStateRoot();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        delegate.run();
    }

    /**
     * Launches Services directly, without use of the "app browser" from {@link Browser}. The
     * approximate startup sequence is:
     * <ol>
     *     <li>Scan the classpath for {@link RuntimeConstructable} classes,
     *     registering their no-op constructors as the default factories for their
     *     class ids.</li>
     *     <li>Create the application's {@link Hedera} singleton, which overrides
     *     the default factory for the stable {@literal 0x8e300b0dfdafbb1a} class
     *     id of the Services Merkle tree root with a reference to its
     *     {@link Hedera#newMerkleStateRoot()} method.</li>
     *     <li>Determine this node's <b>self id</b> by searching the <i>config.txt</i>
     *     in the working directory for any address book entries with IP addresses
     *     local to this machine; if there is there is more than one such entry,
     *     fail unless the command line args include a {@literal -local N} arg.</li>
     *     <li>Build a {@link Platform} instance from Services application metadata
     *     and the working directory <i>settings.txt</i>, providing the same
     *     {@link Hedera#newMerkleStateRoot()} method reference as the genesis state
     *     factory. (<b>IMPORTANT:</b> This step instantiates and invokes
     *     {@link SwirldState#init(Platform, InitTrigger, SoftwareVersion)}
     *     on a {@link MerkleStateRoot} instance that delegates the call back to our
     *     Hedera instance.)</li>
     *     <li>Call {@link Hedera#init(Platform, NodeId)} to complete startup phase
     *     validation and register notification listeners on the platform.</li>
     *     <li>Invoke {@link Platform#start()}.</li>
     * </ol>
     *
     * <p>Please see the <i>startup-phase-lifecycle.png</i> in this directory to visualize
     * the sequence of events in the startup phase and the centrality of the {@link Hedera}
     * singleton.
     *
     * @param args optionally, what node id to run; required if the address book is ambiguous
     */
    public static void main(final String... args) throws Exception {
        BootstrapUtils.setupConstructableRegistry();
        final Hedera hedera = newHedera();

        // Determine which node to run locally
        // Load config.txt address book file and parse address book
        final AddressBook diskAddressBook = loadAddressBook(DEFAULT_CONFIG_FILE_NAME);
        // parse command line arguments
        final CommandLineArgs commandLineArgs = CommandLineArgs.parse(args);

        // Only allow 1 node to be specified by the command line arguments.
        if (commandLineArgs.localNodesToStart().size() > 1) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Multiple nodes were supplied via the command line. Only one node can be started per java process.");
            exitSystem(NODE_ADDRESS_MISMATCH);
        }

        // get the list of configured nodes from the address book
        // for each node in the address book, check if it has a local IP (local to this computer)
        // additionally if a command line arg is supplied then limit matching nodes to that node id
        final List<NodeId> nodesToRun = getNodesToRun(diskAddressBook, commandLineArgs.localNodesToStart());
        // hard exit if no nodes are configured to run
        checkNodesToRun(nodesToRun);

        final NodeId selfId = ensureSingleNode(nodesToRun, commandLineArgs.localNodesToStart());

        final SoftwareVersion version = hedera.getSoftwareVersion();
        logger.info("Starting node {} with version {}", selfId, version);

        final var configuration = buildConfiguration();
        final var keysAndCerts =
                initNodeSecurity(diskAddressBook, configuration).get(selfId);

        setupGlobalMetrics(configuration);
        final var metrics = getMetricsProvider().createPlatformMetrics(selfId);
        final var time = Time.getCurrent();
        final var fileSystemManager = FileSystemManager.create(configuration);
        final var recycleBin =
                RecycleBin.create(metrics, configuration, getStaticThreadManager(), time, fileSystemManager, selfId);

        // Create initial state for the platform
        final var reservedState = getInitialState(
                configuration,
                recycleBin,
                version,
                hedera::newMerkleStateRoot,
                SignedStateFileUtils::readState,
                Hedera.APP_NAME,
                Hedera.SWIRLD_NAME,
                selfId,
                diskAddressBook);

        final var cryptography = CryptographyFactory.create();
        CryptographyHolder.set(cryptography);
        // the AddressBook is not changed after this point, so we calculate the hash now
        cryptography.digestSync(diskAddressBook);

        // Initialize the Merkle cryptography
        final var merkleCryptography = MerkleCryptographyFactory.create(configuration, cryptography);
        MerkleCryptoFactory.set(merkleCryptography);

        // Create the platform context
        final var platformContext = PlatformContext.create(
                configuration,
                Time.getCurrent(),
                metrics,
                cryptography,
                FileSystemManager.create(configuration),
                recycleBin,
                merkleCryptography);

        final var initialState = reservedState.state();
        final var stateHash = reservedState.hash();

        SignedState signedState = initialState.get();
        State state = (State) signedState.getState();

        final SoftwareVersion previousSoftwareVersion;
        final InitTrigger trigger;

        if (initialState.get().isGenesisState()) {
            previousSoftwareVersion = NO_VERSION;
            trigger = GENESIS;
        } else {
            previousSoftwareVersion =
                    initialState.get().getState().getReadablePlatformState().getCreationSoftwareVersion();
            trigger = RESTART;
        }

        // We do not support downgrading from one version to an older version.
        ServicesSoftwareVersion deserializedVersion = getServicesSoftwareVersion(previousSoftwareVersion);

        // Migrate and initialize the State API before creating the platform
        migrateAndInitializeServices(state, deserializedVersion, trigger, metrics, hedera);

        // Initialize the address book and set on platform builder
        final var addressBook = initializeAddressBook(selfId, version, initialState, diskAddressBook, platformContext);

        // Follow the Inversion of Control pattern by injecting all needed dependencies into the PlatformBuilder.
        final var roster = createRoster(addressBook);
        final var platformBuilder = PlatformBuilder.create(
                        Hedera.APP_NAME, Hedera.SWIRLD_NAME, version, initialState, selfId)
                .withPlatformContext(platformContext)
                .withConfiguration(configuration)
                .withAddressBook(addressBook)
                // C.f. https://github.com/hashgraph/hedera-services/issues/14751,
                // we need to choose the correct roster in the following cases:
                //  - At genesis, a roster loaded from disk
                //  - At restart, the active roster in the saved state
                //  - At upgrade boundary, the candidate roster in the saved state IF
                //    that state satisfies conditions (e.g. the roster has been keyed)
                .withRoster(roster)
                .withKeysAndCerts(keysAndCerts);

        hedera.setInitialStateHash(stateHash);
        // IMPORTANT: A surface-level reading of this method will undersell the centrality
        // of the Hedera instance. It is actually omnipresent throughout both the startup
        // and runtime phases of the application.
        //
        // Let's see why. When we build the platform, the builder will either:
        //   (1) Create a genesis state; or,
        //   (2) Deserialize a saved state.
        // In both cases the state object will be created by the hedera::newState method
        // reference bound to our Hedera instance. Because,
        //   (1) We provided this method as the genesis state factory right above; and,
        //   (2) Our Hedera instance's constructor registered its newState() method with the
        //       ConstructableRegistry as the factory for the Services Merkle tree class id.
        //
        // Now, note that hedera::newState returns MerkleStateRoot instances that delegate
        // their lifecycle methods to an injected instance of MerkleStateLifecycles---and
        // hedera::newState injects an instance of MerkleStateLifecyclesImpl which primarily
        // delegates these calls back to the Hedera instance itself.
        //
        // Thus, the Hedera instance centralizes nearly all the setup and runtime logic for the
        // application. It implements this logic by instantiating a Dagger2 @Singleton component
        // whose object graph roots include the Ingest, PreHandle, Handle, and Query workflows;
        // as well as other infrastructure components that need to be initialized or accessed
        // at specific points in the Swirlds application lifecycle.
        final Platform platform = platformBuilder.build();
        hedera.init(platform, selfId);
        platform.start();
        hedera.run();
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
     * @param state               current state
     * @param deserializedVersion version deserialized
     * @param trigger             trigger that is calling migration
     * @return the state changes caused by the migration
     */
    private static List<Builder> migrateAndInitializeServices(
            @NonNull final State state,
            @Nullable final ServicesSoftwareVersion deserializedVersion,
            @NonNull final InitTrigger trigger,
            @NonNull final Metrics metrics,
            @NonNull final Hedera hedera) {
        if (trigger != GENESIS) {
            requireNonNull(deserializedVersion, "Deserialized version cannot be null for trigger " + trigger);
        }

        final InstantSource instantSource = InstantSource.system();
        ConfigProviderImpl configProvider;
        ServiceMigrator serviceMigrator = new OrderedServiceMigrator();
        final BoundaryStateChangeListener boundaryStateChangeListener = new BoundaryStateChangeListener();
        final KVStateChangeListener kvStateChangeListener = new KVStateChangeListener();
        Factory registryFactory = ServicesRegistryImpl::new;
        BootstrapConfigProviderImpl bootstrapConfigProvider = new BootstrapConfigProviderImpl();

        // Until all service schemas are migrated, MerkleStateRoot will not be able to implement
        // the States API, even if it already has all its children in the Merkle tree, as it will lack
        // state definitions for those children. (And note services may even require migrations for
        // those children to be usable with the current version of the software.)
        ServicesRegistry servicesRegistry =
                registryFactory.create(ConstructableRegistry.getInstance(), bootstrapConfigProvider.configuration());
        final var bootstrapConfig = bootstrapConfigProvider.getConfiguration();
        StreamMode streamMode =
                bootstrapConfig.getConfigData(BlockStreamConfig.class).streamMode();

        configProvider = new ConfigProviderImpl(trigger == GENESIS, metrics);
        final ServicesSoftwareVersion version = getNodeStartupVersion(bootstrapConfig);

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
            final var readableStore =
                    new ReadablePlatformStateStore(state.getReadableStates(PlatformStateService.NAME));
            final var genesisRoster = createRoster(requireNonNull(readableStore.getAddressBook()));

            genesisNetworkInfo = new GenesisNetworkInfo(genesisRoster, ledgerConfig.id());
        }
        final List<Builder> migrationStateChanges = new ArrayList<>();
        if (isNotEmbedded(instantSource)) {
            if (!(state instanceof MerkleStateRoot merkleStateRoot)) {
                throw new IllegalStateException("State must be a MerkleStateRoot");
            }
            migrationStateChanges.addAll(merkleStateRoot.platformStateInitChangesOrThrow());
        }

        AppContext appContext = new AppContextImpl(
                instantSource,
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())),
                hedera);

        BlockStreamService blockStreamService = new BlockStreamService();
        registerServiceRuntimeConstructables(servicesRegistry, blockStreamService, appContext);

        // (FUTURE) In principle, the FileService could actually change the active configuration during a
        // migration, which implies we should be passing the config provider and not a static configuration
        // here; but this is a currently unneeded affordance
        blockStreamService.resetMigratedLastBlockHash();
        final var migrationChanges = serviceMigrator.doMigrations(
                state,
                servicesRegistry,
                deserializedVersion,
                version,
                configProvider.getConfiguration(),
                genesisNetworkInfo,
                metrics);
        migrationStateChanges.addAll(migrationChanges);
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
        return migrationStateChanges;
    }

    // Register all service schema RuntimeConstructable factories before platform init
    private static void registerServiceRuntimeConstructables(
            ServicesRegistry servicesRegistry, BlockStreamService blockStreamService, AppContext appContext) {

        Function<AppContext, TssBaseService> tssBaseServiceFactory = (appCtx -> new TssBaseServiceImpl(
                appCtx,
                ForkJoinPool.commonPool(),
                ForkJoinPool.commonPool(),
                new PlaceholderTssLibrary(),
                ForkJoinPool.commonPool()));
        TssBaseService tssBaseService = tssBaseServiceFactory.apply(appContext);
        ContractService contractServiceImpl = new ContractServiceImpl(appContext);

        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        contractServiceImpl,
                        new FileServiceImpl(),
                        tssBaseService,
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        blockStreamService,
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl(),
                        new RosterService(),
                        PLATFORM_STATE_SERVICE)
                .forEach(servicesRegistry::register);
    }

    private static boolean isNotEmbedded(InstantSource instantSource) {
        return instantSource == InstantSource.system();
    }

    private static ServicesSoftwareVersion getServicesSoftwareVersion(SoftwareVersion previousSoftwareVersion) {
        ServicesSoftwareVersion deserializedVersion = null;
        if (previousSoftwareVersion instanceof ServicesSoftwareVersion servicesSoftwareVersion) {
            deserializedVersion = servicesSoftwareVersion;
        } else if (previousSoftwareVersion instanceof HederaSoftwareVersion hederaSoftwareVersion) {
            deserializedVersion = new ServicesSoftwareVersion(
                    hederaSoftwareVersion.servicesVersion(), hederaSoftwareVersion.configVersion());
        } else {
            if (previousSoftwareVersion != null) {
                logger.fatal("Deserialized state not created with Hedera software");
                throw new IllegalStateException("Deserialized state not created with Hedera software");
            }
        }
        return deserializedVersion;
    }

    private static ServicesSoftwareVersion getNodeStartupVersion(@NonNull final Configuration config) {
        final var versionConfig = config.getConfigData(VersionConfig.class);
        return new ServicesSoftwareVersion(
                versionConfig.servicesVersion(),
                config.getConfigData(HederaConfig.class).configVersion());
    }

    private static void unmarkMigrationRecordsStreamed(@NonNull final State state) {
        final var blockServiceState = state.getWritableStates(BlockRecordService.NAME);
        final var blockInfoState = blockServiceState.<BlockInfo>getSingleton(BLOCK_INFO_STATE_KEY);
        final var currentBlockInfo = requireNonNull(blockInfoState.get());
        final var nextBlockInfo =
                currentBlockInfo.copyBuilder().migrationRecordsStreamed(false).build();
        blockInfoState.put(nextBlockInfo);
        logger.info("Unmarked post-upgrade work as done");
        ((WritableSingletonStateBase<BlockInfo>) blockInfoState).commit();
    }
    /**
     * Build the configuration for this node.
     *
     * @return the configuration
     */
    @NonNull
    private static Configuration buildConfiguration() {
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSource(SystemEnvironmentConfigSource.getInstance())
                .withSource(SystemPropertiesConfigSource.getInstance());
        rethrowIO(() ->
                BootstrapUtils.setupConfigBuilder(configurationBuilder, getAbsolutePath(DEFAULT_SETTINGS_FILE_NAME)));
        final Configuration configuration = configurationBuilder.build();
        checkConfiguration(configuration);
        return configuration;
    }

    /**
     * Selects the node to run locally from either the command line arguments or the address book.
     *
     * @param nodesToRun        the list of nodes configured to run based on the address book.
     * @param localNodesToStart the node ids specified on the command line.
     * @return the node which should be run locally.
     * @throws ConfigurationException if more than one node would be started or the requested node is not configured.
     */
    private static NodeId ensureSingleNode(
            @NonNull final List<NodeId> nodesToRun, @NonNull final Set<NodeId> localNodesToStart) {
        requireNonNull(nodesToRun);
        requireNonNull(localNodesToStart);
        // If no node is specified on the command line and detection by AB IP address is ambiguous, exit.
        if (nodesToRun.size() > 1 && localNodesToStart.isEmpty()) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Multiple nodes are configured to run. Only one node can be started per java process.");
            exitSystem(NODE_ADDRESS_MISMATCH);
            throw new ConfigurationException(
                    "Multiple nodes are configured to run. Only one node can be started per java process.");
        }

        // If a node is specified on the command line, use that node.
        final NodeId requestedNodeId = localNodesToStart.stream().findFirst().orElse(null);

        // If a node is specified on the command line but does not have a matching local IP address in the AB, exit.
        if (nodesToRun.size() > 1 && !nodesToRun.contains(requestedNodeId)) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "The requested node id {} is not configured to run. Please check the address book.",
                    requestedNodeId);
            exitSystem(NODE_ADDRESS_MISMATCH);
            throw new ConfigurationException(String.format(
                    "The requested node id %s is not configured to run. Please check the address book.",
                    requestedNodeId));
        }

        // Return either the node requested via the command line or the only matching node from the AB.
        return requestedNodeId != null ? requestedNodeId : nodesToRun.get(0);
    }

    /**
     * Loads the address book from the specified path.
     *
     * @param addressBookPath the relative path and file name of the address book.
     * @return the address book.
     */
    private static AddressBook loadAddressBook(@NonNull final String addressBookPath) {
        requireNonNull(addressBookPath);
        try {
            final LegacyConfigProperties props =
                    LegacyConfigPropertiesLoader.loadConfigFile(FileUtils.getAbsolutePath(addressBookPath));
            props.appConfig().ifPresent(c -> ParameterProvider.getInstance().setParameters(c.params()));
            return props.getAddressBook();
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Error loading address book", e);
            exitSystem(CONFIGURATION_ERROR);
            throw e;
        }
    }

    private static Hedera newHedera() {
        return new Hedera(
                ConstructableRegistry.getInstance(),
                ServicesRegistryImpl::new,
                new OrderedServiceMigrator(),
                InstantSource.system(),
                appContext -> new TssBaseServiceImpl(
                        appContext,
                        ForkJoinPool.commonPool(),
                        ForkJoinPool.commonPool(),
                        new PlaceholderTssLibrary(),
                        ForkJoinPool.commonPool()));
    }
}
