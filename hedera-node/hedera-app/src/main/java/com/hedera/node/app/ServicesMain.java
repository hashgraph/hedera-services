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
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.system.SystemExitCode.CONFIGURATION_ERROR;
import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static com.swirlds.platform.system.SystemExitUtils.exitSystem;
import static com.swirlds.platform.system.address.AddressBookUtils.createRoster;
import static com.swirlds.platform.system.address.AddressBookUtils.initializeAddressBook;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.services.OrderedServiceMigrator;
import com.hedera.node.app.services.ServicesRegistryImpl;
import com.hedera.node.app.tss.TssBaseServiceImpl;
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
import com.swirlds.platform.CommandLineArgs;
import com.swirlds.platform.ParameterProvider;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
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
     * Launches Services directly, without use of the "app browser" from {@link com.swirlds.platform.Browser}. The
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

        final var cryptography = CryptographyFactory.create();
        CryptographyHolder.set(cryptography);
        // the AddressBook is not changed after this point, so we calculate the hash now
        cryptography.digestSync(diskAddressBook);

        // Initialize the Merkle cryptography
        final var merkleCryptography = MerkleCryptographyFactory.create(configuration, cryptography);
        MerkleCryptoFactory.set(merkleCryptography);

        // Register with the ConstructableRegistry classes which need configuration.
        BootstrapUtils.setupConstructableRegistryWithConfiguration(configuration);

        // Create the platform context
        final var platformContext = PlatformContext.create(
                configuration,
                Time.getCurrent(),
                metrics,
                cryptography,
                FileSystemManager.create(configuration),
                recycleBin,
                merkleCryptography);
        // Create initial state for the platform
        final var reservedState = getInitialState(
                platformContext,
                version,
                hedera::newMerkleStateRoot,
                SignedStateFileUtils::readState,
                Hedera.APP_NAME,
                Hedera.SWIRLD_NAME,
                selfId,
                diskAddressBook);
        final var initialState = reservedState.state();
        final var stateHash = reservedState.hash();

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
                appContext -> new TssBaseServiceImpl(appContext, ForkJoinPool.commonPool(), ForkJoinPool.commonPool()));
    }
}
