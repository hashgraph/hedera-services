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

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    private final AtomicBoolean initialized = new AtomicBoolean();

    /**
     * Key-value pairs specified by {@link #withValue(String, String)}.
     */
    private final Map<String, String> properties = new HashMap<>();

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

    @NonNull
    @Override
    public Configuration build() {
        try (final Locked ignored = initializationLock.lock()) {
            if (initialized.get()) {
                throw new IllegalStateException("Configuration already initialized");
            }
            if (!properties.isEmpty()) {
                withSource(new SimpleConfigSource(properties).withOrdinal(CUSTOM_PROPERTY_ORDINAL));
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

    @NonNull
    @Override
    public ConfigurationBuilder withSource(@NonNull final ConfigSource configSource) {
        addConfigSource(configSource);
        return this;
    }

    @NonNull
    @Override
    public ConfigurationBuilder withSources(@NonNull final ConfigSource... configSources) {
        Arrays.stream(configSources).forEach(this::addConfigSource);
        return this;
    }

    private void addConfigSource(@NonNull final ConfigSource configSource) {
        Objects.requireNonNull(configSource, "configSource must not be null");
        if (initialized.get()) {
            throw new IllegalStateException("ConfigSource can not be added to initialized config");
        }
        configSourceService.addConfigSource(configSource);
    }

    @NonNull
    @Override
    public ConfigurationBuilder withConverter(@NonNull final ConfigConverter<?> converter) {
        addConverter(converter);
        return this;
    }

    @NonNull
    @Override
    public ConfigurationBuilder withConverters(@NonNull final ConfigConverter<?>... converters) {
        Arrays.stream(converters).forEach(this::addConverter);
        return this;
    }

    private void addConverter(@NonNull final ConfigConverter<?> converter) {
        Objects.requireNonNull(converter, "converter must not be null");
        if (initialized.get()) {
            throw new IllegalStateException("Converters can not be added to initialized config");
        }
        converterService.addConverter(converter);
    }

    @NonNull
    @Override
    public ConfigurationBuilder withValidator(@NonNull final ConfigValidator validator) {
        addValidator(validator);
        return this;
    }

    @NonNull
    @Override
    public ConfigurationBuilder withValidators(@NonNull final ConfigValidator... validators) {
        Arrays.stream(validators).forEach(this::addValidator);
        return this;
    }

    private void addValidator(@NonNull final ConfigValidator validator) {
        Objects.requireNonNull(validator, "validator must not be null");
        if (initialized.get()) {
            throw new IllegalStateException("ConfigValidator can not be added to initialized config");
        }
        validationService.addValidator(validator);
    }

    @NonNull
    @Override
    public <T extends Record> ConfigurationBuilder withConfigDataType(@NonNull final Class<T> type) {
        addConfigDataType(type);
        return this;
    }

    @NonNull
    @Override
    public ConfigurationBuilder withConfigDataTypes(@NonNull final Class<? extends Record>... types) {
        Arrays.stream(types).forEach(this::addConfigDataType);
        return this;
    }

    private <T extends Record> void addConfigDataType(@NonNull final Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        if (initialized.get()) {
            throw new IllegalStateException("ConfigDataType can not be added to initialized config");
        }
        configuration.addConfigDataType(type);
    }

    /**
     * Sets the value for the config.
     *
     * @param propertyName name of the property
     * @param value        the value
     * @return the {@link ConfigurationBuilder} instance (for fluent API)
     */
    @NonNull
    public ConfigurationBuilder withValue(@NonNull final String propertyName, @NonNull final String value) {
        Objects.requireNonNull(propertyName, "propertyName must not be null");
        Objects.requireNonNull(value, "value must not be null");
        properties.put(propertyName, value);
        return this;
    }
}
