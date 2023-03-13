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
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import java.util.EnumSet;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code IntegerAccumulator} accumulates an {@code int}-value.
 * <p>
 * It is reset in regular intervals. The exact timing depends on the implementation.
 * <p>
 * An {@code IntegerAccumulator} is reset to the {@link #getInitialValue() initialValue}.
 * If no {@code initialValue} was specified, the {@code IntegerAccumulator} is reset to {@code 0}.
 */
public interface IntegerAccumulator extends Metric {

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
        return DataType.INT;
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
    default Integer get(final ValueType valueType) {
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
    int get();

    /**
     * Returns the {@code initialValue} of the {@code IntegerAccumulator}
     *
     * @return the initial value
     */
    int getInitialValue();

    /**
     * Atomically updates the current value with the results of applying the {@code accumulator}-function of this
     * {@code IntegerAccumulator} to the current and given value.
     * <p>
     * The function is applied with the current value as its first argument, and the provided {@code other} as the
     * second argument.
     *
     * @param other
     * 		the second parameter
     */
    void update(final int other);

    /**
     * Configuration of an {@link IntegerAccumulator}
     */
    final class Config extends MetricConfig<IntegerAccumulator, IntegerAccumulator.Config> {

        private final IntBinaryOperator accumulator;
        private final IntSupplier initializer;

        private final int initialValue;

        /**
         * Constructor of {@code IntegerGauge.Config}
         *
         * By default, the {@link #getAccumulator() accumulator} is set to {@code Integer::max},
         * the {@link #getInitialValue() initialValue} is set to {@code 0},
         * and {@link #getFormat() format} is set to {@code "%d"}.
         *
         * @param category
         * 		the kind of metric (metrics are grouped or filtered by this)
         * @param name
         * 		a short name for the metric
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, "%d");
            this.accumulator = Integer::max;
            this.initializer = null;
            this.initialValue = 0;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final IntBinaryOperator accumulator,
                final IntSupplier initializer,
                final int initialValue) {

            super(category, name, description, unit, format);
            this.accumulator = throwArgNull(accumulator, "accumulator");
            this.initializer = initializer;
            this.initialValue = initialValue;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public IntegerAccumulator.Config withDescription(final String description) {
            return new IntegerAccumulator.Config(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getAccumulator(),
                    getInitializer(),
                    getInitialValue());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public IntegerAccumulator.Config withUnit(final String unit) {
            return new IntegerAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    unit,
                    getFormat(),
                    getAccumulator(),
                    getInitializer(),
                    getInitialValue());
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
        public IntegerAccumulator.Config withFormat(final String format) {
            return new IntegerAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    format,
                    getAccumulator(),
                    getInitializer(),
                    getInitialValue());
        }

        /**
         * Getter of the {@code accumulator}
         *
         * @return the accumulator
         */
        public IntBinaryOperator getAccumulator() {
            return accumulator;
        }

        /**
         * Fluent-style setter of the accumulator.
         * <p>
         * The accumulator should be side-effect-free, since it may be re-applied when attempted updates fail
         * due to contention among threads.
         *
         * @param accumulator
         * 		The {@link IntBinaryOperator} that is used to accumulate the value.
         * @return a new configuration-object with updated {@code initialValue}
         */
        public IntegerAccumulator.Config withAccumulator(final IntBinaryOperator accumulator) {
            return new IntegerAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    accumulator,
                    getInitializer(),
                    getInitialValue());
        }

        /**
         * Getter of the {@code initializer}
         *
         * @return the initializer
         */
        public IntSupplier getInitializer() {
            return initializer;
        }

        /**
         * Fluent-style setter of the initial value.
         * <p>
         * If both {@code initializer} and {@code initialValue} are set, the {@code initialValue} is ignored
         *
         * @param initializer
         * 		the initializer
         * @return a new configuration-object with updated {@code initializer}
         */
        public IntegerAccumulator.Config withInitializer(final IntSupplier initializer) {
            return new IntegerAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getAccumulator(),
                    throwArgNull(initializer, "initializer"),
                    getInitialValue());
        }

        /**
         * Getter of the initial value
         *
         * @return the initial value
         */
        public int getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue
         * 		the initial value
         * @return a new configuration-object with updated {@code initialValue}
         */
        public IntegerAccumulator.Config withInitialValue(final int initialValue) {
            return new IntegerAccumulator.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getAccumulator(),
                    getInitializer(),
                    initialValue);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<IntegerAccumulator> getResultClass() {
            return IntegerAccumulator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        IntegerAccumulator create(final MetricsFactory factory) {
            return factory.createIntegerAccumulator(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .appendSuper(super.toString())
                    .append("initialValue", initializer != null ? initializer.getAsInt() : initialValue)
                    .toString();
        }
    }
}
