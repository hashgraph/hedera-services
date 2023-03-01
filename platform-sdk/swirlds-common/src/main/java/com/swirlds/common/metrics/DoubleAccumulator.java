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

import static com.swirlds.common.metrics.FloatFormats.FORMAT_11_3;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.IntegerAccumulator.ConfigBuilder;
import java.util.EnumSet;
import java.util.function.DoubleBinaryOperator;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A {@code DoubleAccumulator} accumulates a {@code double}-value.
 * <p>
 * It is reset in regular intervals. The exact timing depends on the implementation.
 * <p>
 * A {@code DoubleAccumulator} is reset to the {@link #getInitialValue() initialValue}.
 * If no {@code initialValue} was specified, the {@code DoubleAccumulator} is reset to {@code 0.0}.
 */
public non-sealed interface DoubleAccumulator extends BaseMetric {

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
        return DataType.FLOAT;
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
    default Double get(final ValueType valueType) {
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
    double get();

    /**
     * Returns the {@code initialValue} of the {@code DoubleAccumulator}
     *
     * @return the initial value
     */
    double getInitialValue();

    /**
     * Atomically updates the current value with the results of applying the {@code accumulator}-function of this
     * {@code DoubleAccumulator} to the current and given value.
     * <p>
     * The function is applied with the current value as its first argument, and the provided {@code other} as the
     * second argument.
     *
     * @param other
     * 		the second parameter
     */
    void update(final double other);

    /**
     * Configuration of a {@link DoubleAccumulator}
     */
    record Config (
            String category,
            String name,
            String description,
            String unit,
            String format,
            DoubleBinaryOperator accumulator,
            double initialValue
    ) implements MetricConfig<DoubleAccumulator> {

        /**
         * Constructor of {@code Counter.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param accumulator the accumulator-function
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
         * Constructor of {@code DoubleAccumulator.Config}
         *
         * By default, the {@link #accumulator} is set to {@code Double::max},
         * the {@link #initialValue} is set to {@code 0.0},
         * and {@link #format} is set to {@link FloatFormats#FORMAT_11_3}.
         *
         * @param category
         * 		the kind of metric (metrics are grouped or filtered by this)
         * @param name
         * 		a short name for the metric
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            this(category, name, name, "", FORMAT_11_3, Double::max, 0.0);
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
        public DoubleAccumulator.Config withDescription(final String description) {
            return new DoubleAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
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
        public DoubleAccumulator.Config withUnit(final String unit) {
            return new DoubleAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
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
        public DoubleAccumulator.Config withFormat(final String format) {
            return new DoubleAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * Getter of the {@code accumulator}
         *
         * @return the accumulator
         * @deprecated Please use {@link #accumulator()} instead
         */
        @Deprecated(forRemoval = true)
        public DoubleBinaryOperator getAccumulator() {
            return accumulator;
        }

        /**
         * Fluent-style setter of the accumulator.
         * <p>
         * The accumulator should be side-effect-free, since it may be re-applied when attempted updates fail
         * due to contention among threads.
         *
         * @param accumulator
         * 		The {@link DoubleBinaryOperator} that is used to accumulate the value.
         * @return a new configuration-object with updated {@code initialValue}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public DoubleAccumulator.Config withAccumulator(final DoubleBinaryOperator accumulator) {
            return new DoubleAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * Getter of the initial value
         *
         * @return the initial value
         * @deprecated Please use {@link #initialValue()} instead
         */
        @Deprecated(forRemoval = true)
        public double getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue
         * 		the initial value
         * @return a new configuration-object with updated {@code initialValue}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public DoubleAccumulator.Config withInitialValue(final double initialValue) {
            return new DoubleAccumulator.Config(category, name, description, unit, format, accumulator, initialValue);
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated this feature will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<DoubleAccumulator> getResultClass() {
            return DoubleAccumulator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DoubleAccumulator create(final MetricsFactory factory) {
            return factory.createDoubleAccumulator(this);
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
