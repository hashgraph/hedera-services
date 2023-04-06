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

import static com.swirlds.base.ArgumentUtils.throwArgNull;

import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    ConfigDataFactory(final Configuration configuration, final ConverterService converterService) {
        this.configuration = CommonUtils.throwArgNull(configuration, "configuration");
        this.converterService = CommonUtils.throwArgNull(converterService, "converterService");
    }

    @SuppressWarnings("unchecked")
    <T extends Record> T createConfigInstance(final Class<T> type)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        CommonUtils.throwArgNull(type, "type");

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

    private Object getValueForRecordComponent(final String namePrefix, final RecordComponent component) {
        final String name = createPropertyName(namePrefix, component);
        final Class<?> valueType = component.getType();
        if (hasDefaultValue(component)) {
            if (Objects.equals(List.class, component.getType())) {
                final Class<?> genericType = getGenericListType(component);
                return configuration.getValues(name, genericType, getDefaultValues(component));
            }
            if (Objects.equals(Set.class, component.getType())) {
                final Class<?> genericType = getGenericSetType(component);
                return configuration.getValueSet(name, genericType, getDefaultValueSet(component));
            }
            return configuration.getValue(name, valueType, getDefaultValue(component));
        } else {
            if (Objects.equals(List.class, component.getType())) {
                final Class<?> genericType = getGenericListType(component);
                return configuration.getValues(name, genericType);
            }
            if (Objects.equals(Set.class, component.getType())) {
                final Class<?> genericType = getGenericSetType(component);
                return configuration.getValueSet(name, genericType);
            }
            return configuration.getValue(name, valueType);
        }
    }

    private static boolean isGenericType(@NonNull final RecordComponent component, @NonNull final Class<?> type) {
        throwArgNull(component, "component");
        throwArgNull(type, "type");
        final ParameterizedType stringSetType = (ParameterizedType) component.getGenericType();
        return Objects.equals(type, stringSetType.getRawType());
    }

    private static <T> Class<T> getGenericSetType(@NonNull final RecordComponent component) {
        if (!isGenericType(component, Set.class)) {
            throw new IllegalArgumentException("Only Set interface is supported");
        }
        return (Class<T>) ConfigReflectionUtils.getSingleGenericTypeArgument(
                (ParameterizedType) component.getGenericType());
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> getGenericListType(final RecordComponent component) {
        if (!isGenericType(component, List.class)) {
            throw new IllegalArgumentException("Only List interface is supported");
        }
        return (Class<T>) ConfigReflectionUtils.getSingleGenericTypeArgument(
                (ParameterizedType) component.getGenericType());
    }

    private <T> Set<T> getDefaultValueSet(final RecordComponent component) {
        CommonUtils.throwArgNull(component, "component");
        final Class<?> type = getGenericSetType(component);
        final String rawValue = getRawValue(component);
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (Set<T>) ConfigListUtils.createList(rawValue).stream()
                .map(value -> converterService.convert(value, type))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getDefaultValues(final RecordComponent component) {
        CommonUtils.throwArgNull(component, "component");
        final Class<?> type = getGenericListType(component);
        final String rawValue = getRawValue(component);
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (List<T>) ConfigListUtils.createList(rawValue).stream()
                .map(value -> converterService.convert(value, type))
                .toList();
    }

    private String getRawValue(final RecordComponent component) {
        final Optional<String> rawDefaultValue = getRawDefaultValue(component);
        if (rawDefaultValue.isEmpty()) {
            throw new IllegalArgumentException("Default value not defined for parameter");
        }
        final String rawValue = rawDefaultValue.get();
        return rawValue;
    }

    private static <T extends Record> String getNamePrefix(final Class<T> type) {
        return Optional.ofNullable(type.getAnnotation(ConfigData.class))
                .map(ConfigData::value)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private <T> T getDefaultValue(final RecordComponent component) {
        final String rawValue = getRawValue(component);
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (T) converterService.convert(rawValue, component.getType());
    }

    private static Optional<String> getRawDefaultValue(final RecordComponent component) {
        return Optional.ofNullable(component.getAnnotation(ConfigProperty.class))
                .map(ConfigProperty::defaultValue)
                .filter(defaultValue -> !Objects.equals(ConfigProperty.UNDEFINED_DEFAULT_VALUE, defaultValue));
    }

    private static boolean hasDefaultValue(final RecordComponent component) {
        return Optional.ofNullable(component.getAnnotation(ConfigProperty.class))
                .map(propertyAnnotation ->
                        !Objects.equals(ConfigProperty.UNDEFINED_DEFAULT_VALUE, propertyAnnotation.defaultValue()))
                .orElse(false);
    }

    private static String createPropertyName(final String prefix, final RecordComponent component) {
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

    private static String createPropertyName(final String prefix, final String name) {
        if (prefix.isBlank()) {
            return name;
        }
        return prefix + "." + name;
    }
}
