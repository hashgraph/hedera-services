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

package com.swirlds.common.config.sources;

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.config.api.source.ConfigSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ConfigSource} that can be used to remap the configuration from old properties to new properties,
 * based on the property mappings. The mappings should be provided as a {@code Map<oldKey:newKey>}.
 */
public class RemappedConfigSource extends AbstractConfigSource {

    private static final Logger logger = LogManager.getLogger(RemappedConfigSource.class);

    /**
     * The original config source.
     */
    private final ConfigSource configSource;
    /**
     * The properties after remapping.
     */
    private final Map<String, String> properties; // <key : value>
    /**
     * The remapped keys.
     */
    private final Map<String, String> remappedKeys; // <newKey : oldKey>

    /**
     * Creates {@code RemappedConfigSource} with the given config source and the property mappings.
     *
     * @param source   the original config source
     * @param mappings the property mappings
     */
    public RemappedConfigSource(final ConfigSource source, final Map<String, String> mappings) {
        this.configSource = Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(mappings, "mappings cannot be null");

        this.properties = new HashMap<>();
        this.remappedKeys = new HashMap<>();

        remapProperties(source, mappings);
        logRemappedProperties();
    }

    /**
     * After remapping properties, the old property will be removed and replaced by the new property.
     * All the remapped property keys will be stored in the {@code remappedKeys} map for logging.
     *
     * @param source   the original config source
     * @param mappings the property mappings
     */
    private void remapProperties(final ConfigSource source, final Map<String, String> mappings) {
        source.getProperties().forEach((key, val) -> {
            if (mappings.containsKey(key)) { // if a property exists in the mappings as a key
                if (remappedKeys.containsKey(key)) {
                    throw new IllegalConfigException(getErrorMessageForPropertyAlreadyMapped(key));
                }
                final String newKey = mappings.get(key);
                if (properties.containsKey(newKey)) {
                    throw new IllegalConfigException(getErrorMessageForPropertyAlreadyMapped(newKey));
                }
                properties.put(newKey, val);
                remappedKeys.put(newKey, key);
            } else if (mappings.containsValue(key)) { // if a property exists in the mappings as a value
                if (remappedKeys.containsKey(key)) {
                    throw new IllegalConfigException(getErrorMessageForPropertyAlreadyMapped(key));
                }
                final String oldKey = mappings.entrySet().stream()
                        .filter(e -> key.equalsIgnoreCase(e.getValue()))
                        .findFirst()
                        .orElseThrow()
                        .getKey();
                properties.put(key, val);
                remappedKeys.put(key, oldKey);
            } else {
                properties.put(key, val);
            }
        });
    }

    /**
     * A convenient method to get the error message for the property that was already mapped.
     *
     * @param key the property key
     * @return the error message
     */
    private String getErrorMessageForPropertyAlreadyMapped(final String key) {
        return "The property '%s' was already mapped to '%s' by '%s'."
                .formatted(
                        key,
                        properties.get(key),
                        Optional.ofNullable(remappedKeys.get(key)).orElse(key));
    }

    /**
     * A convenient method to log the information of the remapped properties.
     */
    private void logRemappedProperties() {
        if (!remappedKeys.isEmpty()) {
            final StringBuilder lines = new StringBuilder();
            lines.append(System.lineSeparator()).append(String.format("%-32s     %s", "<Old Name>", "<New Name>"));
            remappedKeys.entrySet().stream()
                    .sorted((x, y) -> x.getKey().compareToIgnoreCase(y.getKey()))
                    .forEach(entry -> lines.append(System.lineSeparator())
                            .append(String.format("%-32s --> %s", entry.getValue(), entry.getKey())));
            logger.warn(STARTUP.getMarker(), "Configuration remapped from {}!{}", configSource.getName(), lines);
        }
    }

    /**
     * Getter of the remapped property keys.
     *
     * @return the remapped property keys as a {@code Map<newKey:oldKey>}.
     */
    protected Map<String, String> getRemappedPropertyKeys() {
        return Collections.unmodifiableMap(remappedKeys);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, String> getInternalProperties() {
        return Collections.unmodifiableMap(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return configSource.getOrdinal();
    }
}
