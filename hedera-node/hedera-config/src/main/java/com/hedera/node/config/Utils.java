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

package com.hedera.node.config;

import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Utilities for working with config. Ideally this class would not exist, but such functionality would be built into
 * {@link Configuration} itself.
 */
public final class Utils {
    private Utils() {
        // Do not instantiate
        throw new UnsupportedOperationException("Utility Class");
    }

    public static SortedMap<String, Object> networkProperties(@NonNull final Configuration configuration) {
        // Use reflection to get all fields annotated with @NetworkProperty. We do **not** want to actually use
        // reflection for this, it would be much better for there to be an annotation processor used by the config
        // system, and we would have some way to get this list from generated code instead of reflection.
        final var recordProperties = new TreeMap<String, Object>();
        configuration.getConfigDataTypes().forEach(configDataType -> {
            final var propertyNamePrefix = ConfigReflectionUtils.getNamePrefixForConfigDataRecord(configDataType);
            Arrays.stream(configDataType.getRecordComponents())
                    .filter(component -> component.getAnnotation(NetworkProperty.class) != null)
                    .forEach(component -> {
                        final var name = ConfigReflectionUtils.getPropertyNameForConfigDataProperty(
                                propertyNamePrefix, component);
                        final var configData = configuration.getConfigData(configDataType);
                        try {
                            recordProperties.put(name, component.getAccessor().invoke(configData));
                        } catch (final Exception e) {
                            e.printStackTrace();
                        }
                    });
        });

        return Collections.unmodifiableSortedMap(recordProperties);
    }
}
