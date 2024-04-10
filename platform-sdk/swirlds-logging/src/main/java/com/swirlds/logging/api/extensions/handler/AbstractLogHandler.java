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

package com.swirlds.logging.api.extensions.handler;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * An abstract log handler. This class provides some basic functionality that is used by all log handlers.
 */
public abstract class AbstractLogHandler implements LogHandler {

    /**
     * The emergency logger that is used if the log handler is stopped.
     */
    protected static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The configuration key of the log handler. This is used to create configuration keys for the log handler.
     */
    private final String configKey;

    /**
     * The configuration.
     */
    private final Configuration configuration;

    /**
     * The logging level configuration. This is used to define specific logging levels for logger names.
     */
    private final HandlerLoggingLevelConfig loggingLevelConfig;

    /**
     * Creates a new log handler.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public AbstractLogHandler(@NonNull final String configKey, @NonNull final Configuration configuration) {
        this.configKey = Objects.requireNonNull(configKey, "configKey must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.loggingLevelConfig = new HandlerLoggingLevelConfig(configuration, configKey);
    }

    @Override
    public boolean isActive() {
        return Boolean.TRUE.equals(
                configuration.getValue(PROPERTY_HANDLER_ENABLED.formatted(configKey), Boolean.class, false));
    }

    @Override
    public boolean isEnabled(@NonNull final String name, @NonNull final Level level, @Nullable final Marker marker) {
        return loggingLevelConfig.isEnabled(name, level, marker);
    }

    @Override
    public void update(@NonNull final Configuration configuration) {
        loggingLevelConfig.update(configuration);
    }

    /**
     * Returns the configuration
     *
     * @return the configuration
     */
    @NonNull
    protected Configuration getConfiguration() {
        return configuration;
    }
}
