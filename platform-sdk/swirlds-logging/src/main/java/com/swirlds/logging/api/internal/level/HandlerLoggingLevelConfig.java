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

import static com.swirlds.logging.utils.ConfigUtils.configValueOrElse;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This configuration class enables the customization of logging levels for loggers, allowing users to specify levels
 * for either packages or individual classes. The configuration is read from the {@link Configuration} object.
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
    private static final Level DEFAULT_LEVEL = Level.INFO;

    /**
     * The cache for the levels.
     */
    private final Map<String, Level> levelCache = new ConcurrentHashMap<>();

    /**
     * The cache for the marker config.
     */
    private final Map<String, MarkerState> markerConfigCache;
    /**
     * The cache for the markers.
     */
    private final Map<Marker, MarkerState> markerCache = new ConcurrentHashMap<>();

    /**
     * The configuration properties.
     */
    private final Map<String, Level> levelConfigProperties;

    private HandlerLoggingLevelConfig(
            Map<String, MarkerState> markerConfigCache, Map<String, Level> levelConfigProperties) {
        this.markerConfigCache = Collections.unmodifiableMap(markerConfigCache);
        this.levelConfigProperties = Collections.unmodifiableMap(levelConfigProperties);
    }

    /**
     * Creates a new logging level configuration based on the given configuration and handler name.
     *
     * @param configuration The configuration.
     */
    public static HandlerLoggingLevelConfig create(final Configuration configuration) {
        return create(configuration, null);
    }

    /**
     * Extracts the configuration for the given handler name based on the given configuration.
     *
     * @param configuration The configuration.
     * @param handlerName   The name of the handler.
     */
    @NonNull
    public static HandlerLoggingLevelConfig create(
            @NonNull final Configuration configuration, @Nullable final String handlerName) {
        Objects.requireNonNull(configuration, "configuration must not be null");

        final ConfigLevel defaultLevel =
                configValueOrElse(configuration, PROPERTY_LOGGING_LEVEL, ConfigLevel.class, ConfigLevel.UNDEFINED);
        final Boolean inheritLevels = configValueOrElse(
                configuration,
                PROPERTY_LOGGING_HANDLER_INHERIT_LEVELS.formatted(handlerName),
                Boolean.class,
                Boolean.TRUE);

        final String propertyHandler = PROPERTY_LOGGING_HANDLER_LEVEL.formatted(handlerName);
        final ConfigLevel defaultHandlerLevel;
        if (handlerName != null) {
            defaultHandlerLevel =
                    configValueOrElse(configuration, propertyHandler, ConfigLevel.class, ConfigLevel.UNDEFINED);
        } else {
            defaultHandlerLevel = ConfigLevel.UNDEFINED;
        }

        final Map<String, Level> levelConfigProperties = new HashMap<>();
        final Map<String, MarkerState> markerConfigStore = new HashMap<>();

        if (defaultLevel == ConfigLevel.UNDEFINED && defaultHandlerLevel == ConfigLevel.UNDEFINED) {
            levelConfigProperties.put("", DEFAULT_LEVEL);
        } else if (defaultHandlerLevel != ConfigLevel.UNDEFINED) {
            levelConfigProperties.put("", defaultHandlerLevel.level());
        } else {
            if (Boolean.TRUE.equals(inheritLevels)) {
                levelConfigProperties.put("", defaultLevel.level());
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

        return new HandlerLoggingLevelConfig(markerConfigStore, levelConfigProperties);
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
     * @param prefix        prefix of the configuration property
     * @param configuration current configuration
     * @return map of levels and package names
     */
    @NonNull
    private static Map<String, Level> readLevels(
            @NonNull final String prefix, @NonNull final Configuration configuration) {
        final Map<String, Level> result = new HashMap<>();
        final String fullPrefix = PROPERTY_PACKAGE_LEVEL.formatted(prefix);

        configuration.getPropertyNames().filter(n -> n.startsWith(fullPrefix)).forEach(configPropertyName -> {
            final String name = configPropertyName.substring(fullPrefix.length());
            final ConfigLevel level =
                    configuration.getValue(configPropertyName, ConfigLevel.class, ConfigLevel.UNDEFINED);
            if (level != null && level != ConfigLevel.UNDEFINED) {
                result.put(name, level.level());
            }
        });

        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns true if the given level is enabled for the given name.
     *
     * @param name  The name of the logger.
     * @param level The level.
     * @return True if the given level is enabled for the given name.
     */
    public boolean isEnabled(@NonNull final String name, @NonNull final Level level) {
        final Level enabledLevel = levelCache.computeIfAbsent(name, this::getConfiguredLevel);
        return enabledLevel.enabledLoggingOfLevel(level);
    }

    public boolean isEnabled(@NonNull final String name, @NonNull final Level level, @Nullable final Marker marker) {
        if (marker != null) {
            final MarkerState markerState = markerCache.computeIfAbsent(marker, this::getMarkerState);
            if (!markerState.equals(MarkerState.UNDEFINED)) {
                return markerState.equals(MarkerState.ENABLED);
            }
        }
        return isEnabled(name, level);
    }

    private MarkerState getMarkerState(@NonNull final Marker marker) {
        final List<String> allMarkerNames = marker.getAllMarkerNames();
        MarkerState markerState = MarkerState.UNDEFINED;

        for (String markerName : allMarkerNames) {
            MarkerState configMarker = markerConfigCache.get(markerName);
            if (configMarker != null && configMarker != MarkerState.UNDEFINED) {
                if (configMarker == MarkerState.ENABLED) {
                    return MarkerState.ENABLED;
                }
                markerState = configMarker;
            }
        }

        return markerState;
    }

    @NonNull
    private Level getConfiguredLevel(@NonNull final String name) {
        Level configLevel = levelConfigProperties.get(name);
        if (configLevel != null) {
            return configLevel;
        }

        int lastDotIndex = name.lastIndexOf('.');
        while (lastDotIndex != -1) {
            final String substring = name.substring(0, lastDotIndex);
            configLevel = levelConfigProperties.get(substring);
            if (configLevel != null) {
                return configLevel;
            }
            lastDotIndex = name.lastIndexOf('.', lastDotIndex - 1);
        }

        return levelConfigProperties.get("");
    }
}
