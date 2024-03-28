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
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.PlatformBuilder.DEFAULT_SETTINGS_FILE_NAME;
import static com.swirlds.platform.PlatformBuilder.buildPlatformContext;
import static com.swirlds.platform.system.SystemExitCode.CONFIGURATION_ERROR;
import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static com.swirlds.platform.system.SystemExitUtils.exitSystem;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.SystemEnvironmentConfigSource;
import com.swirlds.config.extensions.sources.SystemPropertiesConfigSource;
import com.swirlds.platform.CommandLineArgs;
import com.swirlds.platform.PlatformBuilder;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point.
 *
 * <p>This class simply delegates to either {@link MonoServicesMain} or {@link Hedera} depending on
 * the value of the {@code hedera.services.functions.workflows.enabled} property. If *any* workflows are enabled, then
 * {@link Hedera} is used; otherwise, {@link MonoServicesMain} is used.
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
        final var configProvider = new ConfigProviderImpl(false);
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        if (hederaConfig.workflowsEnabled().isEmpty()) {
            logger.info("No workflows enabled, using mono-service");
            delegate = new MonoServicesMain();
        } else {
            logger.info("One or more workflows enabled, using Hedera");
            delegate = new Hedera(ConstructableRegistry.getInstance());
        }
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
    public SwirldState newState() {
        return delegate.newState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        delegate.run();
    }

    /**
     * Launches the application.
     *
     * @param args First arg, if specified, will be the node ID
     */
    public static void main(final String... args) throws Exception {
        BootstrapUtils.setupConstructableRegistry();
        final var registry = ConstructableRegistry.getInstance();

        final Hedera hedera = new Hedera(registry);

        // Determine which node to run locally
        // Load config.txt address book file and parse address book
        final AddressBook addressBook = loadAddressBook(PlatformBuilder.DEFAULT_CONFIG_FILE_NAME);
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
        final List<NodeId> nodesToRun = getNodesToRun(addressBook, commandLineArgs.localNodesToStart());
        // hard exit if no nodes are configured to run
        checkNodesToRun(nodesToRun);

        final NodeId selfId = ensureSingleNode(nodesToRun, commandLineArgs.localNodesToStart());

        final var config = ConfigurationBuilder.create()
                .withSource(SystemEnvironmentConfigSource.getInstance())
                .withSource(SystemPropertiesConfigSource.getInstance());

        SoftwareVersion version = hedera.getSoftwareVersion();
        logger.info("Starting node {} with version {}", selfId, version);

        final PlatformContext platformContext =
                buildPlatformContext(config, getAbsolutePath(DEFAULT_SETTINGS_FILE_NAME), selfId);

        final PlatformBuilder builder =
                new PlatformBuilder(Hedera.APP_NAME, Hedera.SWIRLD_NAME, version, hedera::newState, selfId);

        builder.withPreviousSoftwareVersionClassId(0x6f2b1bc2df8cbd0bL /* SerializableSemVers.CLASS_ID */);
        builder.withPlatformContext(platformContext);

        final Platform platform = builder.build();
        hedera.init(platform, selfId);
        platform.start();
        hedera.run();
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
            return props.getAddressBook();
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Error loading address book", e);
            exitSystem(CONFIGURATION_ERROR);
            throw e;
        }
    }
}
