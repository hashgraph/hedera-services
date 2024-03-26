/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.util.BootstrapUtils.startJVMPauseDetectorThread;
import static com.swirlds.platform.util.BootstrapUtils.startThreadDumpGenerator;
import static com.swirlds.platform.util.BootstrapUtils.writeSettingsUsed;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.startup.Log4jSetup;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.legacy.payload.NodeStartPayload;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Static setup code for a platform. Logic in this class should gradually disappear as we move away from the use of
 * static stateful objects.
 */
final class StaticPlatformBuilder {

    public static final String LOG4J_FILE_NAME = "log4j2.xml";

    private static final Logger logger = LogManager.getLogger(StaticPlatformBuilder.class);

    // @formatter:off
    private static final String STARTUP_MESSAGE =
            """
              //////////////////////
             // Node is Starting //
            //////////////////////""";
    // @formatter:on

    /**
     * Static stateful variables are evil, but this one is required until we can refactor all the other places that use
     * them.
     */
    private static boolean staticSetupCompleted = false;

    private static DefaultMetricsProvider metricsProvider;

    private static Metrics globalMetrics;

    private StaticPlatformBuilder() {}

    /**
     * Setup global metrics.
     *
     * @param configuration the configuration for this node
     */
    static void setupGlobalMetrics(@NonNull final Configuration configuration) {
        if (metricsProvider == null) {
            metricsProvider = new DefaultMetricsProvider(configuration);
            globalMetrics = metricsProvider.createGlobalMetrics();
            CryptoMetrics.registerMetrics(globalMetrics);
        }
    }

    /**
     * Setup static utilities. If running multiple platforms in the same JVM and this method is called more than once
     * then this method becomes a no-op.
     *
     * @param configuration the configuration for this node
     * @return true if this is the first time this method has been called, false otherwise
     */
    static boolean doStaticSetup(@NonNull final Configuration configuration, @Nullable final Path configPath) {

        if (staticSetupCompleted) {
            // Only setup static utilities once
            return false;
        }
        staticSetupCompleted = true;

        ConfigurationHolder.getInstance().setConfiguration(configuration);

        // Setup logging
        final Path log4jPath = getAbsolutePath(LOG4J_FILE_NAME);
        try {
            Log4jSetup.startLoggingFramework(log4jPath).await();
        } catch (final InterruptedException e) {
            // since the logging framework has not been instantiated, also log to stderr
            e.printStackTrace();
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for log4j to initialize", e);
        }

        // Now that we have a logger, we can start using it for further messages
        logger.info(STARTUP.getMarker(), "\n\n" + STARTUP_MESSAGE + "\n");
        logger.debug(STARTUP.getMarker(), () -> new NodeStartPayload().toString());

        BootstrapUtils.performHealthChecks(configPath, configuration);
        writeSettingsUsed(configuration);

        // Initialize the thread dump generator, if enabled via settings
        startThreadDumpGenerator(configuration);

        // Initialize JVMPauseDetectorThread, if enabled via settings
        startJVMPauseDetectorThread(configuration);

        return true;
    }

    /**
     * Get the static metrics provider.
     */
    @NonNull
    static DefaultMetricsProvider getMetricsProvider() {
        if (metricsProvider == null) {
            throw new IllegalStateException("Metrics provider has not been initialized");
        }
        return metricsProvider;
    }

    /**
     * Get the static global metrics.
     */
    @NonNull
    static Metrics getGlobalMetrics() {
        if (globalMetrics == null) {
            throw new IllegalStateException("Global metrics have not been initialized");
        }
        return globalMetrics;
    }
}
