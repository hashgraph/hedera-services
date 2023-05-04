/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.config.testfixtures;

import com.hedera.node.config.converter.AccountIDConverter;
import com.hedera.node.config.converter.CongestionMultipliersConverter;
import com.hedera.node.config.converter.ContractIDConverter;
import com.hedera.node.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.config.converter.EntityTypeConverter;
import com.hedera.node.config.converter.FileIDConverter;
import com.hedera.node.config.converter.HederaFunctionalityConverter;
import com.hedera.node.config.converter.KeyValuePairConverter;
import com.hedera.node.config.converter.KnownBlockValuesConverter;
import com.hedera.node.config.converter.LegacyContractIdActivationsConverter;
import com.hedera.node.config.converter.MapAccessTypeConverter;
import com.hedera.node.config.converter.ProfileConverter;
import com.hedera.node.config.converter.RecomputeTypeConverter;
import com.hedera.node.config.converter.ScaleFactorConverter;
import com.hedera.node.config.converter.SidecarTypeConverter;
import com.hedera.node.config.validation.EmulatesMapValidator;
import com.swirlds.common.config.ConfigUtils;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class HederaTestConfigProvider {

    private final AutoClosableLock configLock;

    private Configuration configuration;

    private final ConfigurationBuilder builder;

    public HederaTestConfigProvider() {
        this(true);
    }

    public HederaTestConfigProvider(final boolean registerAllTypes) {
        this.configLock = Locks.createAutoLock();
        this.configuration = null;
        if (registerAllTypes) {
            this.builder = ConfigUtils.scanAndRegisterAllConfigTypes(ConfigurationBuilder.create());
        } else {
            this.builder = ConfigurationBuilder.create();
        }

        this.builder.withConverter(new CongestionMultipliersConverter())
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
                .withValidator(new EmulatesMapValidator());

    }

    public HederaTestConfigProvider withValue(final String propertyName, final String value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigProvider withValue(final String propertyName, final int value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigProvider withValue(final String propertyName, final double value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigProvider withValue(final String propertyName, final long value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigProvider withValue(final String propertyName, final boolean value) {
        return this.withSource(new SimpleConfigSource(propertyName, value));
    }

    public HederaTestConfigProvider withValue(final String propertyName, final Object value) {
        Objects.requireNonNull(value, "value must not be null");
        return this.withSource(new SimpleConfigSource(propertyName, value.toString()));
    }

    public Configuration getOrCreateConfig() {
        final Locked ignore = this.configLock.lock();

        final Configuration var2;
        try {
            if (this.configuration == null) {
                this.configuration = this.builder.build();
                ConfigurationHolder.getInstance().setConfiguration(this.configuration);
            }

            var2 = this.configuration;
        } catch (final Throwable var5) {
            if (ignore != null) {
                try {
                    ignore.close();
                } catch (final Throwable var4) {
                    var5.addSuppressed(var4);
                }
            }

            throw var5;
        }

        if (ignore != null) {
            ignore.close();
        }

        return var2;
    }

    private void checkConfigState() {
        final Locked ignore = this.configLock.lock();

        try {
            if (this.configuration != null) {
                throw new IllegalStateException("Configuration already created!");
            }
        } catch (final Throwable var5) {
            if (ignore != null) {
                try {
                    ignore.close();
                } catch (final Throwable var4) {
                    var5.addSuppressed(var4);
                }
            }

            throw var5;
        }

        if (ignore != null) {
            ignore.close();
        }

    }

    public HederaTestConfigProvider withSource(final ConfigSource configSource) {
        this.checkConfigState();
        this.builder.withSource(configSource);
        return this;
    }

    public HederaTestConfigProvider withConverter(@NonNull final ConfigConverter<?> configConverter) {
        this.checkConfigState();
        this.builder.withConverter(configConverter);
        return this;
    }
}
