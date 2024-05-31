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

import com.swirlds.base.utility.Pair;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class LoggingSystemConfig {

    public static final String EMPTY = "";

    /**
     * The cache for the levels.
     */
    private final Map<String /*level+package*/, Set<Integer>> levelConfigCache = new ConcurrentHashMap<>();

    /**
     * The cache for the markers.
     */
    private final Map<String /*MarkerName*/, Pair</*Enabled*/ Set<Integer>, Set</*Disabled*/ Integer>>>
            markerConfigCache = new ConcurrentHashMap<>();

    /**
     * The configuration properties.
     */
    private final Map<String, Map<Level, List<Integer>>> logLevelConfig;

    /**
     * The configuration properties.
     */
    private final Map<String, Map<MarkerState, List<Integer>>> markerConfig;

    /**
     * Total amount of handlers
     */
    private final Integer totalHandlers;

    LoggingSystemConfig(
            @NonNull final Integer totalHandlers,
            @NonNull final Map<String, Map<Level, List<Integer>>> logLevelConfig,
            @NonNull final Map<String, Map<MarkerState, List<Integer>>> markerConfig) {
        this.totalHandlers = totalHandlers;
        this.logLevelConfig = logLevelConfig;
        this.markerConfig = markerConfig;
    }

    /**
     * Returns true if the given level is enabled for the given name.
     *
     * @param name  The name of the logger.
     * @param level The level.
     * @return True if the given level is enabled for the given name.
     */
    public boolean isEnabled(@NonNull final String name, @NonNull final Level level) {
        return !levelConfigCache
                .computeIfAbsent(level + name, k -> this.getHandlers(name, level))
                .isEmpty();
    }

    public boolean isEnabled(@NonNull final String name, @NonNull final Level level, @Nullable final Marker marker) {
        if (marker != null) {
            // A marker collection is enabled if at least one in the chain is enabled.
            // A marker is disabled if all in the chain are disabled
            // if there is at least one UNDEFINED, is up to the logLevel
            final List<String> allMarkerNames = marker.getAllMarkerNames();

            boolean isDisabled = false;
            for (String markerName : allMarkerNames) {
                // For is enabled using the cache could have worst time
                Map<MarkerState, List<Integer>> markerStateListMap = markerConfig.get(markerName);

                if (markerStateListMap != null) {
                    if (markerStateListMap.containsKey(MarkerState.ENABLED)) {
                        return true;
                    } else {
                        isDisabled = isDisabled || markerStateListMap.containsKey(MarkerState.DISABLED);
                    }
                }
            }
            if (isDisabled) {
                return false;
            }
        }
        return isEnabled(name, level);
    }

    @NonNull
    public Set<Integer> getHandlers(
            @NonNull final String name, @NonNull final Level level, @Nullable final Marker marker) {
        final Set<Integer> handlers = getHandlers(name, level);
        if (marker != null) {
            final List<String> allMarkerNames = marker.getAllMarkerNames();
            final Pair</*Enabled*/ Set<Integer>, /*Disabled*/ Set<Integer>> markersConfig =
                    markerConfigCache.computeIfAbsent(
                            String.join("", allMarkerNames), key -> getAllHandlersForActiveMarkers(allMarkerNames));
            markersConfig.right().forEach(handlers::remove);
            handlers.addAll(markersConfig.left());
        }
        return handlers;
    }

    @NonNull
    public Set<Integer> getHandlers(@NonNull final String name, @NonNull final Level level) {
        final Set<Integer> handlers = new TreeSet<>();
        boolean found = getAddHandlersFor(name, level, handlers);

        final StringBuilder buffer = new StringBuilder(name);
        for (int i = buffer.length() - 1; !found && i > 0 && handlers.size() < this.totalHandlers; i--) {
            if ('.' == buffer.charAt(i)) {
                buffer.setLength(i);
                found = getAddHandlersFor(buffer.toString(), level, handlers);
            }
        }
        if (!found && handlers.size() < this.totalHandlers) {
            getAddHandlersFor("", level, handlers);
        }

        return handlers;
    }

    private boolean getAddHandlersFor(
            @NonNull final String name, @NonNull final Level level, @NonNull final Set<Integer> handlers) {
        final Map<Level, List<Integer>> configLevel = logLevelConfig.get(name);
        if (configLevel != null) {
            configLevel.forEach((configuredLevel, handlerIndex) -> {
                if (configuredLevel.enabledLoggingOfLevel(level)) handlers.addAll(handlerIndex);
            });
        }
        return configLevel != null;
    }

    @NonNull
    private Pair</*Enabled*/ Set<Integer>, /*Disabled*/ Set<Integer>> getAllHandlersForActiveMarkers(
            @NonNull final List<String> allMarkerNames) {
        final Set<Integer> enabledList = new HashSet<>();
        final Set<Integer> disabledList = new HashSet<>();
        for (String markerName : allMarkerNames) {

            Map<MarkerState, List<Integer>> markerStateListMap = markerConfig.get(markerName);
            if (markerStateListMap != null) {
                if (markerStateListMap.containsKey(MarkerState.ENABLED)) {
                    enabledList.addAll(markerStateListMap.get(MarkerState.ENABLED));
                } else if (markerStateListMap.containsKey(MarkerState.DISABLED)) {
                    disabledList.addAll(markerStateListMap.get(MarkerState.DISABLED));
                }
            }
        }
        return Pair.of(enabledList, disabledList);
    }
}
