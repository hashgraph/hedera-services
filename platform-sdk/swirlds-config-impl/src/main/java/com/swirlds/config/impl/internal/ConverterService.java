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
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.impl.converters.BigDecimalConverter;
import com.swirlds.config.impl.converters.BigIntegerConverter;
import com.swirlds.config.impl.converters.BooleanConverter;
import com.swirlds.config.impl.converters.ByteConverter;
import com.swirlds.config.impl.converters.ChronoUnitConverter;
import com.swirlds.config.impl.converters.DoubleConverter;
import com.swirlds.config.impl.converters.DurationConverter;
import com.swirlds.config.impl.converters.FileConverter;
import com.swirlds.config.impl.converters.FloatConverter;
import com.swirlds.config.impl.converters.IntegerConverter;
import com.swirlds.config.impl.converters.LongConverter;
import com.swirlds.config.impl.converters.PathConverter;
import com.swirlds.config.impl.converters.ShortConverter;
import com.swirlds.config.impl.converters.StringConverter;
import com.swirlds.config.impl.converters.UriConverter;
import com.swirlds.config.impl.converters.UrlConverter;
import com.swirlds.config.impl.converters.ZonedDateTimeConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class ConverterService implements ConfigLifecycle {

    private final Map<Class<?>, ConfigConverter<?>> converters;

    private boolean initialized = false;

    /**
     * constant converter for strings
     */
    private static final ConfigConverter<String> STRING_CONVERTER = new StringConverter();

    private static final ConfigConverter<Integer> INTEGER_CONVERTER = new IntegerConverter();

    private static final ConfigConverter<Long> LONG_CONVERTER = new LongConverter();

    private static final ConfigConverter<Double> DOUBLE_CONVERTER = new DoubleConverter();

    private static final ConfigConverter<Float> FLOAT_CONVERTER = new FloatConverter();

    private static final ConfigConverter<Short> SHORT_CONVERTER = new ShortConverter();

    private static final ConfigConverter<Byte> BYTE_CONVERTER = new ByteConverter();

    private static final ConfigConverter<Boolean> BOOLEAN_CONVERTER = new BooleanConverter();

    private static final ConfigConverter<BigDecimal> BIG_DECIMAL_CONVERTER = new BigDecimalConverter();

    private static final ConfigConverter<BigInteger> BIG_INTEGER_CONVERTER = new BigIntegerConverter();

    private static final ConfigConverter<URL> URL_CONVERTER = new UrlConverter();

    private static final ConfigConverter<URI> URI_CONVERTER = new UriConverter();

    private static final ConfigConverter<Path> PATH_CONVERTER = new PathConverter();

    private static final ConfigConverter<File> FILE_CONVERTER = new FileConverter();

    private static final ConfigConverter<ZonedDateTime> ZONED_DATE_TIME_CONVERTER = new ZonedDateTimeConverter();

    private static final ConfigConverter<Duration> DURATION_CONVERTER = new DurationConverter();

    private static final ConfigConverter<ChronoUnit> CHRONO_UNIT_CONVERTER = new ChronoUnitConverter();

    ConverterService() {
        this.converters = new HashMap<>();
    }

    @NonNull
    private <T, C extends ConfigConverter<T>> Class<T> getConverterType(@NonNull final Class<C> converterClass) {
        ArgumentUtils.throwArgNull(converterClass, "converterClass");
        return Arrays.stream(converterClass.getGenericInterfaces())
                .filter(ParameterizedType.class::isInstance)
                .map(ParameterizedType.class::cast)
                .filter(parameterizedType -> Objects.equals(ConfigConverter.class, parameterizedType.getRawType()))
                .map(ParameterizedType::getActualTypeArguments)
                .findAny()
                .map(typeArguments -> {
                    if (typeArguments.length != 1) {
                        throw new IllegalStateException("Can not extract generic type for converter " + converterClass);
                    }
                    return (Class<T>) typeArguments[0];
                })
                .orElseGet(() -> getConverterType((Class<C>) converterClass.getSuperclass()));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    <T> T convert(@Nullable final String value, @NonNull final Class<T> targetClass) {
        throwIfNotInitialized();
        ArgumentUtils.throwArgNull(targetClass, "targetClass");
        if (value == null) {
            return null;
        }
        if (Objects.equals(targetClass, String.class)) {
            return (T) value;
        }
        final ConfigConverter<T> converter = (ConfigConverter<T>) converters.get(targetClass);
        if (converter == null) {
            throw new IllegalArgumentException("No converter defined for type '" + targetClass + "'");
        }
        try {
            return converter.convert(value);
        } catch (final Exception ex) {
            throw new IllegalArgumentException("Can not convert to '" + targetClass + "'", ex);
        }
    }

    <T> void addConverter(@NonNull final ConfigConverter<T> converter) {
        throwIfInitialized();
        ArgumentUtils.throwArgNull(converter, "converter");
        final Class<T> converterType = getConverterType(converter.getClass());
        add(converterType, converter);
    }

    private <T> void add(@NonNull final Class<T> converterType, @NonNull final ConfigConverter<T> converter) {
        throwIfInitialized();
        ArgumentUtils.throwArgNull(converterType, "converterType");
        ArgumentUtils.throwArgNull(converter, "converter");

        if (converters.containsKey(converterType)) {
            throw new IllegalStateException("Converter for type '" + converterType + "' already registered");
        }
        this.converters.put(converterType, converter);
    }

    void clear() {
        this.converters.clear();
    }

    @Override
    public void init() {
        throwIfInitialized();
        // Primitives
        add(Integer.TYPE, INTEGER_CONVERTER);
        add(Long.TYPE, LONG_CONVERTER);
        add(Double.TYPE, DOUBLE_CONVERTER);
        add(Float.TYPE, FLOAT_CONVERTER);
        add(Short.TYPE, SHORT_CONVERTER);
        add(Byte.TYPE, BYTE_CONVERTER);
        add(Boolean.TYPE, BOOLEAN_CONVERTER);

        add(String.class, STRING_CONVERTER);
        add(Integer.class, INTEGER_CONVERTER);
        add(Long.class, LONG_CONVERTER);
        add(Double.class, DOUBLE_CONVERTER);
        add(Float.class, FLOAT_CONVERTER);
        add(Short.class, SHORT_CONVERTER);
        add(Byte.class, BYTE_CONVERTER);
        add(Boolean.class, BOOLEAN_CONVERTER);
        add(BigDecimal.class, BIG_DECIMAL_CONVERTER);
        add(BigInteger.class, BIG_INTEGER_CONVERTER);
        add(URL.class, URL_CONVERTER);
        add(URI.class, URI_CONVERTER);
        add(Path.class, PATH_CONVERTER);
        add(File.class, FILE_CONVERTER);
        add(ZonedDateTime.class, ZONED_DATE_TIME_CONVERTER);
        add(Duration.class, DURATION_CONVERTER);
        add(ChronoUnit.class, CHRONO_UNIT_CONVERTER);
        initialized = true;
    }

    @Override
    public void dispose() {
        this.converters.clear();
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    <T> ConfigConverter<T> getConverterForType(@NonNull final Class<T> valueType) {
        throwIfNotInitialized();
        ArgumentUtils.throwArgNull(valueType, "valueType");
        return (ConfigConverter<T>) converters.get(valueType);
    }
}
