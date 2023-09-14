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
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;
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
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.internal.SignedStateLoadingException;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class SwirldsPlatformBuilder {

    private static final Logger logger = LogManager.getLogger(SwirldsPlatformBuilder.class);

    private static final String SWIRLDS_PACKAGE = "com.swirlds";

    // TODO extract this constant
    // @formatter:off
    private static final String STARTUP_MESSAGE =
            """
              //////////////////////
             // Node is Starting //
            //////////////////////""";
    // @formatter:on

    private final NodeId selfId;
    private final SwirldMain appMain;

    private String swirldName = "Unspecified";

    private final ConfigurationBuilder configBuilder;
    private final ConfigurationBuilder bootstrapConfigBuilder;

    /**
     * Static stateful variables are evil, but this one is required until we can refactor all the other places that use
     * them.
     */
    private static boolean staticSetupCompleted = false;

    private static DefaultMetricsProvider metricsProvider;
    private static Metrics globalMetrics;

    /**
     * Create a new platform builder.
     *
     * @param appMain the main class of the application
     * @param selfId  the ID of this node
     */
    public SwirldsPlatformBuilder(@NonNull final SwirldMain appMain, @NonNull final NodeId selfId) {

        this.appMain = Objects.requireNonNull(appMain);
        this.selfId = Objects.requireNonNull(selfId);

        configBuilder =
                ConfigUtils.scanAndRegisterAllConfigTypes(ConfigurationBuilder.create(), Set.of(SWIRLDS_PACKAGE));
        bootstrapConfigBuilder = ConfigurationBuilder.create().withConfigDataType(PathsConfig.class);
    }

    /**
     * Specify the name of the platform. Used for UI only.
     *
     * @param name the name of the platform
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withName(@NonNull final String name) {
        this.swirldName = name;
        return this;
    }

    /**
     * Add a source of configuration.
     *
     * @param source the source of configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigSource(@NonNull final ConfigSource source) {
        configBuilder.withSource(source);
        bootstrapConfigBuilder.withSource(source);
        return this;
    }

    /**
     * Add a configuration value.
     *
     * @param name  the name of the configuration
     * @param value the value of the configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, @Nullable final String value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    /**
     * Add a configuration value.
     *
     * @param name  the name of the configuration
     * @param value the value of the configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final boolean value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    /**
     * Add a configuration value.
     *
     * @param name  the name of the configuration
     * @param value the value of the configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final int value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    /**
     * Add a configuration value.
     *
     * @param name  the name of the configuration
     * @param value the value of the configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final long value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    /**
     * Add a configuration value.
     *
     * @param name  the name of the configuration
     * @param value the value of the configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, final double value) {
        return withConfigSource(new SimpleConfigSource(name, value));
    }

    /**
     * Add a configuration value.
     *
     * @param name  the name of the configuration
     * @param value the value of the configuration
     * @return this
     */
    @NonNull
    public SwirldsPlatformBuilder withConfigValue(@NonNull final String name, @NonNull final Object value) {
        return withConfigSource(new SimpleConfigSource(name, value.toString()));
    }

    /**
     * Build a platform but do not start it.
     *
     * @return a new platform instance
     */
    public SwirldsPlatform build() {
        return build(false);
    }

    /**
     * Setup static utilities. If running multiple platforms in the same JVM, this should be called when constructing
     * the first platform only.
     *
     * @param configuration the configuration for this node
     * @return true if this is the first time this method has been called, false otherwise
     */
    private boolean setupStaticUtilities(@NonNull final Configuration configuration) {
        if (staticSetupCompleted) {
            // Only setup static utilities once
            return false;
        }
        staticSetupCompleted = true;

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

        BootstrapUtils.performHealthChecks(configuration);
        writeSettingsUsed(configuration);

        metricsProvider = new DefaultMetricsProvider(configuration);
        globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);

        // Initialize the thread dump generator, if enabled via settings
        startThreadDumpGenerator(configuration);

        // Initialize JVMPauseDetectorThread, if enabled via settings
        startJVMPauseDetectorThread(configuration);

        return true;
    }

    /**
     * Build a platform.
     *
     * @param start if true then start the platform
     * @return a new platform instance
     */
    public SwirldsPlatform build(final boolean start) {

        // TODO move this into the platform!
        StartupTime.markStartupTime();

        // Setup the configuration, taking into account overrides from the platform builder
        final Configuration bootstrapConfig = bootstrapConfigBuilder.build();
        final PathsConfig bootstrapPaths = bootstrapConfig.getConfigData(PathsConfig.class);
        rethrowIO(() -> BootstrapUtils.setupConfigBuilder(configBuilder, bootstrapPaths));
        final Configuration configuration = configBuilder.build();

        final boolean firstTimeSetup = setupStaticUtilities(configuration);

        // Validate the configuration
        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);
        validatePathToConfigTxt(pathsConfig);
        PlatformConfigUtils.checkConfiguration(configuration);

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final var legacyConfig = LegacyConfigPropertiesLoader.loadConfigFile(pathsConfig.getConfigPath());
        legacyConfig.appConfig().ifPresent(c -> ParameterProvider.getInstance().setParameters(c.params()));
        final AddressBook configAddressBook = legacyConfig.getAddressBook();

        // TODO
        //        // Determine which nodes to run locally
        //        final List<NodeId> nodesToRun = getNodesToRun(configAddressBook, Set.of(selfId));
        checkNodesToRun(List.of(selfId));

        final Map<NodeId, Crypto> crypto = initNodeSecurity(configAddressBook, configuration);
        final PlatformContext platformContext = new DefaultPlatformContext(selfId, metricsProvider, configuration);

        // the AddressBook is not changed after this point, so we calculate the hash now
        platformContext.getCryptography().digestSync(configAddressBook);

        final RecycleBinImpl recycleBin = rethrowIO(() -> new RecycleBinImpl(
                configuration, platformContext.getMetrics(), getStaticThreadManager(), Time.getCurrent(), selfId));
        recycleBin.start(); // TODO this should not start here!

        // We can't send a "real" dispatch, since the dispatcher will not have been started by the
        // time this class is used.
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final EmergencyRecoveryManager emergencyRecoveryManager = new EmergencyRecoveryManager(
                stateConfig, new Shutdown()::shutdown, basicConfig.getEmergencyRecoveryFileLoadDir());

        SwirldsPlatform platform;
        try (final ReservedSignedState initialState = getInitialState(
                platformContext,
                recycleBin,
                appMain,
                appMain.getClass().getName(),
                swirldName,
                selfId,
                configAddressBook,
                emergencyRecoveryManager)) {

            final SoftwareVersion appVersion = appMain.getSoftwareVersion();
            final boolean softwareUpgrade = detectSoftwareUpgrade(appVersion, initialState.get());

            // Initialize the address book from the configuration and platform saved state.
            final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                    selfId, appVersion, softwareUpgrade, initialState.get(), configAddressBook.copy(), platformContext);

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
                    crypto.get(selfId),
                    recycleBin,
                    selfId,
                    appMain.getClass().getName(),
                    swirldName,
                    appVersion,
                    softwareUpgrade,
                    initialState.get(),
                    addressBookInitializer.getPreviousAddressBook(),
                    emergencyRecoveryManager,
                    appMain);

        } catch (final SignedStateLoadingException e) {
            throw new RuntimeException("unable to load state from disk", e);
        }

        if (firstTimeSetup) {
            MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);
            metricsProvider.start();
        }

        if (start) {
            platform.start();
        }

        return platform;
    }
}
