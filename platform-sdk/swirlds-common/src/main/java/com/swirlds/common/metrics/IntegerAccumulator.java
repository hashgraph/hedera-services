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

import java.util.EnumSet;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code IntegerAccumulator} accumulates an {@code int}-value.
 * <p>
 * It is reset in regular intervals. The exact timing depends on the implementation.
 * <p>
 * An {@code IntegerAccumulator} is reset to the {@link #getInitialValue() initialValue}. If no {@code initialValue} was
 * specified, the {@code IntegerAccumulator} is reset to {@code 0}.
 */
public non-sealed interface IntegerAccumulator extends BaseMetric {

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
     * @param other the second parameter
     */
    void update(final int other);

    /**
     * Configuration of an {@link IntegerAccumulator}
     */
    record Config(
            String category,
            String name,
            String description,
            String unit,
            String format,
            IntBinaryOperator accumulator,
            IntSupplier initializer,
            int initialValue
    ) implements MetricConfig<IntegerAccumulator> {

        private static final IntSupplier DEFAULT_INITIALIZER = () -> 0;

        /**
         * Constructor of {@code IntegerAccumulator.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param accumulator the accumulator-function
         * @param initializer the initializer-function
         * @param initialValue the initial value
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
         * Constructor of {@code IntegerGauge.Config}
         * <p>
         * By default, the {@link #accumulator} is set to {@code Integer::max}, the {@link #initializer} is set to
         * always return {@code 0}, and {@link #format} is set to {@code "%d"}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            this(category, name, name, "", "%d", Integer::max, null, 0);
        }

        /**
         * Sets the {@link Metric#getDescription() Metric.description} in fluent style.
         *
         * @param description the description
         * @return a new configuration-object with updated {@code description}
         * @throws IllegalArgumentException if {@code description} is {@code null}, too long or consists only of
         * whitespaces
         * @deprecated Please use {@link ConfigBuilder} instead.
         */
        @Deprecated(forRemoval = true)
        public IntegerAccumulator.Config withDescription(final String description) {
            return new IntegerAccumulator.Config(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    accumulator,
                    initializer,
                    initialValue);
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
        public IntegerAccumulator.Config withUnit(final String unit) {
            return new IntegerAccumulator.Config(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    accumulator,
                    initializer,
                    initialValue);
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
        public IntegerAccumulator.Config withFormat(final String format) {
            return new IntegerAccumulator.Config(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    accumulator,
                    initializer,
                    initialValue);
        }

        /**
         * Getter of the {@code accumulator}
         *
         * @return the accumulator
         * @deprecated Please use {@link #accumulator()} instead
         */
        @Deprecated(forRemoval = true)
        public IntBinaryOperator getAccumulator() {
            return accumulator();
        }

        /**
         * Fluent-style setter of the accumulator.
         * <p>
         * The accumulator should be side-effect-free, since it may be re-applied when attempted updates fail due to
         * contention among threads.
         *
         * @param accumulator The {@link IntBinaryOperator} that is used to accumulate the value.
         * @return a new configuration-object with updated {@code initialValue}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerAccumulator.Config withAccumulator(final IntBinaryOperator accumulator) {
            return new IntegerAccumulator.Config(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    accumulator,
                    initializer,
                    initialValue);
        }

        /**
         * Getter of the {@code initializer}
         *
         * @return the initializer
         * @deprecated Please use {@link #initializer()} instead
         */
        @Deprecated(forRemoval = true)
        public IntSupplier getInitializer() {
            return initializer();
        }

        /**
         * Fluent-style setter of the initial value.
         * <p>
         * If both {@code initializer} and {@code initialValue} are set, the {@code initialValue} is ignored
         *
         * @param initializer the initializer
         * @return a new configuration-object with updated {@code initializer}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerAccumulator.Config withInitializer(final IntSupplier initializer) {
            throwArgNull(initializer, "initializer");
            return new IntegerAccumulator.Config(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    accumulator,
                    initializer,
                    initialValue);
        }

        /**
         * Getter of the initial value
         *
         * @return the initial value
         * @deprecated Please use {@link #initializer()} instead
         */
        @Deprecated(forRemoval = true)
        public int getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue the initial value
         * @return a new configuration-object with updated {@code initialValue}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerAccumulator.Config withInitialValue(final int initialValue) {
            return new IntegerAccumulator.Config(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    accumulator,
                    initializer,
                    initialValue);
        }

        /**
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<IntegerAccumulator> getResultClass() {
            return IntegerAccumulator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public IntegerAccumulator create(final MetricsFactory factory) {
            return factory.createIntegerAccumulator(this);
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
                    .append("initialValue", initializer != null ? initializer.getAsInt() : initialValue)
                    .toString();
        }
    }


    class ConfigBuilder extends
            AbstractMetricConfigBuilder<IntegerAccumulator, Config, ConfigBuilder> {

        private IntBinaryOperator accumulator = Integer::max;
        private IntSupplier initializer = Config.DEFAULT_INITIALIZER;
        private int initialValue;

        public ConfigBuilder(final String category, final String name) {
            super(category, name);
        }

        public ConfigBuilder(final MetricConfig<?> config) {
            super(config);
        }

        @Override
        public ConfigBuilder withUnit(String unit) {
            return super.withUnit(unit);
        }

        @Override
        public ConfigBuilder withFormat(String format) {
            return super.withFormat(format);
        }

        public IntBinaryOperator getAccumulator() {
            return accumulator;
        }

        public IntegerAccumulator.ConfigBuilder withAccumulator(IntBinaryOperator accumulator) {
            this.accumulator = throwArgNull(accumulator, "accumulator");
            return this;
        }

        public IntSupplier getInitializer() {
            return initializer;
        }

        public IntegerAccumulator.ConfigBuilder withInitializer(IntSupplier initializer) {
            this.initializer = throwArgNull(initializer, "initializer");
            return this;
        }

        public int getInitialValue() {
            return initialValue;
        }

        public IntegerAccumulator.ConfigBuilder withInitialValue(int initialValue) {
            this.initialValue = initialValue;
            return this;
        }

        @Override
        public IntegerAccumulator.Config build() {
            return new IntegerAccumulator.Config(getCategory(), getName(), getDescription(), getUnit(), getFormat(),
                    accumulator, initializer, initialValue);
        }

        @Override
        protected IntegerAccumulator.ConfigBuilder self() {
            return this;
        }
    }

}
