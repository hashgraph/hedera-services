/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.io.utility.FileUtils.rethrowIO;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.JVM_PAUSE_WARN;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getBrowserWindow;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setInsets;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.showBrowserWindow;
import static com.swirlds.platform.state.address.AddressBookUtils.getOwnHostCount;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.system.SystemExitReason.NODE_ADDRESS_MISMATCH;

import com.swirlds.common.StartupTime;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.config.export.ConfigExport;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.config.sources.MappedConfigSource;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.context.internal.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.fchashmap.config.FCHashMapConfig;
import com.swirlds.jasperdb.config.JasperDbConfig;
import com.swirlds.logging.payloads.NodeAddressMismatchPayload;
import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.p2p.portforwarding.PortForwarder;
import com.swirlds.p2p.portforwarding.PortMapping;
import com.swirlds.platform.chatter.config.ChatterConfig;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.config.ConfigMappings;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.config.legacy.ConfigPropertiesSource;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.event.preconsensus.PreConsensusEventStreamConfig;
import com.swirlds.platform.gui.internal.InfoApp;
import com.swirlds.platform.gui.internal.InfoMember;
import com.swirlds.platform.gui.internal.InfoSwirld;
import com.swirlds.platform.gui.internal.StateHierarchy;
import com.swirlds.platform.health.OSHealthChecker;
import com.swirlds.platform.health.clock.OSClockSpeedSourceChecker;
import com.swirlds.platform.health.entropy.OSEntropyChecker;
import com.swirlds.platform.health.filesystem.OSFileSystemChecker;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.state.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import com.swirlds.platform.system.Shutdown;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.platform.util.MetricsDocUtils;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.UIManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry;
import org.apache.logging.log4j.spi.LoggerContextFactory;

/**
 * The Browser that launches the Platforms that run the apps.
 */
public class Browser {
    // Each member is represented by an AddressBook entry in config.txt. On a given computer, a single java
    // process runs all members whose listed internal IP address matches some address on that computer. That
    // Java process will instantiate one Platform per member running on that machine. But there will be only
    // one static Browser that they all share.
    //
    // Every member, whatever computer it is running on, listens on 0.0.0.0, on its internal port. Every
    // member connects to every other member, by computing its IP address as follows: If that other member
    // is also on the same host, use 127.0.0.1. If it is on the same LAN[*], use its internal address.
    // Otherwise, use its external address.
    //
    // This way, a single config.txt can be shared across computers unchanged, even if, for example, those
    // computers are on different networks in Amazon EC2.
    //
    // [*] Two members are considered to be on the same LAN if their listed external addresses are the same.

    private static Logger logger = LogManager.getLogger(Browser.class);

    private static Thread[] appRunThreads;

    private static Browser INSTANCE;

    private final Configuration configuration;

    private static final String STARTUP_MESSAGE =
            """
                      //////////////////////
                     // Node is Starting //
                    //////////////////////""";

