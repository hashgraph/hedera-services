/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.startJVMPauseDetectorThread;
import static com.swirlds.platform.util.BootstrapUtils.startThreadDumpGenerator;
import static com.swirlds.platform.util.BootstrapUtils.validatePathToConfigTxt;
import static com.swirlds.platform.util.BootstrapUtils.writeSettingsUsed;

import com.swirlds.base.time.Time;
import com.swirlds.common.StartupTime;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.logging.legacy.payload.NodeStartPayload;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SwirldsPlatformBuilder {
    private static final Logger logger = LogManager.getLogger(SwirldsPlatformBuilder.class);
    private static final String SWIRLDS_PACKAGE = "com.swirlds";
    private static final String HEDERA_PACKAGE = "com.hedera";
    // @formatter:off
    private static final String STARTUP_MESSAGE =
            """
              //////////////////////
             // Node is Starting //
            //////////////////////""";
    // @formatter:on

    private NodeId nodeId;
    private String swirldName = "Unspecified";
    private Supplier<SwirldMain> mainSupplier = null;
    private final ConfigurationBuilder configBuilder;
    private final ConfigurationBuilder bootstrapConfigBuilder;

    public SwirldsPlatformBuilder() {
        configBuilder =
                ConfigUtils.scanAndRegisterAllConfigTypes(ConfigurationBuilder.create(), Set.of(SWIRLDS_PACKAGE));
        bootstrapConfigBuilder = ConfigurationBuilder.create().withConfigDataType(PathsConfig.class);
    }

    public SwirldsPlatformBuilder withNodeId(final long nodeId) {
        this.nodeId = new NodeId(nodeId);
        return this;
    }

    public SwirldsPlatformBuilder withName(@NonNull final String name) {
        this.swirldName = name;
        return this;
    }

    public SwirldsPlatformBuilder withMain(@NonNull final Supplier<SwirldMain> mainSupplier) {
        this.mainSupplier = mainSupplier;
        return this;
    }

    public SwirldsPlatformBuilder withConfigSource(@NonNull final ConfigSource source) {
        configBuilder.withSource(source);
        bootstrapConfigBuilder.withSource(source);
        return this;
    }

    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, @Nullable final String value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final boolean value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final int value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final long value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final double value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, @NonNull final Object value) {
        return withConfigSource(new SimpleConfigSource(name, value.toString()));
    }

    public SwirldsPlatform buildAndStart() {
        StartupTime.markStartupTime();

        // Setup the configuration, taking into account overrides from the platform builder
        final Configuration bootstrapConfig = bootstrapConfigBuilder.build();
        final PathsConfig bootstrapPaths = bootstrapConfig.getConfigData(PathsConfig.class);
        rethrowIO(() -> BootstrapUtils.setupConfigBuilder(configBuilder, bootstrapPaths));
        final Configuration configuration = configBuilder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);

        // Setup logging, using the configuration to tell us the location of the log4j2.xml
        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
        final Path log4jPath = pathsConfig.getLogPath();
        try {
            Log4jSetup.startLoggingFramework(log4jPath).await();
        } catch (final InterruptedException e) {
            CommonUtils.tellUserConsole("Interrupted while waiting for log4j to initialize");
            Thread.currentThread().interrupt();
        }

        // Now that we have a logger, we can start using it for further messages
        logger.info(STARTUP.getMarker(), "\n\n" + STARTUP_MESSAGE + "\n");
        logger.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

        // Validate the configuration
        validatePathToConfigTxt(pathsConfig);
        PlatformConfigUtils.checkConfiguration(configuration);

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final var legacyConfig = LegacyConfigPropertiesLoader.loadConfigFile(pathsConfig.getConfigPath());
        final AddressBook configAddressBook = legacyConfig.getAddressBook();

        // Determine which nodes to run locally
        final List<NodeId> nodesToRun = getNodesToRun(configAddressBook, Set.of(nodeId));
        checkNodesToRun(nodesToRun);

        // Load all SwirldMain instances for locally run nodes.
        final var appMain = mainSupplier.get();
        final var appMains = Map.of(nodeId, appMain);
        legacyConfig.appConfig().ifPresent(c -> ParameterProvider.getInstance().setParameters(c.params()));

        // Check the OS to see if it is healthy
        BootstrapUtils.performHealthChecks(configuration);

        // Write the settingsUsed.txt file
        writeSettingsUsed(configuration);

        // If enabled, clean out the signed state directory. Needs to be done before the platform/state is started up,
        // as we don't want to delete the temporary file directory if it ends up being put in the saved state directory.
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final String mainClassName =
                stateConfig.getMainClassName(appMain.getClass().getName());
        if (stateConfig.cleanSavedStateDirectory()) {
            SignedStateFileUtils.cleanStateDirectory(mainClassName);
        }

        // Create the various keys and certificates (which are saved in various Crypto objects).
        // Save the certificates in the trust stores.
        // Save the trust stores in the address book.
        logger.debug(STARTUP.getMarker(), "About do crypto instantiation");
        final Map<NodeId, Crypto> crypto = initNodeSecurity(configAddressBook, configuration);
        logger.debug(STARTUP.getMarker(), "Done with crypto instantiation");

        // the AddressBook is not changed after this point, so we calculate the hash now
        CryptographyHolder.get().digestSync(configAddressBook);

        // Setup metrics system
        final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        final Metrics globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);

        final PlatformContext platformContext = new DefaultPlatformContext(nodeId, metricsProvider, configuration);

        // 8. Create the Main
        final var appVersion = appMain.getSoftwareVersion();

        final RecycleBinImpl recycleBin = rethrowIO(() -> new RecycleBinImpl(
                configuration, platformContext.getMetrics(), getStaticThreadManager(), Time.getCurrent(), nodeId));
        recycleBin.start();

        // We can't send a "real" dispatch, since the dispatcher will not have been started by the
        // time this class is used.
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final EmergencyRecoveryManager emergencyRecoveryManager = new EmergencyRecoveryManager(
                stateConfig, new Shutdown()::shutdown, basicConfig.getEmergencyRecoveryFileLoadDir());

        final ReservedSignedState initialState;
        try {
            initialState = getInitialState(
                    platformContext,
                    recycleBin,
                    appMain,
                    mainClassName,
                    swirldName,
                    nodeId,
                    configAddressBook,
                    emergencyRecoveryManager);
        } catch (final SignedStateLoadingException e) {
            throw new RuntimeException("unable to load state from disk", e);
        }

        SwirldsPlatform platform;
        try (initialState) {
            // check software version compatibility
            final boolean softwareUpgrade = detectSoftwareUpgrade(appVersion, initialState.get());

            // Initialize the address book from the configuration and platform saved state.
            final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                    nodeId, appVersion, softwareUpgrade, initialState.get(), configAddressBook.copy(), platformContext);

            if (!initialState.get().isGenesisState()) {
                final State state = initialState.get().getState();
                // Update the address book with the current address book read from config.txt.
                // Eventually we will not do this, and only transactions will be capable of
                // modifying the address book.
                state.getPlatformState()
                        .setAddressBook(
                                addressBookInitializer.getCurrentAddressBook().copy());
            }

            platform = new SwirldsPlatform(
                    platformContext,
                    crypto.get(nodeId),
                    recycleBin,
                    nodeId,
                    mainClassName,
                    swirldName,
                    appVersion,
                    softwareUpgrade,
                    initialState.get(),
                    addressBookInitializer.getPreviousAddressBook(),
                    emergencyRecoveryManager);
        }

        // init appMain
        appMain.init(platform, nodeId);

        // Write all metrics information to file
        MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);

        logger.debug(STARTUP.getMarker(), "Starting platforms");

        platform.start();

        new ThreadConfiguration(getStaticThreadManager())
                .setNodeId(nodeId)
                .setComponent("app")
                .setThreadName("appMain")
                .setRunnable(appMains.get(nodeId))
                .setDaemon(false) // IMPORTANT: this swirlds app thread must be non-daemon
                .build(true);

        // Initialize the thread dump generator, if enabled via settings
        startThreadDumpGenerator(configuration);

        // Initialize JVMPauseDetectorThread, if enabled via settings
        startJVMPauseDetectorThread(configuration);

        logger.info(STARTUP.getMarker(), "Starting metrics");
        metricsProvider.start();

        logger.debug(STARTUP.getMarker(), "Done with starting platforms");

        CommonUtils.tellUserConsole("This computer has an internal IP address:  " + Network.getInternalIPAddress());
        logger.trace(
                STARTUP.getMarker(), "All of this computer's addresses: {}", () -> Network.getOwnAddresses().stream()
                        .map(InetAddress::getHostAddress)
                        .collect(Collectors.joining(", ")));

        logger.debug(STARTUP.getMarker(), "main() finished");

        return platform;
    }
}
