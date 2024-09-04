/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_CONFIG_FILE_NAME;
import static com.swirlds.platform.builder.PlatformBuildConstants.DEFAULT_SETTINGS_FILE_NAME;
import static com.swirlds.platform.builder.PlatformBuildConstants.LOG4J_FILE_NAME;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.setupGlobalMetrics;
import static com.swirlds.platform.crypto.CryptoStatic.initNodeSecurity;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.addPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.moveBrowserWindowToFront;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setBrowserWindow;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.showBrowserWindow;
import static com.swirlds.platform.state.signed.StartupStateUtils.getInitialState;
import static com.swirlds.platform.system.address.AddressBookUtils.createRoster;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.loadSwirldMains;
import static com.swirlds.platform.util.BootstrapUtils.setupBrowserWindow;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyFactory;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptographyFactory;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.config.PathsConfig;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.crypto.KeysAndCerts;
import com.swirlds.platform.gui.GuiEventStorage;
import com.swirlds.platform.gui.hashgraph.HashgraphGuiSource;
import com.swirlds.platform.gui.hashgraph.internal.StandardGuiSource;
import com.swirlds.platform.gui.internal.StateHierarchy;
import com.swirlds.platform.gui.internal.WinBrowser;
import com.swirlds.platform.gui.model.InfoApp;
import com.swirlds.platform.gui.model.InfoMember;
import com.swirlds.platform.gui.model.InfoSwirld;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.snapshot.SignedStateFileUtils;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldMain;
import com.swirlds.platform.system.SystemExitCode;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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

        launch(commandLineArgs, false);
    }

    /**
     * Launch the browser with the command line arguments already parsed
     *
     * @param commandLineArgs the parsed command line arguments
     * @param pcesRecovery    if true, the platform will be started in PCES recovery mode
     */
    public static void launch(@NonNull final CommandLineArgs commandLineArgs, final boolean pcesRecovery) {
        if (STARTED.getAndSet(true)) {
            return;
        }

        final Path log4jPath = getAbsolutePath(LOG4J_FILE_NAME);
        try {
            Log4jSetup.startLoggingFramework(log4jPath).await();
        } catch (final InterruptedException e) {
            CommonUtils.tellUserConsole("Interrupted while waiting for log4j to initialize");
            Thread.currentThread().interrupt();
        }

        logger = LogManager.getLogger(Browser.class);

        try {
            launchUnhandled(commandLineArgs, pcesRecovery);
        } catch (final Throwable e) {
            logger.error(EXCEPTION.getMarker(), "Unable to start Browser", e);
            throw new RuntimeException("Unable to start Browser", e);
        }
    }

    /**
     * Launch the browser but do not handle any exceptions
     *
     * @param commandLineArgs the parsed command line arguments
     * @param pcesRecovery    if true, the platform will be started in PCES recovery mode
     */
    private static void launchUnhandled(@NonNull final CommandLineArgs commandLineArgs, final boolean pcesRecovery)
            throws Exception {
        Objects.requireNonNull(commandLineArgs);

        final PathsConfig defaultPathsConfig = ConfigurationBuilder.create()
                .withConfigDataType(PathsConfig.class)
                .build()
                .getConfigData(PathsConfig.class);

        // Load config.txt file, parse application jar file name, main class name, address book, and parameters
        final ApplicationDefinition appDefinition =
                ApplicationDefinitionLoader.loadDefault(defaultPathsConfig, getAbsolutePath(DEFAULT_CONFIG_FILE_NAME));

        // Determine which nodes to run locally
        final List<NodeId> nodesToRun =
                getNodesToRun(appDefinition.getConfigAddressBook(), commandLineArgs.localNodesToStart());
        checkNodesToRun(nodesToRun);

        // Load all SwirldMain instances for locally run nodes.
        final Map<NodeId, SwirldMain> appMains = loadSwirldMains(appDefinition, nodesToRun);
        ParameterProvider.getInstance().setParameters(appDefinition.getAppParameters());

        final boolean showUi = !GraphicsEnvironment.isHeadless();

        final GuiEventStorage guiEventStorage;
        final HashgraphGuiSource guiSource;
        Metrics guiMetrics = null;
        if (showUi) {
            setupBrowserWindow();
            setStateHierarchy(new StateHierarchy(null));
            final InfoApp infoApp = getStateHierarchy().getInfoApp(appDefinition.getApplicationName());
            final InfoSwirld infoSwirld = new InfoSwirld(infoApp, new byte[CryptoConstants.HASH_SIZE_BYTES]);
            new InfoMember(infoSwirld, "Node" + nodesToRun.getFirst().id());

            // Duplicating config here is ugly, but Browser is test only code now.
            // In the future we should clean it up, but it's not urgent to do so.
            final ConfigurationBuilder guiConfigBuilder = ConfigurationBuilder.create();
            BootstrapUtils.setupConfigBuilder(guiConfigBuilder, getAbsolutePath(DEFAULT_SETTINGS_FILE_NAME));
            final Configuration guiConfig = guiConfigBuilder.build();

            guiEventStorage = new GuiEventStorage(guiConfig, appDefinition.getConfigAddressBook());

            guiSource = new StandardGuiSource(appDefinition.getConfigAddressBook(), guiEventStorage);
        } else {
            guiSource = null;
            guiEventStorage = null;
        }

        final Map<NodeId, SwirldsPlatform> platforms = new HashMap<>();
        for (int index = 0; index < nodesToRun.size(); index++) {
            final NodeId nodeId = nodesToRun.get(index);
            final SwirldMain appMain = appMains.get(nodeId);

            final ConfigurationBuilder configBuilder = ConfigurationBuilder.create();
            final List<Class<? extends Record>> configTypes = appMain.getConfigDataTypes();
            for (final Class<? extends Record> configType : configTypes) {
                configBuilder.withConfigDataType(configType);
            }

            rethrowIO(() ->
                    BootstrapUtils.setupConfigBuilder(configBuilder, getAbsolutePath(DEFAULT_SETTINGS_FILE_NAME)));
            final Configuration configuration = configBuilder.build();
            setupGlobalMetrics(configuration);
            guiMetrics = getMetricsProvider().createPlatformMetrics(nodeId);
            final var recycleBin = RecycleBin.create(
                    guiMetrics,
                    configuration,
                    getStaticThreadManager(),
                    Time.getCurrent(),
                    FileSystemManager.create(configuration),
                    nodeId);
            final var cryptography = CryptographyFactory.create();
            CryptographyHolder.set(cryptography);
            final KeysAndCerts keysAndCerts = initNodeSecurity(appDefinition.getConfigAddressBook(), configuration)
                    .get(nodeId);

            // the AddressBook is not changed after this point, so we calculate the hash now
            cryptography.digestSync(appDefinition.getConfigAddressBook());
            final var merkleCryptography = MerkleCryptographyFactory.create(configuration, CryptographyHolder.get());
            MerkleCryptoFactory.set(merkleCryptography);

            final var platformContext = PlatformContext.create(
                    configuration,
                    Time.getCurrent(),
                    guiMetrics,
                    cryptography,
                    FileSystemManager.create(configuration),
                    recycleBin,
                    MerkleCryptographyFactory.create(configuration, CryptographyHolder.get()));
            final ReservedSignedState initialState = getInitialState(
                    platformContext,
                    appMain.getSoftwareVersion(),
                    appMain::newMerkleStateRoot,
                    SignedStateFileUtils::readState,
                    appMain.getClass().getName(),
                    appDefinition.getSwirldName(),
                    nodeId,
                    appDefinition.getConfigAddressBook());
            final PlatformBuilder builder = PlatformBuilder.create(
                    appMain.getClass().getName(),
                    appDefinition.getSwirldName(),
                    appMain.getSoftwareVersion(),
                    initialState,
                    nodeId);
            final AddressBook addressBook = initializeAddressBook(
                    nodeId,
                    appMain.getSoftwareVersion(),
                    initialState,
                    appDefinition.getConfigAddressBook(),
                    platformContext);

            if (showUi && index == 0) {
                builder.withPreconsensusEventCallback(guiEventStorage::handlePreconsensusEvent);
                builder.withConsensusSnapshotOverrideCallback(guiEventStorage::handleSnapshotOverride);
            }

            final SwirldsPlatform platform = (SwirldsPlatform) builder.withConfiguration(configuration)
                    .withMetrics(guiMetrics)
                    .withFileSystemManager(platformContext.getFileSystemManager())
                    .withTime(platformContext.getTime())
                    .withCryptography(platformContext.getCryptography())
                    .withAddressBook(addressBook)
                    .withRecycleBin(recycleBin)
                    .withRoster(createRoster(appDefinition.getConfigAddressBook()))
                    .withKeysAndCerts(keysAndCerts)
                    .build();
            platforms.put(nodeId, platform);

            if (showUi) {
                if (index == 0) {
                    guiMetrics = platform.getContext().getMetrics();
                }
            }
        }

        addPlatforms(platforms.values());

        // FUTURE WORK: PCES recovery not compatible with non-Browser launched apps
        if (pcesRecovery) {
            // PCES recovery is only expected to be done on a single node
            // due to the structure of Browser atm, it makes more sense to enable the feature for multiple platforms
            platforms.values().forEach(SwirldsPlatform::performPcesRecovery);
            SystemExitUtils.exitSystem(SystemExitCode.NO_ERROR, "PCES recovery done");
        }

        startPlatforms(new ArrayList<>(platforms.values()), appMains);

        if (showUi) {
            setBrowserWindow(
                    new WinBrowser(nodesToRun.getFirst(), guiSource, guiEventStorage.getConsensus(), guiMetrics));
            showBrowserWindow(null);
            moveBrowserWindowToFront();
        }
    }

    public static @NonNull AddressBook initializeAddressBook(
            final NodeId selfId,
            final SoftwareVersion version,
            final ReservedSignedState initialState,
            final AddressBook bootstrapAddressBook,
            final PlatformContext platformContext) {
        final boolean softwareUpgrade = detectSoftwareUpgrade(version, initialState.get());
        // Initialize the address book from the configuration and platform saved state.
        final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                selfId, version, softwareUpgrade, initialState.get(), bootstrapAddressBook.copy(), platformContext);

        if (addressBookInitializer.hasAddressBookChanged()) {
            final MerkleRoot state = initialState.get().getState();
            // Update the address book with the current address book read from config.txt.
            // Eventually we will not do this, and only transactions will be capable of
            // modifying the address book.
            final PlatformStateAccessor platformState = state.getPlatformState();
            platformState.bulkUpdate(v -> {
                v.setAddressBook(addressBookInitializer.getCurrentAddressBook().copy());
                v.setPreviousAddressBook(
                        addressBookInitializer.getPreviousAddressBook() == null
                                ? null
                                : addressBookInitializer
                                        .getPreviousAddressBook()
                                        .copy());
            });
        }

        // At this point the initial state must have the current address book set.  If not, something is wrong.
        final AddressBook addressBook =
                initialState.get().getState().getPlatformState().getAddressBook();
        if (addressBook == null) {
            throw new IllegalStateException("The current address book of the initial state is null.");
        }
        return addressBook;
    }

    /**
     * Start all local platforms.
     *
     * @param platforms the platforms to start
     */
    private static void startPlatforms(
            @NonNull final List<SwirldsPlatform> platforms, @NonNull final Map<NodeId, SwirldMain> appMains) {

        final List<Thread> startThreads = new ArrayList<>();
        for (final SwirldsPlatform platform : platforms) {
            final Thread thread = new ThreadConfiguration(getStaticThreadManager())
                    .setThreadName("start-node-" + platform.getSelfId().id())
                    .setRunnable(() -> startPlatform(platform, appMains.get(platform.getSelfId())))
                    .build(true);
            startThreads.add(thread);
        }

        for (final Thread startThread : startThreads) {
            try {
                startThread.join();
            } catch (final InterruptedException e) {
                logger.error(EXCEPTION.getMarker(), "Interrupted while waiting for platform to start", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Start a platform and its associated app.
     *
     * @param platform the platform to start
     * @param appMain  the app to start
     */
    private static void startPlatform(@NonNull final SwirldsPlatform platform, @NonNull final SwirldMain appMain) {
        appMain.init(platform, platform.getSelfId());
        platform.start();
        new ThreadConfiguration(getStaticThreadManager())
                .setNodeId(platform.getSelfId())
                .setComponent("app")
                .setThreadName("appMain")
                .setRunnable(appMain)
                .setDaemon(false)
                .build(true);
    }
}
