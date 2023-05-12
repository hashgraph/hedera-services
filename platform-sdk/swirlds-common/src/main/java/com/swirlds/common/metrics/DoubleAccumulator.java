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
import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

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
public interface DoubleAccumulator extends Metric {

    /**
     * {@inheritDoc}
     */
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
     */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /**
     * {@inheritDoc}
     */
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
    final class Config extends MetricConfig<DoubleAccumulator, DoubleAccumulator.Config> {

        private final DoubleBinaryOperator accumulator;
        private final double initialValue;

        /**
         * Constructor of {@code DoubleAccumulator.Config}
         *
         * By default, the {@link #getAccumulator() accumulator} is set to {@code Double::max},
         * the {@link #getInitialValue() initialValue} is set to {@code 0.0},
         * and {@link #getFormat() format} is set to {@link FloatFormats#FORMAT_11_3}.
         *
         * @param category
         * 		the kind of metric (metrics are grouped or filtered by this)
         * @param name
         * 		a short name for the metric
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, FORMAT_11_3);
            this.accumulator = Double::max;
            this.initialValue = 0.0;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final DoubleBinaryOperator accumulator,
                final double initialValue) {

            super(category, name, description, unit, format);
            this.accumulator = throwArgNull(accumulator, "accumulator");
            this.initialValue = initialValue;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DoubleAccumulator.Config withDescription(final String description) {
            return new DoubleAccumulator.Config(
                    getCategory(), getName(), description, getUnit(), getFormat(), getAccumulator(), getInitialValue());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DoubleAccumulator.Config withUnit(final String unit) {
            return new DoubleAccumulator.Config(
                    getCategory(), getName(), getDescription(), unit, getFormat(), getAccumulator(), getInitialValue());
        }

        /**
         * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
         *
         * @param format
         * 		the format-string
         * @return a new configuration-object with updated {@code format}
         * @throws IllegalArgumentException
         * 		if {@code format} is {@code null} or consists only of whitespaces
         */
        public DoubleAccumulator.Config withFormat(final String format) {
            return new DoubleAccumulator.Config(
                    getCategory(), getName(), getDescription(), getUnit(), format, getAccumulator(), getInitialValue());
        }

        /**
         * Getter of the {@code accumulator}
         *
         * @return the accumulator
         */
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
         */
        public DoubleAccumulator.Config withAccumulator(final DoubleBinaryOperator accumulator) {
            return new DoubleAccumulator.Config(
                    getCategory(), getName(), getDescription(), getUnit(), getFormat(), accumulator, getInitialValue());
        }

        /**
         * Getter of the initial value
         *
         * @return the initial value
         */
        public double getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue
         * 		the initial value
         * @return a new configuration-object with updated {@code initialValue}
         */
        public DoubleAccumulator.Config withInitialValue(final double initialValue) {
            return new DoubleAccumulator.Config(
                    getCategory(), getName(), getDescription(), getUnit(), getFormat(), getAccumulator(), initialValue);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<DoubleAccumulator> getResultClass() {
            return DoubleAccumulator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        DoubleAccumulator create(final MetricsFactory factory) {
            return factory.createDoubleAccumulator(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                    .appendSuper(super.toString())
                    .append("initialValue", initialValue)
                    .toString();
        }
    }
}
