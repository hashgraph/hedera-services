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
import java.util.function.BiFunction;
import java.util.function.IntBinaryOperator;
import java.util.function.IntSupplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code IntegerPairAccumulator} accumulates two {@code int}-values, which can be updated atomically.
 * <p>
 * When reading the value of an {@code IntegerPairAccumulator}, both {@code ints} are combined.
 * <p>
 * An {@code IntegerAccumulator} is reset in regular intervals. The exact timing depends on the implementation.
 *
 * @param <T> The type of the combined value
 */
public non-sealed interface IntegerPairAccumulator<T> extends BaseMetric {

    BiFunction<Integer, Integer, Double> AVERAGE = (sum, count) -> {
        if (count == 0) {
            // avoid division by 0
            return 0.0;
        }
        return ((double) sum) / count;
    };

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
    default T get(final ValueType valueType) {
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
    T get();

    /**
     * Returns the left {@code int}-value
     *
     * @return the left value
     */
    int getLeft();

    /**
     * Returns the right {@code int}-value
     *
     * @return the right value
     */
    int getRight();

    /**
     * Atomically updates the current values with the results of applying the {@code accumulators} of this
     * {@code IntegerPairAccumulator} to the current and given values.
     * <p>
     * The function is applied with the current values as their first argument, and the provided {@code leftValue} and
     * {@code rightValue }as the second arguments.
     *
     * @param leftValue  the value combined with the left {@code int}
     * @param rightValue the value combined with the right {@code int}
     */
    void update(final int leftValue, final int rightValue);

    /**
     * Configuration of a {@link IntegerPairAccumulator}
     */
    record Config<T> (
            String category,
            String name,
            String description,
            String unit,
            String format,
            Class<T> type,
            BiFunction<Integer, Integer, T> resultFunction,
            IntBinaryOperator leftAccumulator,
            IntBinaryOperator rightAccumulator,
            IntSupplier leftInitializer,
            IntSupplier rightInitializer
    ) implements MetricConfig<IntegerPairAccumulator<T>> {

        private static final IntSupplier DEFAULT_INITIALIZER = () -> 0;

        /**
         * Constructor of {@code IntegerPairAccumulator.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param type the type of the values this {@code IntegerPairAccumulator} returns
         * @param resultFunction the function that is used to calculate the {@code IntegerAccumulator}'s value.
         * @param leftAccumulator the function that is used to combine the left {@code int}-values
         * @param rightAccumulator the function that is used to combine the right {@code int}-values
         * @param leftInitializer the function that is used to initialize the left {@code int}-value
         * @param rightInitializer the function that is used to initialize the right {@code int}-value
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         * (except for {@code unit} which can be empty)
         */
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
            throwArgNull(type, "type");
            throwArgNull(resultFunction, "resultFunction");
            throwArgNull(leftAccumulator, "leftAccumulator");
            throwArgNull(rightAccumulator, "rightAccumulator");
            throwArgNull(leftInitializer, "leftInitializer");
            throwArgNull(rightInitializer, "rightInitializer");
        }

        /**
         * Constructor of {@code IntegerPairAccumulator.Config}
         * <p>
         * The accumulators are by default set to {@code Integer::sum}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param type the type of the values this {@code IntegerPairAccumulator} returns
         * @param resultFunction the function that is used to calculate the {@code IntegerAccumulator}'s value.
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(
                final String category,
                final String name,
                final Class<T> type,
                final BiFunction<Integer, Integer, T> resultFunction) {
            this(category, name, name, "", "%s", type, resultFunction, Integer::sum, Integer::sum,
                    DEFAULT_INITIALIZER, DEFAULT_INITIALIZER);
        }

        /**
         * Sets the {@link Metric#getDescription() Metric.description} in fluent style.
         *
         * @param description the description
         * @return a new configuration-object with updated {@code description}
         * @throws IllegalArgumentException if {@code description} is {@code null}, too long or consists only of whitespaces
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withDescription(final String description) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
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
        public IntegerPairAccumulator.Config<T> withUnit(final String unit) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
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
        public IntegerPairAccumulator.Config<T> withFormat(final String format) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
        }

        /**
         * Getter of the type of the returned values
         *
         * @return the type of the returned values
         * @deprecated Please use {@link #type()} instead
         */
        @Deprecated(forRemoval = true)
        public Class<T> getType() {
            return type();
        }

        /**
         * Getter of the {@code resultFunction}
         *
         * @return the {@code resultFunction}
         * @deprecated Please use {@link #resultFunction()} instead
         */
        @Deprecated(forRemoval = true)
        public BiFunction<Integer, Integer, T> getResultFunction() {
            return resultFunction();
        }

        /**
         * Getter of the {@code leftAccumulator}
         *
         * @return the {@code leftAccumulator}
         * @deprecated Please use {@link #leftAccumulator()} instead
         */
        @Deprecated(forRemoval = true)
        public IntBinaryOperator getLeftAccumulator() {
            return leftAccumulator();
        }

        /**
         * Fluent-style setter of the left accumulator.
         *
         * @param leftAccumulator the left accumulator
         * @return a new configuration-object with updated {@code leftAccumulator}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withLeftAccumulator(final IntBinaryOperator leftAccumulator) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
        }

        /**
         * Getter of the {@code rightAccumulator}
         *
         * @return the {@code rightAccumulator}
         * @deprecated Please use {@link #rightAccumulator()} instead
         */
        @Deprecated(forRemoval = true)
        public IntBinaryOperator getRightAccumulator() {
            return rightAccumulator();
        }

        /**
         * Fluent-style setter of the right accumulator.
         *
         * @param rightAccumulator the right accumulator value
         * @return a new configuration-object with updated {@code rightAccumulator}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withRightAccumulator(final IntBinaryOperator rightAccumulator) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
        }

        /**
         * Getter of the {@code leftInitializer}
         *
         * @return the {@code leftInitializer}
         * @deprecated Please use {@link #leftInitializer()} instead
         */
        @Deprecated(forRemoval = true)
        public IntSupplier getLeftInitializer() {
            return leftInitializer();
        }

        /**
         * Fluent-style setter of the left initializer.
         *
         * @param leftInitializer the left initializer
         * @return a new configuration-object with updated {@code leftInitializer}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withLeftInitializer(final IntSupplier leftInitializer) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
        }

        /**
         * Fluent-style setter for a constant left initial value
         *
         * @param leftInitialValue the left initial value
         * @return a new configuration-object with updated {@code leftInitializer}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withLeftInitialValue(final int leftInitialValue) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitialValue == 0 ? DEFAULT_INITIALIZER : () -> leftInitialValue,
                    rightInitializer);
        }

        /**
         * Getter of the {@code rightInitializer}
         *
         * @return the {@code rightInitializer}
         * @deprecated Please use {@link #rightInitializer()} instead
         */
        @Deprecated(forRemoval = true)
        public IntSupplier getRightInitializer() {
            return rightInitializer();
        }

        /**
         * Fluent-style setter of the right initializer.
         *
         * @param rightInitializer the right initializer value
         * @return a new configuration-object with updated {@code rightInitializer}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withRightInitializer(final IntSupplier rightInitializer) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitializer);
        }

        /**
         * Fluent-style setter for a constant right initial value
         *
         * @param rightInitialValue the right initial value
         * @return a new configuration-object with updated {@code rightInitializer}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public IntegerPairAccumulator.Config<T> withRightInitialValue(final int rightInitialValue) {
            return new IntegerPairAccumulator.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    resultFunction,
                    leftAccumulator,
                    rightAccumulator,
                    leftInitializer,
                    rightInitialValue == 0 ? DEFAULT_INITIALIZER : () -> rightInitialValue);
        }

        /**
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings({"unchecked", "removal"})
        @Deprecated(forRemoval = true)
        @Override
        public Class<IntegerPairAccumulator<T>> getResultClass() {
            return (Class<IntegerPairAccumulator<T>>) (Class<?>) IntegerPairAccumulator.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public IntegerPairAccumulator<T> create(final MetricsFactory factory) {
            return factory.createIntegerPairAccumulator(this);
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
                    .append("type", type.getName())
                    .toString();
        }
    }

    class ConfigBuilder<T> extends AbstractMetricConfigBuilder<IntegerPairAccumulator<T>, Config<T>, ConfigBuilder<T>> {

        private final Class<T> type;

        private final BiFunction<Integer, Integer, T> resultFunction;

        private IntBinaryOperator leftAccumulator;
        private IntBinaryOperator rightAccumulator;

        private IntSupplier leftInitializer;
        private int leftInitialValue;

        private IntSupplier rightInitializer;
        private int rightInitialValue;

        public ConfigBuilder(final String category, final String name, final Class<T> type, final BiFunction<Integer, Integer, T> resultFunction) {
            super(category, name);
            this.type = throwArgNull(type, "type");
            this.resultFunction = throwArgNull(resultFunction, "resultFunction");
            this.leftAccumulator = Integer::sum;
            this.rightAccumulator = Integer::sum;
            this.leftInitializer = Config.DEFAULT_INITIALIZER;
            this.rightInitializer = Config.DEFAULT_INITIALIZER;
        }

        public ConfigBuilder(final MetricConfig<?> config, final Class<T> type, final BiFunction<Integer, Integer, T> resultFunction) {
            super(config);
            this.type = throwArgNull(type, "type");
            this.resultFunction = throwArgNull(resultFunction, "resultFunction");
            this.leftAccumulator = Integer::sum;
            this.rightAccumulator = Integer::sum;
            this.leftInitializer = Config.DEFAULT_INITIALIZER;
            this.rightInitializer = Config.DEFAULT_INITIALIZER;
        }

        @Override
        public ConfigBuilder<T> withUnit(String unit) {
            return super.withUnit(unit);
        }

        @Override
        public ConfigBuilder<T> withFormat(String format) {
            return super.withFormat(format);
        }

        public Class<T> getType() {
            return type;
        }

        public BiFunction<Integer, Integer, T> getResultFunction() {
            return resultFunction;
        }

        public IntBinaryOperator getLeftAccumulator() {
            return leftAccumulator;
        }

        public ConfigBuilder<T> withLeftAccumulator(IntBinaryOperator leftAccumulator) {
            this.leftAccumulator = throwArgNull(leftAccumulator, "leftAccumulator");
            return this;
        }

        public IntBinaryOperator getRightAccumulator() {
            return rightAccumulator;
        }

        public ConfigBuilder<T> withRightAccumulator(IntBinaryOperator rightAccumulator) {
            this.rightAccumulator = throwArgNull(rightAccumulator, "rightAccumulator");
            return this;
        }

        public IntSupplier getLeftInitializer() {
            return leftInitializer;
        }

        public ConfigBuilder<T> withLeftInitializer(IntSupplier leftInitializer) {
            this.leftInitializer = throwArgNull(leftInitializer, "leftInitializer");
            return this;
        }

        public int getLeftInitialValue() {
            return leftInitialValue;
        }

        public ConfigBuilder<T> withLeftInitialValue(int leftInitialValue) {
            this.leftInitialValue = leftInitialValue;
            return this;
        }

        public IntSupplier getRightInitializer() {
            return rightInitializer;
        }

        public ConfigBuilder<T> withRightInitializer(IntSupplier rightInitializer) {
            this.rightInitializer = throwArgNull(rightInitializer, "rightInitializer");
            return this;
        }

        public int getRightInitialValue() {
            return rightInitialValue;
        }

        public ConfigBuilder<T> withRightInitialValue(int rightInitialValue) {
            this.rightInitialValue = rightInitialValue;
            return this;
        }

        @Override
        public Config<T> build() {
            IntSupplier leftInit;
            if (leftInitializer != null) {
                leftInit = leftInitializer;
            } else if (leftInitialValue != 0) {
                leftInit = () -> leftInitialValue;
            } else {
                leftInit = Config.DEFAULT_INITIALIZER;
            }
            IntSupplier rightInit;
            if (rightInitializer != null) {
                rightInit = rightInitializer;
            } else if (rightInitialValue != 0) {
                rightInit = () -> rightInitialValue;
            } else {
                rightInit = Config.DEFAULT_INITIALIZER;
            }
            return new Config<>(getCategory(), getName(), getDescription(), getUnit(), getFormat(), type,
                    resultFunction, leftAccumulator, rightAccumulator, leftInit, rightInit);
        }

        @Override
        protected ConfigBuilder<T> self() {
            return this;
        }

    }
}
