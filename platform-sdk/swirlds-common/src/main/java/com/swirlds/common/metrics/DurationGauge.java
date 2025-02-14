// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.metrics;

import static com.swirlds.metrics.api.Metric.ValueType.VALUE;

import com.swirlds.base.units.UnitConstants;
import com.swirlds.metrics.api.FloatFormats;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricType;
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
    @NonNull
    @Override
    default MetricType getMetricType() {
        return MetricType.GAUGE;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    default DataType getDataType() {
        return DataType.FLOAT;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    default Double get(@NonNull final ValueType valueType) {
        Objects.requireNonNull(valueType, "valueType must not be null");
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

        private static final String UNSUPPORTED_TIME_UNIT = "Unsupported time unit: ";

        private final @NonNull ChronoUnit timeUnit;

        /**
         * Constructor of {@code DoubleGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name     a short name for the metric
         * @param timeUnit the time unit in which to display this duration
         * @throws NullPointerException     if one of the parameters is {@code null}
         * @throws IllegalArgumentException if one of the parameters consists only of whitespaces
         */
        public Config(@NonNull final String category, @NonNull final String name, final @NonNull ChronoUnit timeUnit) {
            super(category, name, name, getUnit(timeUnit), getFormat(timeUnit));
            this.timeUnit = timeUnit;
        }

        /**
         * Constructor of {@code DoubleGauge.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name     a short name for the metric
         * @param description metric description
         * @param timeUnit the time unit in which to display this duration
         * @throws NullPointerException     if one of the parameters is {@code null}
         * @throws IllegalArgumentException if one of the parameters consists only of whitespaces
         */
        private Config(
                @NonNull final String category,
                @NonNull final String name,
                @NonNull final String description,
                final @NonNull ChronoUnit timeUnit) {
            super(category, name, description, getUnit(timeUnit), getFormat(timeUnit));
            // at this point, timeUnit was checked for null in getUnit() and getFormat()
            this.timeUnit = timeUnit;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public DurationGauge.Config withDescription(@NonNull final String description) {
            return new DurationGauge.Config(getCategory(), getName(), description, getTimeUnit());
        }

        /**
         * The unit of a {@link DurationGauge} depends on the configured {@link ChronoUnit}. Therefore, it is not
         * possible to specify the unit and this method throws an {@link UnsupportedOperationException}
         */
        @NonNull
        @Override
        public DurationGauge.Config withUnit(@NonNull final String unit) {
            throw new UnsupportedOperationException("a String unit is not compatible with this class");
        }

        /**
         * Getter of the {@code timeUnit}
         *
         * @return the {@code timeUnit}
         */
        @NonNull
        public ChronoUnit getTimeUnit() {
            return timeUnit;
        }

        /**
         * {@inheritDoc}
         */
        @NonNull
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
            Objects.requireNonNull(timeUnit, "timeUnit must not be null");
            return switch (timeUnit) {
                case NANOS, MICROS -> FloatFormats.FORMAT_DECIMAL_0;
                case MILLIS, SECONDS -> FloatFormats.FORMAT_DECIMAL_3;
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }

        @NonNull
        private static String getUnit(final ChronoUnit timeUnit) {
            Objects.requireNonNull(timeUnit, "timeUnit must not be null");
            return switch (timeUnit) {
                case NANOS -> UnitConstants.NANOSECOND_UNIT;
                case MICROS -> UnitConstants.MICROSECOND_UNIT;
                case MILLIS -> UnitConstants.MILLISECOND_UNIT;
                case SECONDS -> UnitConstants.SECOND_UNIT;
                default -> throw new IllegalArgumentException(UNSUPPORTED_TIME_UNIT + timeUnit);
            };
        }
    }
}
