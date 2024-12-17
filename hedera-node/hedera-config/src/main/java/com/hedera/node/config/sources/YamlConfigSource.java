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

package com.hedera.node.config.sources;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

/**
 * A config source that wraps a {@link Properties} object.
 */
public class YamlConfigSource implements ConfigSource {

    /**
     * The {@link ObjectMapper} used to convert maps to JSON.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * The map that contains all not-list properties.
     */
    private final Map<String, String> properties;

    /**
     * The map that contains all list properties.
     */
    private final Map<String, List<String>> listProperties;

    /**
     * The ordinal of this config source.
     */
    private final int ordinal;

    /**
     * Creates a new {@link YamlConfigSource} instance with the given file name and default ordinal.
     *
     * @param fileName the name of the file that contains the properties
     * @see ConfigSource#DEFAULT_ORDINAL
     */
    public YamlConfigSource(@NonNull final String fileName) {
        this(fileName, DEFAULT_ORDINAL);
    }

    /**
     * Creates a new {@link YamlConfigSource} instance with the given file name and ordinal.
     *
     * @param fileName the name of the file that contains the properties
     * @param ordinal the ordinal of this config source
     */
    public YamlConfigSource(@NonNull final String fileName, final int ordinal) {
        requireNonNull(fileName, "fileName must not be null");
        this.properties = new HashMap<>();
        this.listProperties = new HashMap<>();
        this.ordinal = ordinal;

        convertYamlToMaps(fileName);
    }

    private void convertYamlToMaps(String resourceName) {
        try (final InputStream in =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("Resource not found: " + resourceName));
            }
            final Yaml yaml = new Yaml();
            final Object rawData = yaml.load(in);
            processYamlNode("", rawData, properties, listProperties);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load resource " + resourceName, e);
        }
    }

    @SuppressWarnings("unchecked")
    private void processYamlNode(
            @NonNull final String prefix,
            @NonNull final Object node,
            @NonNull final Map<String, String> simpleProps,
            @NonNull final Map<String, List<String>> listProps) {

        if (!(node instanceof Map)) {
            return;
        }

        final Map<String, Object> map = (Map<String, Object>) node;
        for (final Map.Entry<String, Object> entry : map.entrySet()) {
            final String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            final Object value = entry.getValue();

            try {
                switch (value) {
                    case final List<?> list -> handleList(listProps, list, newPrefix);
                    case final Map<?, ?> mapValue -> handleMap(simpleProps, listProps, mapValue, newPrefix);
                    case null -> simpleProps.put(newPrefix, null);
                    default -> simpleProps.put(newPrefix, value.toString());
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to convert map to JSON for key: " + newPrefix, e);
            }
        }
    }

    private void handleMap(
            final @NonNull Map<String, String> simpleProps,
            final @NonNull Map<String, List<String>> listProps,
            final Map<?, ?> mapValue,
            final String newPrefix)
            throws JsonProcessingException {
        if (mapValue.values().stream().noneMatch(v -> v instanceof Map || v instanceof List)) {
            simpleProps.put(newPrefix, OBJECT_MAPPER.writeValueAsString(mapValue));
        } else {
            processYamlNode(newPrefix, mapValue, simpleProps, listProps);
        }
    }

    private void handleList(
            final @NonNull Map<String, List<String>> listProps, final List<?> list, final String newPrefix) {
        if (!list.isEmpty() && list.getFirst() instanceof Map) {
            final List<String> jsonList = list.stream()
                    .map(item -> {
                        try {
                            return OBJECT_MAPPER.writeValueAsString(item);
                        } catch (JsonProcessingException e) {
                            throw new IllegalStateException("Failed to convert map to JSON", e);
                        }
                    })
                    .toList();
            listProps.put(newPrefix, jsonList);
        } else {
            listProps.put(newPrefix, list.stream().map(Object::toString).toList());
        }
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Set<String> getPropertyNames() {
        return Stream.concat(properties.keySet().stream(), listProperties.keySet().stream())
                .collect(Collectors.toSet());
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public String getValue(@NonNull String propertyName) throws NoSuchElementException {
        requireNonNull(propertyName, "propertyName must not be null");
        if (isListProperty(propertyName)) {
            throw new NoSuchElementException("Property " + propertyName + " is a list property");
        }
        return properties.get(propertyName);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isListProperty(@NonNull final String propertyName) throws NoSuchElementException {
        requireNonNull(propertyName, "propertyName must not be null");
        return listProperties.containsKey(propertyName);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public List<String> getListValue(@NonNull final String propertyName) throws NoSuchElementException {
        requireNonNull(propertyName, "propertyName must not be null");
        if (!isListProperty(propertyName)) {
            throw new NoSuchElementException("Property " + propertyName + " is not a list property");
        }
        return listProperties.get(propertyName);
    }

    /** {@inheritDoc} */
    @Override
    public int getOrdinal() {
        return ordinal;
    }
}
