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

package com.swirlds.logging.api.internal.level;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A configuration that can be used to configure the logging levels of loggers. This class supports to define levels for
 * packages or classes. The configuration is read from the {@link Configuration} object.
 * <p>
 * Like for example in spring boot a configuration like this can be used:
 * <p>
 * {@code logging.level.com.swirlds=DEBUG}
 * <p>
 * In that case all packages and classes under the package {@code com.swirlds} will be configured to use the level
 * DEBUG. If a more specific configuration is defined, that configuration will be used instead. For example:
 * <p>
 * {@code logging.level.com.swirlds.logging.api=INFO}
 * <p>
 * In that case all packages and classes under the package {@code com.swirlds.logging.api} will be configured to use the
 * level INFO.
 * TODO: Timo: Remove this class
 */
@Deprecated(forRemoval = true)
public class LoggingLevelConfig {

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The default prefix for the configuration.
     */
    private static final String DEFAULT_PREFIX = "logging.level";

    /**
     * The cache for the levels.
     */
    private final Map<String, Level> levelCache;

    /**
     * The configuration properties.
     */
    private final Map<String, String> levelConfigProperties;

    /**
     * The prefix for the configuration.
     */
    private final String prefix;

    /**
     * The default level.
     */
    private final Level defaultLevel;

    /**
     * Creates a new logging level configuration based on the given configuration.
     *
     * @param configuration The configuration.
     */
    public LoggingLevelConfig(@NonNull Configuration configuration) {
        this(configuration, DEFAULT_PREFIX);
    }

    /**
     * Creates a new logging level configuration based on the given configuration and prefix.
     *
     * @param configuration The configuration.
     * @param prefix        The prefix.
     */
    public LoggingLevelConfig(Configuration configuration, String prefix) {
        this(configuration, prefix, Level.INFO);
    }

    /**
     * Creates a new logging level configuration based on the given configuration and defaultLevel.
     *
     * @param configuration The configuration.
     * @param defaultLevel  The default level.
     */
    public LoggingLevelConfig(Configuration configuration, Level defaultLevel) {
        this(configuration, DEFAULT_PREFIX, defaultLevel);
    }

    /**
     * Creates a new logging level configuration based on the given configuration, prefix and defaultLevel.
     *
     * @param configuration The configuration.
     * @param prefix        The prefix.
     * @param defaultLevel  The default level.
     */
    public LoggingLevelConfig(
            @NonNull Configuration configuration, @NonNull String prefix, @NonNull Level defaultLevel) {
        this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
        this.defaultLevel = Objects.requireNonNull(defaultLevel, "defaultLevel must not be null");
        this.levelCache = new ConcurrentHashMap<>();
        this.levelConfigProperties = new ConcurrentHashMap<>();
        update(configuration);
    }

    /**
     * Updates the configuration based on the given configuration. That method can be used to change the logging level
     * dynamically at runtime.
     *
     * @param configuration The configuration.
     */
    public void update(final @NonNull Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        levelConfigProperties.clear();
        configuration.getPropertyNames().filter(n -> n.startsWith(this.prefix)).forEach(configPropertyName -> {
            final String name = configPropertyName.substring(this.prefix.length());
            if (name.startsWith(".")) {
                levelConfigProperties.put(name.substring(1), configuration.getValue(configPropertyName));
            } else {
                levelConfigProperties.put(name, configuration.getValue(configPropertyName));
            }
        });
        levelCache.clear();
    }

    /**
     * Returns true if the given level is enabled for the given name.
     *
     * @param name  The name.
     * @param level The level.
     * @return True if the given level is enabled for the given name.
     */
    public boolean isEnabled(@NonNull String name, @NonNull Level level) {
        if (level == null) {
            EMERGENCY_LOGGER.logNPE("level");
            return true;
        }
        if (name == null) {
            EMERGENCY_LOGGER.logNPE("name");
            return true;
        }
        final Level enabledLevel = levelCache.computeIfAbsent(name.trim(), this::getConfiguredLevel);
        return enabledLevel.enabledLoggingOfLevel(level);
    }

    @NonNull
    private Level getConfiguredLevel(@NonNull String name) {
        return levelConfigProperties.keySet().stream()
                .filter(n -> name.trim().startsWith(n))
                .reduce((a, b) -> {
                    if (a.length() > b.length()) {
                        return a;
                    } else {
                        return b;
                    }
                })
                .map(levelConfigProperties::get)
                .map(String::toUpperCase)
                .map(n -> Level.valueOfOrElse(n, defaultLevel))
                .orElse(defaultLevel);
    }
}
