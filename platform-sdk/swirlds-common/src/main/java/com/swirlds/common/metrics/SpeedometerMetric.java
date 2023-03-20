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

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.common.metrics.IntegerAccumulator.ConfigBuilder;
import java.util.EnumSet;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This class measures how many times per second the cycle() method is called. It is recalculated every
 * period, where its period is 0.1 seconds by default. If instantiated with gamma=0.9, then half the
 * weighting comes from the last 7 periods. If 0.99 it's 70 periods, 0.999 is 700, etc.
 * <p>
 * The timer starts at instantiation, and can be reset with the reset() method.
 */
public non-sealed interface SpeedometerMetric extends BaseMetric {

    /**
     * {@inheritDoc}
     *
     * @deprecated {@link MetricType} turned out to be too limited. You can use the class-name instead.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    @Override
    default MetricType getMetricType() {
        return MetricType.SPEEDOMETER;
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
        return EnumSet.of(VALUE, MAX, MIN, STD_DEV);
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
    Double get(final ValueType valueType);

    /**
     * Getter of the {@code halfLife}
     *
     * @return the {@code halfLife}
     */
    double getHalfLife();

    /**
     * calling update(N) is equivalent to calling cycle() N times almost simultaneously. Calling cycle() is
     * equivalent to calling update(1). Calling update(0) will update the estimate of the cycles per second
     * by looking at the current time (to update the seconds) without incrementing the count of the cycles.
     * So calling update(0) repeatedly with no calls to cycle() will cause the cycles per second estimate to
     * go asymptotic to zero.
     * <p>
     * The speedometer initially keeps a simple, uniformly-weighted average of the number of calls to
     * cycle() per second since the start of the run. Over time, that makes each new call to cycle() have
     * less weight (because there are more of them). Eventually, the weight of a new call drops below the
     * weight it would have under the exponentially-weighted average. At that point, it switches to the
     * exponentially-weighted average.
     *
     * @param value
     * 		number of cycles to record
     */
    void update(final double value);

    /**
     * This is the method to call repeatedly. The average calls per second will be calculated.
     */
    void cycle();

    /**
     * Get the average number of times per second the cycle() method was called. This is an
     * exponentially-weighted average of recent timings.
     *
     * @return the estimated number of calls to cycle() per second, recently
     */
    double get();

    /**
     * Configuration of a {@link SpeedometerMetric}
     */
    record Config (
            String category,
            String name,
            String description,
            String unit,
            String format,
            double halfLife
    ) implements MetricConfig<SpeedometerMetric> {

        /**
         * Constructor of {@code SpeedometerMetric.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a description of the metric
         * @param unit the unit of the metric
         * @param format the format of the metric
         * @param halfLife the half-life of the exponential moving average
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         * (except for {@code unit} which can be empty)
         */
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
        }

        /**
         * Constructor of {@code SpeedometerMetric.Config}
         * <p>
         * The {@code halfLife} is by default set to {@code SettingsCommon.halfLife}.
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            this(category, name, name, "", FloatFormats.FORMAT_11_3, SettingsCommon.halfLife);
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
        public SpeedometerMetric.Config withDescription(final String description) {
            return new SpeedometerMetric.Config(category, name, description, unit, format, halfLife);
        }

        /**
         * Sets the {@link Metric#getUnit() Metric.unit} in fluent style.
         *
         * @param unit the unit
         * @return a new configuration-object with updated {@code unit}
         * @throws IllegalArgumentException if {@code unit} is {@code null}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        public SpeedometerMetric.Config withUnit(final String unit) {
            return new SpeedometerMetric.Config(category, name, description, unit, format, halfLife);
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
        public SpeedometerMetric.Config withFormat(final String format) {
            return new SpeedometerMetric.Config(category, name, description, unit, format, halfLife);
        }

        /**
         * Getter of the {@code halfLife}.
         *
         * @return the {@code halfLife}
         * @deprecated Please use {@link #halfLife()} instead
         */
        @Deprecated(forRemoval = true)
        public double getHalfLife() {
            return halfLife;
        }

        /**
         * Fluent-style setter of the {@code halfLife}.
         *
         * @param halfLife
         * 		the {@code halfLife}
         * @return a reference to {@code this}
         * @deprecated Please use {@link ConfigBuilder} instead
         */
        @Deprecated(forRemoval = true)
        public SpeedometerMetric.Config withHalfLife(final double halfLife) {
            return new SpeedometerMetric.Config(category, name, description, unit, format, halfLife);
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<SpeedometerMetric> getResultClass() {
            return SpeedometerMetric.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SpeedometerMetric create(final MetricsFactory factory) {
            return factory.createSpeedometerMetric(this);
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
                    .append("halfLife", halfLife)
                    .toString();
        }
    }
}
