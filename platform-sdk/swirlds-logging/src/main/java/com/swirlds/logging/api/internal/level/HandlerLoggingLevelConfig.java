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
import java.util.concurrent.atomic.AtomicReference;

/**
 * This configuration class enables the customization of logging levels for loggers, allowing users to specify levels for either packages or individual classes. The configuration is read from the {@link Configuration} object.
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

    private static final String PROPERTY_LOGGING_LEVEL = "logging.level";
    private static final String PROPERTY_LOGGING_HANDLER_LEVEL = "logging.handler.%s.level";
    private static final String PROPERTY_PACKAGE_LEVEL = "%s.";
    private static final String PROPERTY_LOGGING_MARKER = "logging.marker";
    private static final String PROPERTY_LOGGING_HANDLER_MARKER = "logging.handler.%s.marker";
    private static final String PROPERTY_LOGGING_HANDLER_INHERIT_LEVELS = "logging.handler.%s.inheritLevels";
    private static final ConfigLevel DEFAULT_LEVEL = ConfigLevel.INFO;

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
    private final AtomicReference<Map<String, MarkerState>> markerConfigCache;

    /**
     * The configuration properties.
     */
    private final AtomicReference<Map<String, ConfigLevel>> levelConfigProperties;

    /**
     * The prefix for the configuration.
     */
    private final String handlerName;

    /**
     * Creates a new logging level configuration based on the given configuration and handler name.
     *
     * @param configuration The configuration.
     * @param handlerName        The name of the handler.
     */
    public HandlerLoggingLevelConfig(@NonNull final Configuration configuration, @Nullable final String handlerName) {
        this.handlerName = handlerName;
        this.levelCache = new ConcurrentHashMap<>();
        this.markerConfigCache = new AtomicReference<>(new ConcurrentHashMap<>());
        this.levelConfigProperties = new AtomicReference<>(new ConcurrentHashMap<>());
        update(configuration);
    }

    /**
     * Creates a new root configuration based on the given configuration.
     * @param configuration The configuration.
     */
    public HandlerLoggingLevelConfig(@NonNull final Configuration configuration) {
        this(configuration, null);
    }

    /**
     * Extracts the configuration for the given handler name based on the given configuration.
     *
     * @param handlerName The name of the handler.
     * @param configuration The configuration.
     */
    @NonNull
    private static ExtractedLoggingConfig extractConfig(
            @Nullable final String handlerName, @NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        final ConfigLevel defaultLevel =
                configuration.getValue(PROPERTY_LOGGING_LEVEL, ConfigLevel.class, ConfigLevel.UNDEFINED);
        final ConfigLevel defaultHandlerLevel;
        final String propertyHandler = PROPERTY_LOGGING_HANDLER_LEVEL.formatted(handlerName);
        final Boolean inheritLevels = configuration.getValue(
                PROPERTY_LOGGING_HANDLER_INHERIT_LEVELS.formatted(handlerName), Boolean.class, Boolean.TRUE);

        final Map<String, ConfigLevel> levelConfigProperties = new ConcurrentHashMap<>();
        final Map<String, MarkerState> markerConfigStore = new ConcurrentHashMap<>();

        if (handlerName != null) {
            defaultHandlerLevel = configuration.getValue(propertyHandler, ConfigLevel.class, ConfigLevel.UNDEFINED);
        } else {
            defaultHandlerLevel = ConfigLevel.UNDEFINED;
        }
        if (defaultLevel == ConfigLevel.UNDEFINED && defaultHandlerLevel == ConfigLevel.UNDEFINED) {
            levelConfigProperties.put("", DEFAULT_LEVEL);
        } else if (defaultHandlerLevel != ConfigLevel.UNDEFINED) {
            levelConfigProperties.put("", defaultHandlerLevel);
        } else {
            if (Boolean.TRUE.equals(inheritLevels)) {
                levelConfigProperties.put("", defaultLevel);
            } else {
                levelConfigProperties.put("", DEFAULT_LEVEL);
            }
        }
        if (Boolean.TRUE.equals(inheritLevels)) {
            levelConfigProperties.putAll(readLevels(PROPERTY_LOGGING_LEVEL, configuration));
            markerConfigStore.putAll(readMarkers(PROPERTY_LOGGING_MARKER, configuration));
        }
        if (handlerName != null) {
            levelConfigProperties.putAll(readLevels(propertyHandler, configuration));
            markerConfigStore.putAll(
                    readMarkers(PROPERTY_LOGGING_HANDLER_MARKER.formatted(handlerName), configuration));
        }
        return new ExtractedLoggingConfig(levelConfigProperties, markerConfigStore);
    }

    /**
     * Updates the handler config based on the given configuration.
     *
     * @param configuration The configuration.
     */
    public void update(@NonNull final Configuration configuration) {
        final ExtractedLoggingConfig extractedLoggingConfig = extractConfig(handlerName, configuration);
        levelCache.clear();
        this.levelConfigProperties.set(Collections.unmodifiableMap(extractedLoggingConfig.levelConfigProperties()));
        this.markerConfigCache.set(extractedLoggingConfig.markerConfigStore());
    }

    @NonNull
    private static Map<String, MarkerState> readMarkers(
            @NonNull final String prefix, @NonNull final Configuration configuration) {
        final Map<String, MarkerState> result = new HashMap<>();
        final String fullPrefix = PROPERTY_PACKAGE_LEVEL.formatted(prefix);

        configuration.getPropertyNames().filter(n -> n.startsWith(fullPrefix)).forEach(configPropertyName -> {
            final String name = configPropertyName.substring(fullPrefix.length());
            final MarkerState markerState =
                    configuration.getValue(configPropertyName, MarkerState.class, MarkerState.UNDEFINED);
            result.put(name, markerState);
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
    @NonNull
    private static Map<String, ConfigLevel> readLevels(
            @NonNull final String prefix, @NonNull final Configuration configuration) {
        final Map<String, ConfigLevel> result = new HashMap<>();
        final String fullPrefix = PROPERTY_PACKAGE_LEVEL.formatted(prefix);

        configuration.getPropertyNames().filter(n -> n.startsWith(fullPrefix)).forEach(configPropertyName -> {
            final String name = configPropertyName.substring(fullPrefix.length());
            final ConfigLevel level =
                    configuration.getValue(configPropertyName, ConfigLevel.class, ConfigLevel.UNDEFINED);
            if (level != ConfigLevel.UNDEFINED) {
                if (containsUpperCase(name)) {
                    result.put(name, level);
                } else {
                    result.put(PROPERTY_PACKAGE_LEVEL.formatted(name), level);
                }
            }
        });

        return Collections.unmodifiableMap(result);
    }

    private static boolean containsUpperCase(@NonNull final String name) {
        return name.chars().anyMatch(Character::isUpperCase);
    }

    /**
     * Returns true if the given level is enabled for the given name.
     *
     * @param name  The name of the logger.
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
            final List<MarkerState> markerStates = allMarkerNames.stream()
                    .map(markerName -> markerConfigCache.get().computeIfAbsent(markerName, n -> MarkerState.UNDEFINED))
                    .filter(markerState -> markerState != MarkerState.UNDEFINED)
                    .toList();
            if (!markerStates.isEmpty()) {
                if (markerStates.stream().anyMatch(markerState -> markerState == MarkerState.ENABLED)) {
                    return true;
                } else if (markerStates.stream().allMatch(markerState -> markerState == MarkerState.DISABLED)) {
                    return false;
                }
            }
        }

        final ConfigLevel enabledLevel = levelCache.computeIfAbsent(name.trim(), this::getConfiguredLevel);
        return enabledLevel.enabledLoggingOfLevel(level);
    }

    @NonNull
    private ConfigLevel getConfiguredLevel(@NonNull final String name) {
        final String stripName = name.strip();
        final Map<String, ConfigLevel> stringConfigLevelMap = levelConfigProperties.get();
        return stringConfigLevelMap.keySet().stream()
                .filter(stripName::startsWith)
                .reduce((a, b) -> {
                    if (a.length() > b.length()) {
                        return a;
                    } else {
                        return b;
                    }
                })
                .map(stringConfigLevelMap::get)
                .orElseThrow();
    }

    /**
     * Helper record to store the extracted logging configuration.
     *
     * @param levelConfigProperties The level configuration properties
     * @param markerConfigStore     The marker configuration store
     */
    private record ExtractedLoggingConfig(
            @NonNull Map<String, ConfigLevel> levelConfigProperties,
            @NonNull Map<String, MarkerState> markerConfigStore) {}
}
