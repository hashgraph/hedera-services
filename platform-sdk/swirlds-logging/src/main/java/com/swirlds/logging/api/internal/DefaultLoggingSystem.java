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

package com.swirlds.logging.api.internal;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import com.swirlds.logging.api.extensions.provider.LogProvider;
import com.swirlds.logging.api.extensions.provider.LogProviderFactory;
import com.swirlds.logging.api.internal.configuration.LogConfiguration;
import com.swirlds.logging.api.internal.emergency.EmergencyLoggerImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The default logging system is a singleton that is used as the logging system. It acts as a wrapper around a single
 * {@link LoggingSystem} instance. In theory it is possible to have multiple logging systems, but in practice at runtime
 * this is the only one that should be used. A custom {@link LoggingSystem} instance can for example be created for
 * tests or benchmarks.
 */
public class DefaultLoggingSystem {

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The singleton instance holder for a more flexible singelton instantiation.
     */
    private static class InstanceHolder {

        /**
         * The real singleton instance.
         */
        private static final DefaultLoggingSystem INSTANCE = new DefaultLoggingSystem();
    }

    /**
     * Flag that defines if the logging system has been initialized.
     */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * The logging system that is internally used.
     */
    private final LoggingSystem internalLoggingSystem;

    /**
     * The default constructor.
     */
    private DefaultLoggingSystem() {
        final Configuration configuration = new LogConfiguration();
        this.internalLoggingSystem = new LoggingSystem(configuration);
        installHandlers(configuration);
        installProviders(configuration);

        // TODO:  EmergencyLogger.setInnerLogger();

        EmergencyLoggerImpl.getInstance().publishLoggedEvents().stream()
                .map(event ->
                        this.internalLoggingSystem.getLogEventFactory().createLogEvent(event, "EMERGENCY-LOGGER-QUEUE"))
                .forEach(internalLoggingSystem::accept);
        INITIALIZED.set(true);
    }

    /**
     * Loads all {@link LogHandlerFactory} instances by SPI / {@link ServiceLoader} and installs them into the logging
     * system.
     *
     * @param configuration The configuration.
     */
    private void installHandlers(final Configuration configuration) {
        final ServiceLoader<LogHandlerFactory> serviceLoader = ServiceLoader.load(LogHandlerFactory.class);
        final List<LogHandler> handlers = serviceLoader.stream()
                .map(Provider::get)
                .map(factory -> factory.create(configuration))
                .filter(LogHandler::isActive)
                .toList();
        handlers.forEach(internalLoggingSystem::addHandler);
        EMERGENCY_LOGGER.log(Level.DEBUG, handlers.size() + " logging handlers installed: " + handlers);
    }

    /**
     * Loads all {@link LogProviderFactory} instances by SPI / {@link ServiceLoader} and installs them into the logging
     * system.
     *
     * @param configuration The configuration.
     */
    private void installProviders(final Configuration configuration) {
        final ServiceLoader<LogProviderFactory> serviceLoader = ServiceLoader.load(LogProviderFactory.class);
        final List<LogProvider> providers = serviceLoader.stream()
                .map(Provider::get)
                .map(factory -> factory.create(configuration))
                .filter(LogProvider::isActive)
                .toList();
        providers.forEach(p -> p.install(internalLoggingSystem.getLogEventFactory(), internalLoggingSystem));
        EMERGENCY_LOGGER.log(Level.DEBUG, providers.size() + " logging providers installed: " + providers);
    }

    /**
     * Returns the singleton instance.
     *
     * @return The singleton instance.
     */
    public static DefaultLoggingSystem getInstance() {
        return InstanceHolder.INSTANCE;
    }

    /**
     * Returns the logger with the given name.
     *
     * @param loggerName The logger name.
     * @return The logger.
     */
    @NonNull
    public Logger getLogger(@NonNull String loggerName) {
        return internalLoggingSystem.getLogger(loggerName);
    }

    /**
     * Adds the given log handler to the logging system.
     *
     * @param logHandler The log handler.
     */
    public void addHandler(LogHandler logHandler) {
        internalLoggingSystem.addHandler(logHandler);
    }

    /**
     * Removes the given log handler from the logging system.
     *
     * @param logHandler
     */
    public void removeHandler(LogHandler logHandler) {
        internalLoggingSystem.removeHandler(logHandler);
    }

    /**
     * Returns true if the logging system has been initialized.
     *
     * @return True if the logging system has been initialized.
     */
    public static boolean isInitialized() {
        return INITIALIZED.get();
    }
}
