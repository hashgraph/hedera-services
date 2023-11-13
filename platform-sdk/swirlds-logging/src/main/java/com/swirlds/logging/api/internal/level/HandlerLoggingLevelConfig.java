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
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.emergency.EmergencyLogger;
import com.swirlds.logging.api.extensions.emergency.EmergencyLoggerProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
 */
public class HandlerLoggingLevelConfig {

    public static final String PROPERTY_LOGGING_LEVEL = "logging.level";
    public static final String PROPERTY_LOGGING_HANDLER_LEVEL = "logging.handler.%s.level";
    public static final String PROPERTY_PACKAGE_LEVEL = "%s.";
    public static final String PROPERTY_LOGGING_MARKER = "logging.marker";
    public static final String PROPERTY_LOGGING_HANDLER_MARKER = "logging.handler.%s.marker";

    /**
     * The emergency logger.
     */
    private static final EmergencyLogger EMERGENCY_LOGGER = EmergencyLoggerProvider.getEmergencyLogger();

    /**
     * The cache for the levels.
     */
    private final Map<String, ConfigLevel> levelCache;

    /**
     * The cache for the markers.
     */
    private final Map<String, MarkerDecision> markerCache;

    /**
     * The configuration properties.
     */
    private final Map<String, ConfigLevel> levelConfigProperties;

    /**
     * The prefix for the configuration.
     */
    private final String name;

    /**
     * Creates a new logging level configuration based on the given configuration and handler name.
     *
     * @param configuration The configuration.
     * @param name        The name.
     */
    public HandlerLoggingLevelConfig(@NonNull Configuration configuration, @NonNull String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.levelCache = new ConcurrentHashMap<>();
        this.markerCache = new ConcurrentHashMap<>();
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

        final String propertyHandler = PROPERTY_LOGGING_HANDLER_LEVEL.formatted(name);
        final ConfigLevel defaultLevel =
                configuration.getValue(PROPERTY_LOGGING_LEVEL, ConfigLevel.class, ConfigLevel.UNDEFINED);
        final ConfigLevel defaultHandlerLevel =
                configuration.getValue(propertyHandler, ConfigLevel.class, ConfigLevel.UNDEFINED);

        levelConfigProperties.clear();
        if (defaultLevel == ConfigLevel.UNDEFINED && defaultHandlerLevel == ConfigLevel.UNDEFINED) {
            levelConfigProperties.put("", ConfigLevel.INFO);
        } else if (defaultHandlerLevel != ConfigLevel.UNDEFINED) {
            levelConfigProperties.put("", defaultHandlerLevel);
        } else {
            levelConfigProperties.put("", defaultLevel);
        }

        levelConfigProperties.putAll(readLevels(PROPERTY_LOGGING_LEVEL, configuration));
        levelConfigProperties.putAll(readLevels(propertyHandler, configuration));
        levelCache.clear();

        markerCache.clear();
        markerCache.putAll(readMarkers(PROPERTY_LOGGING_MARKER, configuration));
        markerCache.putAll(readMarkers(PROPERTY_LOGGING_HANDLER_MARKER.formatted(name), configuration));
    }

    private Map<String, MarkerDecision> readMarkers(final String prefix, final Configuration configuration) {
        final Map<String, MarkerDecision> result = new HashMap<>();
        final String fullPrefix = PROPERTY_PACKAGE_LEVEL.formatted(prefix);

        configuration.getPropertyNames().filter(n -> n.startsWith(fullPrefix)).forEach(configPropertyName -> {
            final String name = configPropertyName.substring(fullPrefix.length());
            final MarkerDecision markerDecision =
                    configuration.getValue(configPropertyName, MarkerDecision.class, MarkerDecision.INHERIT);
            result.put(name, markerDecision);
        });

        return result;
    }

    /**
     * Reads the levels from the given configuration.
     *
     * @param prefix prefix of the configuration property
     * @param configuration current configuration
     *
     * @return map of levels and package names
     */
    private Map<String, ConfigLevel> readLevels(
            @NonNull final String prefix, @NonNull final Configuration configuration) {
        final Map<String, ConfigLevel> result = new HashMap<>();
        final String fullPrefix = PROPERTY_PACKAGE_LEVEL.formatted(prefix);

        configuration.getPropertyNames().filter(n -> n.startsWith(fullPrefix)).forEach(configPropertyName -> {
            final String name = configPropertyName.substring(fullPrefix.length());
            final ConfigLevel level =
                    configuration.getValue(configPropertyName, ConfigLevel.class, ConfigLevel.UNDEFINED);
            if (level != ConfigLevel.UNDEFINED) {
                result.put(name, level);
            }
        });

        return Collections.unmodifiableMap(result);
    }

    public boolean isEnabled(@NonNull String name, @NonNull Level level) {
        return isEnabled(name, level, null);
    }

    /**
     * Returns true if the given level is enabled for the given name.
     *
     * @param name  The name.
     * @param level The level.
     *
     * @return True if the given level is enabled for the given name.
     */
    public boolean isEnabled(@NonNull final String name, @NonNull final Level level, @Nullable final Marker marker) {
        if (level == null) {
            EMERGENCY_LOGGER.logNPE("level");
            return true;
        }
        if (name == null) {
            EMERGENCY_LOGGER.logNPE("name");
            return true;
        }
        if (marker != null) {
            final List<String> allMarkerNames = marker.getAllMarkerNames();
            final List<MarkerDecision> markerDecisions = allMarkerNames.stream()
                    .map(markerName -> markerCache.computeIfAbsent(markerName, n -> MarkerDecision.INHERIT))
                    .filter(markerDecision -> markerDecision != MarkerDecision.INHERIT)
                    .toList();
            if (!markerDecisions.isEmpty()) {
                if (markerDecisions.stream().anyMatch(markerDecision -> markerDecision == MarkerDecision.ENABLED)) {
                    return true;
                } else if (markerDecisions.stream()
                        .allMatch(markerDecision -> markerDecision == MarkerDecision.DISABLED)) {
                    return false;
                }
            }
        }

        final ConfigLevel enabledLevel = levelCache.computeIfAbsent(name.trim(), this::getConfiguredLevel);
        return enabledLevel.enabledLoggingOfLevel(level);
    }

    @NonNull
    private ConfigLevel getConfiguredLevel(@NonNull String name) {
        return levelConfigProperties.keySet().stream()
                .filter(n -> name.trim().startsWith(n))
                .filter(key -> levelConfigProperties.get(key) != ConfigLevel.UNDEFINED)
                .reduce((a, b) -> {
                    if (a.length() > b.length()) {
                        return a;
                    } else {
                        return b;
                    }
                })
                .map(levelConfigProperties::get)
                .orElseThrow();
    }
}
