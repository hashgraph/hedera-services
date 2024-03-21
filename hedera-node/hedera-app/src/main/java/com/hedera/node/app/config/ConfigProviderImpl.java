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

package com.hedera.node.app.config;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.sources.PropertyConfigSource;
import com.hedera.node.config.sources.SettingsConfigSource;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.SystemEnvironmentConfigSource;
import com.swirlds.config.extensions.sources.SystemPropertiesConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of the {@link ConfigProvider} interface.
 */
@Singleton
public class ConfigProviderImpl extends ConfigProviderBase {
    private static final Logger logger = LogManager.getLogger(ConfigProviderImpl.class);

    /**
     * The actual underlying versioned configuration provided by this provider. This must provided thread-safe access
     * since many threads (ingestion, pre-handle, etc.) will be accessing it from different threads while it is
     * generally only updated on the handle thread (except during startup). The handle thread will also access it.
     */
    private final AtomicReference<VersionedConfiguration> configuration;
    /** Provides synchronous access to updating the configuration to one thread at a time. */
    private final AutoClosableLock updateLock = Locks.createAutoLock();

    /**
     * Create a new instance, particularly from dependency injection.
     */
    public ConfigProviderImpl() {
        this(false);
    }

    /**
     * Create a new instance. You must specify whether to use the genesis.properties file as a source for the
     * configuration. This should only be true if the node is starting from genesis.
     */
    public ConfigProviderImpl(final boolean useGenesisSource) {
        final var builder = createConfigurationBuilder();
        addFileSources(builder, useGenesisSource);
        final Configuration config = builder.build();
        configuration = new AtomicReference<>(new VersionedConfigImpl(config, 0));
    }

    @Override
    @NonNull
    public VersionedConfiguration getConfiguration() {
        return configuration.get();
    }

    /**
     * This method must be called when the network properties or permissions are overridden.
     * It will update the configuration and increase the version.
     *
     * @param networkProperties the network properties file content
     * @param permissions the permissions file content
     */
    public void update(@NonNull final Bytes networkProperties, @NonNull final Bytes permissions) {
        logger.info("Updating configuration caused by properties or permissions override.");
        try (final var ignoredLock = updateLock.lock()) {
            final var builder = createConfigurationBuilder();
            addFileSources(builder, false);
            addByteSource(builder, networkProperties);
            addByteSource(builder, permissions);
            final Configuration config = builder.build();
            configuration.set(
                    new VersionedConfigImpl(config, this.configuration.get().getVersion() + 1));
        }
    }

    private ConfigurationBuilder createConfigurationBuilder() {
        final ConfigurationBuilder builder = ConfigurationBuilder.create();
        builder.autoDiscoverExtensions()
                .withSource(SystemEnvironmentConfigSource.getInstance())
                .withSource(SystemPropertiesConfigSource.getInstance())
                .withSource(new PropertyConfigSource(SEMANTIC_VERSION_PROPERTIES_DEFAULT_PATH, 500));
        return builder;
    }

    private void addByteSource(@NonNull final ConfigurationBuilder builder, @NonNull final Bytes propertyFileContent) {
        requireNonNull(builder);
        requireNonNull(propertyFileContent);
        try {
            final var configurationList =
                    ServicesConfigurationList.PROTOBUF.parseStrict(propertyFileContent.toReadableSequentialData());
            final var configSource = new SettingsConfigSource(configurationList.nameValueOrThrow(), 101);
            builder.withSource(configSource);
        } catch (ParseException | NullPointerException e) {
            // Ignore. This method may be called with a partial file during regular execution.
        }
    }

    private void addFileSources(@NonNull final ConfigurationBuilder builder, final boolean useGenesisSource) {
        requireNonNull(builder);

        if (useGenesisSource) {
            try {
                addFileSource(builder, GENESIS_PROPERTIES_PATH_ENV, GENESIS_PROPERTIES_DEFAULT_PATH, 400);
            } catch (final Exception e) {
                throw new IllegalStateException("Can not create config source for genesis properties", e);
            }
        }

        try {
            addFileSource(builder, APPLICATION_PROPERTIES_PATH_ENV, APPLICATION_PROPERTIES_DEFAULT_PATH, 100);
        } catch (final Exception e) {
            throw new IllegalStateException("Can not create config source for application properties", e);
        }
    }
}
