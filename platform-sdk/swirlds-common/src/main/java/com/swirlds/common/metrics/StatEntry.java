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

import static com.swirlds.common.metrics.Metric.ValueType.MAX;
import static com.swirlds.common.metrics.Metric.ValueType.MIN;
import static com.swirlds.common.metrics.Metric.ValueType.STD_DEV;
import static com.swirlds.common.metrics.Metric.ValueType.VALUE;
import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.IntegerAccumulator.ConfigBuilder;
import com.swirlds.common.statistics.StatsBuffered;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A very flexible implementation of Metric which behavior is mostly passed to it via lambdas.
 *
 * @deprecated This class will be removed. Use one of the specialized {@link Metric}-implementations instead.
 */
@Deprecated(forRemoval = true)
public non-sealed interface StatEntry extends BaseMetric {

    /**
     * {@inheritDoc}
     *
     * @deprecated {@link MetricType} turned out to be too limited. You can use the class-name instead.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    default MetricType getMetricType() {
        return MetricType.STAT_ENTRY;
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
        return getBuffered() == null ? EnumSet.of(VALUE) : EnumSet.of(VALUE, MAX, MIN, STD_DEV);
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
    default Object get(final ValueType valueType) {
        throwArgNull(valueType, "valueType");
        if (getBuffered() == null) {
            if (valueType == VALUE) {
                return getStatsStringSupplier().get();
            }
            throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
        } else {
            return switch (valueType) {
                case VALUE -> getStatsStringSupplier().get();
                case MAX -> getBuffered().getMax();
                case MIN -> getBuffered().getMin();
                case STD_DEV -> getBuffered().getStdDev();
                default -> throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
            };
        }
    }

    /**
     * Getter for {@code buffered}
     *
     * @return the {@link StatsBuffered}, if available, otherwise {@code null}
     */
    StatsBuffered getBuffered();

    /**
     * Getter for {@code reset}, a lambda that resets the metric, using the given half life
     *
     * @return the reset-lambda, if available, otherwise {@code null}
     */
    Consumer<Double> getReset();

    /**
     * Getter for {@code statsStringSupplier}, a lambda that returns the metric value
     *
     * @return the lambda
     */
    Supplier<Object> getStatsStringSupplier();

    /**
     * Getter for {@code resetStatsStringSupplier}, a lambda that returns the statistic string and resets it at the same
     * time
     *
     * @return the lambda
     */
    Supplier<Object> getResetStatsStringSupplier();

    /**
     * Configuration of a {@link StatEntry}.
     *
     * @param <T> the type of the value that will be contained in the {@code StatEntry}
     */
    record Config<T>(
            String category,
            String name,
            String description,
            String unit,
            String format,
            Class<T> type,
            StatsBuffered buffered,
            Function<Double, StatsBuffered> init,
            Consumer<Double> reset,
            Supplier<T> statsStringSupplier,
            Supplier<T> resetStatsStringSupplier

    ) implements MetricConfig<StatEntry> {

        /**
         * Constructor of {@code Counter.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param type the type of the values this {@code StatEntry} returns
         * @param buffered the {@link StatsBuffered}, if available, otherwise {@code null}
         * @param init a lambda that initializes the {@link StatsBuffered}, using the given half life
         * @param reset a lambda that resets the metric, using the given half life
         * @param statsStringSupplier a lambda that returns the metric value
         * @throws IllegalArgumentException if one of the mandatory parameters is {@code null} or consists only of
         * whitespaces (except for {@code unit} which can be empty)
         */
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
            throwArgNull(type, "type");
            throwArgNull(statsStringSupplier, "statsStringSupplier");
            throwArgNull(resetStatsStringSupplier, "resetStatsStringSupplier");
        }

        /**
         * stores all the parameters, which can be accessed directly
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param type the type of the values this {@code StatEntry} returns
         * @param statsStringSupplier a lambda that returns the metric string
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(
                final String category, final String name, final Class<T> type, final Supplier<T> statsStringSupplier) {
            this(
                    category,
                    name,
                    name,
                    "",
                    FloatFormats.FORMAT_11_3,
                    type,
                    null,
                    null,
                    null,
                    statsStringSupplier,
                    statsStringSupplier);
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
        public StatEntry.Config<T> withDescription(final String description) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
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
        public StatEntry.Config<T> withUnit(final String unit) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
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
        public StatEntry.Config<T> withFormat(final String format) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
        }

        /**
         * Getter of the type of the returned values
         *
         * @return the type of the returned values
         * @deprecated Please use {@link #type()} instead
         */
        @Deprecated(forRemoval = true)
        public Class<T> getType() {
            return type;
        }

        /**
         * Getter of {@code buffered}
         *
         * @return {@code buffered}
         * @deprecated Please use {@link #buffered()} instead
         */
        @Deprecated(forRemoval = true)
        public StatsBuffered getBuffered() {
            return buffered;
        }

        /**
         * Fluent-style setter of {@code buffered}.
         *
         * @param buffered the {@link StatsBuffered}
         * @return a reference to {@code this}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public StatEntry.Config<T> withBuffered(final StatsBuffered buffered) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
        }

        /**
         * Getter of {@code init}
         *
         * @return {@code init}
         * @deprecated Please use {@link #init()} instead
         */
        @Deprecated(forRemoval = true)
        public Function<Double, StatsBuffered> getInit() {
            return init;
        }

        /**
         * Fluent-style setter of {@code init}.
         *
         * @param init the init-function
         * @return a reference to {@code this}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public StatEntry.Config<T> withInit(final Function<Double, StatsBuffered> init) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
        }

        /**
         * Getter of {@code reset}
         *
         * @return {@code reset}
         * @deprecated Please use {@link #reset()} instead
         */
        @Deprecated(forRemoval = true)
        public Consumer<Double> getReset() {
            return reset;
        }

        /**
         * Fluent-style setter of {@code reset}.
         *
         * @param reset the reset-function
         * @return a reference to {@code this}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public StatEntry.Config<T> withReset(final Consumer<Double> reset) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
        }

        /**
         * Getter of {@code statsStringSupplier}
         *
         * @return {@code statsStringSupplier}
         * @deprecated Please use {@link #statsStringSupplier()} instead
         */
        @Deprecated(forRemoval = true)
        public Supplier<T> getStatsStringSupplier() {
            return statsStringSupplier;
        }

        /**
         * Getter of {@code resetStatsStringSupplier}
         *
         * @return {@code resetStatsStringSupplier}
         * @deprecated Please use {@link #resetStatsStringSupplier()} instead
         */
        @Deprecated(forRemoval = true)
        public Supplier<T> getResetStatsStringSupplier() {
            return resetStatsStringSupplier;
        }

        /**
         * Fluent-style setter of {@code resetStatsStringSupplier}.
         *
         * @param resetStatsStringSupplier the reset-supplier
         * @return a reference to {@code this}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public StatEntry.Config<T> withResetStatsStringSupplier(final Supplier<T> resetStatsStringSupplier) {
            return new StatEntry.Config<>(
                    category,
                    name,
                    description,
                    unit,
                    format,
                    type,
                    buffered,
                    init,
                    reset,
                    statsStringSupplier,
                    resetStatsStringSupplier);
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<StatEntry> getResultClass() {
            return StatEntry.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public StatEntry create(final MetricsFactory factory) {
            return factory.createStatEntry(this);
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
                    .append("type", type)
                    .toString();
        }
    }
}
