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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains utility methods for the platform config.
 */
public class PlatformConfigUtils {
    private static final Logger logger = LogManager.getLogger(PlatformConfigUtils.class);

    private final Configuration configuration;
    private final Set<String> configNames;

    /**
     * Constructs a new {@link PlatformConfigUtils} instance.
     *
     * @param configuration the configuration to check
     */
    private PlatformConfigUtils(@NonNull final Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        configNames = getConfigNames();
    }

    /**
     * Checks the given configuration for not known configuration and mapped properties.
     *
     * @param configuration the configuration to check
     */
    public static void checkConfiguration(@NonNull final Configuration configuration) {
        final PlatformConfigUtils platformConfigUtils = new PlatformConfigUtils(configuration);
        platformConfigUtils.logNotKnownConfigProperties();
        platformConfigUtils.logAppliedMappedProperties();
    }

    /**
     * Logs all configuration properties that are not known by any configuration data type.
     */
    private void logNotKnownConfigProperties() {
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

    /**
     * Logs all applied mapped properties. And suggests to change the new property name.
     */
    private void logAppliedMappedProperties() {
        final Map<String, String> mappings = ConfigMappings.MAPPINGS.stream()
                .collect(Collectors.toMap(ConfigMapping::originalName, ConfigMapping::mappedName));

        configNames.stream().filter(mappings::containsKey).forEach(name -> {
            final String message = ("Configuration property '%s' was mapped to '%s'. "
                            + "Consider change the new property name")
                    .formatted(name, mappings.get(name));

            logger.info(STARTUP.getMarker(), message);
        });
    }

    /**
     * Collects all configuration property names from all sources.
     *
     * @return the set of all configuration property names
     */
    private Set<String> getConfigNames() {
        return configuration.getConfigDataTypes().stream()
                .flatMap(configDataType -> {
                    final String propertyNamePrefix =
                            ConfigReflectionUtils.getNamePrefixForConfigDataRecord(configDataType);
                    return Arrays.stream(configDataType.getRecordComponents())
                            .map(component -> ConfigReflectionUtils.getPropertyNameForConfigDataProperty(
                                    propertyNamePrefix, component));
                })
                .collect(Collectors.toSet());
    }
}
