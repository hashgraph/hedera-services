/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.internal;

import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigValidator;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is an implementation of the {@link ConfigurationBuilder} interface that is based on the given config
 * implementation.
 */
final class ConfigurationBuilderImpl implements ConfigurationBuilder {

    /**
     * Internal lock to support a thread safe initialization
     */
    private final AutoClosableLock initializationLock = Locks.createAutoLock();

    /**
     * The configuration instance
     */
    private final ConfigurationImpl configuration;

    /**
     * The service that handles all {@link ConfigSource} instances
     */
    private final ConfigSourceService configSourceService;

    /**
     * The service that handles all {@link ConfigConverter} instances
     */
    private final ConverterService converterService;

    /**
     * The service that handles all {@link ConfigValidator} instances
     */
    private final ConfigValidationService validationService;

    /**
     * The service that holds all properties of the configuration
     */
    private final ConfigPropertiesService propertiesService;

    /**
     * initialization flag
     */
    private AtomicBoolean initialized = new AtomicBoolean();

    /**
     * Default constructor that creates all internal services
     */
    public ConfigurationBuilderImpl() {
        configSourceService = new ConfigSourceService();
        converterService = new ConverterService();
        propertiesService = new ConfigPropertiesService(configSourceService);
        validationService = new ConfigValidationService(converterService);
        configuration = new ConfigurationImpl(propertiesService, converterService, validationService);
    }

    @Override
    public Configuration build() {
        try (final Locked ignored = initializationLock.lock()) {
            if (initialized.get()) {
                throw new IllegalStateException("Configuration already initialized");
            }
            configSourceService.init();
            converterService.init();
            propertiesService.init();
            validationService.init();
            configuration.init();
            initialized.set(true);
        }
        return configuration;
    }

    @Override
    public ConfigurationBuilder withSource(final ConfigSource configSource) {
        addConfigSource(configSource);
        return this;
    }

    @Override
    public ConfigurationBuilder withSources(final ConfigSource... configSources) {
        Arrays.stream(configSources).forEach(this::addConfigSource);
        return this;
    }

    private void addConfigSource(final ConfigSource configSource) {
        CommonUtils.throwArgNull(configSource, "configSource");
        if (initialized.get()) {
            throw new IllegalStateException("ConfigSource can not be added to initialized config");
        }
        configSourceService.addConfigSource(configSource);
    }

    @Override
    public ConfigurationBuilder withConverter(final ConfigConverter<?> converter) {
        addConverter(converter);
        return this;
    }

    @Override
    public ConfigurationBuilder withConverters(final ConfigConverter<?>... converters) {
        Arrays.stream(converters).forEach(this::addConverter);
        return this;
    }

    private void addConverter(final ConfigConverter<?> converter) {
        CommonUtils.throwArgNull(converter, "converter");
        if (initialized.get()) {
            throw new IllegalStateException("Converters can not be added to initialized config");
        }
        converterService.addConverter(converter);
    }

    @Override
    public ConfigurationBuilder withValidator(final ConfigValidator validator) {
        addValidator(validator);
        return this;
    }

    @Override
    public ConfigurationBuilder withValidators(final ConfigValidator... validators) {
        Arrays.stream(validators).forEach(this::addValidator);
        return this;
    }

    private void addValidator(final ConfigValidator validator) {
        CommonUtils.throwArgNull(validator, "validator");
        if (initialized.get()) {
            throw new IllegalStateException("ConfigValidator can not be added to initialized config");
        }
        validationService.addValidator(validator);
    }

    @Override
    public <T extends Record> ConfigurationBuilder withConfigDataType(final Class<T> type) {
        addConfigDataType(type);
        return this;
    }

    @Override
    public ConfigurationBuilder withConfigDataTypes(final Class<? extends Record>... types) {
        Arrays.stream(types).forEach(this::addConfigDataType);
        return this;
    }

    private <T extends Record> void addConfigDataType(final Class<T> type) {
        CommonUtils.throwArgNull(type, "type");
        if (initialized.get()) {
            throw new IllegalStateException("ConfigDataType can not be added to initialized config");
        }
        configuration.addConfigDataType(type);
    }
}
