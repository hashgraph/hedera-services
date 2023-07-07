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

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.spi.HapiUtils.parseAccount;
import static com.swirlds.common.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static com.swirlds.common.system.InitTrigger.GENESIS;
import static com.swirlds.common.system.InitTrigger.RESTART;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.info.CurrentPlatformStatusImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.networkadmin.ReadableRunningHashLeafStore;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.MerkleSchemaRegistry;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
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
import com.swirlds.common.system.transaction.Transaction;
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
 **********                    **********                                                                       *
 *******                          *******                                                                       *
 *****                              *****                                                                       *
 ****                                ****      ___           ___           ___           ___           ___      *
 **         HHHH          HHHH         **     /\  \         /\  \         /\  \         /\  \         /\  \     *
 **         HHHH          HHHH         **    /::\  \       /::\  \       /::\  \       /::\  \       /::\  \    *
 *          HHHHHHHHHHHHHHHHHH          *   /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \     /:/\:\  \   *
            HHHHHHHHHHHHHHHHHH             /::\~\:\  \   /:/  \:\__\   /::\~\:\  \   /::\~\:\  \   /::\~\:\  \  *
            HHHH          HHHH            /:/\:\ \:\__\ /:/__/ \:|__| /:/\:\ \:\__\ /:/\:\ \:\__\ /:/\:\ \:\__\ *
            HHHHHHHHHHHHHHHHHH            \:\~\:\ \/__/ \:\  \ /:/  / \:\~\:\ \/__/ \/_|::\/:/  / \/__\:\/:/  / *
 *          HHHHHHHHHHHHHHHHHH          *  \:\ \:\__\    \:\  /:/  /   \:\ \:\__\      |:|::/  /       \::/  /  *
 **         HHHH          HHHH         **   \:\ \/__/     \:\/:/  /     \:\ \/__/      |:|\/__/        /:/  /   *
 ***        HHHH          HHHH        ***    \:\__\        \::/__/       \:\__\        |:|  |         /:/  /    *
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
    static {
        // Helidon uses java.util.logging, so we need to set up the bridge before it has a chance to log anything
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    private static final Logger logger = LogManager.getLogger(Hedera.class);
    private static final int STATE_VERSION_NEWER_THAN_SOFTWARE_VERSION_EXIT_CODE = 10;
    private static final int VERSION_NOT_IN_SAVED_STATE_EXIT_CODE = 11;
    // This should come from configuration, NOT be hardcoded.
    public static final int MAX_SIGNED_TXN_SIZE = 6144;

    /**
     * Defines the registration information for a service.
     *
     * @param name     The name of the service.
     * @param service  The service implementation itself.
     * @param registry The {@link MerkleSchemaRegistry} with which the service registers its schemas.
     */
    private record ServiceRegistration(
            @NonNull String name, @NonNull Service service, @NonNull MerkleSchemaRegistry registry) {}

    /** Required for state management. Used by platform for deserialization of state. */
    private final ConstructableRegistry constructableRegistry;
    /** The registry of all known services */
    private final ServicesRegistry servicesRegistry;
    /** The current version of THIS software */
    private final HederaSoftwareVersion version;
    /** The Hashgraph Platform. This is set during state initialization. */
    private Platform platform;
    /** The configuration for this node */
    private ConfigProviderImpl configProvider;
    /**
     * Dependencies managed by Dagger. Set during state initialization. The mono-service requires this object, but none
     * of the rest of the system (and particularly the modular implementation) uses it directly. Rather, it is created
     * and used to initialize the system, and more concrete dependencies are used from there.
     */
    private HederaInjectionComponent daggerApp;

    /*==================================================================================================================
    *
    * Hedera Object Construction.
    *
    =================================================================================================================*/

    /**
     * Create a new Hedera instance.
     *
     * @param constructableRegistry The registry to use during the deserialization process
     */
    public Hedera(@NonNull final ConstructableRegistry constructableRegistry) {
        this.constructableRegistry = requireNonNull(constructableRegistry);

        // Print welcome message
        logger.info("Welcome to Hedera! Developed with love by the Open Source Community. "
                + "https://github.com/hashgraph/hedera-services");

        // Let the user know which mode they are starting in (DEV vs. TEST vs. PROD)
        final var bootstrapConfig = new ConfigProviderImpl(false).getConfiguration();
        final var hederaConfig = bootstrapConfig.getConfigData(HederaConfig.class);
        final var activeProfile = Profile.valueOf(hederaConfig.activeProfile());
        logger.info("Starting in {} mode", activeProfile);

        // Read the software version
        logger.debug("Loading Software Version");
        final var versionConfig = bootstrapConfig.getConfigData(VersionConfig.class);
        version = new HederaSoftwareVersion(versionConfig.hapiVersion(), versionConfig.servicesVersion());
        logger.info(
                "Creating Hedera Consensus Node {} with HAPI {}",
                () -> HapiUtils.toString(version.getHapiVersion()),
                () -> HapiUtils.toString(version.getServicesVersion()));

        // Create all the service implementations
        logger.info("Registering schemas for services");
        // FUTURE: Use the service loader framework to load these services!
        this.servicesRegistry = new ServicesRegistryImpl(Set.of(
                new ConsensusServiceImpl(),
                new ContractServiceImpl(),
                new FileServiceImpl(),
                new FreezeServiceImpl(),
                new NetworkServiceImpl(),
                new ScheduleServiceImpl(),
                new TokenServiceImpl(),
                new UtilServiceImpl(),
                new RecordCacheService()));

        // Register MerkleHederaState with the ConstructableRegistry, so we can use a constructor
        // OTHER THAN the default constructor to make sure it has the config and other info
        // it needs to be created correctly.
        try {
            logger.debug("Register MerkleHederaState with ConstructableRegistry");
            constructableRegistry.registerConstructable(
                    new ClassConstructorPair(MerkleHederaState.class, this::newState));
        } catch (final ConstructableRegistryException e) {
            logger.error("Failed to register MerkleHederaState with ConstructableRegistry", e);
            throw new RuntimeException(e);
        }
    }

    /** Gets the port the gRPC server is listening on, or {@code -1} if there is no server listening. */
    public int getGrpcPort() {
        return daggerApp.grpcServerManager().port();
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
     * <p>Called by the platform <b>ONLY</b> during genesis (that is, if there is no saved state). However, it is also
     * called indirectly by {@link ConstructableRegistry} due to registration in this class' constructor.
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
            @Nullable final SoftwareVersion previousVersion) {

        // We do nothing for EVENT_STREAM_RECOVERY. This is a special case that is handled by the platform.
        if (trigger == EVENT_STREAM_RECOVERY) {
            logger.debug("Skipping state initialization for trigger {}", trigger);
            return;
        }

        //noinspection ConstantValue
        assert dualState != null : "Platform should never pass a null dual state";
        logger.info(
                "Initializing Hedera state with trigger {} and previous version {}",
                () -> trigger,
                () -> previousVersion == null ? "<NONE>" : previousVersion);

        // We do not support downgrading from one version to an older version.
        final var deserializedVersion = (HederaSoftwareVersion) previousVersion;
        if (isDowngrade(version, deserializedVersion)) {
            logger.fatal(
                    "Fatal error, state source version {} is higher than node software version {}",
                    deserializedVersion,
                    version);
            System.exit(STATE_VERSION_NEWER_THAN_SOFTWARE_VERSION_EXIT_CODE);
        }

        // This is the *FIRST* time in the initialization sequence that we have access to the platform. Grab it!
        // This instance should never change on us, once it has been set
        assert this.platform == null || this.platform == platform : "Platform should never change once set";
        this.platform = platform;

        // Different paths for different triggers. Every trigger should be handled here. If a new trigger is added,
        // since there is no 'default' case, it will cause a compile error, so you will know you have to deal with it
        // here. This is intentional so as to avoid forgetting to handle a new trigger.
        switch (trigger) {
            case GENESIS -> genesis(state, dualState);
            case RESTART -> restart(state, dualState, deserializedVersion);
            case RECONNECT -> reconnect();
                // We exited from this method early if we were recovering from an event stream.
            case EVENT_STREAM_RECOVERY -> throw new RuntimeException("Should never be reached");
        }

        // This field has to be set by the time we get here. It will be set by both the genesis and restart code
        // branches. One of those two is called before a "reconnect" trigger, so we should be fully guaranteed that this
        // assertion will hold true.
        assert configProvider != null : "Config Provider *must* have been set by now!";

        // Since we now have an "app" instance, we can update the dual state accessor. This is *ONLY* used by the app to
        // produce a log summary after a freeze. We should refactor to not have a global reference to this.
        updateDualState(dualState);

        logger.info("Validating ledger state...");
        validateLedgerState(state);
        logger.info("Ledger state ok");
    }

    /**
     * Called by this class when we detect it is time to do migration. This is only used as part of genesis or restart,
     * not as a result of reconnect.
     */
    private void onMigrate(
            @NonNull final MerkleHederaState state, @Nullable final HederaSoftwareVersion deserializedVersion) {
        final var currentVersion = version.getServicesVersion();
        final var previousVersion = deserializedVersion == null ? null : deserializedVersion.getServicesVersion();
        logger.info(
                "Migrating from version {} to {}",
                () -> previousVersion == null ? "<NONE>" : HapiUtils.toString(previousVersion),
                () -> HapiUtils.toString(currentVersion));

        for (final var service : servicesRegistry.services()) {
            // FUTURE We should have metrics here to keep track of how long it takes to migrate each service
            final var serviceName = service.getServiceName();
            final var registry = new MerkleSchemaRegistry(constructableRegistry, serviceName);
            service.registerSchemas(registry);
            registry.migrate(state, previousVersion, currentVersion, configProvider.getConfiguration());
            logger.info("Migrated Service {}", serviceName);
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
     */
    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId nodeId) {
        assert this.platform == platform : "Platform must be the same instance";
        logger.info("Initializing Hedera app with HederaNode#{}", nodeId);

        // Check that UTF-8 is in use. Otherwise, the node will be subject to subtle bugs in string handling that will
        // lead to ISS.
        final var defaultCharset = daggerApp.nativeCharset().get();
        if (!isUTF8(defaultCharset)) {
            logger.error(
                    "Fatal precondition violation in HederaNode#{}:" + "default charset is {} and not UTF-8",
                    daggerApp.nodeId(),
                    defaultCharset);
            daggerApp.systemExits().fail(1);
        }

        // Check that the digest factory supports SHA-384.
        final var digestFactory = daggerApp.digestFactory();
        if (!sha384DigestIsAvailable(digestFactory)) {
            logger.error(
                    "Fatal precondition violation in HederaNode#{}:" + "digest factory does not support SHA-384",
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
                switch (notification.getNewStatus()) {
                    case ACTIVE -> {
                        run();
                        logger.info("Hederanode#{} is ACTIVE", nodeId);
                    }
                    case BEHIND -> {
                        logger.info("Hederanode#{} is BEHIND", nodeId);
                        shutdownGrpcServer();
                    }
                    case DISCONNECTED -> {
                        logger.info("Hederanode#{} is DISCONNECTED", nodeId);
                        shutdownGrpcServer();
                    }
                    case FREEZE_COMPLETE -> {
                        logger.info("Hederanode#{} is in FREEZE_COMPLETE", nodeId);
                        shutdownGrpcServer();
                    }
                    case REPLAYING_EVENTS -> logger.info("Hederanode#{} is REPLAYING_EVENTS", nodeId);
                    case STARTING_UP -> logger.info("Hederanode#{} is STARTING_UP", nodeId);
                    case CATASTROPHIC_FAILURE -> {
                        logger.info("Hederanode#{} is in CATASTROPHIC_FAILURE", nodeId);
                        shutdownGrpcServer();
                    }
                    case CHECKING -> logger.info("Hederanode#{} is CHECKING", nodeId);
                    case OBSERVING -> logger.info("Hederanode#{} is OBSERVING", nodeId);
                    case FREEZING -> logger.info("Hederanode#{} is FREEZING", nodeId);
                    case RECONNECT_COMPLETE -> logger.info("Hederanode#{} is RECONNECT_COMPLETE", nodeId);
                }
            });

            // TBD: notifications.register(ReconnectCompleteListener.class, daggerApp.reconnectListener());
            // The main job of the reconnect listener (com.hedera.node.app.service.mono.state.logic.ReconnectListener)
            // is to log some output (including hashes from the tree for the main state per service) and then to
            // "catchUpOnMissedSideEffects". This last part worries me, because it looks like it invades into the space
            // filled by the freeze service. How should we coordinate lifecycle like reconnect with the services? I am
            // tempted to say that each service has lifecycle methods we can invoke (optional methods on the Service
            // interface), but I worry about the order of invocation on different services. Which service gets called
            // before which other service? Does it matter?

            // TBD: notifications.register(StateWriteToDiskCompleteListener.class,
            // It looks like this notification is handled by
            // com.hedera.node.app.service.mono.state.logic.StateWriteToDiskListener
            // which looks like it is related to freeze / upgrade.
            // daggerApp.stateWriteToDiskListener());

            // TBD: notifications.register(NewSignedStateListener.class, daggerApp.newSignedStateListener());
            // com.hedera.node.app.service.mono.state.exports.NewSignedStateListener
            // Has some relationship to freeze/upgrade, but also with balance exports. This was the trigger that
            // caused us to export balance files on a certain schedule.

            // TBD: notifications.register(IssListener.class, daggerApp.issListener());
            // com.hedera.node.app.service.mono.state.forensics.ServicesIssListener
            // This is something that MUST be implemented by the Hedera app module. We use this to respond to detected
            // ISS events, logging, restarting, etc.
        } catch (final Exception e) {
            logger.error("Fatal precondition violation in HederaNode#{}", daggerApp.nodeId(), e);
            daggerApp.systemExits().fail(1); // TBD: Better exit code?
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

    /** Verifies some aspects of the ledger state */
    private void validateLedgerState(@NonNull final HederaState state) {
        // For a non-zero stake node, validates presence of a self-account in the address book.
        final var selfNodeInfo = daggerApp.networkInfo().selfNodeInfo();
        if (!selfNodeInfo.zeroStake() && selfNodeInfo.accountId() == null) {
            logger.fatal("Node is not zero-stake, but has no known account");
            daggerApp.systemExits().fail(1); // TBD What code to use?
        }

        // Verify the ledger state. At the moment, this is a sanity check that we still have all HBARs present and
        // accounted for. We may do more checks in the future. Every check we add slows down restart, especially when
        // we start loading massive amounts of state from disk.
        try {
            daggerApp.ledgerValidator().validate(state);
        } catch (Throwable th) {
            logger.fatal("Ledger validation failed", th);
            daggerApp.systemExits().fail(1); // TBD What code to use?
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
        startGrpcServer();
    }

    /**
     * Invoked by the platform to handle pre-consensus events. This only happens after {@link #run()} has been called.
     */
    private void onPreHandle(@NonNull final Event event, @NonNull final HederaState state) {
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

    /**
     * Invoked by the platform to handle a round of consensus events.  This only happens after {@link #run()} has been
     * called.
     */
    private void onHandleConsensusRound(
            @NonNull final Round round, @NonNull final SwirldDualState dualState, @NonNull final HederaState state) {
        daggerApp.workingStateAccessor().setHederaState(state);
        // TBD: Add in dual state when needed ::  daggerApp.dualStateAccessor().setDualState(dualState);
        daggerApp.handleWorkflow().handleRound(state, round);
    }

    /*==================================================================================================================
    *
    * gRPC Server Lifecycle
    *
    =================================================================================================================*/

    /** Start the gRPC Server if it is not already running. */
    void startGrpcServer() {
        daggerApp.grpcServerManager().start();
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

    /** Implements the code flow for initializing the state of a new Hedera node with NO SAVED STATE. */
    private void genesis(@NonNull final MerkleHederaState state, @NonNull final SwirldDualState dualState) {
        logger.info("Genesis Initialization");

        logger.info("Initializing Configuration");
        this.configProvider = new ConfigProviderImpl(true);
        logConfiguration();

        // Create all the nodes in the merkle tree for all the services
        onMigrate(state, null);

        // Now that we have the state created, we are ready to create the dependency graph with Dagger
        initializeDagger(state, GENESIS);

        // Store the version in state
        // TODO Who is responsible for saving this in the tree? I assumed it went into dual state... not sensible!
        logger.debug("Saving version information in state");
        //        final var networkCtx = stateChildren.networkCtx();
        //        networkCtx.setStateVersion(StateVersions.CURRENT_VERSION);

        // For now, we have to update the stake details manually. When we have dynamic address book,
        // then we'll move this to be shared with all state initialization flows and not just genesis
        // and restart.
        logger.debug("Initializing stake details");
        //        daggerApp.sysFilesManager().updateStakeDetails();

        // TODO Not sure
        //        networkCtx.markPostUpgradeScanStatus();
    }

    // TODO SHOULD BE USED FOR ALL START/RESTART/GENESIS SCENARIOS
    private void stateInitializationFlow() {
        /*
        final var lastThrottleExempt = bootstrapProperties.getLongProperty(ACCOUNTS_LAST_THROTTLE_EXEMPT);
        // The last throttle-exempt account is configurable to make it easy to start dev networks
        // without throttling
        numberConfigurer.configureNumbers(hederaNums, lastThrottleExempt);

        workingState.updateFrom(activeState);
        log.info("Context updated with working state");

        final var activeHash = activeState.runningHashLeaf().getRunningHash().getHash();
        recordStreamManager.setInitialHash(activeHash);
        log.info("Record running hash initialized");

        if (hfs.numRegisteredInterceptors() == 0) {
            fileUpdateInterceptors.forEach(hfs::register);
            log.info("Registered {} file update interceptors", fileUpdateInterceptors.size());
        }
         */
    }

    // TODO SHOULD BE USED FOR ALL START/RESTART/GENESIS SCENARIOS
    private void storeFlow() {
        /*
        backingTokenRels.rebuildFromSources();
        backingAccounts.rebuildFromSources();
        backingTokens.rebuildFromSources();
        backingNfts.rebuildFromSources();
        log.info("Backing stores rebuilt");

        usageLimits.resetNumContracts();
        aliasManager.rebuildAliasesMap(workingState.accounts(), (num, account) -> {
            if (account.isSmartContract()) {
                usageLimits.recordContracts(1);
            }
        });
        log.info("Account aliases map rebuilt");
         */
    }

    // TODO SHOULD BE USED FOR ALL START/RESTART/GENESIS SCENARIOS
    private void entitiesFlow() {
        /*
        expiries.reviewExistingPayerRecords();
        log.info("Payer records reviewed");
        // Use any entities stored in state to rebuild queue of expired entities.
        log.info("Short-lived entities reviewed");

        sigImpactHistorian.invalidateCurrentWindow();
        log.info("Signature impact history invalidated");

        // Re-initialize the "observable" system files; that is, the files which have
        // associated callbacks managed by the SysFilesCallback object. We explicitly
        // re-mark the files are not loaded here, in case this is a reconnect.
        networkCtxManager.setObservableFilesNotLoaded();
        networkCtxManager.loadObservableSysFilesIfNeeded();
         */
    }

    private void ensureSystemAccounts() {
        /*
        final long systemAccounts = properties.getIntProperty(LEDGER_NUM_SYSTEM_ACCOUNTS);
        final long expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        final long tinyBarFloat = properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT);

        for (long num = 1; num <= systemAccounts; num++) {
            final var id = STATIC_PROPERTIES.scopedAccountWith(num);
            if (accounts.contains(id)) {
                continue;
            }
            final HederaAccount account;
            if (num == accountNums.treasury()) {
                account = accountWith(tinyBarFloat, expiry);
            } else {
                account = accountWith(ZERO_BALANCE, expiry);
            }
            accounts.put(id, account);
            systemAccountsCreated.add(account);
        }

        final var stakingRewardAccountNum = accountNums.stakingRewardAccount();
        final var stakingRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(stakingRewardAccountNum);
        final var nodeRewardAccountNum = accountNums.nodeRewardAccount();
        final var nodeRewardAccountId = STATIC_PROPERTIES.scopedAccountWith(nodeRewardAccountNum);
        final var stakingFundAccounts = List.of(stakingRewardAccountId, nodeRewardAccountId);
        for (final var id : stakingFundAccounts) {
            if (!accounts.contains(id)) {
                final var stakingFundAccount = accountSupplier.get();
                customizeAsStakingFund(stakingFundAccount);
                accounts.put(id, stakingFundAccount);
            }
        }
        for (long num = 900; num <= 1000; num++) {
            final var id = STATIC_PROPERTIES.scopedAccountWith(num);
            if (!accounts.contains(id)) {
                final var account = accountWith(ZERO_BALANCE, expiry);
                accounts.put(id, account);
                systemAccountsCreated.add(account);
            }
        }

        treasuryCloner.ensureTreasuryClonesExist();

        var ledgerFloat = 0L;
        final var allIds = accounts.idSet();
        for (final var id : allIds) {
            ledgerFloat += accounts.getImmutableRef(id).getBalance();
        }
        log.info("Ledger float is {} tinyBars in {} accounts.", ledgerFloat, allIds.size());
                 */
    }

    // Only called during genesis
    private void createAddressBookIfMissing() {
        // Get the address book from the platform and create a NodeAddressBook, and write the protobuf bytes of
        // this into state. (This should be done by the File service schema. Or somebody who owns it.) To do that,
        // we need to make the address book available in the SPI so the file service can get it. Or, is it owned
        // by the network admin service, and the current storage is in the file service, but doesn't actually belong
        // there. I tend to think that is the case. But we use the file service today and a special file and that is
        // actually depended on by the mirror node. So to change that would require a HIP.
        /*
        writeFromBookIfMissing(fileNumbers.addressBook(), this::platformAddressBookToGrpc);
         */
    }

    // Only called during genesis
    private void createNodeDetailsIfMissing() {
        // Crazy! Same contents as the address book, but this one is "node details" file. Two files with the same
        // contents? Why?
        /*
        writeFromBookIfMissing(fileNumbers.nodeDetails(), this::platformAddressBookToGrpc);
         */
    }

    // Only called during genesis
    private void createUpdateFilesIfMissing() {
        /*
        final var firstUpdateNum = fileNumbers.firstSoftwareUpdateFile();
        final var lastUpdateNum = fileNumbers.lastSoftwareUpdateFile();
        final var specialFiles = hfs.specialFiles();
        for (var updateNum = firstUpdateNum; updateNum <= lastUpdateNum; updateNum++) {
            final var disFid = fileNumbers.toFid(updateNum);
            if (!hfs.exists(disFid)) {
                materialize(disFid, systemFileInfo(), new byte[0]);
            } else if (!specialFiles.contains(disFid)) {
                // This can be the case for file 0.0.150, whose metadata had
                // been created for the legacy MerkleDiskFs. But whatever its
                // contents were doesn't matter now. Just make sure it exists
                // in the MerkleSpecialFiles!
                specialFiles.update(disFid, new byte[0]);
            }
        }
         */
    }

    private void doGenesisHousekeeping() {
        /*
        // List the node ids in the address book at genesis
        final List<Long> genesisNodeIds = idsFromAddressBook(addressBook);

        // Prepare the stake info manager for managing the new node ids
        stakeInfoManager.prepForManaging(genesisNodeIds);
         */
    }

    private void buildStakingInfoMap(
            final AddressBook addressBook,
            final BootstrapProperties bootstrapProperties,
            final WritableKVState<EntityNum, MerkleStakingInfo> stakingInfos) {
        final var numberOfNodes = addressBook.getSize();
        final long maxStakePerNode = bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT) / numberOfNodes;
        final long minStakePerNode = maxStakePerNode / 2;
        for (int i = 0; i < numberOfNodes; i++) {
            final var nodeNum = EntityNum.fromLong(addressBook.getNodeId(i).id());
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
            @Nullable final HederaSoftwareVersion deserializedVersion) {
        logger.debug("Restart Initialization");

        // The deserialized version can ONLY be null if we are in genesis, otherwise something is wrong with the state
        if (deserializedVersion == null) {
            logger.fatal("Fatal error, previous software version not found in saved state!");
            System.exit(VERSION_NOT_IN_SAVED_STATE_EXIT_CODE);
        }

        // This configuration is based on what is in state *RIGHT NOW*, before any possible upgrade. This is the config
        // that must be passed to the migration methods.
        // TODO: Actually, we should reinitialize the config on each step along the migration path, so we should pass
        //       the config provider to the migration code and let it get the right version of config as it goes.
        logger.info("Initializing Configuration");
        this.configProvider = new ConfigProviderImpl(false);

        // Migrate to the most recent state, if needed
        final boolean upgrade = isUpgrade(version, deserializedVersion);
        if (upgrade) {
            logger.debug("Upgrade detected");
            onMigrate(state, deserializedVersion);
        }

        // TODO Update the configuration with whatever is the new latest version in state. In reality, we shouldn't
        //      be messing with configuration during migration, but it could happen (by the file service), so we should
        //      be defensive about it so the software is always correct, even in that very unlikely scenario.
        //        this.configProvider.update(null);

        // Now that we have the state created, we are ready to create the all the dagger dependencies
        initializeDagger(state, RESTART);

        // We may still want to change the address book without an upgrade. But note
        // that without a dynamic address book, this MUST be a no-op during reconnect.
        //        final var stakingInfo = stateChildren.stakingInfo();
        //        final var networkCtx = stateChildren.networkCtx();
        //        daggerApp.stakeStartupHelper().doRestartHousekeeping(stateChildren.addressBook(), stakingInfo);
        //        if (upgrade) {
        //            dualState.setFreezeTime(null);
        //            networkCtx.discardPreparedUpgradeMeta();
        ////            if (version.hasMigrationRecordsFrom(deserializedVersion)) {
        ////                networkCtx.markMigrationRecordsNotYetStreamed();
        ////            }
        //        }

        // This updates the working state accessor with our children
        //        daggerApp.initializationFlow().runWith(stateChildren, configProvider);
        //        if (upgrade) {
        //            daggerApp.stakeStartupHelper().doUpgradeHousekeeping(networkCtx, stateChildren.accounts(),
        // stakingInfo);
        //        }

        // Once we have a dynamic address book, this will run unconditionally
        //        daggerApp.sysFilesManager().updateStakeDetails();
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
    * Random private helper methods
    *
    =================================================================================================================*/

    private void initializeDagger(@NonNull final MerkleHederaState state, @NonNull final InitTrigger trigger) {
        logger.debug("Initializing dagger");
        final var selfId = platform.getSelfId();
        if (daggerApp == null) {
            final var nodeAddress = platform.getAddressBook().getAddress(selfId);
            final var nodeSelfAccount = parseAccount(nodeAddress.getMemo());
            final var runningHashStore = new ReadableStoreFactory(state).getStore(ReadableRunningHashLeafStore.class);
            final var initialHash = runningHashStore.getRunningHash();
            // Fully qualified so as to not confuse javadoc
            daggerApp = com.hedera.node.app.DaggerHederaInjectionComponent.builder()
                    .initTrigger(trigger)
                    .configuration(configProvider)
                    .staticAccountMemo(nodeAddress.getMemo())
                    .initialHash(initialHash)
                    .platform(platform)
                    .maxSignedTxnSize(MAX_SIGNED_TXN_SIZE)
                    .crypto(CryptographyHolder.get())
                    .currentPlatformStatus(new CurrentPlatformStatusImpl(platform))
                    .selfId(nodeSelfAccount)
                    .servicesRegistry(servicesRegistry)
                    .bootstrapProps(new BootstrapProperties(false)) // TBD REMOVE
                    .instantSource(InstantSource.system())
                    .build();

            daggerApp.workingStateAccessor().setHederaState(state);
        }
    }

    private void updateDualState(final SwirldDualState dualState) {
        //        daggerApp.dualStateAccessor().setDualState(dualState);
        logger.info(
                "Dual state includes freeze time={} and last frozen={}",
                dualState.getFreezeTime(),
                dualState.getLastFrozenTime());
    }

    private boolean isUpgrade(final HederaSoftwareVersion deployedVersion, final SoftwareVersion deserializedVersion) {
        return deployedVersion.isAfter(deserializedVersion);
    }

    private boolean isDowngrade(
            final HederaSoftwareVersion deployedVersion, final SoftwareVersion deserializedVersion) {
        return deployedVersion.isBefore(deserializedVersion);
    }

    private void logConfiguration() {
        // TODO Need reflection to print out all of the configuration values.
        if (logger.isInfoEnabled()) {
            final var config = configProvider.getConfiguration();
            final var lines = new ArrayList<String>();
            lines.add("Active Configuration:");
            config.getPropertyNames().forEach(name -> lines.add(name + " = " + config.getValue(name)));
            logger.info(String.join("\n", lines));
        }
    }
}
