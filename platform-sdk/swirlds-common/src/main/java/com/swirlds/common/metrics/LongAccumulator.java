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

package com.swirlds.common.metrics;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.IntegerAccumulator.ConfigBuilder;
import java.util.EnumSet;
import java.util.function.LongBinaryOperator;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code LongAccumulator} accumulates a {@code long}-value.
 * <p>
 * It is reset in regular intervals. The exact timing depends on the implementation.
 * <p>
 * A {@code LongAccumulator} is reset to the {@link #getInitialValue() initialValue}. If no {@code initialValue} was
 * specified, the {@code LongAccumulator} is reset to {@code 0L}.
 */
public non-sealed interface LongAccumulator extends BaseMetric {

    /**
     * {@inheritDoc}
     *
     * @deprecated {@link MetricType} turned out to be too limited. You can use the class-name instead.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    default MetricType getMetricType() {
        return MetricType.ACCUMULATOR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default DataType getDataType() {
        return DataType.INT;
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated {@code ValueType} turned out to be too limited. You can get sub-metrics via
     * {@link #getBaseMetrics()}.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated {@code ValueType} turned out to be too limited. You can get sub-metrics via
     * {@link #getBaseMetrics()}.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    default Long get(final ValueType valueType) {
        throwArgNull(valueType, "valueType");
        if (valueType == VALUE) {
            return get();
        }
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    /**
     * Get the current value
     *
     * @return the current value
     */
    long get();

    /**
     * Returns the {@code initialValue} of the {@code LongAccumulator}
     *
     * @return the initial value
     */
    long getInitialValue();

    /**
     * Atomically updates the current value with the results of applying the {@code operator} of this
     * {@code LongAccumulator} to the current and given value.
     * <p>
     * The function is applied with the current value as its first argument, and the provided {@code other} as the
     * second argument.
     *
     * @param other the update value
     */
    void update(final long other);

    /**
     * Configuration of a {@link LongAccumulator}
     */
    record Config(
            String category,
            String name,
            String description,
            String unit,
            String format,
            LongBinaryOperator accumulator,
            long initialValue

    ) implements MetricConfig<LongAccumulator> {

        /**
         * Constructor of {@code LongAccumulator.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param accumulator the accumulator of the metric
         * @param initialValue the initial value of the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         * (except for {@code unit} which can be empty)
         */
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
            throwArgNull(accumulator, "accumulator");
        }

        /**
         * Constructor of {@code LongAccumulator.Config}
         * <p>
         * By default, the {@link #accumulator} is set to {@code Long::max}, the {@link #initialValue} is set to
         * {@code 0L}, and {@link #format} is set to {@code "%d"}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            this(category, name, name, "", "%d", Long::max, 0L);
        }

        /**
         * Sets the {@link Metric#getDescription() Metric.description} in fluent style.
         *
         * @param description the description
         * @return a new configuration-object with updated {@code description}
         * @throws IllegalArgumentException if {@code description} is {@code null}, too long or consists only of whitespaces
         * @deprecated Please use {@link ConfigBuilder} instead.
         */
        @Deprecated(forRemoval = true)
        public LongAccumulator.Config withDescription(final String description) {
            return new LongAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * Sets the {@link Metric#getUnit() Metric.unit} in fluent style.
         *
         * @param unit the unit
         * @return a new configuration-object with updated {@code unit}
         * @throws IllegalArgumentException if {@code unit} is {@code null}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public LongAccumulator.Config withUnit(final String unit) {
            return new LongAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
         *
         * @param format the format-string
         * @return a new configuration-object with updated {@code format}
         * @throws IllegalArgumentException if {@code format} is {@code null} or consists only of whitespaces
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public LongAccumulator.Config withFormat(final String format) {
            return new LongAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * Getter of the {@code accumulator}
         *
         * @return the accumulator
         * @deprecated Please use {@link #accumulator()} instead
         */
        @Deprecated(forRemoval = true)
        public LongBinaryOperator getAccumulator() {
            return accumulator();
        }

        /**
         * Fluent-style setter of the accumulator.
         * <p>
         * The accumulator should be side-effect-free, since it may be re-applied when attempted updates fail due to
         * contention among threads.
         *
         * @param accumulator The {@link LongBinaryOperator} that is used to accumulate the value.
         * @return a new configuration-object with updated {@code initialValue}
         * @throws IllegalArgumentException if {@code accumulator} is {@code null}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public LongAccumulator.Config withAccumulator(final LongBinaryOperator accumulator) {
            return new LongAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * Getter of the {@link LongAccumulator#getInitialValue() initialValue}
         *
         * @return the initial value
         * @deprecated Please use {@link #initialValue()} instead
         */
        @Deprecated(forRemoval = true)
        public long getInitialValue() {
            return initialValue();
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue the initial value
         * @return a reference to {@code this}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public LongAccumulator.Config withInitialValue(final long initialValue) {
            return new LongAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<LongAccumulator> getResultClass() {
            return LongAccumulator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LongAccumulator create(final MetricsFactory factory) {
            return factory.createLongAccumulator(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("category", category)
                    .append("name", name)
                    .append("description", description)
                    .append("unit", unit)
                    .append("format", format)
                    .append("initialValue", initialValue)
                    .toString();
        }
    }
}
