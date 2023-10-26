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

package com.swirlds.platform.util;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static com.swirlds.common.system.SystemExitUtils.exitSystem;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.config.export.ConfigExport;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.config.sources.ThreadCountPropertyConfigSource;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatusConfig;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.fchashmap.config.FCHashMapConfig;
import com.swirlds.gui.WindowConfig;
import com.swirlds.logging.payloads.NodeAddressMismatchPayload;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.platform.JVMPauseDetectorThread;
import com.swirlds.platform.ThreadDumpGenerator;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.config.internal.ConfigMappings;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamConfig;
import com.swirlds.platform.gossip.ProtocolConfig;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.health.OSHealthChecker;
import com.swirlds.platform.health.clock.OSClockSpeedSourceChecker;
import com.swirlds.platform.health.entropy.OSEntropyChecker;
import com.swirlds.platform.health.filesystem.OSFileSystemChecker;
import com.swirlds.platform.network.Network;
import com.swirlds.platform.state.address.AddressBookNetworkUtils;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import com.swirlds.platform.uptime.UptimeConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility methods that are helpful when starting up a JVM.
 */
public final class BootstrapUtils {

    /**
     * The logger for this class
     */
    private static final Logger logger = LogManager.getLogger(BootstrapUtils.class);

    private BootstrapUtils() {}

    /**
     * Load the config for paths
     *
     * @return the paths configuration files
     */
    public static @NonNull PathsConfig loadPathsConfig() {
        final PathsConfig pathsConfig = ConfigurationHolder.getConfigData(PathsConfig.class);
        return pathsConfig;
    }

    /**
     * Load the configuration for the platform.
     *
     * @param configurationBuilder the configuration builder to setup
     * @param settingsPath         the path to the settings.txt file
     * @throws IOException if there is a problem reading the configuration files
     */
    public static void setupConfigBuilder(
            @NonNull final ConfigurationBuilder configurationBuilder, @NonNull final Path settingsPath)
            throws IOException {

        final ConfigSource settingsConfigSource = LegacyFileConfigSource.ofSettingsFile(settingsPath);
        final ConfigSource mappedSettingsConfigSource = ConfigMappings.addConfigMapping(settingsConfigSource);
        final ConfigSource threadCountPropertyConfigSource = new ThreadCountPropertyConfigSource();

        // Load Configuration Definitions
        configurationBuilder
                .withSource(mappedSettingsConfigSource)
                .withSource(threadCountPropertyConfigSource)
                .withConfigDataType(BasicConfig.class)
                .withConfigDataType(StateConfig.class)
                .withConfigDataType(CryptoConfig.class)
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(ReconnectConfig.class)
                .withConfigDataType(FCHashMapConfig.class)
                .withConfigDataType(MerkleDbConfig.class)
                .withConfigDataType(ChatterConfig.class)
                .withConfigDataType(AddressBookConfig.class)
                .withConfigDataType(VirtualMapConfig.class)
                .withConfigDataType(ConsensusConfig.class)
                .withConfigDataType(ThreadConfig.class)
                .withConfigDataType(DispatchConfiguration.class)
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(PrometheusConfig.class)
                .withConfigDataType(OSHealthCheckConfig.class)
                .withConfigDataType(WiringConfig.class)
                .withConfigDataType(PreconsensusEventStreamConfig.class)
                .withConfigDataType(SyncConfig.class)
                .withConfigDataType(UptimeConfig.class)
                .withConfigDataType(RecycleBinConfig.class)
                .withConfigDataType(EventConfig.class)
                .withConfigDataType(EventCreationConfig.class)
                .withConfigDataType(PathsConfig.class)
                .withConfigDataType(SocketConfig.class)
                .withConfigDataType(PlatformStatusConfig.class)
                .withConfigDataType(TransactionConfig.class)
                .withConfigDataType(ProtocolConfig.class);
    }

    /**
     * Perform health all health checks
     *
     * @param configPath     the path to the config.txt file
     * @param configuration the configuration
     */
    public static void performHealthChecks(@NonNull final Path configPath, @NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration);
        final OSFileSystemChecker osFileSystemChecker = new OSFileSystemChecker(configPath);

