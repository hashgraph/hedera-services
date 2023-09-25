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

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
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
import static com.swirlds.platform.util.BootstrapUtils.writeSettingsUsed;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Builds a {@link SwirldsPlatform} instance.
 */
public final class PlatformBuilder {

    private static final Logger logger = LogManager.getLogger(PlatformBuilder.class);

    private static final String SWIRLDS_PACKAGE = "com.swirlds";

    // @formatter:off
    private static final String STARTUP_MESSAGE =
            """
              //////////////////////
             // Node is Starting //
            //////////////////////""";
    // @formatter:on

    private final String appName;
    private final SoftwareVersion softwareVersion;
    private final Supplier<SwirldState> genesisStateBuilder;
    private final NodeId selfId;
    private final String swirldName;

    private ConfigurationBuilder configurationBuilder;

    /**
     * Static stateful variables are evil, but this one is required until we can refactor all the other places that use
     * them.
     */
    private static boolean staticSetupCompleted = false;

    private static DefaultMetricsProvider metricsProvider;

    private static Metrics globalMetrics;

    /**
     * The path to config.txt.
     */
    private Path configPath = getAbsolutePath("config.txt");

    /**
     * The path to settings.txt.
     */
    private Path settingsPath = getAbsolutePath("settings.txt");

    /**
     * A function that mimics the old init() behavior of SwirldsMain. Will eventually be removed.
     */
    private BiConsumer<Platform, NodeId> legacyInit;

    /**
     * Create a new platform builder.
     *
     * @param appName             the name of the application, currently used for deciding where to store states on
     *                            disk
     * @param swirldName          the name of the swirld, currently used for deciding where to store states on disk
     * @param selfId              the ID of this node
     * @param softwareVersion     the software version of the application
     * @param genesisStateBuilder a supplier that will be called to create the genesis state, if necessary
     */
    public PlatformBuilder(
            @NonNull final String appName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final Supplier<SwirldState> genesisStateBuilder,
            @NonNull final NodeId selfId) {

        this.appName = Objects.requireNonNull(appName);
        this.swirldName = Objects.requireNonNull(swirldName);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        this.genesisStateBuilder = Objects.requireNonNull(genesisStateBuilder);
        this.selfId = Objects.requireNonNull(selfId);
    }

    /**
     * Set the configuration builder to use. If not provided then one is generated when the platform is built.
     *
     * @param configurationBuilder the configuration builder to use
     * @return this
     */
    @NonNull
    public PlatformBuilder withConfigurationBuilder(@Nullable final ConfigurationBuilder configurationBuilder) {
        this.configurationBuilder = configurationBuilder;
        return this;
    }

    /**
     * The path to the settings file. Default is "./settings.txt" where "./" is the current working directory of the
     * JVM.
     *
     * @param path the path to settings.txt
     * @return this
     * @throws IllegalArgumentException if the path does not exist
     */
    public PlatformBuilder withSettingsPath(@NonNull final Path path) {
        Objects.requireNonNull(path);
        final Path absolutePath = getAbsolutePath(path);
        if (!Files.exists(absolutePath)) {
            throw new IllegalArgumentException("File " + absolutePath + " does not exist");
        }
        this.settingsPath = absolutePath;
        return this;
    }

    /**
     * The path to the config file. Default is "./config.txt" where "./" is the current working directory of the JVM.
     *
     * @param path the path to config.txt
     * @return this
     * @throws IllegalArgumentException if the path does not exist
     */
    public PlatformBuilder withConfigPath(@NonNull final Path path) {
        Objects.requireNonNull(path);
        final Path absolutePath = getAbsolutePath(path);
        if (!Files.exists(absolutePath)) {
            throw new IllegalArgumentException("File " + absolutePath + " does not exist");
        }
        this.configPath = absolutePath;
        return this;
    }