    /**
     * Prevent this class from being instantiated.
     */
    private Browser(final Set<Integer> localNodesToStart) throws IOException {
        logger.info(STARTUP.getMarker(), "\n\n" + STARTUP_MESSAGE + "\n");
        logger.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

        // The properties from the config.txt
        final LegacyConfigProperties configurationProperties = LegacyConfigPropertiesLoader.loadConfigFile(
                Settings.getInstance().getConfigPath());

        final ConfigSource settingsConfigSource = LegacyFileConfigSource.ofSettingsFile();
        final ConfigSource mappedSettingsConfigSource = ConfigMappings.addConfigMapping(settingsConfigSource);

        final ConfigSource configPropertiesConfigSource = new ConfigPropertiesSource(configurationProperties);
        final ConfigSource mappedConfigPropertiesConfigSource = new MappedConfigSource(configPropertiesConfigSource);

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final ApplicationDefinition appDefinition =
                ApplicationDefinitionLoader.load(configurationProperties, localNodesToStart);

        // Load all SwirldMain instances for locally run nodes.
        final Map<Long, SwirldMain> appMains = loadSwirldMains(appDefinition, localNodesToStart);

        // Load Configuration Definitions
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSource(mappedSettingsConfigSource)
                .withSource(mappedConfigPropertiesConfigSource)
                .withConfigDataType(BasicConfig.class)
                .withConfigDataType(StateConfig.class)
                .withConfigDataType(CryptoConfig.class)
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(ReconnectConfig.class)
                .withConfigDataType(FCHashMapConfig.class)
                .withConfigDataType(JasperDbConfig.class)
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
                .withConfigDataType(PreConsensusEventStreamConfig.class);

        // Assume all locally run instances provide the same configuration definitions to the configuration builder.
        if (appMains.size() > 0) {
            appMains.values().iterator().next().updateConfigurationBuilder(configurationBuilder);
        }

        this.configuration = configurationBuilder.build();

        // Set the configuration on all SwirldMain instances.
        appMains.values().forEach(swirldMain -> swirldMain.setConfiguration(configuration));

        ConfigurationHolder.getInstance().setConfiguration(configuration);
        CryptographyHolder.reset();

        OSHealthChecker.performOSHealthChecks(
                configuration.getConfigData(OSHealthCheckConfig.class),
                List.of(
                        OSClockSpeedSourceChecker::performClockSourceSpeedCheck,
                        OSEntropyChecker::performEntropyChecks,
                        OSFileSystemChecker::performFileSystemCheck));

        try {
            // discover the inset size and set the look and feel
            if (!GraphicsEnvironment.isHeadless()) {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
                final JFrame jframe = new JFrame();
                jframe.setPreferredSize(new Dimension(200, 200));
                jframe.pack();
                setInsets(jframe.getInsets());
                jframe.dispose();
            }

            // Read from data/settings.txt (where data is in same directory as .jar, usually sdk/) to change
            // the default settings given in the Settings class. This file won't normally exist. But it can
            // be used for testing and debugging. This is NOT documented for users.
            //
            // Also, if the settings.txt file exists, then after reading it and changing the settings, write
            // all the current settings to settingsUsed.txt, some of which might have been changed by
            // settings.txt
            Settings.getInstance().loadSettings();

            // Provide swirlds.common the settings it needs via the SettingsCommon class
            Settings.populateSettingsCommon();

            // Update Settings based on config.txt
            configurationProperties.tls().ifPresent(tls -> Settings.getInstance()
                    .setUseTLS(tls));
            configurationProperties.maxSyncs().ifPresent(value -> Settings.getInstance()
                    .setMaxOutgoingSyncs(value));
            configurationProperties.transactionMaxBytes().ifPresent(value -> Settings.getInstance()
                    .setTransactionMaxBytes(value));
            configurationProperties.ipTos().ifPresent(ipTos -> Settings.getInstance()
                    .setSocketIpTos(ipTos));
            configurationProperties
                    .saveStatePeriod()
                    .ifPresent(value -> Settings.getInstance().getState().saveStatePeriod = value);

            // Write the settingsUsed.txt file
            writeSettingsUsed(configuration);

            // find all the apps in data/apps and stored states in data/states
            setStateHierarchy(new StateHierarchy(null));

            // read from config.txt (in same directory as .jar, usually sdk/)
            // to fill in the following three variables, which define the
            // simulation to run.

            try {

                if (Files.exists(Settings.getInstance().getConfigPath())) {
                    CommonUtils.tellUserConsole("Reading the configuration from the file:   "
                            + Settings.getInstance().getConfigPath());
                } else {
                    CommonUtils.tellUserConsole("A config.txt file could be created here:   "
                            + Settings.getInstance().getConfigPath());
                    return;
                }
                // instantiate all Platform objects, which each instantiates a Statistics object
                logger.debug(STARTUP.getMarker(), "About to run startPlatforms()");
                startPlatforms(configuration, appDefinition, appMains);

                // create the browser window, which uses those Statistics objects
                showBrowserWindow();
                for (final Frame f : Frame.getFrames()) {
                    if (!f.equals(getBrowserWindow())) {
                        f.toFront();
                    }
                }

                CommonUtils.tellUserConsole(
                        "This computer has an internal IP address:  " + Network.getInternalIPAddress());
                logger.trace(
                        STARTUP.getMarker(),
                        "All of this computer's addresses: {}",
                        () -> (Arrays.toString(Network.getOwnAddresses2())));

                // port forwarding
                if (Settings.getInstance().isDoUpnp()) {
                    final List<PortMapping> portsToBeMapped = new LinkedList<>();
                    synchronized (getPlatforms()) {
                        for (final Platform p : getPlatforms()) {
                            final Address address = p.getSelfAddress();
                            final String ip = Address.ipString(address.getListenAddressIpv4());
                            final PortMapping pm = new PortMapping(
                                    ip,
                                    // ip address (not used by portMapper, which tries all external port
                                    // network
                                    // interfaces)
                                    // (should probably use ports >50000, this is considered the dynamic
                                    // range)
                                    address.getPortInternalIpv4(),
                                    address.getPortExternalIpv4(), // internal port
                                    PortForwarder.Protocol.TCP // transport protocol
                            );
                            portsToBeMapped.add(pm);
                        }
                    }
                    Network.doPortForwarding(getStaticThreadManager(), portsToBeMapped);
                }
            } catch (final Exception e) {
                logger.error(EXCEPTION.getMarker(), "", e);
            }

        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "", e);
        }

