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

package com.hedera.node.app.config;

import com.hedera.node.app.config.internal.ConfigurationAdaptor;
import com.hedera.node.app.config.internal.VersionedConfigImpl;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.ConfigProvider;
import com.hedera.node.app.spi.config.VersionedConfiguration;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of the {@link ConfigProvider} interface.
 */
public class ConfigProviderImpl implements ConfigProvider {

    private final PropertySource propertySource;

    private final AtomicReference<VersionedConfiguration> configuration;

    private final AutoClosableLock updateLock = Locks.createAutoLock();

    /**
     * Constructor.
     *
     * @param propertySource the property source to use. All properties will be read from this source.
     */
    public ConfigProviderImpl(@NonNull final PropertySource propertySource) {
        this.propertySource = Objects.requireNonNull(propertySource, "propertySource");
        final Configuration config = new ConfigurationAdaptor(propertySource);
        configuration = new AtomicReference<>(new VersionedConfigImpl(config, 0));
    }

    /**
     * This method must be called if a property has changed. It will update the configuration and increase the version.
     * This should happen whenever {@link GlobalDynamicProperties#reload()} is called.
     */
    public void update() {
        try (final var lock = updateLock.lock()) {
            final Configuration config = new ConfigurationAdaptor(propertySource);
            final VersionedConfiguration versionedConfig =
                    new VersionedConfigImpl(config, this.configuration.get().getVersion() + 1);
            configuration.set(versionedConfig);
        }
    }

    @Override
    public VersionedConfiguration getConfiguration() {
        return configuration.get();
    }
}
