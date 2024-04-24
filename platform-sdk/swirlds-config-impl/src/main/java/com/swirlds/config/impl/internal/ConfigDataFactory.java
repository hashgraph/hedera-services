/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.DefaultValue;
import com.swirlds.config.extensions.reflection.ConfigReflectionUtils;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Internal factory for config data objects. See {@link Configuration#getConfigData(Class)} for a detailed description
 * on config data objects.
 */
class ConfigDataFactory {

    /**
     * The configuration that is internally used to fill the properties of the config data instances.
     */
    private final Configuration configuration;

    /**
     * The converter service that is used to convert raw values from the config to custom data types.
     */
    private final ConverterService converterService;

    ConfigDataFactory(@NonNull final Configuration configuration, @NonNull final ConverterService converterService) {
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.converterService = Objects.requireNonNull(converterService, "converterService must not be null");
    }

    @SuppressWarnings("unchecked")
    @NonNull
    <T extends Record> T createConfigInstance(@NonNull final Class<T> type)
            throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Objects.requireNonNull(type, "type must not be null");

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
    private Object getValueForRecordComponent(
            @NonNull final String namePrefix, @NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");
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
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(type, "type must not be null");
        final ParameterizedType stringSetType = (ParameterizedType) component.getGenericType();
        return Objects.equals(type, stringSetType.getRawType());
    }

    private static <T> Class<T> getGenericSetType(@NonNull final RecordComponent component) {
        if (!isGenericType(component, Set.class)) {
            throw new IllegalArgumentException("Only Set interface is supported");
        }
        return (Class<T>)
                ConfigReflectionUtils.getSingleGenericTypeArgument((ParameterizedType) component.getGenericType());
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private static <T> Class<T> getGenericListType(@NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");
        if (!isGenericType(component, List.class)) {
            throw new IllegalArgumentException("Only List interface is supported");
        }
        final Class<T> cls = (Class<T>)
                ConfigReflectionUtils.getSingleGenericTypeArgument((ParameterizedType) component.getGenericType());
        if (cls == null) {
            throw new IllegalArgumentException("No generic class found!");
        }
        return cls;
    }

    @Nullable
    private <T> Set<T> getDefaultValueSet(@NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");
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
    @Nullable
    private <T> List<T> getDefaultValues(@NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");
        final Class<?> type = getGenericListType(component);
        final String rawValue = getRawValue(component);
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (List<T>) ConfigListUtils.createList(rawValue).stream()
                .map(value -> converterService.convert(value, type))
                .toList();
    }

    @NonNull
    private String getRawValue(@NonNull final RecordComponent component) {
        final Optional<String> rawDefaultValue = getRawDefaultValue(component);
        if (rawDefaultValue.isEmpty()) {
            throw new IllegalArgumentException("Default value not defined for parameter");
        }
        return rawDefaultValue.get();
    }

    @NonNull
    private static <T extends Record> String getNamePrefix(@NonNull final Class<T> type) {
        Objects.requireNonNull(type, "type must not be null");
        return Optional.ofNullable(type.getAnnotation(ConfigData.class))
                .map(ConfigData::value)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T> T getDefaultValue(@NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");
        final String rawValue = getRawValue(component);
        if (Objects.equals(ConfigProperty.NULL_DEFAULT_VALUE, rawValue)) {
            return null;
        }
        return (T) converterService.convert(rawValue, component.getType());
    }

    @NonNull
    private static Optional<String> getRawDefaultValue(@NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");

        final ConfigProperty configPropertyAnnotation = component.getAnnotation(ConfigProperty.class);
        final DefaultValue defaultValueAnnotation = component.getAnnotation(DefaultValue.class);
        verifyAtMostOneConfigAnnotation(component, configPropertyAnnotation, defaultValueAnnotation);

        if (configPropertyAnnotation != null && isDefaultDefined(configPropertyAnnotation.defaultValue())) {
            return Optional.of(configPropertyAnnotation.defaultValue());
        } else if (defaultValueAnnotation != null && isDefaultDefined(defaultValueAnnotation.value())) {
            return Optional.of(defaultValueAnnotation.value());
        } else {
            return Optional.empty();
        }
    }

    private static boolean hasDefaultValue(@NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");

        final ConfigProperty configPropertyAnnotation = component.getAnnotation(ConfigProperty.class);
        final DefaultValue defaultValueAnnotation = component.getAnnotation(DefaultValue.class);
        verifyAtMostOneConfigAnnotation(component, configPropertyAnnotation, defaultValueAnnotation);

        if (configPropertyAnnotation != null) {
            return isDefaultDefined(configPropertyAnnotation.defaultValue());
        } else if (defaultValueAnnotation != null) {
            return isDefaultDefined(defaultValueAnnotation.value());
        } else {
            return false;
        }
    }

    /**
     * Check if the default value is defined or if it should be considered to be undefined.
     *
     * @param defaultValue the default value to check
     * @return true if the default value is defined, false otherwise
     */
    private static boolean isDefaultDefined(@Nullable final String defaultValue) {
        return !Objects.equals(ConfigProperty.UNDEFINED_DEFAULT_VALUE, defaultValue);
    }

    /**
     * Verify that only one of the two config annotations is present on the record component.
     *
     * @param component                the record component to verify
     * @param configPropertyAnnotation the config property annotation, null if not present
     * @param defaultValueAnnotation  the config default annotation, null if not present
     */
    private static void verifyAtMostOneConfigAnnotation(
            @NonNull final RecordComponent component,
            @Nullable final ConfigProperty configPropertyAnnotation,
            @Nullable final DefaultValue defaultValueAnnotation) {
        if (configPropertyAnnotation != null && defaultValueAnnotation != null) {
            throw new IllegalArgumentException(
                    "Can not have both @ConfigProperty and @DefaultValue annotations on the same record component: "
                            + component.getDeclaringRecord().getName() + "." + component.getName());
        }
    }

    @NonNull
    private static String createPropertyName(@NonNull final String prefix, @NonNull final RecordComponent component) {
        Objects.requireNonNull(component, "component must not be null");
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
        Objects.requireNonNull(prefix, "prefix must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (prefix.isBlank()) {
            return name;
        }
        return prefix + "." + name;
    }
}
