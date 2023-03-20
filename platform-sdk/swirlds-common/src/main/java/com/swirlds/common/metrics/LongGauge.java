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
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code LongGauge} stores a single {@code long} value.
 * <p>
 * Only the current value is stored, no history or distribution is kept.
 */
public non-sealed interface LongGauge extends BaseMetric {

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
     * Set the current value
     *
     * @param newValue
     * 		the new value
     */
    void set(final long newValue);

    /**
     * Configuration of a {@link LongGauge}
     */
    record Config (
            String category,
            String name,
            String description,
            String unit,
            String format,
            long initialValue
    ) implements MetricConfig<LongGauge> {

        /**
         * Constructor of {@code LongGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param initialValue the initial value of the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         * (except for {@code unit} which can be empty)
         *
         */
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
        }

        /**
         * Constructor of {@code LongGauge.Config}
         * <p>
         * The {@link #initialValue} is by default set to {@code 0L}, the {@link #format} is set to "%d".
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            this(category, name, name, "", "%d", 0L);
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
        public LongGauge.Config withDescription(final String description) {
            return new LongGauge.Config(category, name, description, unit, format, initialValue);
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
        public LongGauge.Config withUnit(final String unit) {
            return new LongGauge.Config(category, name, description, unit, format, initialValue);
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
        public LongGauge.Config withFormat(final String format) {
            return new LongGauge.Config(category, name, description, unit, format, initialValue);
        }

        /**
         * Getter of the {@code initialValue}
         *
         * @return the {@code initialValue}
         * @deprecated Please use {@link #initialValue()} instead
         */
        @Deprecated(forRemoval = true)
        public long getInitialValue() {
            return initialValue();
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue
         * 		the initial value
         * @return a new configuration-object with updated {@code initialValue}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        public LongGauge.Config withInitialValue(final long initialValue) {
            return new LongGauge.Config(category, name, description, unit, format, initialValue);
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<LongGauge> getResultClass() {
            return LongGauge.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LongGauge create(final MetricsFactory factory) {
            return factory.createLongGauge(this);
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
