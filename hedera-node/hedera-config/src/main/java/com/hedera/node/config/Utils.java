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

package com.hedera.node.config;

import com.amh.config.NetworkProperty;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.reflection.ConfigReflectionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for working with config. Ideally this class would not exist, but such functionality would be built into
 * {@link Configuration} itself.
 */
public final class Utils {
    private static final Logger logger = LogManager.getLogger(Utils.class);

    private Utils() {
        // Do not instantiate
        throw new UnsupportedOperationException("Utility Class");
    }

    public static SortedMap<String, Object> networkProperties(@NonNull final Configuration configuration) {
        // Get all fields annotated with @NetworkProperty
        return filteredProperties(configuration, component -> component.getAnnotation(
                NetworkProperty.class) != null);
    }

    public static SortedMap<String, Object> allProperties(@NonNull final Configuration configuration) {
        return filteredProperties(configuration, component -> true);
    }

    private static SortedMap<String, Object> filteredProperties(
            @NonNull final Configuration configuration, @NonNull final Predicate<RecordComponent> filter) {
        // Use reflection to get all fields that match the predicate. We do **not** want to actually use reflection for
        // this, it would be much better for there to be an annotation processor used by the config system, and we would
        // have some way to get this list from generated code instead of reflection.
        final var recordProperties = new TreeMap<String, Object>();
        configuration.getConfigDataTypes().forEach(configDataType -> {
            final var propertyNamePrefix = ConfigReflectionUtils.getNamePrefixForConfigDataRecord(configDataType);
            Arrays.stream(configDataType.getRecordComponents()).filter(filter).forEach(component -> {
                final var name =
                        ConfigReflectionUtils.getPropertyNameForConfigDataProperty(propertyNamePrefix, component);
                final var configData = configuration.getConfigData(configDataType);
                try {
                    recordProperties.put(name, component.getAccessor().invoke(configData));
                } catch (final Exception e) {
                    logger.warn("Unable to load config property value for {}", name, e);
                }
            });
        });

        return Collections.unmodifiableSortedMap(recordProperties);
    }
}