    /**
     * A function that mimics the old init() behavior of SwirldsMain. Will eventually be removed.
     *
     * @param legacyInit the function to call
     * @return this
     * @deprecated this will no longer be supported after control is inverted
     */
    @Deprecated(forRemoval = true)
    public PlatformBuilder withLegacyInit(@NonNull final BiConsumer<Platform, NodeId> legacyInit) {
        this.legacyInit = Objects.requireNonNull(legacyInit);
        return this;
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

        BootstrapUtils.performHealthChecks(configPath, configuration);
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
     * Build the configuration for the node.
     *
     * @return the configuration
     */
    @NonNull
    private Configuration buildConfiguration() {
        if (configurationBuilder == null) {
            configurationBuilder = ConfigurationBuilder.create();
        }

        if (!Files.exists(configPath)) {
            throw new IllegalStateException("File " + configPath + " does not exist");
        }
        if (!Files.exists(settingsPath)) {
            throw new IllegalStateException("File " + settingsPath + " does not exist");
        }

        ConfigUtils.scanAndRegisterAllConfigTypes(configurationBuilder, Set.of(SWIRLDS_PACKAGE));
        rethrowIO(() -> BootstrapUtils.setupConfigBuilder(configurationBuilder, configPath, settingsPath));

        final Configuration configuration = configurationBuilder.build();
        PlatformConfigUtils.checkConfiguration(configuration);

        return configuration;
    }

    /**
     * Parse the address book from the config.txt file.
     *
     * @return the address book
     */
    private AddressBook loadConfigAddressBook() {
        final LegacyConfigProperties legacyConfig = LegacyConfigPropertiesLoader.loadConfigFile(configPath);
        legacyConfig.appConfig().ifPresent(c -> ParameterProvider.getInstance().setParameters(c.params()));
        return legacyConfig.getAddressBook();
    }

    /**
     * Build a platform.
     *
     * @param start if true then start the platform
     * @return a new platform instance
     */
    public SwirldsPlatform build(final boolean start) {
        final Configuration configuration = buildConfiguration();

        final boolean firstTimeSetup = setupStaticUtilities(configuration);

        final AddressBook configAddressBook = loadConfigAddressBook();

        checkNodesToRun(List.of(selfId));

        final Map<NodeId, Crypto> crypto = initNodeSecurity(configAddressBook, configuration);
        final PlatformContext platformContext = new DefaultPlatformContext(selfId, metricsProvider, configuration);

        // the AddressBook is not changed after this point, so we calculate the hash now
        platformContext.getCryptography().digestSync(configAddressBook);

        final RecycleBinImpl recycleBin = rethrowIO(() -> new RecycleBinImpl(
                configuration, platformContext.getMetrics(), getStaticThreadManager(), Time.getCurrent(), selfId));

        // We can't send a "real" dispatch, since the dispatcher will not have been started by the
        // time this class is used.
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final EmergencyRecoveryManager emergencyRecoveryManager = new EmergencyRecoveryManager(
                stateConfig, new Shutdown()::shutdown, basicConfig.getEmergencyRecoveryFileLoadDir());

        try (final ReservedSignedState initialState = getInitialState(
                platformContext,
                recycleBin,
                softwareVersion,
                genesisStateBuilder,
                appName,
                swirldName,
                selfId,
                configAddressBook,
                emergencyRecoveryManager)) {

            final boolean softwareUpgrade = detectSoftwareUpgrade(softwareVersion, initialState.get());

            // Initialize the address book from the configuration and platform saved state.
            final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                    selfId,
                    softwareVersion,
                    softwareUpgrade,
                    initialState.get(),
                    configAddressBook.copy(),
                    platformContext);

            if (!initialState.get().isGenesisState()) {
                final State state = initialState.get().getState();
                // Update the address book with the current address book read from config.txt.
                // Eventually we will not do this, and only transactions will be capable of
                // modifying the address book.
                state.getPlatformState()
                        .setAddressBook(
                                addressBookInitializer.getCurrentAddressBook().copy());
            }

            final SwirldsPlatform platform = new SwirldsPlatform(
                    platformContext,
                    crypto.get(selfId),
                    recycleBin,
                    selfId,
                    appName,
                    swirldName,
                    softwareVersion,
                    softwareUpgrade,
                    initialState.get(),
                    addressBookInitializer.getPreviousAddressBook(),
                    emergencyRecoveryManager);

            if (legacyInit != null) {
                legacyInit.accept(platform, selfId);
            }

            if (firstTimeSetup) {
                MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);
                metricsProvider.start();
            }

            if (start) {
                platform.start();
            }

            return platform;

        } catch (final SignedStateLoadingException e) {
            throw new RuntimeException("unable to load state from disk", e);
        }
    }
}
