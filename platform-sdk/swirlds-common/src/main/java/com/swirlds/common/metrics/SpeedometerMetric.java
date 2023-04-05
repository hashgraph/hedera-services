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
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.internal.SettingsCommon;
import java.util.EnumSet;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This class measures how many times per second the cycle() method is called. It is recalculated every
 * period, where its period is 0.1 seconds by default. If instantiated with gamma=0.9, then half the
 * weighting comes from the last 7 periods. If 0.99 it's 70 periods, 0.999 is 700, etc.
 * <p>
 * The timer starts at instantiation, and can be reset with the reset() method.
 */
public interface SpeedometerMetric extends Metric {

    /**
     * {@inheritDoc}
     */
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
     */
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE, MAX, MIN, STD_DEV);
    }

    /**
     * {@inheritDoc}
     */
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
    final class Config extends MetricConfig<SpeedometerMetric, SpeedometerMetric.Config> {

        private final double halfLife;

        /**
         * Constructor of {@code SpeedometerMetric.Config}
         *
         * The {@code halfLife} is by default set to {@code SettingsCommon.halfLife}.
         *
         * @param category
         * 		the kind of metric (metrics are grouped or filtered by this)
         * @param name
         * 		a short name for the metric
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, FloatFormats.FORMAT_11_3);
            this.halfLife = SettingsCommon.halfLife;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final double halfLife) {

            super(category, name, description, unit, format);
            this.halfLife = halfLife;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SpeedometerMetric.Config withDescription(final String description) {
            return new SpeedometerMetric.Config(
                    getCategory(), getName(), description, getUnit(), getFormat(), getHalfLife());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SpeedometerMetric.Config withUnit(final String unit) {
            return new SpeedometerMetric.Config(
                    getCategory(), getName(), getDescription(), unit, getFormat(), getHalfLife());
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
        public SpeedometerMetric.Config withFormat(final String format) {
            return new SpeedometerMetric.Config(
                    getCategory(), getName(), getDescription(), getUnit(), format, getHalfLife());
        }

        /**
         * Getter of the {@code halfLife}.
         *
         * @return the {@code halfLife}
         */
        public double getHalfLife() {
            return halfLife;
        }

        /**
         * Fluent-style setter of the {@code halfLife}.
         *
         * @param halfLife
         * 		the {@code halfLife}
         * @return a reference to {@code this}
         */
        public SpeedometerMetric.Config withHalfLife(final double halfLife) {
            return new SpeedometerMetric.Config(
                    getCategory(), getName(), getDescription(), getUnit(), getFormat(), halfLife);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<SpeedometerMetric> getResultClass() {
            return SpeedometerMetric.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        SpeedometerMetric create(final MetricsFactory factory) {
            return factory.createSpeedometerMetric(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                    .appendSuper(super.toString())
                    .append("halfLife", halfLife)
                    .toString();
        }
    }
}
