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
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 */
public class LoggingSystemConfigFactory {

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

    public static LoggingSystemConfig createLoggingSystemConfig(
            @NonNull final Configuration configuration, List<LogHandler> logHandlers) {
        final Map<String, String> configMap = filterLoggingProperties(configuration);
        final Map<String, Integer> handlerIndexMap =
                logHandlers.stream().collect(Collectors.toMap(LogHandler::getName, logHandlers::indexOf));
        return new LoggingSystemConfig(
                logHandlers.size(),
                createLoggingLevelMap(configMap, handlerIndexMap),
                createMarkerMap(configMap, handlerIndexMap));
    }

    /**
     * Creates a map of marker states for various log handlers based on the provided logging configurations.
     * <p>
     * This method processes the given logging configuration and handler information to produce a nested map structure.
     * The resulting map allows for determining which marker states are set for specific logging contexts (e.g.,
     * package names) and which handlers are responsible for them.
     * </p>
     * <p>
     * The method performs the following steps:
     * </p>
     * <ul>
     *     <li>Extracts root marker states from the configuration.</li>
     *     <li>Extracts handler-specific marker states from the configuration.</li>
     *     <li>Merges these states based on inheritance rules defined in the configuration.</li>
     *     <li>Ensures a default marker state if none is specified.</li>
     *     <li>Creates a map that links each logging context and marker state to the corresponding handler indices.</li>
     * </ul>
     *
     * @param configMap A map containing all logging configurations as key-value pairs.
     *                     Keys represent configuration properties, and values represent their settings.
     * @param handlerIndexMap A map where the keys are handler names and the values are handler indices.
     * @return A nested map structure that maps logging contexts to their respective marker states and handler indices.
     *         <ul>
     *             <li>Outer map key: Logging context (e.g., package name).</li>
     *             <li>Middle map key: Marker state.</li>
     *             <li>Innermost list: Indices of handlers with that marker state.</li>
     *         </ul>
     */
    @NonNull
    private static Map<String, Map<MarkerState, List<Integer>>> createMarkerMap(
            @NonNull final Map<String, String> configMap, @NonNull final Map<String, Integer> handlerIndexMap) {

        final Map<String, MarkerState> rootMakerConfigMap = configMap.entrySet().stream()
                .filter(configEntry -> configEntry.getKey().startsWith(PROPERTY_LOGGING_MARKER))
                .map(configEntry ->
                        Pair.of(configEntry.getKey().replace(PROPERTY_LOGGING_MARKER, EMPTY), configEntry.getValue()))
                .filter(configPair -> Objects.nonNull(configPair.right()))
                .map(nonNullConfigPair -> Pair.of(
                        nonNullConfigPair.left(), nonNullConfigPair.right().toUpperCase()))
                .collect(Collectors.toMap(Pair::left, configPair -> MarkerState.valueOf(configPair.right())));

        final Map<String, Map<String, MarkerState>> handlerMarkerConfigMap = configMap.entrySet().stream()
                .filter(configEntry ->
                        HANDLER_MARKER_PATTERN.matcher(configEntry.getKey()).matches())
                .map(handlerConfigEntry -> Triple.of(
                        getHandlerName(handlerConfigEntry.getKey()),
                        handlerConfigEntry.getKey().replaceFirst(HANDLER_MARKER_REPLACE_PATTERN.pattern(), EMPTY),
                        handlerConfigEntry.getValue()))
                .filter(handlerMarkerValueTriple -> Objects.nonNull(handlerMarkerValueTriple.right()))
                .map(nonNullhandlerMarkerValueTriple -> Triple.of(
                        nonNullhandlerMarkerValueTriple.left(),
                        nonNullhandlerMarkerValueTriple.middle(),
                        MarkerState.valueOf(
                                nonNullhandlerMarkerValueTriple.right().toUpperCase())))
                .collect(Collectors.groupingBy(Triple::left, Collectors.toMap(Triple::middle, Triple::right)));

        // FUTURE-WORK: do markers always inherit from root with no configuration disabling this behaviour
        // For all handlers, override the rootMarkerConfigurationMap with it's own configuration
        final Map<String, Map<Integer, MarkerState>> mergedMarkerConfigMap = handlerIndexMap.entrySet().stream()
                .flatMap(handlerIndexEntry -> {
                    final String hName = handlerIndexEntry.getKey();
                    final Map<String, MarkerState> mergedMarkerConfiguration = new HashMap<>(rootMakerConfigMap);
                    if (handlerMarkerConfigMap.containsKey(hName)) {
                        mergedMarkerConfiguration.putAll(handlerMarkerConfigMap.get(hName));
                    }
                    return mergedMarkerConfiguration.entrySet().stream()
                            .map(mergedConfigEntry -> Triple.of(
                                    mergedConfigEntry.getKey(),
                                    handlerIndexEntry.getValue(),
                                    mergedConfigEntry.getValue()));
                })
                .collect(Collectors.groupingBy(Triple::left, Collectors.toMap(Triple::middle, Triple::right)));

        return mergedMarkerConfigMap.entrySet().stream()
                .flatMap(mergedConfigEntry -> {
                    final Map<Integer, MarkerState> integerMarkerStateHashMap =
                            new HashMap<>(mergedConfigEntry.getValue());
                    handlerIndexMap
                            .values()
                            .forEach(v -> integerMarkerStateHashMap.putIfAbsent(v, MarkerState.UNDEFINED));
                    return integerMarkerStateHashMap.entrySet().stream()
                            .map(e1 -> Triple.of(e1.getKey(), mergedConfigEntry.getKey(), e1.getValue()));
                })
                .collect(Collectors.groupingBy(
                        Triple::middle,
                        Collectors.groupingBy(Triple::right, Collectors.mapping(Triple::left, Collectors.toList()))));
    }

