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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.addPlatforms;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.moveBrowserWindowToFront;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.setStateHierarchy;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.showBrowserWindow;
import static com.swirlds.platform.util.BootstrapUtils.checkNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.getNodesToRun;
import static com.swirlds.platform.util.BootstrapUtils.loadPathsConfig;
import static com.swirlds.platform.util.BootstrapUtils.loadSwirldMains;
import static com.swirlds.platform.util.BootstrapUtils.setupBrowserWindow;

import com.swirlds.common.config.PathsConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.internal.ApplicationDefinition;
import com.swirlds.common.startup.CommandLineArgs;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SystemExitCode;
import com.swirlds.common.system.SystemExitUtils;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.internal.PlatformConfigUtils;
import com.swirlds.platform.crypto.CryptoConstants;
import com.swirlds.platform.gui.internal.StateHierarchy;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
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
     * @param pcesRecovery if true, the platform will be started in PCES recovery mode
     */
    public static void launch(@NonNull final CommandLineArgs commandLineArgs, final boolean pcesRecovery) {
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
            launchUnhandled(commandLineArgs, pcesRecovery);
        } catch (final Exception e) {
            logger.error(EXCEPTION.getMarker(), "Unable to start Browser", e);
            throw new RuntimeException("Unable to start Browser", e);
        }
    }

    /**
     * Launch the browser but do not handle any exceptions
     *
     * @param commandLineArgs the parsed command line arguments
     * @param pcesRecovery if true, the platform will be started in PCES recovery mode
     */
    private static void launchUnhandled(@NonNull final CommandLineArgs commandLineArgs, final boolean pcesRecovery)
            throws Exception {
        Objects.requireNonNull(commandLineArgs);

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

        //        ConfigurationHolder.getInstance().setConfiguration(configuration);
        //        CryptographyHolder.reset();

        setupBrowserWindow();
        setStateHierarchy(new StateHierarchy(null));
        appDefinition.setSwirldId(new byte[CryptoConstants.HASH_SIZE_BYTES]);

        final Map<NodeId, SwirldsPlatform> platforms = new HashMap<>();
        for (final NodeId nodeId : nodesToRun) {
            final SwirldMain appMain = appMains.get(nodeId);

            final SwirldsPlatform platform = new SwirldsPlatformBuilder(appMain, nodeId).build();
            platforms.put(nodeId, platform);
        }

        addPlatforms(platforms.values());

        // TODO
        if (pcesRecovery) {
            // PCES recovery is only expected to be done on a single node
            // due to the structure of Browser atm, it makes more sense to enable the feature for multiple platforms
            platforms.values().forEach(SwirldsPlatform::performPcesRecovery);
            SystemExitUtils.exitSystem(SystemExitCode.NO_ERROR, "PCES recovery done");
        }

        platforms.values().forEach(SwirldsPlatform::start);

        showBrowserWindow(null);
        moveBrowserWindowToFront();
    }
}
