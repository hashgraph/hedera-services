/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.base.ArgumentUtils;
import com.swirlds.base.units.UnitConstants;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Stores a single duration. The output unit is determined by configuration
 */
public interface DurationGauge extends Metric {

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
        return DataType.FLOAT;
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
    default Double get(@NonNull final ValueType valueType) {
        Objects.requireNonNull(valueType, "valueType");
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
     * @param duration the value to set the gauge to
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
    final class Config extends PlatformMetricConfig<DurationGauge, Config> {

        private static final String TIME_UNIT = "timeUnit";
        private static final String UNSUPPORTED_TIME_UNIT = "Unsupported time unit: ";

        private final ChronoUnit timeUnit;

        /**
         * Constructor of {@code DoubleGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name     a short name for the metric
         * @param timeUnit the time unit in which to display this duration
         * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(@NonNull final String category, @NonNull final String name, final ChronoUnit timeUnit) {
            super(category, fixName(name, timeUnit), getFormat(timeUnit));
            this.timeUnit = timeUnit;
        }

        private static String fixName(@NonNull final String name, final ChronoUnit timeUnit) {
            return ArgumentUtils.throwArgBlank(name, "name") + " " + getAppendix(timeUnit);
        }

        private Config(
                @NonNull final String category,
                @NonNull final String name,
                @NonNull final String description,
                final ChronoUnit timeUnit) {
            super(category, name, description, getUnit(timeUnit), getFormat(timeUnit));
            // at this point, timeUnit was checked for null in getUnit() and getFormat()
            this.timeUnit = timeUnit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public DurationGauge.Config withDescription(@NonNull final String description) {
            return new DurationGauge.Config(getCategory(), getName(), description, getTimeUnit());
        }

        /**
         * The unit of a {@link DurationGauge} depends on the configured {@link ChronoUnit}. Therefore, it is not
         * possible to specify the unit and this method throws an {@link UnsupportedOperationException}
         */
        @Override
        public DurationGauge.Config withUnit(@NonNull final String unit) {
            throw new UnsupportedOperationException("a String unit is not compatible with this class");
        }

        /**
         * Getter of the {@code timeUnit}
         *
         * @return the {@code timeUnit}
         */
        public ChronoUnit getTimeUnit() {
            return timeUnit;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<DurationGauge> getResultClass() {
            return DurationGauge.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public DurationGauge create(@NonNull final PlatformMetricsFactory factory) {
            return factory.createDurationGauge(this);
        }

        @NonNull
        private static String getFormat(@NonNull final ChronoUnit timeUnit) {
            Objects.requireNonNull(timeUnit, TIME_UNIT);
            return switch (timeUnit) {
                case NANOS, MICROS -> FloatFormats.FORMAT_DECIMAL_0;
                case MILLIS, SECONDS -> FloatFormats.FORMAT_DECIMAL_3;
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }

        @NonNull
        private static String getUnit(final ChronoUnit timeUnit) {
            Objects.requireNonNull(timeUnit, TIME_UNIT);
            return switch (timeUnit) {
                case NANOS -> UnitConstants.NANOSECOND_UNIT;
                case MICROS -> UnitConstants.MICROSECOND_UNIT;
                case MILLIS -> UnitConstants.MILLISECOND_UNIT;
                case SECONDS -> UnitConstants.SECOND_UNIT;
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }

        @NonNull
        private static String getAppendix(final ChronoUnit timeUnit) {
            Objects.requireNonNull(timeUnit, TIME_UNIT);
            return switch (timeUnit) {
                case NANOS -> "(nanos)";
                case MICROS -> "(micros)";
                case MILLIS -> "(millis)";
                case SECONDS -> "(sec)";
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }
    }
}
