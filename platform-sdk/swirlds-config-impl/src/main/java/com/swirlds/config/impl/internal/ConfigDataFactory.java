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

import com.swirlds.base.ArgumentUtils;
import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Internal factory for config data objects. See {@link Configuration#getConfigData(Class)} for a detailed description
 * on config data objects.
 */
class ConfigDataFactory {

    /**
     * The configuration that is internally used to fill the properties of the config data instances
     */
    private final Configuration configuration;

    /**
     * The converter service that is used to convert raw values from the config to custom data types
     */
    private final ConverterService converterService;

    ConfigDataFactory(@NonNull final Configuration configuration, @NonNull final ConverterService converterService) {
        this.configuration = ArgumentUtils.throwArgNull(configuration, "configuration");
        this.converterService = ArgumentUtils.throwArgNull(converterService, "converterService");
    }

    @SuppressWarnings("unchecked")
    @NonNull
    <T extends Record> T createConfigInstance(@NonNull final Class<T> type)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        ArgumentUtils.throwArgNull(type, "type");

        if (!type.isAnnotationPresent(ConfigData.class)) {
            throw new IllegalArgumentException("Can not create config instance for '" + type + "' since "
                    + ConfigData.class.getName() + "' " + "annotation is missing");
        }
        if (!type.isRecord()) {
            throw new IllegalArgumentException(
                    "Can not create config instance for '" + type + "' since it is not record");
        }
        if (!ConfigReflectionUtils.isPublic(type)) {
            throw new IllegalArgumentException(
                    "Can not create config instance for '" + type + "' since it is not public");
        }
        if (type.getConstructors().length != 1) {
            throw new IllegalArgumentException(
                    "Can not create config instance for '" + type + "' since it has not exactly 1 constructor");
        }

        final String namePrefix = getNamePrefix(type);
        final Object[] paramValues = Arrays.stream(type.getRecordComponents())
                .map(component -> getValueForRecordComponent(namePrefix, component))
                .toArray(Object[]::new);
        final Constructor<T> constructor = (Constructor<T>) type.getConstructors()[0];
        return constructor.newInstance(paramValues);
    }

    @Nullable
    private Object getValueForRecordComponent(@NonNull final String namePrefix,
            @NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        final String name = createPropertyName(namePrefix, component);
        final Class<?> valueType = component.getType();
        if (hasDefaultValue(component)) {
            if (Objects.equals(List.class, component.getType())) {
                final Class<?> genericType = getGenericListType(component);
                return configuration.getValues(name, genericType, getDefaultValues(component));
            }
            return configuration.getValue(name, valueType, getDefaultValue(component));
        } else {
            if (Objects.equals(List.class, component.getType())) {
                final Class<?> genericType = getGenericListType(component);
                return configuration.getValues(name, genericType);
            }
            return configuration.getValue(name, valueType);
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static <T> Class<T> getGenericListType(@NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        final ParameterizedType stringListType = (ParameterizedType) component.getGenericType();
        if (!Objects.equals(List.class, stringListType.getRawType())) {
            throw new IllegalArgumentException("Only List interface is supported");
        }
        final Class<T> cls = (Class<T>) ConfigReflectionUtils.getSingleGenericTypeArgument(stringListType);
        if (cls == null) {
            throw new IllegalArgumentException("No generic class found!");
        }
        return cls;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> List<T> getDefaultValues(@NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        final Class<?> type = getGenericListType(component);
        final Optional<String> rawDefaultValue = getRawDefaultValue(component);
        if (rawDefaultValue.isEmpty()) {
            throw new IllegalArgumentException("Default value not defined for parameter");
        }
        final String rawValue = rawDefaultValue.get();
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (List<T>) ConfigListUtils.createList(rawValue).stream()
                .map(value -> converterService.convert(value, type))
                .toList();
    }

    @NonNull
    private static <T extends Record> String getNamePrefix(final @NonNull Class<T> type) {
        ArgumentUtils.throwArgNull(type, "type");
        return Optional.ofNullable(type.getAnnotation(ConfigData.class))
                .map(ConfigData::value)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> T getDefaultValue(@NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        final Optional<String> rawDefaultValue = getRawDefaultValue(component);
        if (rawDefaultValue.isEmpty()) {
            throw new IllegalArgumentException("Default value not defined for parameter");
        }
        final String rawValue = rawDefaultValue.get();
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (T) converterService.convert(rawValue, component.getType());
    }

    @NonNull
    private static Optional<String> getRawDefaultValue(@NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        return Optional.ofNullable(component.getAnnotation(ConfigProperty.class))
                .map(ConfigProperty::defaultValue)
                .filter(defaultValue -> !Objects.equals(ConfigProperty.UNDEFINED_DEFAULT_VALUE, defaultValue));
    }

    private static boolean hasDefaultValue(@NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        return Optional.ofNullable(component.getAnnotation(ConfigProperty.class))
                .map(propertyAnnotation ->
                        !Objects.equals(ConfigProperty.UNDEFINED_DEFAULT_VALUE, propertyAnnotation.defaultValue()))
                .orElse(false);
    }

    @NonNull
    private static String createPropertyName(@NonNull final String prefix, @NonNull final RecordComponent component) {
        ArgumentUtils.throwArgNull(component, "component");
        return Optional.ofNullable(component.getAnnotation(ConfigProperty.class))
                .map(propertyAnnotation -> {
                    if (!propertyAnnotation.value().isBlank()) {
                        return createPropertyName(prefix, propertyAnnotation.value());
                    } else {
                        return createPropertyName(prefix, component.getName());
                    }
                })
                .orElseGet(() -> createPropertyName(prefix, component.getName()));
    }

    @NonNull
    private static String createPropertyName(@NonNull final String prefix, @NonNull final String name) {
        ArgumentUtils.throwArgNull(prefix, "prefix");
        ArgumentUtils.throwArgNull(name, "name");
        if (prefix.isBlank()) {
            return name;
        }
        return prefix + "." + name;
    }
}
