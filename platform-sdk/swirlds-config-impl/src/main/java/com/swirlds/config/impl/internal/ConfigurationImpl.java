// SPDX-License-Identifier: Apache-2.0
package com.swirlds.config.impl.internal;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
            @NonNull final ConfigPropertiesService propertiesService,
            @NonNull final ConverterService converterService,
            @NonNull final ConfigValidationService validationService) {
        this.propertiesService = Objects.requireNonNull(propertiesService, "propertiesService must not be null");
        this.converterService = Objects.requireNonNull(converterService, "converterService must not be null");
        this.validationService = Objects.requireNonNull(validationService, "validationService must not be null");
        this.configDataService = new ConfigDataService(this, converterService);
    }

    @NonNull
    @Override
    public Stream<String> getPropertyNames() {
        return propertiesService.getPropertyNames();
    }

    @Override
    public boolean exists(@NonNull final String propertyName) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        return propertiesService.containsKey(propertyName);
    }

    @Override
    public boolean isListValue(@NonNull final String propertyName) {
        return propertiesService.isListProperty(propertyName);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getValue(@NonNull final String propertyName, @NonNull final Class<T> propertyType) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        Objects.requireNonNull(propertyType, "propertyType must not be null");
        final String rawValue = getValue(propertyName);
        if (Objects.equals(propertyType, String.class)) {
            return (T) rawValue;
        }
        return converterService.convert(rawValue, propertyType);
    }

    @Nullable
    @Override
    public <T> T getValue(
            @NonNull final String propertyName, @NonNull final Class<T> propertyType, @Nullable final T defaultValue) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        Objects.requireNonNull(propertyType, "propertyType must not be null");
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValue(propertyName, propertyType);
    }

    @Nullable
    @Override
    public String getValue(@NonNull final String propertyName) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        if (propertiesService.isListProperty(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' is a list property");
        }
        if (!exists(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' not defined in configuration");
        }
        return propertiesService.getProperty(propertyName);
    }

    @Nullable
    @Override
    public String getValue(@NonNull final String propertyName, @Nullable final String defaultValue) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        if (propertiesService.isListProperty(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' is a list property");
        }
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return propertiesService.getProperty(propertyName);
    }

    @Nullable
    @Override
    public List<String> getValues(@NonNull final String propertyName) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        if (!propertiesService.isListProperty(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' is not a list property");
        }
        if (!exists(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' not defined in configuration");
        }
        return propertiesService.getListProperty(propertyName);
    }

    @Nullable
    @Override
    public List<String> getValues(@NonNull final String propertyName, @Nullable final List<String> defaultValue) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        if (!propertiesService.isListProperty(propertyName)) {
            throw new NoSuchElementException("Property '" + propertyName + "' is not a list property");
        }
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return propertiesService.getListProperty(propertyName);
    }

    @Nullable
    @Override
    public <T> List<T> getValues(@NonNull final String propertyName, @NonNull final Class<T> propertyType) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        Objects.requireNonNull(propertyType, "propertyType must not be null");
        final List<String> values;
        if (!propertiesService.isListProperty(propertyName)) {
            final String value = getValue(propertyName);
            values = ConfigListUtils.createList(value);
        } else {
            values = getValues(propertyName);
        }
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(v -> converterService.convert(v, propertyType))
                .toList();
    }

    @Nullable
    @Override
    public <T> List<T> getValues(
            @NonNull final String propertyName,
            @NonNull final Class<T> propertyType,
            @Nullable final List<T> defaultValue) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        Objects.requireNonNull(propertyType, "propertyType must not be null");
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValues(propertyName, propertyType);
    }

    @Override
    @Nullable
    public Set<String> getValueSet(@NonNull final String propertyName) {
        final List<String> values = getValues(propertyName);
        if (values == null) {
            return null;
        }
        return Set.copyOf(values);
    }

    @Override
    @Nullable
    public Set<String> getValueSet(@NonNull final String propertyName, @Nullable final Set<String> defaultValue) {
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValueSet(propertyName);
    }

    @Override
    @Nullable
    public <T> Set<T> getValueSet(@NonNull final String propertyName, @NonNull final Class<T> propertyType)
            throws NoSuchElementException, IllegalArgumentException {
        final List<T> values = getValues(propertyName, propertyType);
        if (values == null) {
            return null;
        }
        return Set.copyOf(values);
    }

    @Override
    @Nullable
    public <T> Set<T> getValueSet(
            @NonNull final String propertyName,
            @NonNull final Class<T> propertyType,
            @Nullable final Set<T> defaultValue)
            throws IllegalArgumentException {
        if (!exists(propertyName)) {
            return defaultValue;
        }
        return getValueSet(propertyName, propertyType);
    }

    @NonNull
    @Override
    public <T extends Record> T getConfigData(@NonNull final Class<T> type) {
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

    public <T extends Record> void addConfigDataType(@NonNull final Class<T> type) {
        configDataService.addConfigDataType(type);
    }

    @NonNull
    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        return configDataService.getConfigDataTypes();
    }
}
