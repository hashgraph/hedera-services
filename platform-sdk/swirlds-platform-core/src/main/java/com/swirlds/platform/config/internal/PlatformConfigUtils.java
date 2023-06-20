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

package com.swirlds.platform.config.internal;

import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.common.config.sources.ConfigMapping;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PlatformConfigUtils {
    private static final Logger logger = LogManager.getLogger(PlatformConfigUtils.class);

    private PlatformConfigUtils() {
        // Utility class
    }

    public static void logNotKnownConfigProperties(@NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration);

        final Set<String> configNames = configuration.getConfigDataTypes().stream()
                .flatMap(configDataType -> {
                    final String propertyNamePrefix =
                            ConfigReflectionUtils.getNamePrefixForConfigDataRecord(configDataType);
                    return Arrays.stream(configDataType.getRecordComponents())
                            .map(component -> ConfigReflectionUtils.getPropertyNameForConfigDataProperty(
                                    propertyNamePrefix, component));
                })
                .collect(Collectors.toSet());
        ConfigMappings.MAPPINGS.stream().map(ConfigMapping::originalName).forEach(configNames::add);
        configuration
                .getPropertyNames()
                .filter(name -> !configNames.contains(name))
                .forEach(name -> {
                    final String message =
                            "Configuration property '%s' is not used by any configuration data type".formatted(name);
                    logger.warn(STARTUP.getMarker(), message);
                });
    }
}
