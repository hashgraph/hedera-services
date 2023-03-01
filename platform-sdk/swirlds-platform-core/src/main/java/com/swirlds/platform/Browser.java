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
import static com.swirlds.platform.system.SystemExitReason.NODE_ADDRESS_MISMATCH;

import com.swirlds.common.StartupTime;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.config.export.ConfigExport;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.AliasConfigSource;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.context.internal.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.internal.ConfigurationException;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.IntegerAccumulator;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.auxiliary.AverageAndMaxMetric;
import com.swirlds.common.metrics.auxiliary.MaxIntegerMetric;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.interrupt.Uninterruptable;
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
import com.swirlds.platform.config.ConfigAliases;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.config.legacy.ConfigPropertiesSource;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.gui.internal.InfoApp;
import com.swirlds.platform.gui.internal.InfoMember;
import com.swirlds.platform.gui.internal.InfoSwirld;
import com.swirlds.platform.gui.internal.StateHierarchy;
import com.swirlds.platform.state.signed.SignedStateFileUtils;
import com.swirlds.platform.swirldapp.AppLoaderException;
import com.swirlds.platform.swirldapp.SwirldAppLoader;
import com.swirlds.platform.system.SystemUtils;
import com.swirlds.platform.util.MetricsDocUtils;
import com.swirlds.virtualmap.config.VirtualMapConfig;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.UIManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The Browser that launches the Platforms that run the apps. The Browser has only one public method, which
 * normally does nothing. See the javadoc on the main method for how it can be useful for an app to call it
 * during app development.
 * <p>
 * All class member variables and methods of this class are static, and it can't be instantiated.
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

    /** the thread for each Platform.run */
    private static Thread[] platformRunThreads;

    private static Thread[] appRunThreads;

    private static Browser INSTANCE;

    private final Configuration configuration;

    /**
     * Prevent this class from being instantiated.
     */
    private Browser(final Set<Integer> localNodesToStart) throws IOException {
        logger.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

        // The properties from the config.txt
        final LegacyConfigProperties configurationProperties = LegacyConfigPropertiesLoader.loadConfigFile(
                Settings.getInstance().getConfigPath());

        final ConfigSource settingsConfigSource = LegacyFileConfigSource.ofSettingsFile();
        final ConfigSource settingsAliasConfigSource = ConfigAliases.addConfigAliases(settingsConfigSource);

        final ConfigSource configPropertiesConfigSource = new ConfigPropertiesSource(configurationProperties);
        final ConfigSource configPropertiesAliasConfigSource = new AliasConfigSource(configPropertiesConfigSource);

        this.configuration = ConfigurationBuilder.create()
                .withSource(settingsAliasConfigSource)
                .withSource(configPropertiesAliasConfigSource)
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
                .build();

        ConfigurationHolder.getInstance().setConfiguration(configuration);
        CryptographyHolder.reset();

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
                startPlatforms(configuration, configurationProperties, localNodesToStart);

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
     * Writes all settings and config values to settingsUsed.txt
     *
     * @param configuration
     * 		the configuration values to write
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
     * Start the browser running, if it isn't already running. If it's already running, then Browser.main
     * does nothing. Normally, an app calling Browser.main has no effect, because it was the browser that
     * launched the app in the first place, so the browser is already running.
     * <p>
     * But during app development, it can be convenient to give the app a main method that calls
     * Browser.main. If there is a config.txt file that says to run the app that is being developed, then
     * the developer can run the app within Eclipse. Eclipse will call the app's main() method, which will
     * call the browser's main() method, which launches the browser. The app's main() then returns, and the
     * app stops running. Then the browser will load the app (because of the config.txt file) and let it run
     * normally within the browser. All of this happens within Eclipse, so the Eclipse debugger works, and
     * Eclipse breakpoints within the app will work.
     *
     * @param args
     * 		args is ignored, and has no effect
     */
    public static synchronized void launch(final String... args) {
        if (INSTANCE != null) {
            return;
        }

        StartupTime.markStartupTime();

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

        // Initialize the log4j2 configuration and logging subsystem if a log4j2.xml file is present in the current
        // working directory
        try {
            Log4jSetup.startLoggingFramework(Settings.getInstance().getLogPath());
            logger = LogManager.getLogger(Browser.class);
        } catch (final Exception e) {
            LogManager.getLogger(Browser.class).fatal("Unable to load log context", e);
            System.err.println("FATAL Unable to load log context: " + e);
        }
        try {
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
     * {@link Settings#getJVMPauseDetectorSleepMs()}
     * setting.
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
     * @param appDefinition
     * 		the app definition
     * @param appLoader
     * 		an object capable of loading the app
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

    private void createLocalPlatforms(
            final ApplicationDefinition appDefinition,
            final Crypto[] crypto,
            final InfoSwirld infoSwirld,
            final SwirldAppLoader appLoader,
            final Configuration configuration,
            final MetricsProvider metricsProvider) {

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

                final SwirldMain appMain = buildAppMain(appDefinition, appLoader);
                appMain.setConfiguration(configuration);

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
                        addressBook.copy(),
                        platformContext,
                        platformName,
                        // name of the app's SwirldMain class
                        appDefinition.getMainClassName(),
                        // the name of this swirld
                        appDefinition.getSwirldName(),
                        appMain.getSoftwareVersion(),
                        appMain::newState);

                new InfoMember(infoSwirld, i, platform);

                appMain.init(platform, nodeId);

                final Thread appThread = new ThreadConfiguration(getStaticThreadManager())
                        .setNodeId(nodeId.getId())
                        .setComponent("app")
                        .setThreadName("appMain")
                        .setRunnable(appMain)
                        .build();
                appRunThreads[ownHostIndex] = appThread;

                platformRunThreads[ownHostIndex] = new ThreadConfiguration(getStaticThreadManager())
                        .setDaemon(false)
                        .setPriority(Settings.getInstance().getThreadPriorityNonSync())
                        .setNodeId((long) ownHostIndex)
                        .setComponent(SwirldsPlatform.PLATFORM_THREAD_POOL_NAME)
                        .setThreadName("platformRun")
                        .setRunnable(() -> {
                            platform.run();
                            // When the SwirldMain quits, end the run() for this platform instance
                            Uninterruptable.abortAndLogIfInterrupted(
                                    appThread::join, "interrupted when waiting for app thread to terminate");
                        })
                        .build();

                ownHostIndex++;
                synchronized (getPlatforms()) {
                    getPlatforms().add(platform);
                }
            }
        }
    }

    /**
     * Instantiate and run all the local platforms specified in the given config.txt file. This method reads
     * in and parses the config.txt file.
     *
     * @throws UnknownHostException
     * 		problems getting an IP address for another user
     * @throws SocketException
     * 		problems getting the IP address for self
     * @throws AppLoaderException
     * 		if there are issues loading the user app
     * @throws ConstructableRegistryException
     * 		if there are issues registering
     *        {@link com.swirlds.common.constructable.RuntimeConstructable} classes
     */
    private void startPlatforms(
            final Configuration configuration,
            final LegacyConfigProperties configurationProperties,
            final Set<Integer> localNodesToStart)
            throws AppLoaderException, ConstructableRegistryException {

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final ApplicationDefinition appDefinition;
        final AddressBook addressBook;
        try {
            appDefinition = ApplicationDefinitionLoader.load(configurationProperties, localNodesToStart);
            addressBook = appDefinition.getAddressBook();
        } catch (final ConfigurationException ex) {
            return;
        }

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
        platformRunThreads = new Thread[ownHostCount];
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

        // Try to load the app
        final SwirldAppLoader appLoader;
        try {
            appLoader = SwirldAppLoader.loadSwirldApp(appDefinition.getMainClassName(), appDefinition.getAppJarPath());
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

        // Setup metrics system
        final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        final Metrics globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);



        // TODO: Temporary code added for testing. Do not forget to remove before pushing.
        final AverageAndMaxMetric groupMetric = globalMetrics.getOrCreate(
                new AverageAndMaxMetric.ConfigBuilder("michaelsTest", "myGroupMetric").build()
        );
        final MaxIntegerMetric metricWrapper = globalMetrics.getOrCreate(
                new MaxIntegerMetric.ConfigBuilder("michaelsTest", "myMetricWrapper").build()
        );
        final IntegerAccumulator baseMetric = globalMetrics.getOrCreate(
                new IntegerAccumulator.ConfigBuilder("michaelsTest", "myBaseMetric").build()
        );

        final Random random = new Random();
        final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            groupMetric.update(random.nextInt(100));
            metricWrapper.update(random.nextInt(100));
            baseMetric.update(random.nextInt(100));
        }, 0, 500, TimeUnit.MILLISECONDS);


        // Create all instances for all nodes that should run locally
        createLocalPlatforms(appDefinition, crypto, infoSwirld, appLoader, configuration, metricsProvider);

        // Write all metrics information to file
        MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);

        // the platforms need to start after all the initial loading has been done
        for (int nodeIndex = 0; nodeIndex < platformRunThreads.length; nodeIndex++) {
            platformRunThreads[nodeIndex].start();
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
        launch(args);
    }
}
