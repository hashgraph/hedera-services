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

package com.swirlds.platform.startup;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.impl.Log4jContextFactory;
import org.apache.logging.log4j.core.util.DefaultShutdownCallbackRegistry;
import org.apache.logging.log4j.spi.LoggerContextFactory;

/**
 * Utility methods for bootstrapping log4j.
 */
public final class Log4jSetup {

    private Log4jSetup() {}

    /**
     * Initialize the log4j2 configuration and logging subsystem if log4j config file is present in the path specified
     *
     * @param configPath
     * 		the path to the log4j configuration file. If path does not exist then this method is a no-op.
     */
    public static void startLoggingFramework(@Nullable final Path configPath) {
        if (configPath == null) {
            return;
        }
        try {
            if (Files.exists(configPath)) {
                final LoggerContext context = (LoggerContext) LogManager.getContext(false);
                context.setConfigLocation(configPath.toUri());
            }
            Logger logger = LogManager.getLogger(Log4jSetup.class);

            if (Thread.getDefaultUncaughtExceptionHandler() == null) {
                Thread.setDefaultUncaughtExceptionHandler((final Thread t, final Throwable e) ->
                        logger.error(EXCEPTION.getMarker(), "exception on thread {}", t.getName(), e));
            }

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
            LogManager.getLogger(Log4jSetup.class).fatal("Unable to load log context", e);
            System.err.println("FATAL Unable to load log context: " + e);
        }
    }
}
