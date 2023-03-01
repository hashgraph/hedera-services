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
import com.swirlds.common.utility.Units;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Stores a single duration. The output unit is determined by configuration
 */
public non-sealed interface DurationGauge extends BaseMetric {

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
    default Double get(final ValueType valueType) {
        throwArgNull(valueType, "valueType");
        if (valueType == VALUE) {
            return get();
        }
        throw new IllegalArgumentException("Unsupported ValueType: " + valueType);
    }

    /**
     * @return the current stored duration in nanoseconds
     */
    long getNanos();

    /**
     * Set the gauge to the value supplied
     *
     * @param duration
     * 		the value to set the gauge to
     */
    void set(Duration duration);

    /**
     * Get the current value in units supplied in the constructor
     *
     * @return the current value
     */
    double get();

    /**
     * Configuration of a {@link DurationGauge}
     */
    record Config (
            String category,
            String name,
            String description,
            ChronoUnit timeUnit
    ) implements MetricConfig<DurationGauge> {

        private static final String UNSUPPORTED_TIME_UNIT = "Unsupported time unit: ";

        private static String fixName(final String name, final ChronoUnit timeUnit) {
            final String appendix = switch (timeUnit) {
                case NANOS -> "(nanos)";
                case MICROS -> "(micros)";
                case MILLIS -> "(millis)";
                case SECONDS -> "(sec)";
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
            return name + " " + appendix;
        }

        /**
         * Constructor of {@code DoubleGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param description a longer description of the metric
         * @param timeUnit the time unit in which to display this duration
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(timeUnit, "timeUnit");
        }

        /**
         * Constructor of {@code DoubleGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name a short name for the metric
         * @param timeUnit the time unit in which to display this duration
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name, final ChronoUnit timeUnit) {
            this(
                    category,
                    fixName(throwArgBlank(name, "name"), throwArgNull(timeUnit, "timeUnit")),
                    fixName(name, timeUnit),
                    timeUnit);
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
        public DurationGauge.Config withDescription(final String description) {
            return new DurationGauge.Config(category, name, description, timeUnit);
        }

        /**
         * Getter of the {@code timeUnit}
         *
         * @return the {@code timeUnit}
         * @deprecated Please use {@link #timeUnit()} instead.
         */
        @Deprecated(forRemoval = true)
        public ChronoUnit getTimeUnit() {
            return timeUnit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String unit() {
            return switch (timeUnit) {
                case NANOS -> Units.NANOSECOND_UNIT;
                case MICROS -> Units.MICROSECOND_UNIT;
                case MILLIS -> Units.MILLISECOND_UNIT;
                case SECONDS -> Units.SECOND_UNIT;
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String format() {
            return switch (timeUnit) {
                case NANOS, MICROS -> FloatFormats.FORMAT_DECIMAL_0;
                case MILLIS, SECONDS -> FloatFormats.FORMAT_DECIMAL_3;
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<DurationGauge> getResultClass() {
            return DurationGauge.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DurationGauge create(final MetricsFactory factory) {
            return factory.createDurationGauge(this);
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
                    .append("timeUnit", timeUnit)
                    .toString();
        }
    }
}
