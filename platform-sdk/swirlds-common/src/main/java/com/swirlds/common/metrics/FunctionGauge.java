/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
import java.util.function.Supplier;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * A {@code FunctionGauge} maintains a single value.
 * <p>
 * Unlike the other gauges, the value of a {@code FunctionGauge} is not explicitly set. Instead,
 * a {@link java.util.function.Supplier} has to be specified, which reads the current value of this gauge.
 * <p>
 * Only the current value is stored, no history or distribution is kept.
 *
 * @param <T> the type of the contained value
 */
public non-sealed interface FunctionGauge<T> extends BaseMetric {

    /**
     * {@inheritDoc}
     *
     * @deprecated {@link MetricType} turned out to be too limited. You can use the class-name instead.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    default MetricType getMetricType() {
        return MetricType.GAUGE;
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
     * Configuration of a {@link FunctionGauge}
     *
     * @param <T> the type of the value that will be contained in the {@code FunctionGauge}
     */
    record Config<T> (
            String category,
            String name,
            String description,
            String unit,
            String format,
            Class<T> type,
            Supplier<T> supplier
    ) implements MetricConfig<FunctionGauge<T>> {

        /**
         * Constructor of {@code FunctionGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format-string
         * @param type the type of the values this {@code FunctionGauge} returns
         * @param supplier the {@code Supplier} of the value of this {@code Gauge}
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
            throwArgNull(supplier, "supplier");
        }

        /**
         * Constructor of {@code FunctionGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param type the type of the values this {@code FunctionGauge} returns
         * @param supplier the {@code Supplier} of the value of this {@code Gauge}
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name, final Class<T> type, final Supplier<T> supplier) {
            this(category, name, name, "", "%s", type, supplier);
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
        public FunctionGauge.Config<T> withDescription(final String description) {
            return new FunctionGauge.Config<>(category, name, description, unit, format, type, supplier);
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
        public FunctionGauge.Config<T> withUnit(final String unit) {
            return new FunctionGauge.Config<>(category, name, description, unit, format, type, supplier);
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
        public FunctionGauge.Config<T> withFormat(final String format) {
            return new FunctionGauge.Config<>(category, name, description, unit, format, type, supplier);
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
         * Getter of the {@code supplier}
         *
         * @return the {@code supplier}
         * @deprecated Please use {@link #supplier()} instead
         */
        @Deprecated(forRemoval = true)
        public Supplier<T> getSupplier() {
            return supplier;
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings({"unchecked", "removal"})
        @Deprecated(forRemoval = true)
        @Override
        public Class<FunctionGauge<T>> getResultClass() {
            return (Class<FunctionGauge<T>>) (Class<?>) FunctionGauge.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public FunctionGauge<T> create(final MetricsFactory factory) {
            return factory.createFunctionGauge(this);
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
