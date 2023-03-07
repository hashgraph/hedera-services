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

package com.hedera.node.app;

import static com.hedera.node.app.service.mono.ServicesState.EMPTY_HASH;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.node.app.spi.config.PropertyNames.HEDERA_FIRST_USER_ENTITY;
import static com.hedera.node.app.spi.config.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.protobuf.ByteString;
import com.hedera.node.app.grpc.GrpcServiceBuilder;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.admin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.mono.context.StateChildrenProvider;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.context.properties.SerializableSemVers;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.StateVersions;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.network.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.MerkleHederaState.MerkleWritableStates;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.Round;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.platform.gui.SwirldsGui;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.helidon.grpc.server.GrpcRouting;
import io.helidon.grpc.server.GrpcServer;
import io.helidon.grpc.server.GrpcServerConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * Represents the Hedera Consensus Node.
 *
 * <p>This is the main entry point for the Hedera Consensus Node. It contains initialization logic for the
 * node, including its state. It constructs some artifacts for gluing the mono-service with the modular
 * service infrastructure. It constructs the Dagger dependency tree, and manages the gRPC server, and in all
 * other ways, controls execution of the node. If you want to understand our system, this is a great place to start!
 */
public final class Hedera implements SwirldMain {
    private static final Logger logger = LogManager.getLogger(Hedera.class);
    // This should come from configuration, NOT be hardcoded.
    public static final int MAX_SIGNED_TXN_SIZE = 6144;

    /**
     * Defines the registration information for a service.
     *
     * @param name The name of the service.
     * @param service The service implementation itself.
     * @param registry The {@link MerkleSchemaRegistry} with which the service registers its schemas.
     */
    private record ServiceRegistration(
            @NonNull String name,
            @NonNull Service service,
            @NonNull MerkleSchemaRegistry registry) {
    }

    /** The registry of all known services */
    private final Map<String, ServiceRegistration> serviceRegistry;
    /** The current version of THIS software */
    private final SerializableSemVers version;
    /** The BootstrapProperties for this node */
    private final BootstrapProperties bootstrapProps;
    /** A latch used to signal shutdown of the gRPC server */
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    /** The Hashgraph Platform. This is set during state initialization. */
    private Platform platform;
    /** Used to interface with the mono-service. */
    private StateChildrenProvider stateChildren;

    /**
     * Dependencies managed by Dagger. Set during state initialization. The mono-service requires this object,
     * but none of the rest of the system (and particularly the modular implementation) uses it directly. Rather,
     * it is created and used to initialize the system, and more concrete dependencies are used from there.
     */
    private HederaApp daggerApp;

    /*==================================================================================================================
     *
     * Hedera Object Construction.
     *
     =================================================================================================================*/

