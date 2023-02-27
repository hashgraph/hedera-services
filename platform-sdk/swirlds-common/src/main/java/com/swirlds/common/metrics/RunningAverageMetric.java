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

import com.swirlds.common.internal.SettingsCommon;
import java.util.EnumSet;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * This class maintains a running average of some numeric value. It is exponentially weighted in time, with
 * a given half life. If it is always given the same value, then that value will be the average, regardless
 * of the timing.
 */
public interface RunningAverageMetric extends Metric {

    /**
     * {@inheritDoc}
     */
    @Override
    default MetricType getMetricType() {
        return MetricType.RUNNING_AVERAGE;
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
     * Incorporate "value" into the running average. If it is the same on every call, then the average will
     * equal it, no matter how those calls are timed. If it has various values on various calls, then the
     * running average will weight the more recent ones more heavily, with a half life of halfLife seconds,
     * where halfLife was passed in when this object was instantiated.
     * <p>
     * If this is called repeatedly with a value of X over a long period, then suddenly all calls start
     * having a value of Y, then after half-life seconds, the average will have moved halfway from X to Y,
     * regardless of how often update was called, as long as it is called at least once at the end of that
     * period.
     *
     * @param value
     * 		the value to incorporate into the running average
     */
    void update(final double value);

    /**
     * Get the average of recent calls to recordValue(). This is an exponentially-weighted average of recent
     * calls, with the weighting by time, not by number of calls to recordValue().
     *
     * @return the running average as of the last time recordValue was called
     */
    double get();

    /**
     * Configuration of a {@link RunningAverageMetric}
     */
    final class Config extends MetricConfig<RunningAverageMetric, RunningAverageMetric.Config> {

        private final double halfLife;

        /**
         * Constructor of {@code RunningAverageMetric.Config}
         *
         * The {@code halfLife} is by default set to {@code SettingsCommon.halfLife}.
         *
         * @param category
         * 		the kind of metric (stats are grouped or filtered by this)
         * @param name
         * 		a short name for the statistic
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
        public RunningAverageMetric.Config withDescription(final String description) {
            return new RunningAverageMetric.Config(
                    getCategory(), getName(), description, getUnit(), getFormat(), getHalfLife());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RunningAverageMetric.Config withUnit(final String unit) {
            return new RunningAverageMetric.Config(
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
        public RunningAverageMetric.Config withFormat(final String format) {
            return new RunningAverageMetric.Config(
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
         * @return a new configuration-object with updated {@code halfLife}
         */
        public RunningAverageMetric.Config withHalfLife(final double halfLife) {
            return new RunningAverageMetric.Config(
                    getCategory(), getName(), getDescription(), getUnit(), getFormat(), halfLife);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<RunningAverageMetric> getResultClass() {
            return RunningAverageMetric.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        RunningAverageMetric create(final MetricsFactory factory) {
            return factory.createRunningAverageMetric(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .appendSuper(super.toString())
                    .append("halfLife", halfLife)
                    .toString();
        }
    }
}
