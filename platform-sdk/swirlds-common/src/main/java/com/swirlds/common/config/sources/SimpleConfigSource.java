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

package com.swirlds.common.config.sources;

import static com.swirlds.common.config.sources.ConfigSourceOrdinalConstants.PROGRAMMATIC_VALUES_ORDINAL;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} implementation that can be used to provide values of properties
 * programmatically by calling {@link #withValue(String, Long)} (String, String)}.
 */
public final class SimpleConfigSource extends AbstractConfigSource {

    private final Map<String, String> internalProperties;

    private int oridinal = PROGRAMMATIC_VALUES_ORDINAL;

    /**
     * Creates an instance without any config properties
     */
    public SimpleConfigSource() {
        this.internalProperties = new HashMap<>();
    }

    /**
     * Creates an instance without any config properties
     */
    public SimpleConfigSource(@NonNull final Map<String, String> properties) {
        // defensive copy
        this.internalProperties = new HashMap<>(Objects.requireNonNull(properties));
    }

    /**
     * Creates an instance and directly adds the given config property
     *
     * @param propertyName name of the config property
     * @param value        value of the config property
     */
    public SimpleConfigSource(final String propertyName, final String value) {
        this();
        withValue(propertyName, value);
    }

    /**
     * Creates an instance and directly adds the given config property
     *
     * @param propertyName name of the config property
     * @param value        value of the config property
     */
    public SimpleConfigSource(final String propertyName, final Integer value) {
        this();
        withValue(propertyName, value);
    }

    /**
     * Creates an instance and directly adds the given config property
     *
     * @param propertyName name of the config property
     * @param value        value of the config property
     */
    public SimpleConfigSource(final String propertyName, final Long value) {
        this();
        withValue(propertyName, value);
    }

    /**
     * Creates an instance and directly adds the given config property
     *
     * @param propertyName name of the config property
     * @param value        value of the config property
     */
    public SimpleConfigSource(final String propertyName, final Double value) {
        this();
        withValue(propertyName, value);
    }

    /**
     * Creates an instance and directly adds the given config property
     *
     * @param propertyName name of the config property
     * @param value        value of the config property
     */
    public SimpleConfigSource(final String propertyName, final Float value) {
        this();
        withValue(propertyName, value);
    }

    /**
     * Creates an instance and directly adds the given config property
     *
     * @param propertyName name of the config property
     * @param value        value of the config property
     */
    public SimpleConfigSource(final String propertyName, final Boolean value) {
        this();
        withValue(propertyName, value);
    }

    /**
     * Adds a string value to this source
     *
     * @param propertyName name of the property
     * @param value        default value
     */
    public SimpleConfigSource withValue(final String propertyName, final String value) {
        setValue(propertyName, value, v -> v);
        return this;
    }

    /**
     * Adds an int value to this source
     *
     * @param propertyName name of the peoprty
     * @param value        default value
     */
    public SimpleConfigSource withValue(final String propertyName, final Integer value) {
        setValue(propertyName, value, v -> Integer.toString(v));
        return this;
    }

    /**
     * Adds a double value to this source
     *
     * @param propertyName name of the peoprty
     * @param value        default value
     */
    public SimpleConfigSource withValue(final String propertyName, final Double value) {
        setValue(propertyName, value, v -> Double.toString(v));
        return this;
    }

    /**
     * Adds a float value to this source
     *
     * @param propertyName name of the peoprty
     * @param value        default value
     */
    public SimpleConfigSource withValue(final String propertyName, final Float value) {
        setValue(propertyName, value, v -> Float.toString(v));
        return this;
    }

    /**
     * Adds a long value to this source
     *
     * @param propertyName name of the peoprty
     * @param value        default value
     */
    public SimpleConfigSource withValue(final String propertyName, final Long value) {
        setValue(propertyName, value, v -> Long.toString(v));
        return this;
    }

    /**
     * Adds a boolean value to this source
     *
     * @param propertyName name of the peoprty
     * @param value        default value
     */
    public SimpleConfigSource withValue(final String propertyName, final Boolean value) {
        setValue(propertyName, value, v -> Boolean.toString(v));
        return this;
    }

    private <T> void setValue(final String propertyName, final T value, Function<T, String> converter) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(converter, "converter");
        internalProperties.put(
                propertyName, Optional.ofNullable(value).map(converter::apply).orElse(null));
    }

    /**
     * Adds a list value to this source
     *
     * @param propertyName name of the property
     * @param values       default values list
     */
    public SimpleConfigSource withBooleanValues(final String propertyName, final List<Boolean> values) {
        setValues(propertyName, values, v -> Boolean.toString(v));
        return this;
    }

    /**
     * Adds a list value to this source
     *
     * @param propertyName name of the property
     * @param values       default values list
     */
    public SimpleConfigSource withIntegerValues(final String propertyName, final List<Integer> values) {
        setValues(propertyName, values, v -> Integer.toString(v));
        return this;
    }

    /**
     * Adds a list value to this source
     *
     * @param propertyName name of the property
     * @param values       default values list
     */
    public SimpleConfigSource withLongValues(final String propertyName, final List<Long> values) {
        setValues(propertyName, values, v -> Long.toString(v));
        return this;
    }

    /**
     * Adds a list value to this source
     *
     * @param propertyName name of the property
     * @param values       default values list
     */
    public SimpleConfigSource withDoubleValues(final String propertyName, final List<Double> values) {
        setValues(propertyName, values, v -> Double.toString(v));
        return this;
    }

    /**
     * Adds a list value to this source
     *
     * @param propertyName name of the property
     * @param values       default values list
     */
    public SimpleConfigSource withStringValues(final String propertyName, final List<String> values) {
        setValues(propertyName, values, v -> v);
        return this;
    }

    private <T> void setValues(final String propertyName, final List<T> values, Function<T, String> converter) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(converter, "converter");
        if (values == null) {
            internalProperties.put(propertyName, null);
        } else if (values.isEmpty()) {
            internalProperties.put(propertyName, Configuration.EMPTY_LIST);
        } else {
            String rawValues = values.stream().map(converter::apply).collect(Collectors.joining(","));
            internalProperties.put(propertyName, rawValues);
        }
    }

    /**
     * Specify the ordinal of this source. Default is {@link ConfigSourceOrdinalConstants#PROGRAMMATIC_VALUES_ORDINAL}.
     *
     * @param ordinal the ordinal
     * @return this
     */
    public SimpleConfigSource withOrdinal(final int ordinal) {
        this.oridinal = ordinal;
        return this;
    }

    /**
     * Removes all default properties
     */
    public void reset() {
        internalProperties.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, String> getInternalProperties() {
        return Collections.unmodifiableMap(internalProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return oridinal;
    }
}
