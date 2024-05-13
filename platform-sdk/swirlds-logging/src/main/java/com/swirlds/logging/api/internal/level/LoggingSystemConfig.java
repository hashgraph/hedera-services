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

import static com.swirlds.logging.api.internal.level.ConfigLevel.UNDEFINED;

import com.swirlds.base.utility.Pair;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.SimpleConfigSource;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
public class LoggingSystemConfig {

    private static final String PROPERTY_LOGGING = "logging.";
    private static final String PROPERTY_LOGGING_LEVEL = "logging.level";
    private static final String PROPERTY_LOGGING_MARKER = "logging.marker.";
    private static final String PROPERTY_LOGGING_HANDLER_ENABLED = "logging.handler.%s.enabled";
    private static final String PROPERTY_LOGGING_HANDLER_INHERIT_LEVELS = "logging.handler.%s.inheritLevels";
    private static final Level DEFAULT_LEVEL = Level.INFO;
    private static final Pattern LOGGING_LEVELROOT_REPLACE_PATTERN = Pattern.compile("logging\\.level\\.?");
    private static final Pattern HANDLER_PATTERN = Pattern.compile("logging\\.handler\\.(\\w+)(\\.?.*)");
    private static final Pattern HANDLER_LEVEL_PATTERN = Pattern.compile("logging\\.handler\\.(\\w+)\\.level(.*)");
    private static final Pattern HANDLER_LEVEL_REPLACE_PATTERN =
            Pattern.compile("logging\\.handler\\.(\\w+)\\.level\\.?");
    private static final Pattern HANDLER_MARKER_PATTERN = Pattern.compile("logging\\.handler\\.(\\w+)\\.marker(.*)");
    private static final Pattern HANDLER_MARKER_REPLACE_PATTERN =
            Pattern.compile("logging\\.handler\\.(\\w+)\\.marker\\.?");
    public static final String EMPTY = "";
    /**
     * The cache for the levels.
     */
    private final List<LogHandler> logHandlers;

    /**
     * The cache for the levels.
     */
    private final Map<String /*level+package*/, List<LogHandler>> levelConfigCache = new ConcurrentHashMap<>();

    /**
     * The cache for the markers.
     */
    private final Map<String /*MarkerName+State*/, List<LogHandler>> markerConfigCache = new ConcurrentHashMap<>();

    /**
     * The configuration properties.
     */
    private final Map<String, Map<Level, List<Integer>>> levelConfigProperties;

    /**
     * The configuration properties.
     */
    private final Map<String, Map<MarkerState, List<Integer>>> markerConfigProperties;

    LoggingSystemConfig(Configuration configuration) {
        final Map<String, String> allLogConfig = filterLoggingProperties(configuration);
        this.logHandlers = createLogHandlers(allLogConfig);
        Map<String, Integer> handlerNamePerIndex =
                logHandlers.stream().collect(Collectors.toMap(LogHandler::getName, logHandlers::indexOf));
        this.levelConfigProperties = createLoggingLevelMap(allLogConfig, handlerNamePerIndex);
        this.markerConfigProperties = createMarkerMap(allLogConfig, handlerNamePerIndex);
    }

