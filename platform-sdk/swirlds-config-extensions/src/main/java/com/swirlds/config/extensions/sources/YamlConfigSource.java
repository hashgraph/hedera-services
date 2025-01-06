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

package com.swirlds.config.extensions.sources;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

/**
 * A config source that reads properties from a YAML file.
 * <br>
 * The config source reads the properties from the YAML file.
 * <br>
 * The keys of the properties are the full path of the property in the YAML file, separated by dots.
 * For example:
 * <code>
 *     a:
 *       b:
 *          c: value
 * </code>
 * For the above YAML file, the key for the property would be "a.b.c" to retrieve the "value". This complies with the way the
 * properties are stored and accessed in the {@link ConfigSource} interface.
 * <br>
 * All list elements are stored as JSON strings and can be deserialized in an {@link com.swirlds.config.api.converter.ConfigConverter}
 */
public class YamlConfigSource implements ConfigSource {

    /**
     * The logger
     */
    private static final Logger logger = LogManager.getLogger(YamlConfigSource.class);

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

        try (InputStream resource =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)) {
            if (resource == null) {
                throw new UncheckedIOException(new IOException("Resource not found: " + fileName));
            }
            convertYamlToMaps(resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read YAML file " + fileName, e);
        }
    }

    /**
     * Creates a new {@link YamlConfigSource} instance with the given file path and default ordinal.
     *
     * @param filePath the name of the file that contains the properties
     * @see ConfigSource#DEFAULT_ORDINAL
     */
    public YamlConfigSource(@NonNull final Path filePath) {
        this(filePath, DEFAULT_ORDINAL);
    }

    /**
     * Creates a new {@link YamlConfigSource} instance with the given file path and ordinal.
     *
     * @param filePath the name of the file that contains the properties
     * @param ordinal the ordinal of this config source
     */
    public YamlConfigSource(@NonNull final Path filePath, final int ordinal) {
        requireNonNull(filePath, "filePath must not be null");
        this.properties = new HashMap<>();
        this.listProperties = new HashMap<>();
        this.ordinal = ordinal;

        if (!Files.exists(filePath)) {
            logger.warn("File {} does not exist, no properties will be loaded", filePath);
            return;
        }

        try (InputStream resource = Files.newInputStream(filePath)) {
            convertYamlToMaps(resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read YAML file " + filePath, e);
        }
    }

    private void convertYamlToMaps(@NonNull final InputStream resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        final Yaml yaml = new Yaml();
        final Object rawData = yaml.load(resource);
        processYamlNode("", rawData, properties, listProperties);
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