        OSHealthChecker.performOSHealthChecks(
                configuration.getConfigData(OSHealthCheckConfig.class),
                List.of(
                        OSClockSpeedSourceChecker::performClockSourceSpeedCheck,
                        OSEntropyChecker::performEntropyChecks,
                        osFileSystemChecker::performFileSystemCheck));
    }

    /**
     * Sets up the browser window
     */
    public static void setupBrowserWindow()
            throws UnsupportedLookAndFeelException, ClassNotFoundException, InstantiationException,
                    IllegalAccessException {
        // discover the inset size and set the look and feel
        if (!GraphicsEnvironment.isHeadless()) {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            final JFrame jframe = new JFrame();
            jframe.setPreferredSize(new Dimension(200, 200));
            jframe.pack();
            WindowConfig.setInsets(jframe.getInsets());
            jframe.dispose();
        }
    }

    /**
     * Add all classes to the constructable registry.
     */
    public static void setupConstructableRegistry() {
        try {
            ConstructableRegistry.getInstance().registerConstructables("");
        } catch (final ConstructableRegistryException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Load the SwirldMain for the app.
     *
     * @param appMainName the name of the app main class
     */
    public static @NonNull SwirldMain loadAppMain(@NonNull final String appMainName) {
        Objects.requireNonNull(appMainName);
        try {
            final Class<?> mainClass = Class.forName(appMainName);
            final Constructor<?>[] constructors = mainClass.getDeclaredConstructors();
            Constructor<?> constructor = null;
            for (final Constructor<?> c : constructors) {
                if (c.getGenericParameterTypes().length == 0) {
                    constructor = c;
                    break;
                }
            }

            if (constructor == null) {
                throw new RuntimeException("Class " + appMainName + " does not have a zero arg constructor.");
            }

            return (SwirldMain) constructor.newInstance();
        } catch (final ClassNotFoundException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Detect if there is a software upgrade. This method will throw an IllegalStateException if the loaded signed state
     * software version is greater than the current application software version.
     *
     * @param appVersion        the version of the app
     * @param loadedSignedState the signed state that was loaded from disk
     * @return true if there is a software upgrade, false otherwise
     */
    public static boolean detectSoftwareUpgrade(
            @NonNull final SoftwareVersion appVersion, @Nullable final SignedState loadedSignedState) {
        Objects.requireNonNull(appVersion, "The app version must not be null.");

        final SoftwareVersion loadedSoftwareVersion = loadedSignedState == null
                ? null
                : loadedSignedState
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getCreationSoftwareVersion();
        final int versionComparison = loadedSoftwareVersion == null ? 1 : appVersion.compareTo(loadedSoftwareVersion);
        final boolean softwareUpgrade;
        if (versionComparison < 0) {
            throw new IllegalStateException(
                    "The current software version `" + appVersion + "` is prior to the software version `"
                            + loadedSoftwareVersion + "` that created the state that was loaded from disk.");
        } else if (versionComparison > 0) {
            softwareUpgrade = true;
            logger.info(
                    STARTUP.getMarker(),
                    "Software upgrade in progress. Previous software version was {}, current version is {}.",
                    loadedSoftwareVersion,
                    appVersion);
        } else {
            softwareUpgrade = false;
            logger.info(
                    STARTUP.getMarker(),
                    "Not upgrading software, current software is version {}.",
                    loadedSoftwareVersion);
        }
        return softwareUpgrade;
    }

    /**
     * Instantiate and start the thread dump generator.
     *
     * @param configuration the configuration object
     */
    public static void startThreadDumpGenerator(@NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration);
        final ThreadConfig threadConfig = configuration.getConfigData(ThreadConfig.class);

        if (threadConfig.threadDumpPeriodMs() > 0) {
            final Path dir = getAbsolutePath(threadConfig.threadDumpLogDir());
            if (!Files.exists(dir)) {
                rethrowIO(() -> Files.createDirectories(dir));
            }
            logger.info(STARTUP.getMarker(), "Starting thread dump generator and save to directory {}", dir);
            ThreadDumpGenerator.generateThreadDumpAtIntervals(dir, threadConfig.threadDumpPeriodMs());
        }
    }

    /**
     * Instantiate and start the JVMPauseDetectorThread, if enabled via the
     * {@link BasicConfig#jvmPauseDetectorSleepMs()} setting.
     *
     * @param configuration the configuration object
     */
    public static void startJVMPauseDetectorThread(@NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration);

        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        if (basicConfig.jvmPauseDetectorSleepMs() > 0) {
            final JVMPauseDetectorThread jvmPauseDetectorThread = new JVMPauseDetectorThread(
                    (pauseTimeMs, allocTimeMs) -> {
                        if (pauseTimeMs > basicConfig.jvmPauseReportMs()) {
                            logger.warn(
                                    EXCEPTION.getMarker(),
                                    "jvmPauseDetectorThread detected JVM paused for {} ms, allocation pause {} ms",
                                    pauseTimeMs,
                                    allocTimeMs);
                        }
                    },
                    basicConfig.jvmPauseDetectorSleepMs());
            jvmPauseDetectorThread.start();
            logger.debug(STARTUP.getMarker(), "jvmPauseDetectorThread started");
        }
    }

    /**
     * Build the app main.
     *
     * @param appDefinition the app definition
     * @param appLoader     an object capable of loading the app
     * @return the new app main
     */
    public static @NonNull SwirldMain buildAppMain(
            @NonNull final ApplicationDefinition appDefinition, @NonNull final SwirldAppLoader appLoader) {
        Objects.requireNonNull(appDefinition);
        Objects.requireNonNull(appLoader);
        try {
            return appLoader.instantiateSwirldMain();
        } catch (final Exception e) {
            CommonUtils.tellUserConsolePopup(
                    "ERROR",
                    "ERROR: There are problems starting class " + appDefinition.getMainClassName() + "\n"
                            + StackTrace.getStackTrace(e));
            logger.error(EXCEPTION.getMarker(), "Problems with class {}", appDefinition.getMainClassName(), e);
            throw new RuntimeException("Problems with class " + appDefinition.getMainClassName(), e);
        }
    }

    /**
     * Writes all settings and config values to settingsUsed.txt
     *
     * @param configuration the configuration values to write
     */
    public static void writeSettingsUsed(@NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration);
        final StringBuilder settingsUsedBuilder = new StringBuilder();

        // Add all settings values to the string builder
        final PathsConfig pathsConfig = configuration.getConfigData(PathsConfig.class);

        settingsUsedBuilder.append(System.lineSeparator());
        settingsUsedBuilder.append("------------- All Configuration -------------");
        settingsUsedBuilder.append(System.lineSeparator());

        // Add all config values to the string builder
        ConfigExport.addConfigContents(configuration, settingsUsedBuilder);

        // Write the settingsUsed.txt file
        final Path settingsUsedPath =
                pathsConfig.getSettingsUsedDir().resolve(PlatformConfigUtils.SETTING_USED_FILENAME);
        try (final OutputStream outputStream = new FileOutputStream(settingsUsedPath.toFile())) {
            outputStream.write(settingsUsedBuilder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException | RuntimeException e) {
            logger.error(STARTUP.getMarker(), "Failed to write settingsUsed to file {}", settingsUsedPath, e);
        }
    }

    /**
     * Determine which nodes should be run locally
     *
     * @param addressBook       the address book
     * @param localNodesToStart local nodes specified to start by the user
     * @return the nodes to run locally
     */
    public static @NonNull List<NodeId> getNodesToRun(
            @NonNull final AddressBook addressBook, @NonNull final Set<NodeId> localNodesToStart) {
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(localNodesToStart);
        final List<NodeId> nodesToRun = new ArrayList<>();
        for (final Address address : addressBook) {
            // if the local nodes to start are not specified, start all local nodes. Otherwise, start specified.
            if (AddressBookNetworkUtils.isLocal(address)
                    && (localNodesToStart.isEmpty() || localNodesToStart.contains(address.getNodeId()))) {
                nodesToRun.add(address.getNodeId());
            }
        }

        return nodesToRun;
    }

    /**
     * Checks the nodes to run and exits if there are no nodes to run
     *
     * @param nodesToRun the nodes to run
     */
    public static void checkNodesToRun(@NonNull final Collection<NodeId> nodesToRun) {
        // if the local machine did not match any address in the address book then we should log an error and exit
        if (nodesToRun.isEmpty()) {
            final String externalIpAddress = Network.getExternalIpAddress().getIpAddress();
            logger.error(
                    EXCEPTION.getMarker(),
                    new NodeAddressMismatchPayload(Network.getInternalIPAddress(), externalIpAddress));
            exitSystem(NODE_ADDRESS_MISMATCH);
        }
        logger.info(STARTUP.getMarker(), "there are {} nodes with local IP addresses", nodesToRun.size());
    }

    /**
     * Load all {@link SwirldMain} instances for locally run nodes.  Locally run nodes are indicated in two possible
     * ways.  One is through the set of local nodes to start.  The other is through {@link Address ::isOwnHost} being
     * true.
     *
     * @param appDefinition the application definition
     * @param nodesToRun    the locally run nodeIds
     * @return a map from nodeIds to {@link SwirldMain} instances
     */
    @NonNull
    public static Map<NodeId, SwirldMain> loadSwirldMains(
            @NonNull final ApplicationDefinition appDefinition, @NonNull final Collection<NodeId> nodesToRun) {
        Objects.requireNonNull(appDefinition, "appDefinition must not be null");
        Objects.requireNonNull(nodesToRun, "nodesToRun must not be null");
        try {
            // Create the SwirldAppLoader
            final SwirldAppLoader appLoader;
            try {
                appLoader =
                        SwirldAppLoader.loadSwirldApp(appDefinition.getMainClassName(), appDefinition.getAppJarPath());
            } catch (final AppLoaderException e) {
                CommonUtils.tellUserConsolePopup("ERROR", e.getMessage());
                throw e;
            }

            // Register all RuntimeConstructable classes
            logger.debug(STARTUP.getMarker(), "Scanning the classpath for RuntimeConstructable classes");
            final long start = System.currentTimeMillis();
            ConstructableRegistry.getInstance().registerConstructables("", appLoader.getClassLoader());
            logger.debug(
                    STARTUP.getMarker(),
                    "Done with registerConstructables, time taken {}ms",
                    System.currentTimeMillis() - start);

            // Create the SwirldMain instances
            final Map<NodeId, SwirldMain> appMains = new HashMap<>();
            for (final NodeId nodeId : nodesToRun) {
                appMains.put(nodeId, buildAppMain(appDefinition, appLoader));
            }
            return appMains;
        } catch (final Exception ex) {
            throw new RuntimeException("Error loading SwirldMains", ex);
        }
    }
}
