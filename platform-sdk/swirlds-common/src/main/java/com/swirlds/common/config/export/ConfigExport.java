/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.config.export;

import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class that provides functionallity to print information about the config
 */
public final class ConfigExport {

    private ConfigExport() {}

    /**
     * Provides information about the config with 1 line per config property to the consumer. The format of one line
     * looks like this:
     * <p>
     * <code>name   -> value    [(NOT )USED IN RECORD]</code>
     * </p>
     *
     * @param configuration
     * 		the configuration
     * @param lineConsumer
     * 		the line consumer
     */
    public static void printConfig(final Configuration configuration, final Consumer<String> lineConsumer) {
        CommonUtils.throwArgNull(configuration, "configuration");
        CommonUtils.throwArgNull(lineConsumer, "lineConsumer");

        // Properties defined in record configs, including values overridden by configured sources
        final Map<String, Object> recordProperties = getPropertiesForConfigDataRecords(configuration);

        // Properties defined in property file but do not exist in record configs
        final Map<String, Object> nonRecordProperties = new HashMap<>();
        configuration
                .getPropertyNames()
                .filter(name -> !recordProperties.containsKey(name))
                .forEach(name -> nonRecordProperties.put(name, configuration.getValue(name)));

        final Set<String> allConfigNames = combine(recordProperties.keySet(), nonRecordProperties.keySet());
        final Set<Object> allConfigValues = combine(recordProperties.values(), nonRecordProperties.values());

        final int maxNameLength = getMaxPropertyLength(allConfigNames);
        final int maxValueLength = getMaxPropertyLength(allConfigValues);

        // Write all record defined values first, in alphabetical order
        recordProperties.keySet().stream().sorted().forEach(name -> {
            final Object value = recordProperties.get(name);
            final String line = buildLine(name, value, maxNameLength, maxValueLength, "  [USED IN RECORD]");
            lineConsumer.accept(line);
        });

        // Write all values not defined in records next, in alphabetical order
        nonRecordProperties.keySet().stream().sorted().forEach(name -> {
            final Object value = nonRecordProperties.get(name);
            final String line = buildLine(name, value, maxNameLength, maxValueLength, "  [NOT USED IN RECORD]");
            lineConsumer.accept(line);
        });
    }

    private static <T> Set<T> combine(final Collection<T> set1, final Collection<T> set2) {
        return Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toSet());
    }

    private static String buildLine(
            final String name,
            final Object value,
            final int maxNameLength,
            final int maxValueLength,
            final String suffix) {
        return name + createSpaces(name, maxNameLength)
                + " -> "
                + value
                + createSpaces(value.toString(), maxValueLength)
                + suffix;
    }

    /**
     * Writes information about the config with 1 line per config property to the given stream. The format of one line
     * looks like this:
     * <p>
     * <code>name   -> value    [(NOT )USED IN RECORD]</code>
     * </p>
     *
     * @param configuration
     * 		the configuration
     * @param printStream
     * 		the OutputStream in that the info should be written
     * @throws IOException
     * 		if writing to the stream fails
     */
    public static void printConfig(final Configuration configuration, final OutputStream printStream)
            throws IOException {
        CommonUtils.throwArgNull(configuration, "configuration");
        CommonUtils.throwArgNull(printStream, "printStream");
        final StringBuilder builder = new StringBuilder();
        printConfig(configuration, line -> builder.append(line).append(System.lineSeparator()));
        printStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void addConfigContents(final Configuration configuration, final StringBuilder builder) {
        CommonUtils.throwArgNull(configuration, "configuration");
        CommonUtils.throwArgNull(builder, "builder");
        printConfig(configuration, line -> builder.append(line).append(System.lineSeparator()));
    }

    private static Map<String, Object> getPropertiesForConfigDataRecords(final Configuration configuration) {
        final Map<String, Object> map = new HashMap<>();
        configuration
                .getConfigDataTypes()
                .forEach(configDataType -> putValuesInMap(configuration.getConfigData(configDataType), map));
        return map;
    }

    private static void putValuesInMap(final Record configData, final Map<String, Object> map) {
        final Class<? extends Record> configDataType = configData.getClass();
        final String propertyNamePrefix = ConfigReflectionUtils.getNamePrefixForConfigDataRecord(configDataType);
        Arrays.stream(configDataType.getRecordComponents()).forEach(component -> {
            final String name =
                    ConfigReflectionUtils.getPropertyNameForConfigDataProperty(propertyNamePrefix, component);
            final Object value = getComponentValue(configData, component);
            map.put(name, value);
        });
    }

    private static Object getComponentValue(final Record configData, final RecordComponent component) {
        try {
            return component.getAccessor().invoke(configData);
        } catch (final Exception e) {
            throw new IllegalStateException(
                    "Can not access config value for record type '" + component.getClass() + "."
                            + component.getAccessor().getName() + "'",
                    e);
        }
    }

    private static String createSpaces(final String value, final int maxLength) {
        return IntStream.range(value.length(), maxLength).mapToObj(i -> " ").reduce("", (a, b) -> a + b);
    }

    private static int getMaxPropertyLength(final Set<?> values) {
        return values.stream()
                .map(String::valueOf)
                .mapToInt(String::length)
                .max()
                .orElse(0);
    }
}