    /**
     * Filters all configuration not related to logging
     *
     * @param configuration current configuration
     * @return map of all configuration related to logging
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

    /**
     * Creates a map of logging levels for all handlers based on the provided logging configurations.
     * <p>
     * This method processes the given logging configuration and handler information to produce a nested map structure.
     * The resulting map allows for determining which logging levels are enabled for specific logging contexts (e.g.,
     * package names) and which handlers are responsible for them.
     * </p>
     * <p>
     * The method performs the following steps:
     * </p>
     * <ul>
     *     <li>Extracts root logging levels from the configuration.</li>
     *     <li>Extracts handler-specific logging levels from the configuration.</li>
     *     <li>Merges these levels based on inheritance rules defined in the configuration.</li>
     *     <li>Ensures a default logging level if none is specified.</li>
     *     <li>Replicates the information for logging levels lower than configured.</li>
     *     <li>Creates a map that links each logging context and level to the corresponding handler indices.</li>
     * </ul>
     *
     * @param configMap A map containing all logging configurations as key-value pairs.
     *                     Keys represent configuration properties, and values represent their settings.
     * @param handlerIndexMap A map where the keys are handler names and the values are handler indices.
     * @return A nested map structure that maps logger names (packages) to their respective logging levels and handler indexes in a list.
     *         <ul>
     *             <li>Outer map key: The logging context (e.g., package name).</li>
     *             <li>Middle map key: The logging level.</li>
     *             <li>Innermost list: Contains the indices of the handlers.</li>
     *         </ul>
     */
    @NonNull
    private static Map<String, Map<Level, List<Integer>>> createLoggingLevelMap(
            @NonNull final Map<String, String> configMap, @NonNull final Map<String, Integer> handlerIndexMap) {
        Map<String, Level> rootLevelMap = configMap.entrySet().stream()
                .filter(configEntry -> configEntry.getKey().startsWith(PROPERTY_LOGGING_LEVEL))
                .map(loggingLevelConfigEntry -> Pair.of(
                        loggingLevelConfigEntry
                                .getKey()
                                .replaceFirst(LOGGING_LEVELROOT_REPLACE_PATTERN.pattern(), EMPTY),
                        loggingLevelConfigEntry.getValue().toUpperCase()))
                .map(configPair -> Pair.of(configPair.left(), ConfigLevel.valueOf(configPair.right())))
                .filter(configPair -> configPair.right() != UNDEFINED)
                .collect(Collectors.toMap(
                        Pair::left,
                        filteredConfigPair -> filteredConfigPair.right().level()));

        Map<String, Map<String, Level>> handlersLevelMap = configMap.entrySet().stream()
                .filter(configEntry ->
                        HANDLER_LEVEL_PATTERN.matcher(configEntry.getKey()).matches())
                .map(handlersLoggingLevelEntry -> Pair.of(
                        handlersLoggingLevelEntry.getKey(),
                        handlersLoggingLevelEntry.getValue().toUpperCase()))
                .map(handlersLoggingPair ->
                        Pair.of(handlersLoggingPair.left(), ConfigLevel.valueOf(handlersLoggingPair.right())))
                .map(hanldersLoggingPair -> Triple.of(
                        getHandlerName(hanldersLoggingPair.left()),
                        hanldersLoggingPair.left().replaceAll(HANDLER_LEVEL_REPLACE_PATTERN.pattern(), EMPTY),
                        hanldersLoggingPair.right().level()))
                .collect(Collectors.groupingBy(Triple::left, Collectors.toMap(Triple::middle, Triple::right)));

        // Now Collect to Final Map:
        // Flatten the structure into a nested map where the outer key is the logging context,
        // the middle key is the logging level, and the innermost list contains the indices.
        return handlerIndexMap.entrySet().stream()
                .flatMap(entry -> {
                    final String hName = entry.getKey();
                    Map<String, Level> loggingLevels =
                            new HashMap<>(handlersLevelMap.getOrDefault(hName, Collections.emptyMap()));
                    final int index = entry.getValue();
                    final Map<String, Map<Level, Integer>> applicableHandlersConfiguration = new HashMap<>();
                    if (Boolean.parseBoolean(configMap
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
    public static List<LogHandler> createLogHandlers(@NonNull final Configuration configuration) {
        final Map<String, String> configMap = filterLoggingProperties(configuration);
        Map<String, Configuration> handlersConfigMap = configMap.entrySet().stream()
                .filter(configEntry ->
                        HANDLER_PATTERN.matcher(configEntry.getKey()).matches())
                .filter(handlerConfigEntry -> !HANDLER_LEVEL_PATTERN
                        .matcher(handlerConfigEntry.getKey())
                        .matches())
                .filter(filteredHandlerConfigEntry -> !HANDLER_MARKER_PATTERN
                        .matcher(filteredHandlerConfigEntry.getKey())
                        .matches())
                .collect(Collectors.groupingBy(
                        efilteredHandlerConfigEntry -> getHandlerName(efilteredHandlerConfigEntry.getKey()),
                        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(Map.Entry::getKey, handlerNameToConfigMapEntry -> ConfigurationBuilder.create()
                                .withSource(new SimpleConfigSource(handlerNameToConfigMapEntry.getValue()))
                                .build()));

        return handlersConfigMap.entrySet().stream()
                .filter(handlerConfigMapEntry ->
                        getEnabled(handlerConfigMapEntry.getValue(), handlerConfigMapEntry.getKey()))
                .map(filteredHandlerConfigEntry -> LoggingSystemConfigFactory.createLoggingHandler(
                        filteredHandlerConfigEntry.getKey(), filteredHandlerConfigEntry.getValue()))
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

    private static LogHandler createLoggingHandler(String handlerName, Configuration configuration) {
        final Map<String, LogHandlerFactory> servicesMap = ServiceLoader.load(LogHandlerFactory.class).stream()
                .map(ServiceLoader.Provider::get)
                .collect(Collectors.toUnmodifiableMap(LogHandlerFactory::getTypeName, Function.identity()));

        final String handlerType = configuration.getValue("logging.handler." + handlerName + ".type");
        final LogHandlerFactory handlerFactory = servicesMap.get(handlerType);
        if (handlerFactory == null) {
            throw new IllegalArgumentException("No handlerFactory found for logging handler '" + handlerName + "'"
                    + "  type '" + handlerType + "'");
        }
        return handlerFactory.create(handlerName, configuration);
    }

    /**
     * A tuple of 3 elements.
     * We have pairs in swirlds.utils, but we do not have this concept.
     *
     *
     * @param left
     * @param middle
     * @param right
     */
    private record Triple<X, Y, Z>(X left, Y middle, Z right) {

        static <X, Y, Z> Triple<X, Y, Z> of(X left, Y middle, Z right) {
            return new Triple<>(left, middle, right);
        }
    }
}
