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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.addPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.moveBrowserWindowToFront;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.showBrowserWindow;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;
import static com.swirlds.platform.util.BootstrapUtils.getInitialState;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.loadPathsConfig;
import static com.swirlds.platform.util.BootstrapUtils.loadSwirldMains;
import static com.swirlds.platform.util.BootstrapUtils.setupBrowserWindow;
import static com.swirlds.platform.util.BootstrapUtils.startJVMPauseDetectorThread;
import static com.swirlds.platform.util.BootstrapUtils.startThreadDumpGenerator;
import static com.swirlds.platform.util.BootstrapUtils.writeSettingsUsed;

import com.swirlds.base.time.Time;
import com.swirlds.common.StartupTime;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.io.utility.RecycleBinImpl;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.MetricsProvider;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.startup.CommandLineArgs;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.gui.model.GuiModel;
import com.swirlds.gui.model.InfoApp;
import com.swirlds.gui.model.InfoMember;
import com.swirlds.gui.model.InfoSwirld;
import com.swirlds.logging.payloads.NodeStartPayload;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.gui.internal.StateHierarchy;
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
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    /**
     * True if the browser has been launched
     */
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    // @formatter:off
    private static final String STARTUP_MESSAGE =
            """
              //////////////////////
             // Node is Starting //
            //////////////////////""";
    // @formatter:on

    /**
     * Main method for starting the browser
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        parseCommandLineArgsAndLaunch(args);
    }

    /**
     * Parse the command line arguments and launch the browser
     *
     * @param args command line arguments
     */
    public static void parseCommandLineArgsAndLaunch(@NonNull final String... args) {
        final CommandLineArgs commandLineArgs = CommandLineArgs.parse(args);

        launch(commandLineArgs);
    }

    /**
     * Launch the browser with the command line arguments already parsed
     *
     * @param commandLineArgs the parsed command line arguments
     */
    public static void launch(@NonNull final CommandLineArgs commandLineArgs) {
        if (STARTED.getAndSet(true)) {
            return;
        }

        final Path log4jPath =
                ConfigurationHolder.getConfigData(PathsConfig.class).getLogPath();
        try {
            Log4jSetup.startLoggingFramework(log4jPath).await();
        } catch (final InterruptedException e) {
            CommonUtils.tellUserConsole("Interrupted while waiting for log4j to initialize");
            Thread.currentThread().interrupt();
        }

        logger = LogManager.getLogger(Browser.class);

        try {
            launchUnhandled(commandLineArgs);
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Unable to start Browser", e);
            throw new RuntimeException("Unable to start Browser", e);
        }
    }

    /**
     * Launch the browser but do not handle any exceptions
     *
     * @param commandLineArgs the parsed command line arguments
     */
    private static void launchUnhandled(@NonNull final CommandLineArgs commandLineArgs) throws Exception {
        Objects.requireNonNull(commandLineArgs);

        StartupTime.markStartupTime();
        logger.info(STARTUP.getMarker(), "\n\n" + STARTUP_MESSAGE + "\n");
        logger.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

        // This contains the default PathsConfigs values, since the overrides haven't been loaded in yet
        final PathsConfig pathsConfig = loadPathsConfig();

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final ApplicationDefinition appDefinition =
                ApplicationDefinitionLoader.loadDefault(pathsConfig.getConfigPath());

        // Determine which nodes to run locally
        final List<NodeId> nodesToRun =
                getNodesToRun(appDefinition.getConfigAddressBook(), commandLineArgs.localNodesToStart());
        checkNodesToRun(nodesToRun);

        // Load all SwirldMain instances for locally run nodes.
        final Map<NodeId, SwirldMain> appMains = loadSwirldMains(appDefinition, nodesToRun);
        ParameterProvider.getInstance().setParameters(appDefinition.getAppParameters());
        final Configuration configuration = BootstrapUtils.loadConfig(pathsConfig, appMains);
        PlatformConfigUtils.checkConfiguration(configuration);

        ConfigurationHolder.getInstance().setConfiguration(configuration);
        CryptographyHolder.reset();

        BootstrapUtils.performHealthChecks(configuration);

        setupBrowserWindow();
        // Write the settingsUsed.txt file
        writeSettingsUsed(configuration);
        // find all the apps in data/apps and stored states in data/states
        setStateHierarchy(new StateHierarchy(null));

        final AddressBook configAddressBook = appDefinition.getConfigAddressBook();

        // If enabled, clean out the signed state directory. Needs to be done before the platform/state is started up,
        // as we don't want to delete the temporary file directory if it ends up being put in the saved state directory.
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final String mainClassName = stateConfig.getMainClassName(appDefinition.getMainClassName());
        if (stateConfig.cleanSavedStateDirectory()) {
            SignedStateFileUtils.cleanStateDirectory(mainClassName);
        }

        appDefinition.setSwirldId(new byte[CryptoConstants.HASH_SIZE_BYTES]);

        // Create the various keys and certificates (which are saved in various Crypto objects).
        // Save the certificates in the trust stores.
        // Save the trust stores in the address book.
        logger.debug(STARTUP.getMarker(), "About do crypto instantiation");
        final Map<NodeId, Crypto> crypto = initNodeSecurity(appDefinition.getConfigAddressBook(), configuration);
        logger.debug(STARTUP.getMarker(), "Done with crypto instantiation");

        // the AddressBook is not changed after this point, so we calculate the hash now
        CryptographyHolder.get().digestSync(configAddressBook);

        final InfoApp infoApp = getStateHierarchy().getInfoApp(appDefinition.getApplicationName());
        final InfoSwirld infoSwirld = new InfoSwirld(infoApp, appDefinition.getSwirldId());

        // Setup metrics system
        final DefaultMetricsProvider metricsProvider = new DefaultMetricsProvider(configuration);
        final Metrics globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);

        final Map<NodeId, SwirldsPlatform> platforms = new HashMap<>();

        for (final NodeId nodeId : nodesToRun) {
            platforms.put(
                    nodeId,
                    buildPlatform(
                            nodeId,
                            appDefinition,
                            configAddressBook,
                            appMains.get(nodeId),
                            metricsProvider,
                            configuration,
                            infoSwirld,
                            crypto.get(nodeId)));
        }

        addPlatforms(platforms.values());

        // init appMains
        for (final NodeId nodeId : nodesToRun) {
            appMains.get(nodeId).init(platforms.get(nodeId), nodeId);
        }

        // build app threads
        final List<Thread> appRunThreads = new ArrayList<>();
        for (final NodeId nodeId : nodesToRun) {
            final Thread appThread = new ThreadConfiguration(getStaticThreadManager())
                    .setNodeId(nodeId)
                    .setComponent("app")
                    .setThreadName("appMain")
                    .setRunnable(appMains.get(nodeId))
                    .build();
            // IMPORTANT: this swirlds app thread must be non-daemon,
            // so that the JVM will not exit when the main thread exits
            appThread.setDaemon(false);
            appRunThreads.add(appThread);
        }

        // Write all metrics information to file
        MetricsDocUtils.writeMetricsDocumentToFile(globalMetrics, getPlatforms(), configuration);

        logger.debug(STARTUP.getMarker(), "Starting platforms");

        platforms.values().forEach(SwirldsPlatform::start);
        appRunThreads.forEach(Thread::start);

        // Initialize the thread dump generator, if enabled via settings
        startThreadDumpGenerator(configuration);

        // Initialize JVMPauseDetectorThread, if enabled via settings
        startJVMPauseDetectorThread(configuration);

        logger.info(STARTUP.getMarker(), "Starting metrics");
        metricsProvider.start();

        logger.debug(STARTUP.getMarker(), "Done with starting platforms");

        // create the browser window, which uses those Statistics objects
        showBrowserWindow(null);
        moveBrowserWindowToFront();

        CommonUtils.tellUserConsole("This computer has an internal IP address:  " + Network.getInternalIPAddress());
        logger.trace(
                STARTUP.getMarker(), "All of this computer's addresses: {}", () -> Network.getOwnAddresses().stream()
                        .map(InetAddress::getHostAddress)
                        .collect(Collectors.joining(", ")));

        logger.debug(STARTUP.getMarker(), "main() finished");
    }

    /**
     * Build a single instance of a platform
     *
     * @param nodeId            the node id of the platform
     * @param appDefinition     the application definition
     * @param configAddressBook the address book loaded from the config.txt file
     * @param appMain           the swirld main for the platform
     * @param metricsProvider   the metrics provider
     * @param configuration     the configuration
     * @param infoSwirld        the info swirld
     * @param crypto            the crypto instance for this platform
     * @return the built platform
     */
    private static SwirldsPlatform buildPlatform(
            @NonNull final NodeId nodeId,
            @NonNull final ApplicationDefinition appDefinition,
            @NonNull final AddressBook configAddressBook,
            @NonNull final SwirldMain appMain,
            @NonNull final MetricsProvider metricsProvider,
            @NonNull final Configuration configuration,
            @NonNull final InfoSwirld infoSwirld,
            @NonNull final Crypto crypto)
            throws IOException {

        Objects.requireNonNull(nodeId);
        Objects.requireNonNull(appDefinition);
        Objects.requireNonNull(configAddressBook);
        Objects.requireNonNull(appMain);
        Objects.requireNonNull(metricsProvider);
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(infoSwirld);
        Objects.requireNonNull(crypto);

        final Address address = configAddressBook.getAddress(nodeId);
        final int instanceNumber = configAddressBook.getIndexOfNodeId(nodeId);

        // this is a node to start locally.
        final String platformName = address.getNickname()
                + " - " + address.getSelfName()
                + " - " + infoSwirld.getName()
                + " - " + infoSwirld.getApp().getName();

        final PlatformContext platformContext = new DefaultPlatformContext(nodeId, metricsProvider, configuration);

        // name of the app's SwirldMain class
        final String mainClassName = appDefinition.getMainClassName();
        // the name of this swirld
        final String swirldName = appDefinition.getSwirldName();
        final SoftwareVersion appVersion = appMain.getSoftwareVersion();

        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                configuration, platformContext.getMetrics(), getStaticThreadManager(), Time.getCurrent(), nodeId);
        recycleBin.start();

        // We can't send a "real" dispatch, since the dispatcher will not have been started by the
        // time this class is used.
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);
        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final EmergencyRecoveryManager emergencyRecoveryManager = new EmergencyRecoveryManager(
                stateConfig, new Shutdown()::shutdown, basicConfig.getEmergencyRecoveryFileLoadDir());

        final ReservedSignedState initialState = getInitialState(
                platformContext,
                appMain,
                mainClassName,
                swirldName,
                nodeId,
                configAddressBook,
                emergencyRecoveryManager);

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

            GuiModel.getInstance().setPlatformName(nodeId, platformName);
            GuiModel.getInstance().setSwirldId(nodeId, appDefinition.getSwirldId());
            GuiModel.getInstance().setInstanceNumber(nodeId, instanceNumber);

            final SwirldsPlatform platform = new SwirldsPlatform(
                    platformContext,
                    crypto,
                    recycleBin,
                    nodeId,
                    mainClassName,
                    swirldName,
                    appVersion,
                    softwareUpgrade,
                    initialState.get(),
                    addressBookInitializer.getPreviousAddressBook(),
                    emergencyRecoveryManager);
            new InfoMember(infoSwirld, platform);
            return platform;
        }
    }
}
