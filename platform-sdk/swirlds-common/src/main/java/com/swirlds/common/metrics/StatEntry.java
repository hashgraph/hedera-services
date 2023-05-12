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
import static com.swirlds.common.utility.CommonUtils.throwArgNull;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

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
public interface StatEntry extends Metric {

    /**
     * {@inheritDoc}
     */
    @Override
    default MetricType getMetricType() {
        return MetricType.STAT_ENTRY;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return getBuffered() == null ? EnumSet.of(VALUE) : EnumSet.of(VALUE, MAX, MIN, STD_DEV);
    }

    /**
     * {@inheritDoc}
     */
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
     * Getter for {@code resetStatsStringSupplier}, a lambda that returns the statistic string
     * and resets it at the same time
     *
     * @return the lambda
     */
    Supplier<Object> getResetStatsStringSupplier();

    /**
     * Configuration of a {@link StatEntry}.
     *
     * @param <T> the type of the value that will be contained in the {@code StatEntry}
     */
    final class Config<T> extends MetricConfig<StatEntry, Config<T>> {

        private final Class<T> type;
        private final StatsBuffered buffered;
        private final Function<Double, StatsBuffered> init;
        private final Consumer<Double> reset;
        private final Supplier<T> statsStringSupplier;
        private final Supplier<T> resetStatsStringSupplier;

        /**
         * stores all the parameters, which can be accessed directly
         *
         * @param category
         * 		the kind of metric (metrics are grouped or filtered by this)
         * @param name
         * 		a short name for the metric
         * @param type
         * 		the type of the values this {@code StatEntry} returns
         * @param statsStringSupplier
         * 		a lambda that returns the metric string
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(
                final String category, final String name, final Class<T> type, final Supplier<T> statsStringSupplier) {

            super(category, name, FloatFormats.FORMAT_11_3);
            this.type = throwArgNull(type, "type");
            this.buffered = null;
            this.init = null;
            this.reset = null;
            this.statsStringSupplier = throwArgNull(statsStringSupplier, "statsStringSupplier");
            this.resetStatsStringSupplier = statsStringSupplier;
        }

        @SuppressWarnings("java:S107")
        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final Class<T> type,
                final StatsBuffered buffered,
                final Function<Double, StatsBuffered> init,
                final Consumer<Double> reset,
                final Supplier<T> statsStringSupplier,
                final Supplier<T> resetStatsStringSupplier) {
            super(category, name, description, unit, format);
            this.type = throwArgNull(type, "type");
            this.buffered = buffered;
            this.init = init;
            this.reset = reset;
            this.statsStringSupplier = throwArgNull(statsStringSupplier, "statsStringSupplier");
            this.resetStatsStringSupplier = throwArgNull(resetStatsStringSupplier, "resetStatsStringSupplier");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public StatEntry.Config<T> withDescription(final String description) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getType(),
                    getBuffered(),
                    getInit(),
                    getReset(),
                    getStatsStringSupplier(),
                    getResetStatsStringSupplier());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public StatEntry.Config<T> withUnit(final String unit) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    unit,
                    getFormat(),
                    getType(),
                    getBuffered(),
                    getInit(),
                    getReset(),
                    getStatsStringSupplier(),
                    getResetStatsStringSupplier());
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
        public StatEntry.Config<T> withFormat(final String format) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    format,
                    getType(),
                    getBuffered(),
                    getInit(),
                    getReset(),
                    getStatsStringSupplier(),
                    getResetStatsStringSupplier());
        }

        /**
         * Getter of the type of the returned values
         *
         * @return the type of the returned values
         */
        public Class<T> getType() {
            return type;
        }

        /**
         * Getter of {@code buffered}
         *
         * @return {@code buffered}
         */
        public StatsBuffered getBuffered() {
            return buffered;
        }

        /**
         * Fluent-style setter of {@code buffered}.
         *
         * @param buffered
         * 		the {@link StatsBuffered}
         * @return a reference to {@code this}
         */
        public StatEntry.Config<T> withBuffered(final StatsBuffered buffered) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getType(),
                    buffered,
                    getInit(),
                    getReset(),
                    getStatsStringSupplier(),
                    getResetStatsStringSupplier());
        }

        /**
         * Getter of {@code init}
         *
         * @return {@code init}
         */
        public Function<Double, StatsBuffered> getInit() {
            return init;
        }

        /**
         * Fluent-style setter of {@code init}.
         *
         * @param init
         * 		the init-function
         * @return a reference to {@code this}
         */
        public StatEntry.Config<T> withInit(final Function<Double, StatsBuffered> init) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getType(),
                    getBuffered(),
                    init,
                    getReset(),
                    getStatsStringSupplier(),
                    getResetStatsStringSupplier());
        }

        /**
         * Getter of {@code reset}
         *
         * @return {@code reset}
         */
        public Consumer<Double> getReset() {
            return reset;
        }

        /**
         * Fluent-style setter of {@code reset}.
         *
         * @param reset
         * 		the reset-function
         * @return a reference to {@code this}
         */
        public StatEntry.Config<T> withReset(final Consumer<Double> reset) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getType(),
                    getBuffered(),
                    getInit(),
                    reset,
                    getStatsStringSupplier(),
                    getResetStatsStringSupplier());
        }

        /**
         * Getter of {@code statsStringSupplier}
         *
         * @return {@code statsStringSupplier}
         */
        public Supplier<T> getStatsStringSupplier() {
            return statsStringSupplier;
        }

        /**
         * Getter of {@code resetStatsStringSupplier}
         *
         * @return {@code resetStatsStringSupplier}
         */
        public Supplier<T> getResetStatsStringSupplier() {
            return resetStatsStringSupplier;
        }

        /**
         * Fluent-style setter of {@code resetStatsStringSupplier}.
         *
         * @param resetStatsStringSupplier
         * 		the reset-supplier
         * @return a reference to {@code this}
         */
        public StatEntry.Config<T> withResetStatsStringSupplier(final Supplier<T> resetStatsStringSupplier) {
            return new StatEntry.Config<>(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    getFormat(),
                    getType(),
                    getBuffered(),
                    getInit(),
                    getReset(),
                    getStatsStringSupplier(),
                    resetStatsStringSupplier);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<StatEntry> getResultClass() {
            return StatEntry.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        StatEntry create(final MetricsFactory factory) {
            return factory.createStatEntry(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                    .appendSuper(super.toString())
                    .append("type", type.getName())
                    .toString();
        }
    }
}