    /**
     * Create a new Hedera instance.
     *
     * @param constructableRegistry The registry to use during the deserialization process
     * @param bootstrapProps The bootstrap properties
     */
    Hedera(@NonNull final ConstructableRegistry constructableRegistry,
           @NonNull final BootstrapProperties bootstrapProps) {

        // Load properties, configuration, and other things that can be done before a state is created.
        this.bootstrapProps = Objects.requireNonNull(bootstrapProps);

        // Read the software version
        version = SEMANTIC_VERSIONS.deployedSoftwareVersion();
        logger.info("Creating Hedera Consensus Node v{} with HAPI v{}",
                version.getServices(), version.getProto());

        // Create all the service implementations, and register their schemas.
        logger.info("Registering schemas for services");
        final var path = System.getProperty("merkle.db.path", null); // Might want to move to Bootstrap props
        serviceRegistry = createServicesRegistry(constructableRegistry, path == null ? null : Path.of(path));
        serviceRegistry.values().forEach(reg ->
                logger.info("Registered service {} with implementation {}", reg.name, reg.service.getClass()));

        // Register MerkleHederaState with the ConstructableRegistry, so we can use a constructor
        // OTHER THAN the default constructor to make sure it has the config and other info
        // it needs to be created correctly.
        try {
            logger.debug("Register MerkleHederaState with ConstructableRegistry");
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(MerkleHederaState.class, this::newState));
        } catch (ConstructableRegistryException e) {
            logger.error("Failed to register MerkleHederaState with ConstructableRegistry", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Create all service implementations and register their schemas. Return these as a map of
     * service name to {@link ServiceRegistration}. Later, when we migrate, we will use this map
     * to migrate each service to its latest schema.
     */
    private Map<String, ServiceRegistration> createServicesRegistry(
            @NonNull final ConstructableRegistry constructableRegistry,
            @Nullable final Path storageDir) {

        final var services = Map.of(
                ConsensusService.NAME, new ConsensusServiceImpl(),
                ContractService.NAME, new ContractServiceImpl(),
                FileService.NAME, new FileServiceImpl(),
                FreezeService.NAME, new FreezeServiceImpl(),
                NetworkService.NAME, new NetworkServiceImpl(),
                ScheduleService.NAME, new ScheduleServiceImpl(),
                TokenService.NAME, new TokenServiceImpl(),
                UtilService.NAME, new UtilServiceImpl());

        final var map = new HashMap<String, ServiceRegistration>();
        for (final var entry : services.entrySet()) {
            final var serviceName = entry.getKey();
            final var service = entry.getValue();
            final var registry = new MerkleSchemaRegistry(constructableRegistry, storageDir, serviceName);
            service.registerSchemas(registry);
            map.put(serviceName, new ServiceRegistration(serviceName, service, registry));
        }

        return map;
    }

    /**
     * {@inheritDoc}
     *
     * Called immediately after the constructor to get the version of this software.
     * In an upgrade scenario, this version will be greater than the one in the saved
     * state.
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
     * Called by the platform <b>ONLY</b> during genesis (that is, if there is no saved state). However,
     * it is also called indirectly by {@link ConstructableRegistry} due to registration in this class'
     * constructor.
     *
     * @return A new {@link SwirldState} instance.
     */
    @Override
    @NonNull
    public SwirldState newState() {
        return new MerkleHederaState(this::onPreHandle, this::onHandleConsensusRound, this::onStateInitialized);
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
    private void onStateInitialized(
            @NonNull final MerkleHederaState state,
            @NonNull final Platform platform,
            @NonNull final SwirldDualState dualState,
            @NonNull final InitTrigger trigger,
            @NonNull final SoftwareVersion previousVersion) {

        //noinspection ConstantValue
        assert dualState != null : "Platform should never pass a null dual state";
        logger.info("Initializing Hedera state with trigger {} and previous version {}",
                trigger, previousVersion);

        // We do not support downgrading from one version to an older version.
        final var deserializedVersion = (SerializableSemVers) previousVersion;
        if (isDowngrade(version, deserializedVersion)) {
            logger.error("Fatal error, state source version {} is after node software version {}",
                    deserializedVersion, version);
            System.exit(1);
        }

        // This is the *FIRST* time in the initialization sequence that we have access to the platform. Grab it!
        this.platform = platform;

        // Different paths for different triggers.
        switch (trigger) {
            case GENESIS -> genesis(state);
            case RESTART -> restart(state, dualState, deserializedVersion);
            case RECONNECT -> reconnect();
            case EVENT_STREAM_RECOVERY -> eventStreamRecovery();
        }

        // Since we now have an "app" instance, we can update the dual state accessor.
        // This is *ONLY* used by to produce a log summary after a freeze. We should refactor
        // to not have a global reference to this.
        updateDualState(dualState);
    }

    /**
     * Called by this class when we detect it is time to do migration.
     */
    private void onMigrate(
            @NonNull final MerkleHederaState state,
            @NonNull final SerializableSemVers deserializedVersion) {
        final var previousVersion = PbjConverter.toPbj(deserializedVersion.getServices());
        final var currentVersion = PbjConverter.toPbj(version.getServices());
        logger.info("Migrating from version {} to {}", previousVersion, currentVersion);
        for (final var registration : serviceRegistry.values()) {
            // TODO We should have metrics here to keep track of how long it takes to migrate each service
            registration.registry.migrate(state, previousVersion, currentVersion);
            logger.info("Migrated Service {}", registration.name);
        }
    }

    /*==================================================================================================================
     *
     * Initialization Step 3: Initialize the app. Happens once at startup.
     *
     =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * Called <b>AFTER</b> init and migrate have been called on the state (either the new state
     * created from {@link #newState()} or an instance of {@link MerkleHederaState} created by
     * the platform and loaded from the saved state).
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        assert this.platform == platform : "Platform should be the same instance";
        logger.info("Initializing Hedera app with HederaNode#{}", nodeId);

        // Ensure the prefetch queue is created and thread pool is active instead of waiting
        // for lazy-initialization to take place
        daggerApp.prefetchProcessor();

        // Check that UTF-8 is in use. Otherwise, the node will be subject to subtle bugs
        // in string handling that will lead to ISS.
        final var defaultCharset = daggerApp.nativeCharset().get();
        if (!isUTF8(defaultCharset)) {
            logger.error("Fatal precondition violation in HederaNode#{}:" +
                            "default charset is {} and not UTF-8", daggerApp.nodeId(), defaultCharset);
            daggerApp.systemExits().fail(1);
        }

        // Check that the digest factory supports SHA-384.
        final var digestFactory = daggerApp.digestFactory();
        if (!sha384DigestIsAvailable(digestFactory)) {
            logger.error("Fatal precondition violation in HederaNode#{}:" +
                            "digest factory does not support SHA-384", daggerApp.nodeId());
            daggerApp.systemExits().fail(1);
        }

        // Finish initialization
        try {
            Locale.setDefault(Locale.US);
            logger.info("Locale to set to US en");

            validateLedgerState();
            logger.info("Ledger state ok");

            configurePlatform();
            logger.info("Platform is configured w/ callbacks and stats registered");

            exportAccountsIfDesired();
            logger.info("Accounts exported (if requested)");
        } catch (final Exception e) {
            logger.error("Fatal precondition violation in HederaNode#{}", daggerApp.nodeId(), e);
            daggerApp.systemExits().fail(1);
        }
    }

    /** Gets whether the default charset is UTF-8. */
    private boolean isUTF8(@NonNull final Charset defaultCharset) {
        if (!UTF_8.equals(defaultCharset)) {
            logger.error("Default charset is {}, not UTF-8", defaultCharset);
            return false;
        }
        return true;
    }

    /** Gets whether the sha384 digest is available */
    private boolean sha384DigestIsAvailable(@NonNull final NamedDigestFactory digestFactory) {
        try {
            digestFactory.forName("SHA-384");
            return true;
        } catch (final NoSuchAlgorithmException e) {
            logger.error(e);
            return false;
        }
    }

    private void exportAccountsIfDesired() {
        daggerApp.accountsExporter().toFile(daggerApp.workingState().accounts());
    }

    private void configurePlatform() {
        daggerApp.statsManager().initializeFor(platform);
    }

    private void validateLedgerState() {
        daggerApp.ledgerValidator().validate(daggerApp.workingState().accounts());
        daggerApp.nodeInfo().validateSelfAccountIfStaked();
        final var notifications = daggerApp.notificationEngine().get();
        notifications.register(PlatformStatusChangeListener.class, daggerApp.statusChangeListener());
        notifications.register(ReconnectCompleteListener.class, daggerApp.reconnectListener());
        notifications.register(StateWriteToDiskCompleteListener.class, daggerApp.stateWriteToDiskListener());
        notifications.register(NewSignedStateListener.class, daggerApp.newSignedStateListener());
        notifications.register(IssListener.class, daggerApp.issListener());
    }

    /*==================================================================================================================
     *
     * Run the app.
     *
     =================================================================================================================*/

    /**
     * {@inheritDoc}
     *
     * Called by the platform after <b>ALL</b> initialization to start the gRPC servers and begin operation.
     */
    @Override
    public void run() {
        // Start the gRPC servers.
        logger.info("Starting mono gRPC server");
        daggerApp.grpcStarter().startIfAppropriate();

        logger.info("Starting modular gRPC server");
        final var port = daggerApp.nodeLocalProperties().workflowsPort();

        // Create the Ingest and Query workflows. While we are in transition, some required facilities come
        // from `hedera-app`, and some from `mono-service`. Eventually we'll transition all facilities to be
        // from the app module. But this code can be ignorant of that complexity, since the Dagger dependency
        // graph takes care of it.
        final var ingestWorkflow = daggerApp.ingestComponentFactory().get().create().ingestWorkflow();
        final var queryWorkflow = daggerApp.queryComponentFactory().get().create().queryWorkflow();

        // Setup and start the grpc server.
        // At some point I'd like to somehow move the metadata for which transactions are supported
        // by a service to the service, instead of having them all hardcoded here. It isn't clear
        // yet what that API would look like, so for now we do it this way. Maybe we should have
        // a set of annotations that generate the metadata, or maybe we have some code. Whatever
        // we do should work also with workflows.
        final var grpcServer = GrpcServer.create(
                GrpcServerConfiguration.builder().port(port).build(),
                GrpcRouting.builder()
                        .register(new GrpcServiceBuilder("proto.ConsensusService", ingestWorkflow, queryWorkflow)
                                .transaction("createTopic")
                                .transaction("updateTopic")
                                .transaction("deleteTopic")
                                .query("getTopicInfo")
                                .transaction("submitMessage")
                                .build(daggerApp.platform().getMetrics()))
                        .build());
        grpcServer.whenShutdown().thenAccept(server -> shutdownLatch.countDown());
        grpcServer.start();

        // Block this main thread until the server terminates.
        // TODO: Uncomment this code once we enable all operations to work with workflows.
        // Currently, we are enabling each operation step-by-step to work with new Grpc binding.
        //        try {
        //            shutdownLatch.await();
        //        } catch (InterruptedException ignored) {
        //            // An interrupt on this thread means we want to shut down the server.
        //            shutdown();
        //            Thread.currentThread().interrupt();
        //        }
    }

    /**
     * Invoked by the platform to handle pre-consensus events. This only happens after {@link #run()} has been called.
     */
    private void onPreHandle(@NonNull final Event event) {
        // TBD: The pre-handle workflow should be created by dagger and just be something we can delegate to here.
    }

    /**
     * Invoked by the platform to handle a round of consensus events.  This only happens after {@link #run()} has been
     * called.
     */
    private void onHandleConsensusRound(@NonNull final Round round, @NonNull final SwirldDualState dualState) {
        // TBD: The handle workflow should be created by dagger and just be something we can delegate to here.
    }

    /*==================================================================================================================
     *
     * Shutdown of a Hedera node
     *
     =================================================================================================================*/

    /**
     * Called to perform orderly shutdown of the gRPC servers.
     */
    public void shutdown() {
        shutdownLatch.countDown();
    }

    /*==================================================================================================================
     *
     * Genesis Initialization
     *
     =================================================================================================================*/

    /** Implements the code flow for initializing the state of a new Hedera node with NO SAVED STATE. */
    private void genesis(@NonNull final MerkleHederaState state) {
        logger.debug("Genesis Initialization");

        // Create all the nodes in the merkle tree for all the services
        onMigrate(state, version);

        // Initialize the tree with all the pre-built state we need for a basic system,
        // such as the accounts for initial users. Some services might populate their data
        // in their Schema migration handlers.
        final var seqStart = bootstrapProps.getLongProperty(HEDERA_FIRST_USER_ENTITY);
        logger.debug("Creating genesis children at seqStart = {}", seqStart);
        createSpecialGenesisChildren(state, platform.getAddressBook(), seqStart);

        // Now that we have the state created, we are ready to create the dependency graph with Dagger
        initializeDagger(state);

        // Store the version in state (ideally this would move to be something that is done when the
        // network service runs its schema migration)
        logger.debug("Saving version information in state");
        final var networkCtx = stateChildren.networkCtx();
        networkCtx.setStateVersion(StateVersions.CURRENT_VERSION);

        // TODO Not sure
        daggerApp.initializationFlow().runWith(stateChildren, bootstrapProps);
        daggerApp.sysAccountsCreator().ensureSystemAccounts(
                daggerApp.backingAccounts(),
                daggerApp.workingState().addressBook());
        daggerApp.sysFilesManager().createManagedFilesIfMissing();
        daggerApp.stakeStartupHelper().doGenesisHousekeeping(stateChildren.addressBook());

        // For now, we have to update the stake details manually. When we have dynamic address book,
        // then we'll move this to be shared with all state initialization flows and not just genesis
        // and restart.
        logger.debug("Initializing stake details");
        daggerApp.sysFilesManager().updateStakeDetails();

        // TODO Not sure
        networkCtx.markPostUpgradeScanStatus();
    }

    /**
     * Create the special children of the root node that are needed for genesis.
     *
     * <p>It would be good to see if we can break this logic up and have it be part of the individual
     * modules. For example, it would be good if the first Schema version in NetworkServices would
     * put this genesis state into place. However, for to that work, we need to make some information
     * available at migration, such as the initial sequence number and the address book. We either
     * have to make that information globally available to services, or we need to have Dagger injection for
     * schemas to provide ad-hoc dependencies.
     */
    private void createSpecialGenesisChildren(
            @NonNull final MerkleHederaState state,
            @NonNull final AddressBook addressBook,
            final long seqStart) {

        // Prepopulate the NetworkServices state with default values for genesis
        // Can these be moved to Schema version 1 of NetworkServicesImpl?
        final var networkStates = state.createWritableStates(NetworkService.NAME);
        networkStates.getSingleton(NetworkServiceImpl.CONTEXT_KEY).put(genesisNetworkCtxWith(seqStart));
        networkStates.getSingleton(NetworkServiceImpl.RUNNING_HASHES_KEY).put(genesisRunningHashLeaf());
        networkStates.getSingleton(NetworkServiceImpl.SPECIAL_FILES_KEY).put(new MerkleSpecialFiles());
        buildStakingInfoMap(addressBook, bootstrapProps, networkStates.get(NetworkServiceImpl.STAKING_KEY));
        ((MerkleWritableStates) networkStates).commit();

        // Prepopulate the ScheduleServices state with default values for genesis
        final var scheduledStates = state.createWritableStates(ScheduleService.NAME);
        final var neverScheduledState = new MerkleScheduledTransactionsState();
        scheduledStates.getSingleton(ScheduleServiceImpl.SCHEDULING_STATE_KEY).put(neverScheduledState);
        ((MerkleWritableStates) scheduledStates).commit();
    }

    private RecordsRunningHashLeaf genesisRunningHashLeaf() {
        final var genesisRunningHash = new RunningHash();
        genesisRunningHash.setHash(EMPTY_HASH);
        return new RecordsRunningHashLeaf(genesisRunningHash);
    }

    private MerkleNetworkContext genesisNetworkCtxWith(final long seqStart) {
        return new MerkleNetworkContext(null,
                new SequenceNumber(seqStart), seqStart - 1, new ExchangeRates());
    }

    private void buildStakingInfoMap(
            final AddressBook addressBook,
            final BootstrapProperties bootstrapProperties,
            final WritableKVState<EntityNum, MerkleStakingInfo> stakingInfos) {
        final var numberOfNodes = addressBook.getSize();
        long maxStakePerNode = bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT) / numberOfNodes;
        long minStakePerNode = maxStakePerNode / 2;
        for (int i = 0; i < numberOfNodes; i++) {
            final var nodeNum = EntityNum.fromLong(addressBook.getAddress(i).getId());
            final var info = new MerkleStakingInfo(bootstrapProperties);
            info.setMinStake(minStakePerNode);
            info.setMaxStake(maxStakePerNode);
            stakingInfos.put(nodeNum, info);
        }
    }

    /*==================================================================================================================
     *
     * Restart Initialization
     *
     =================================================================================================================*/

    /** Initialize flow for when a node has been restarted. This means it was started from a saved state. */
    private void restart(
            @NonNull final MerkleHederaState state,
            @NonNull final SwirldDualState dualState,
            @NonNull final SerializableSemVers deserializedVersion) {
        logger.debug("Restart Initialization");

        // Migrate to the most recent state, if needed
        final boolean upgrade = isUpgrade(version, deserializedVersion);
        if (upgrade) {
            logger.debug("Upgrade detected");
            onMigrate(state, deserializedVersion);
        }

        // Now that we have the state created, we are ready to create the all the dagger dependencies
        initializeDagger(state);

        // We may still want to change the address book without an upgrade. But note
        // that without a dynamic address book, this MUST be a no-op during reconnect.
        final var stakingInfo = stateChildren.stakingInfo();
        final var networkCtx = stateChildren.networkCtx();
        daggerApp.stakeStartupHelper().doRestartHousekeeping(stateChildren.addressBook(), stakingInfo);
        if (upgrade) {
            dualState.setFreezeTime(null);
            networkCtx.discardPreparedUpgradeMeta();
            if (version.hasMigrationRecordsFrom(deserializedVersion)) {
                networkCtx.markMigrationRecordsNotYetStreamed();
            }
        }

        // This updates the working state accessor with our children
        daggerApp.initializationFlow().runWith(stateChildren, bootstrapProps);
        if (upgrade) {
            daggerApp.stakeStartupHelper().doUpgradeHousekeeping(networkCtx, stateChildren.accounts(), stakingInfo);
        }

        // Once we have a dynamic address book, this will run unconditionally
        daggerApp.sysFilesManager().updateStakeDetails();
    }

    /*==================================================================================================================
     *
     * Reconnect Initialization
     *
     =================================================================================================================*/

    private void reconnect() {
        // No-op
    }

    /*==================================================================================================================
     *
     * Event Stream Recovery Initialization
     *
     =================================================================================================================*/

    private void eventStreamRecovery() {
        // No-op
    }

    /*==================================================================================================================
     *
     * Random private helper methods
     *
     =================================================================================================================*/

    private void initializeDagger(@NonNull final MerkleHederaState state) {
        logger.debug("Initializing dagger");
        final var selfId = platform.getSelfId().getId();
        if (daggerApp == null) {
            // Today, the alias map has to be constructed by walking over all accounts.
            // TODO Populate aliases properly
            final var aliases = new HashMap<ByteString, EntityNum>(); // Unfortunately, have to keep this Google protobuf dependency for the moment :-(
            stateChildren = state.getStateChildrenProvider(platform, aliases);
            final var nodeAddress = stateChildren.addressBook().getAddress(selfId);
            final var initialHash = stateChildren.runningHashLeaf().getRunningHash().getHash();
            // Fully qualified so as to not confuse javadoc
            daggerApp = com.hedera.node.app.DaggerHederaApp.builder()
                    .staticAccountMemo(nodeAddress.getMemo())
                    .bootstrapProps(bootstrapProps)
                    .initialHash(initialHash)
                    .platform(platform)
                    .consoleCreator(SwirldsGui::createConsole)
                    .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
                    .crypto(CryptographyHolder.get())
                    .selfId(selfId)
                    .build();
        }
    }

    private void updateDualState(SwirldDualState dualState) {
        daggerApp.dualStateAccessor().setDualState(dualState);
        logger.info(
                "Dual state includes freeze time={} and last frozen={}",
                dualState.getFreezeTime(),
                dualState.getLastFrozenTime());
    }

    private boolean isUpgrade(SerializableSemVers deployedVersion, SoftwareVersion deserializedVersion) {
        return deployedVersion.isAfter(deserializedVersion);
    }

    private boolean isDowngrade(SerializableSemVers deployedVersion, SoftwareVersion deserializedVersion) {
        return deployedVersion.isBefore(deserializedVersion);
    }
}
