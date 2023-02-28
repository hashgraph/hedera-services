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
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * An {@code LongGauge} stores a single {@code long} value.
 * <p>
 * Only the current value is stored, no history or distribution is kept.
 */
public interface LongGauge extends Metric {

    /**
     * {@inheritDoc}
     */
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
     */
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /**
     * {@inheritDoc}
     */
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
    final class Config extends MetricConfig<LongGauge, LongGauge.Config> {

        private final long initialValue;

        /**
         * Constructor of {@code LongGauge.Config}
         *
         *
         * The {@link #getInitialValue() initialValue} is by default set to {@code 0L},
         * the {@link #getFormat() format} is set to "%d".
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
            this.initialValue = 0L;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final long initialValue) {

            super(category, name, description, unit, format);
            this.initialValue = initialValue;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LongGauge.Config withDescription(final String description) {
            return new LongGauge.Config(
                    getCategory(), getName(), description, getUnit(), getFormat(), getInitialValue());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LongGauge.Config withUnit(final String unit) {
            return new LongGauge.Config(
                    getCategory(), getName(), getDescription(), unit, getFormat(), getInitialValue());
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
        public LongGauge.Config withFormat(final String format) {
            return new LongGauge.Config(
                    getCategory(), getName(), getDescription(), getUnit(), format, getInitialValue());
        }

        /**
         * Getter of the {@code initialValue}
         *
         * @return the {@code initialValue}
         */
        public long getInitialValue() {
            return initialValue;
        }

        /**
         * Fluent-style setter of the initial value.
         *
         * @param initialValue
         * 		the initial value
         * @return a new configuration-object with updated {@code initialValue}
         */
        public LongGauge.Config withInitialValue(final long initialValue) {
            return new LongGauge.Config(
                    getCategory(), getName(), getDescription(), getUnit(), getFormat(), initialValue);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<LongGauge> getResultClass() {
            return LongGauge.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        LongGauge create(final MetricsFactory factory) {
            return factory.createLongGauge(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .appendSuper(super.toString())
                    .append("initialValue", initialValue)
                    .toString();
        }
    }
}
