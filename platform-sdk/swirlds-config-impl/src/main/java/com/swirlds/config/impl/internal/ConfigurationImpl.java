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

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

class ConfigurationImpl implements Configuration, ConfigLifecycle {

    private final ConfigPropertiesService propertiesService;

    private final ConverterService converterService;

    private final ConfigDataService configDataService;

    private final ConfigValidationService validationService;

    private boolean initialized = false;

    ConfigurationImpl(
            final ConfigPropertiesService propertiesService,
            final ConverterService converterService,
            final ConfigValidationService validationService) {
        this.propertiesService = CommonUtils.throwArgNull(propertiesService, "propertiesService");
        this.converterService = CommonUtils.throwArgNull(converterService, "converterService");
        this.validationService = CommonUtils.throwArgNull(validationService, "validationService");
        this.configDataService = new ConfigDataService(this, converterService);
    }

    @Override
    public Stream<String> getPropertyNames() {
        return propertiesService.getPropertyNames();
    }

    @Override
    public boolean exists(final String propertyName) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        return propertiesService.containsKey(propertyName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(final String propertyName, final Class<T> propertyType) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(propertyType, "propertyType");
        final String rawValue = getValue(propertyName);
        if (Objects.equals(propertyType, String.class)) {
            return (T) rawValue;
        }
        return converterService.convert(rawValue, propertyType);
    }

    @Override
    public <T> T getValue(final String propertyName, final Class<T> propertyType, final T defaultValue) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(propertyType, "propertyType");
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValue(propertyName, propertyType);
    }

    @Override
    public List<String> getValues(final String propertyName) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        final String rawValue = getValue(propertyName);
        if (rawValue == null) {
            return null;
        }
        return ConfigListUtils.createList(rawValue);
    }

    @Override
    public List<String> getValues(final String propertyName, final List<String> defaultValue) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValues(propertyName);
    }

    @Override
    public <T> List<T> getValues(final String propertyName, final Class<T> propertyType) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(propertyType, "propertyType");
        final List<String> values = getValues(propertyName);
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(v -> converterService.convert(v, propertyType))
                .toList();
    }

    @Override
    public <T> List<T> getValues(final String propertyName, final Class<T> propertyType, final List<T> defaultValue) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(propertyType, "propertyType");
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValues(propertyName, propertyType);
    }

    @Override
    public Set<String> getValueSet(final String propertyName) {
        final List<String> values = getValues(propertyName);
        if (values == null) {
            return null;
        }
        return Set.copyOf(values);
    }

    @Override
    public Set<String> getValueSet(final String propertyName, final Set<String> defaultValue) {
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValueSet(propertyName);
    }

    @Override
    public <T> Set<T> getValueSet(final String propertyName, final Class<T> propertyType)
            throws NoSuchElementException, IllegalArgumentException {
        final List<T> values = getValues(propertyName, propertyType);
        if (values == null) {
            return null;
        }
        return Set.copyOf(values);
    }

    @Override
    public <T> Set<T> getValueSet(final String propertyName, final Class<T> propertyType, final Set<T> defaultValue)
            throws IllegalArgumentException {
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValueSet(propertyName, propertyType);
    }

    @Override
    public String getValue(final String propertyName) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        if (!exists(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' not defined in configuration");
        }
        return propertiesService.getProperty(propertyName);
    }

    @Override
    public String getValue(final String propertyName, final String defaultValue) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return propertiesService.getProperty(propertyName);
    }

    @Override
    public <T extends Record> T getConfigData(final Class<T> type) {
        return configDataService.getConfigData(type);
    }

    @Override
    public void init() {
        throwIfInitialized();
        configDataService.init();
        validationService.validate(this);
        initialized = true;
    }

    @Override
    public void dispose() {
        initialized = false;
        configDataService.dispose();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    public <T extends Record> void addConfigDataType(final Class<T> type) {
        configDataService.addConfigDataType(type);
    }

    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        return configDataService.getConfigDataTypes();
    }
}
