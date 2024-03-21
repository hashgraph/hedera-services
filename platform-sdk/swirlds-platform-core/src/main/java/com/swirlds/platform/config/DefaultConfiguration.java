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

package com.swirlds.platform.config;

import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.extensions.sources.LegacyFileConfigSource;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.config.internal.ConfigMappings;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A utility class for building a basic configuration with the default configuration sources and paths.
 * <p>
 * Can be used in cli tools to build a basic configuration.
 */
public class DefaultConfiguration {
    private static final Logger logger = LogManager.getLogger(DefaultConfiguration.class);

    private DefaultConfiguration() {
        // Avoid instantiation for utility class
    }

    /**
     * Build a basic configuration with the default configuration sources and paths. Reads configuration form
     * "settings.txt". Registers the configuration to the {@link ConfigurationHolder}.
     *
     * @return the configuration object
     * @throws IOException if there is an error reading the configuration files
     */
    @NonNull
    public static Configuration buildBasicConfiguration() throws IOException {
        return buildBasicConfiguration(getAbsolutePath("settings.txt"), Collections.emptyList());
    }

    /**
     * Build a basic configuration with the default configuration sources and paths. Registers the configuration to the
     * {@link ConfigurationHolder}.
     *
     * @param settingsPath the path to the settings.txt file
     * @return the configuration object
     * @throws IOException if there is an error reading the configuration files
     */
    @NonNull
    public static Configuration buildBasicConfiguration(@NonNull final Path settingsPath) throws IOException {
        return buildBasicConfiguration(settingsPath, Collections.emptyList());
    }

    /**
     * Build a basic configuration with the default configuration sources. Registers the configuration to the
     * {@link ConfigurationHolder}.
     *
     * @param settingsPath       the path to the settings.txt file
     * @param configurationPaths additional paths to configuration files
     * @return the configuration object
     * @throws IOException if there is an error reading the configuration files
     */
    @NonNull
    public static Configuration buildBasicConfiguration(
            @NonNull final Path settingsPath, @NonNull final List<Path> configurationPaths) throws IOException {
        final ConfigSource settingsConfigSource = LegacyFileConfigSource.ofSettingsFile(settingsPath);
        final ConfigSource mappedSettingsConfigSource = ConfigMappings.addConfigMapping(settingsConfigSource);

        final ConfigurationBuilder configurationBuilder =
                ConfigurationBuilder.create().autoDiscoverExtensions().withSource(mappedSettingsConfigSource);

        for (final Path configurationPath : configurationPaths) {
            logger.info(LogMarker.CONFIG.getMarker(), "Loading configuration from {}", configurationPath);
            configurationBuilder.withSource(new LegacyFileConfigSource(configurationPath));
        }

        final Configuration configuration = configurationBuilder.build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);

        return configuration;
    }
}