        logger.debug(STARTUP.getMarker(), "main() finished");
    }

    /**
     * Load all {@link SwirldMain} instances for locally run nodes.  Locally run nodes are indicated in two possible
     * ways.  One is through the set of local nodes to start.  The other is through {@link Address::isOwnHost} being
     * true.
     *
     * @param appDefinition     the application definition
     * @param localNodesToStart the locally run nodeIds
     * @return a map from nodeIds to {@link SwirldMain} instances
     * @throws AppLoaderException             if there are issues loading the user app
     * @throws ConstructableRegistryException if there are issues registering
     *                                        {@link com.swirlds.common.constructable.RuntimeConstructable} classes
     */
    @NonNull
    private Map<Long, SwirldMain> loadSwirldMains(
            @NonNull final ApplicationDefinition appDefinition, @NonNull final Set<Integer> localNodesToStart) {
        throwArgNull(appDefinition, "appDefinition must not be null");
        throwArgNull(localNodesToStart, "localNodesToStart must not be null");
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
            final Map<Long, SwirldMain> appMains = new HashMap<>();
            final AddressBook addressBook = appDefinition.getAddressBook();
            for (int i = 0; i < addressBook.getSize(); i++) {
                final long id = addressBook.getId(i);
                final Address address = addressBook.getAddress(id);
                if (localNodesToStart.contains((int) id) || address.isOwnHost()) {
                    appMains.put(id, buildAppMain(appDefinition, appLoader));
                }
            }
            return appMains;
        } catch (final Exception ex) {
            throw new RuntimeException("Error loading SwirldMains", ex);
        }
    }

    /**
     * Writes all settings and config values to settingsUsed.txt
     *
     * @param configuration the configuration values to write
     */
    private void writeSettingsUsed(final Configuration configuration) {
        final StringBuilder settingsUsedBuilder = new StringBuilder();

        // Add all settings values to the string builder
        if (Files.exists(Settings.getInstance().getSettingsPath())) {
            Settings.getInstance().addSettingsUsed(settingsUsedBuilder);
        }

        settingsUsedBuilder.append(System.lineSeparator());
        settingsUsedBuilder.append("-------------Configuration Values-------------");
        settingsUsedBuilder.append(System.lineSeparator());

        // Add all config values to the string builder
        ConfigExport.addConfigContents(configuration, settingsUsedBuilder);

        // Write the settingsUsed.txt file
        final Path settingsUsedPath =
                Settings.getInstance().getSettingsUsedDir().resolve(SettingConstants.SETTING_USED_FILENAME);
        try (final OutputStream outputStream = new FileOutputStream(settingsUsedPath.toFile())) {
            outputStream.write(settingsUsedBuilder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final IOException | RuntimeException e) {
            logger.error(STARTUP.getMarker(), "Failed to write settingsUsed to file {}", settingsUsedPath, e);
        }
    }

    /**
     * Start the browser running, if it isn't already running. If it's already running, then Browser.main does nothing.
     * Normally, an app calling Browser.main has no effect, because it was the browser that launched the app in the
     * first place, so the browser is already running.
     * <p>
     * But during app development, it can be convenient to give the app a main method that calls Browser.main. If there
     * is a config.txt file that says to run the app that is being developed, then the developer can run the app within
     * Eclipse. Eclipse will call the app's main() method, which will call the browser's main() method, which launches
     * the browser. The app's main() then returns, and the app stops running. Then the browser will load the app
     * (because of the config.txt file) and let it run normally within the browser. All of this happens within Eclipse,
     * so the Eclipse debugger works, and Eclipse breakpoints within the app will work.
     *
     * @param args args is ignored, and has no effect
     */
    public static synchronized void parseCommandLineArgsAndLaunch(final String... args) {
        if (INSTANCE != null) {
            return;
        }

        // This set contains the nodes set by the command line to start, if none are passed, then IP
        // addresses will be compared to determine which node to start
        final Set<Integer> localNodesToStart = new HashSet<>();

        // Parse command line arguments (rudimentary parsing)
        String currentOption = null;
        if (args != null) {
            for (final String item : args) {
                final String arg = item.trim().toLowerCase();
                switch (arg) {
                    case "-local":
                    case "-log":
                        currentOption = arg;
                        break;
                    default:
                        if (currentOption != null) {
                            switch (currentOption) {
                                case "-local":
                                    try {
                                        localNodesToStart.add(Integer.parseInt(arg));
                                    } catch (final NumberFormatException ex) {
                                        // Intentionally suppress the NumberFormatException
                                    }
                                    break;
                                case "-log":
                                    Settings.getInstance().setLogPath(getAbsolutePath(arg));
                                    break;
                            }
                        }
                }
            }
        }

        launch(localNodesToStart, Settings.getInstance().getLogPath());
    }

    /**
     * Launch the browser.
     *
     * @param localNodesToStart a set of nodes that should be started in this JVM instance
     * @param log4jPath         the path to the log4j configuraiton file, if null then log4j is not started
     */
    public static synchronized void launch(final Set<Integer> localNodesToStart, final Path log4jPath) {
        if (INSTANCE != null) {
            return;
        }

        // Initialize the log4j2 configuration and logging subsystem if a log4j2.xml file is present in the current
        // working directory
        try {
            if (log4jPath != null) {
                Log4jSetup.startLoggingFramework(log4jPath);
            }
            logger = LogManager.getLogger(Browser.class);

            final LoggerContextFactory factory = LogManager.getFactory();
            if (factory instanceof final Log4jContextFactory contextFactory) {
                // Do not allow log4j to use its own shutdown hook. Use our own shutdown
                // hook to stop log4j. This allows us to write a final log message before
                // the logger is shut down.
                ((DefaultShutdownCallbackRegistry) contextFactory.getShutdownCallbackRegistry()).stop();
                Runtime.getRuntime()
                        .addShutdownHook(new ThreadConfiguration(getStaticThreadManager())
                                .setComponent("browser")
                                .setThreadName("shutdown-hook")
                                .setRunnable(() -> {
                                    logger.info(STARTUP.getMarker(), "JVM is shutting down.");
                                    LogManager.shutdown();
                                })
                                .build());
            }
        } catch (final Exception e) {
            LogManager.getLogger(Browser.class).fatal("Unable to load log context", e);
            System.err.println("FATAL Unable to load log context: " + e);
        }
        try {
            StartupTime.markStartupTime();
            INSTANCE = new Browser(localNodesToStart);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create Browser", e);
        }
    }

    /**
     * Instantiate and start the thread dump generator, if enabled via the {@link Settings#getThreadDumpPeriodMs()}
     * setting.
     */
    private void startThreadDumpGenerator() {
        if (Settings.getInstance().getThreadDumpPeriodMs() > 0) {
            final Path dir = getAbsolutePath(Settings.getInstance().getThreadDumpLogDir());
            if (!Files.exists(dir)) {
                rethrowIO(() -> Files.createDirectories(dir));
            }
            ThreadDumpGenerator.generateThreadDumpAtIntervals(
                    dir, Settings.getInstance().getThreadDumpPeriodMs());
        }
    }

    /**
     * Instantiate and start the JVMPauseDetectorThread, if enabled via the
     * {@link Settings#getJVMPauseDetectorSleepMs()} setting.
     */
    private void startJVMPauseDetectorThread() {
        if (Settings.getInstance().getJVMPauseDetectorSleepMs() > 0) {
            final JVMPauseDetectorThread jvmPauseDetectorThread = new JVMPauseDetectorThread(
                    (pauseTimeMs, allocTimeMs) -> {
                        if (pauseTimeMs > Settings.getInstance().getJVMPauseReportMs()) {
                            logger.warn(
                                    JVM_PAUSE_WARN.getMarker(),
                                    "jvmPauseDetectorThread detected JVM paused for {} ms, allocation pause {} ms",
                                    pauseTimeMs,
                                    allocTimeMs);
                        }
                    },
                    Settings.getInstance().getJVMPauseDetectorSleepMs());
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
    private static SwirldMain buildAppMain(final ApplicationDefinition appDefinition, final SwirldAppLoader appLoader) {
        try {
            return appLoader.instantiateSwirldMain();
        } catch (final Exception e) {
            CommonUtils.tellUserConsolePopup(
                    "ERROR",
                    "ERROR: There are problems starting class " + appDefinition.getMainClassName() + "\n"
                            + ExceptionUtils.getStackTrace(e));
            logger.error(EXCEPTION.getMarker(), "Problems with class {}", appDefinition.getMainClassName(), e);
            throw new RuntimeException(e);
        }
    }

    private Collection<SwirldsPlatform> createLocalPlatforms(
            @NonNull final ApplicationDefinition appDefinition,
            @NonNull final Crypto[] crypto,
            @NonNull final InfoSwirld infoSwirld,
            @NonNull final Map<Long, SwirldMain> appMains,
            @NonNull final Configuration configuration,
            @NonNull final MetricsProvider metricsProvider) {
        throwArgNull(appDefinition, "the app definition must not be null");
        throwArgNull(crypto, "the crypto array must not be null");
        throwArgNull(infoSwirld, "the infoSwirld must not be null");
        throwArgNull(appMains, "the appMains map must not be null");
        throwArgNull(configuration, "the configuration must not be null");
        throwArgNull(metricsProvider, "the metricsProvider must not be null");

        final List<SwirldsPlatform> platforms = new ArrayList<>();

        final AddressBook addressBook = appDefinition.getAddressBook();

        int ownHostIndex = 0;

        for (int i = 0; i < addressBook.getSize(); i++) {
            final Address address = addressBook.getAddress(i);

            if (address.isOwnHost()) {

                final String platformName = address.getNickname()
                        + " - " + address.getSelfName()
                        + " - " + infoSwirld.name
                        + " - " + infoSwirld.app.name;
                final NodeId nodeId = NodeId.createMain(i);

                final PlatformContext platformContext =
                        new DefaultPlatformContext(nodeId, metricsProvider, configuration);

                SwirldMain appMain = appMains.get(address.getId());

                // name of the app's SwirldMain class
                final String mainClassName = appDefinition.getMainClassName();
                // the name of this swirld
                final String swirldName = appDefinition.getSwirldName();
                final SoftwareVersion appVersion = appMain.getSoftwareVersion();
                // We can't send a "real" dispatch, since the dispatcher will not have been started by the
                // time this class is used.
                final EmergencyRecoveryManager emergencyRecoveryManager = new EmergencyRecoveryManager(
                        Shutdown::immediateShutDown, Settings.getInstance().getEmergencyRecoveryFileLoadDir());

                final SignedState loadedSignedState = getUnmodifiedSignedStateFromDisk(
                        mainClassName, swirldName, nodeId, appVersion, addressBook.copy(), emergencyRecoveryManager);

                // check software version compatibility
                final boolean softwareUpgrade = BootstrapUtils.detectSoftwareUpgrade(appVersion, loadedSignedState);

                final AddressBookConfig addressBookConfig =
                        platformContext.getConfiguration().getConfigData(AddressBookConfig.class);

                // Initialize the address book from the configuration and platform saved state.
                final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                        appVersion, softwareUpgrade, loadedSignedState, addressBook.copy(), addressBookConfig);

                // set here, then given to the state in run(). A copy of it is given to hashgraph.
                final AddressBook initialAddressBook = addressBookInitializer.getInitialAddressBook();

                final SwirldsPlatform platform = new SwirldsPlatform(
                        // window index
                        ownHostIndex,
                        // parameters from the app line of the config.txt file
                        appDefinition.getAppParameters(),
                        // all key pairs and CSPRNG state for this member
                        crypto[i],
                        // the ID for this swirld (immutable since creation of this swirld)
                        appDefinition.getSwirldId(),
                        // address book index, which is the member ID
                        nodeId,
                        // copy of the address book,
                        initialAddressBook,
                        platformContext,
                        platformName,
                        mainClassName,
                        swirldName,
                        appVersion,
                        appMain::newState,
                        loadedSignedState,
                        emergencyRecoveryManager);
                platforms.add(platform);

                new InfoMember(infoSwirld, i, platform);

                appMain.init(platform, nodeId);

                final Thread appThread = new ThreadConfiguration(getStaticThreadManager())
                        .setNodeId(nodeId.getId())
                        .setComponent("app")
                        .setThreadName("appMain")
                        .setRunnable(appMain)
                        .build();
                appRunThreads[ownHostIndex] = appThread;

                ownHostIndex++;
                synchronized (getPlatforms()) {
                    getPlatforms().add(platform);
                }
            }
        }

        return Collections.unmodifiableList(platforms);
    }

    /**
     * Load the signed state from the disk if it is present.
     *
     * @param mainClassName            the name of the app's SwirldMain class.
     * @param swirldName               the name of the swirld to load the state for.
     * @param selfId                   the ID of the node to load the state for.
     * @param appVersion               the version of the app to use for emergency recovery.
     * @param configAddressBook        the address book to use for emergency recovery.
     * @param emergencyRecoveryManager the emergency recovery manager to use for emergency recovery.
     * @return the signed state loaded from disk.
     */
    private SignedState getUnmodifiedSignedStateFromDisk(
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion appVersion,
            @NonNull final AddressBook configAddressBook,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {
        final SavedStateInfo[] savedStateFiles = getSavedStateFiles(mainClassName, selfId, swirldName);
        // We can't send a "real" dispatcher for shutdown, since the dispatcher will not have been started by the
        // time this class is used.
        final SavedStateLoader savedStateLoader = new SavedStateLoader(
                Shutdown::immediateShutDown,
                configAddressBook,
                savedStateFiles,
                appVersion,
                () -> new EmergencySignedStateValidator(emergencyRecoveryManager.getEmergencyRecoveryFile()),
                emergencyRecoveryManager);
        try {
            return savedStateLoader.getSavedStateToLoad();
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Signed state not loaded from disk:", e);
            if (Settings.getInstance().isRequireStateLoad()) {
                SystemUtils.exitSystem(SystemExitReason.SAVED_STATE_NOT_LOADED);
            }
        }
        return null;
    }

    /**
     * Instantiate and run all the local platforms specified in the given config.txt file. This method reads in and
     * parses the config.txt file.
     *
     * @throws UnknownHostException problems getting an IP address for another user
     * @throws SocketException      problems getting the IP address for self
     */
    private void startPlatforms(
            @NonNull final Configuration configuration,
            @NonNull final ApplicationDefinition appDefinition,
            @NonNull final Map<Long, SwirldMain> appMains) {

        final AddressBook addressBook = appDefinition.getAddressBook();

        // If enabled, clean out the signed state directory. Needs to be done before the platform/state is started up,
        // as we don't want to delete the temporary file directory if it ends up being put in the saved state directory.
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final String mainClassName = stateConfig.getMainClassName(appDefinition.getMainClassName());
        if (stateConfig.cleanSavedStateDirectory()) {
            SignedStateFileUtils.cleanStateDirectory(mainClassName);
        }

        final int ownHostCount = getOwnHostCount(addressBook);
        logger.info(STARTUP.getMarker(), "there are {} nodes with local IP addresses", ownHostCount);

        // if the local machine did not match any address in the address book then we should log an error and exit
        if (ownHostCount < 1) {
            final String externalIpAddress = (Network.getExternalIpAddress() != null)
                    ? Network.getExternalIpAddress().getIpAddress()
                    : null;
            logger.error(
                    EXCEPTION.getMarker(),
                    new NodeAddressMismatchPayload(Network.getInternalIPAddress(), externalIpAddress));
            SystemUtils.exitSystem(NODE_ADDRESS_MISMATCH);
        }

        // the thread for each Platform.run
        // will create a new thread with a new Platform for each local address
        // general address number addIndex is local address number i
        appRunThreads = new Thread[ownHostCount];
        appDefinition.setMasterKey(new byte[CryptoConstants.SYM_KEY_SIZE_BYTES]);
        appDefinition.setSwirldId(new byte[CryptoConstants.HASH_SIZE_BYTES]);

        // Create the various keys and certificates (which are saved in various Crypto objects).
        // Save the certificates in the trust stores.
        // Save the trust stores in the address book.

        logger.debug(STARTUP.getMarker(), "About do crypto instantiation");
        final Crypto[] crypto = initNodeSecurity(appDefinition.getAddressBook(), configuration);
        logger.debug(STARTUP.getMarker(), "Done with crypto instantiation");

        // the AddressBook is not changed after this point, so we calculate the hash now
        CryptographyHolder.get().digestSync(addressBook);

        final InfoApp infoApp = getStateHierarchy().getInfoApp(appDefinition.getApplicationName());
        final InfoSwirld infoSwirld = new InfoSwirld(infoApp, appDefinition.getSwirldId());

        logger.debug(STARTUP.getMarker(), "Starting platforms");

        // Setup metrics system
        final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        final Metrics globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);

        // Create all instances for all nodes that should run locally
        final Collection<SwirldsPlatform> platforms =
                createLocalPlatforms(appDefinition, crypto, infoSwirld, appMains, configuration, metricsProvider);

        // Write all metrics information to file
        MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);

        platforms.forEach(SwirldsPlatform::start);

        for (int nodeIndex = 0; nodeIndex < appRunThreads.length; nodeIndex++) {
            appRunThreads[nodeIndex].start();
        }

        // Initialize the thread dump generator, if enabled via settings
        startThreadDumpGenerator();

        // Initialize JVMPauseDetectorThread, if enabled via settings
        startJVMPauseDetectorThread();

        logger.info(STARTUP.getMarker(), "Starting metrics");
        metricsProvider.start();

        logger.debug(STARTUP.getMarker(), "Done with starting platforms");
    }

    public static void main(final String[] args) {
        parseCommandLineArgsAndLaunch(args);
    }
}