    @NonNull
    private static Map<String, Map<MarkerState, List<Integer>>> createMarkerMap(
            @NonNull final Map<String, String> allLogConfig, @NonNull final Map<String, Integer> logHandlers) {

        final Map<String, MarkerState> rootMakerMap = allLogConfig.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PROPERTY_LOGGING_MARKER))
                .map(e -> Pair.of(e.getKey().replace(PROPERTY_LOGGING_MARKER, EMPTY), e.getValue()))
                .filter(p -> Objects.nonNull(p.right()))
                .map(p -> Pair.of(p.left(), p.right().toUpperCase()))
                .collect(Collectors.toMap(Pair::left, p -> MarkerState.valueOf(p.right())));

        final Map<String, Map<String, MarkerState>> handlerMarkerMap = allLogConfig.entrySet().stream()
                .filter(e -> HANDLER_MARKER_PATTERN.matcher(e.getKey()).matches())
                .map(e -> Triple.of(
                        getHandlerName(e.getKey()),
                        e.getKey().replaceFirst(HANDLER_MARKER_REPLACE_PATTERN.pattern(), EMPTY),
                        e.getValue()))
                .filter(t -> Objects.nonNull(t.right()))
                .map(t -> Triple.of(
                        t.left(), t.middle(), MarkerState.valueOf(t.right().toUpperCase())))
                .collect(Collectors.groupingBy(Triple::left, Collectors.toMap(Triple::middle, Triple::right)));

        // FUTURE-WORK: do markers always inherit from root?
        final Map<String, Map<Integer, MarkerState>> handlerMarkerInheritedtMap = logHandlers.entrySet().stream()
                .flatMap(e -> {
                    String hName = e.getKey();
                    Map<String, MarkerState> baseMap = new HashMap<>(rootMakerMap);
                    if (handlerMarkerMap.containsKey(hName)) {
                        baseMap.putAll(handlerMarkerMap.get(hName));
                    }
                    return baseMap.entrySet().stream().map(e1 -> Triple.of(e.getValue(), e1.getKey(), e1.getValue()));
                })
                .collect(Collectors.groupingBy(Triple::middle, Collectors.toMap(Triple::left, Triple::right)));

        return handlerMarkerInheritedtMap.entrySet().stream()
                .flatMap(e -> {
                    HashMap<Integer, MarkerState> integerMarkerStateHashMap = new HashMap<>(e.getValue());
                    logHandlers.values().forEach(v -> integerMarkerStateHashMap.putIfAbsent(v, MarkerState.UNDEFINED));
                    return integerMarkerStateHashMap.entrySet().stream()
                            .map(e1 -> Triple.of(e1.getKey(), e.getKey(), e1.getValue()));
                })
                .collect(Collectors.groupingBy(
                        Triple::middle,
                        Collectors.groupingBy(Triple::right, Collectors.mapping(Triple::left, Collectors.toList()))));
    }

    /**
     * Reads the levels from the given configuration.
     *
     * @param configuration current configuration
     * @return map of levels and package names
     */
    @NonNull
    private static Map<String, String> filterLoggingProperties(@NonNull final Configuration configuration) {
        return configuration
                .getPropertyNames()
                .filter(v -> v.startsWith(PROPERTY_LOGGING))
                .map(v -> Pair.of(v, configuration.getValue(v, (String) null)))
                .filter(p -> Objects.nonNull(p.right()))
                .collect(Collectors.toMap(Pair::left, Pair::right));
    }

    @NonNull
    public static Map<String, Map<Level, List<Integer>>> createLoggingLevelMap(
            @NonNull final Map<String, String> allLogConfig, @NonNull final Map<String, Integer> handlers) {
        Map<String, Level> rootLevelMap = allLogConfig.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PROPERTY_LOGGING_LEVEL))
                .map(e -> Pair.of(
                        e.getKey().replaceFirst(LOGGING_LEVELROOT_REPLACE_PATTERN.pattern(), EMPTY),
                        e.getValue().toUpperCase()))
                .map(p -> Pair.of(p.left(), ConfigLevel.valueOf(p.right())))
                .filter(p -> p.right() != UNDEFINED)
                .collect(Collectors.toMap(Pair::left, p -> p.right().level()));

        Map<String, Map<String, Level>> handlersLevelMap = allLogConfig.entrySet().stream()
                .filter(e -> HANDLER_LEVEL_PATTERN.matcher(e.getKey()).matches())
                .map(e -> Pair.of(e.getKey(), e.getValue().toUpperCase()))
                .map(p -> Pair.of(p.left(), ConfigLevel.valueOf(p.right())))
                .map(p -> Triple.of(
                        getHandlerName(p.left()),
                        p.left().replaceAll(HANDLER_LEVEL_REPLACE_PATTERN.pattern(), EMPTY),
                        p.right().level()))
                .collect(Collectors.groupingBy(Triple::left, Collectors.toMap(Triple::middle, Triple::right)));

        return handlers.entrySet().stream()
                .flatMap(entry -> {
                    final String hName = entry.getKey();
                    Map<String, Level> loggingLevels =
                            new HashMap<>(handlersLevelMap.getOrDefault(hName, Collections.emptyMap()));
                    final int index = entry.getValue();
                    final Map<String, Map<Level, Integer>> applicableHandlersConfiguration = new HashMap<>();
                    if (Boolean.parseBoolean(allLogConfig
                            .getOrDefault(
                                    PROPERTY_LOGGING_HANDLER_INHERIT_LEVELS.formatted(hName), Boolean.TRUE.toString())
                            .toUpperCase())) {
                        final Map<String, Level> merged = new HashMap<>(loggingLevels.size() + rootLevelMap.size());
                        merged.putAll(rootLevelMap);
                        merged.putAll(loggingLevels);
                        loggingLevels = merged;
                        // FUTURE WORK: what happens if root has com.swirlds and handler has com. does it replace the
                        // root
                        //  value or not? if not we need to iterate the map and replace all key's from root that starts
                        //  with any key from e.getValue()
                    }
                    loggingLevels.putIfAbsent(
                            "",
                            DEFAULT_LEVEL); // Make sure that if no default level was added on root and in handler, one
                    // will exist.
                    loggingLevels.forEach((k, v) -> {
                        Map<Level, Integer> levelToIndex = new HashMap<>(Level.values().length);
                        Arrays.stream(Level.values()).forEach(level -> {
                            if (v.enabledLoggingOfLevel(level)) {
                                levelToIndex.put(level, index);
                            }
                        });
                        applicableHandlersConfiguration.put(k, levelToIndex);
                    });

                    return applicableHandlersConfiguration.entrySet().stream()
                            .flatMap(v -> v.getValue().entrySet().stream()
                                    .map(k -> Triple.of(v.getKey(), k.getKey(), k.getValue())));
                })
                .collect(Collectors.groupingBy(
                        Triple::left,
                        Collectors.groupingBy(Triple::middle, Collectors.mapping(Triple::right, Collectors.toList()))));
    }

    @NonNull
    private static List<LogHandler> createLogHandlers(@NonNull final Map<String, String> allLogConfig) {
        Map<String, Configuration> handlersConfigMap = allLogConfig.entrySet().stream()
                .filter(e -> HANDLER_PATTERN.matcher(e.getKey()).matches())
                .filter(e -> !HANDLER_LEVEL_PATTERN.matcher(e.getKey()).matches())
                .filter(e -> !HANDLER_MARKER_PATTERN.matcher(e.getKey()).matches())
                .collect(Collectors.groupingBy(
                        e -> getHandlerName(e.getKey()), Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> ConfigurationBuilder.create()
                        .withSource(new SimpleConfigSource(e.getValue()))
                        .build()));

        return handlersConfigMap.entrySet().stream()
                .filter(e -> getEnabled(e.getValue(), e.getKey()))
                .map(e -> LoggingSystemConfig.createLoggingHandler(e.getKey(), e.getValue()))
                .toList();
    }

    @NonNull
    private static Boolean getEnabled(@NonNull final Configuration configuration, @NonNull final String handlerName) {
        return configuration.getValue(PROPERTY_LOGGING_HANDLER_ENABLED.formatted(handlerName), Boolean.class, true);
    }

    @NonNull
    private static String getHandlerName(@NonNull final String handlerProperty) {
        Matcher matcher = HANDLER_PATTERN.matcher(handlerProperty);
        if (!matcher.find()) throw new IllegalArgumentException("Invalid handler property: " + handlerProperty);

        return matcher.group(1);
    }

    public static LogHandler createLoggingHandler(String handlerName, Configuration configuration) {
        final Map<String, LogHandlerFactory> servicesMap = ServiceLoader.load(LogHandlerFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toUnmodifiableMap(LogHandlerFactory::getTypeName, Function.identity()));

        final String handlerType = configuration.getValue("logging.handler." + handlerName + ".type");
        final LogHandlerFactory handlerFactory = servicesMap.get(handlerType);
        if (handlerFactory == null) {
            throw new IllegalArgumentException("No handler type found for logging handler '" + handlerName + "'");
        }
        return handlerFactory.create(handlerName, configuration);
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
            final List<String> allMarkerNames = marker.getAllMarkerNames();
            boolean foundMarker = false;
            boolean isEnabled = true;
            for (String markerName : allMarkerNames) {
                Map<MarkerState, List<Integer>> markerStateListMap = markerConfigProperties.get(markerName);
                if (markerStateListMap != null) {
                    foundMarker = true;
                    if (markerStateListMap.containsKey(MarkerState.ENABLED)) {
                        return true;
                    } else if (markerStateListMap.containsKey(MarkerState.DISABLED)) {
                        isEnabled = false;
                    }
                }
            }
            if (foundMarker && !isEnabled) {
                return false;
            }
        }
        return isEnabled(name, level);
    }

    @NonNull
    private List<LogHandler> getHandlers(@NonNull final String name, @NonNull final Level level) {
        Set<Integer> handlers = new TreeSet<>();
        boolean found = getAddHandlersFor(name, level, handlers);

        final StringBuilder buffer = new StringBuilder(name);
        for (int i = buffer.length() - 1; !found && i > 0 && handlers.size() < this.logHandlers.size(); i--) {
            if ('.' == buffer.charAt(i)) {
                buffer.setLength(i);
                found = getAddHandlersFor(buffer.toString(), level, handlers);
            }
        }
        if (!found && handlers.size() < this.logHandlers.size()) {
            getAddHandlersFor("", level, handlers);
        }

        return handlers.stream().map(logHandlers::get).collect(Collectors.toList());
    }

    private boolean getAddHandlersFor(
            @NonNull final String name, @NonNull final Level level, @NonNull final Set<Integer> handlers) {
        final Map<Level, List<Integer>> configLevel = levelConfigProperties.get(name);
        if (configLevel != null) {
            configLevel.forEach((configuredLevel, handlerIndex) -> {
                if (configuredLevel.enabledLoggingOfLevel(level)) handlers.addAll(handlerIndex);
            });
        }
        return configLevel != null;
    }

    List<LogHandler> getLogHandlers() {
        return logHandlers;
    }

    private record Triple<X, Y, Z>(X left, Y middle, Z right) {

        static <X, Y, Z> Triple<X, Y, Z> of(X left, Y middle, Z right) {
            return new Triple<>(left, middle, right);
        }
    }
}
