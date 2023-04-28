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

import com.hedera.node.app.config.converter.CongestionMultipliersConverter;
import com.hedera.node.app.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.app.config.converter.EntityTypeConverter;
import com.hedera.node.app.config.converter.KnownBlockValuesConverter;
import com.hedera.node.app.config.converter.LegacyContractIdActivationsConverter;
import com.hedera.node.app.config.converter.MapAccessTypeConverter;
import com.hedera.node.app.config.converter.RecomputeTypeConverter;
import com.hedera.node.app.config.converter.ScaleFactorConverter;
import com.hedera.node.app.config.internal.VersionedConfigImpl;
import com.hedera.node.app.config.source.PropertySourceBasedConfigSource;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.spi.config.ConfigProvider;
import com.hedera.node.app.spi.config.VersionedConfiguration;
import com.hedera.node.app.spi.config.converter.AccountIDConverter;
import com.hedera.node.app.spi.config.converter.ContractIDConverter;
import com.hedera.node.app.spi.config.converter.FileIDConverter;
import com.hedera.node.app.spi.config.converter.HederaFunctionalityConverter;
import com.hedera.node.app.spi.config.converter.KeyValuePairConverter;
import com.hedera.node.app.spi.config.converter.ProfileConverter;
import com.hedera.node.app.spi.config.converter.SidecarTypeConverter;
import com.hedera.node.app.spi.config.data.GlobalConfig;
import com.hedera.node.app.spi.config.data.GlobalDynamicConfig;
import com.hedera.node.app.spi.config.data.NodeConfig;
import com.hedera.node.app.spi.config.validation.EmulatesMapValidator;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
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
        final Configuration config = createConfiguration();
        configuration = new AtomicReference<>(new VersionedConfigImpl(config, 0));
    }

    /**
     * This method must be called if a property has changed. It will update the configuration and increase the version.
     * This should happen whenever {@link GlobalDynamicProperties#reload()} is called.
     */
    public void update() {
        try (final var lock = updateLock.lock()) {
            final Configuration config = createConfiguration();
            final VersionedConfiguration versionedConfig =
                    new VersionedConfigImpl(config, this.configuration.get().getVersion() + 1);
            configuration.set(versionedConfig);
        }
    }

    private Configuration createConfiguration() {
        return ConfigurationBuilder.create()
                .withSource(new PropertySourceBasedConfigSource(propertySource))
                .withConverter(new CongestionMultipliersConverter())
                .withConverter(new EntityScaleFactorsConverter())
                .withConverter(new EntityTypeConverter())
                .withConverter(new KnownBlockValuesConverter())
                .withConverter(new LegacyContractIdActivationsConverter())
                .withConverter(new MapAccessTypeConverter())
                .withConverter(new RecomputeTypeConverter())
                .withConverter(new ScaleFactorConverter())
                .withConverter(new AccountIDConverter())
                .withConverter(new ContractIDConverter())
                .withConverter(new FileIDConverter())
                .withConverter(new HederaFunctionalityConverter())
                .withConverter(new ProfileConverter())
                .withConverter(new SidecarTypeConverter())
                .withConverter(new KeyValuePairConverter())
                .withValidator(new EmulatesMapValidator())
                .withConfigDataType(GlobalConfig.class)
                .withConfigDataType(GlobalDynamicConfig.class)
                .withConfigDataType(NodeConfig.class)
                .build();
    }

    @Override
    public VersionedConfiguration getConfiguration() {
        return configuration.get();
    }
}
