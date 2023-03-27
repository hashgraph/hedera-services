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

package com.swirlds.common.metrics.extensions;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_10_2;
import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.base.time.Time;
import com.swirlds.base.time.TimeFacade;
import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.metrics.Metric;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.utility.Units;

/**
 * Platform-implementation of {@link CountPerSecond}. The granularity of this metric is a millisecond. This metric needs
 * to be reset once every 25 days in order to remain accurate. If not reset at this interval, it will no longer provide
 * accurate data. Every time a snapshot is taken (which is way more frequent than 25 days) the value is reset, because
 * of this, it is highly unlikely to get inaccurate data.
 */
public class CountPerSecond {
    /** An instance that provides the current time */
    private final Time time;

    /** Used to atomically update and reset the time and count */
    private final IntegerPairAccumulator<Double> accumulator;

    /**
     * The default constructor, uses the {@link TimeFacade#getOsTime()}
     *
     * @param config the configuration for this metric
     */
    public CountPerSecond(final Metrics metrics, final CountPerSecond.Config config) {
        this(metrics, config, TimeFacade.getOsTime());
    }

    /**
     * A constructor where a custom {@link Time} instance could be supplied
     *
     * @param config the configuration for this metric
     * @param time   provides the current time
     */
    public CountPerSecond(final Metrics metrics, final CountPerSecond.Config config, final Time time) {
        this.time = time;
        this.accumulator = metrics.getOrCreate(new IntegerPairAccumulator.Config<>(
                        config.getCategory(), config.getName(), Double.class, this::perSecond)
                .withDescription(config.getDescription())
                .withUnit(config.getUnit())
                .withFormat(config.getFormat())
                .withLeftAccumulator(CountPerSecond::noChangeAccumulator)
                .withRightAccumulator(Integer::sum)
                .withLeftInitializer(this::getMilliTime)
                .withRightInitialValue(0));
    }

    /**
     * @return the lower 32 bits of the current millisecond epoch according to the clock stored in this instance
     */
    public int getMilliTime() {
        return (int) (time.currentTimeMillis() % Integer.MAX_VALUE);
    }

    /**
     * Increase the count by 1
     */
    public void count() {
        count(1);
    }

    /**
     * Increase the count by the value provided
     *
     * @param count the amount to increase the count by
     */
    public void count(final int count) {
        accumulator.update(0, count);
    }

    /**
     * Calculates the count per second from the time provided time until now
     *
     * @param startTime the time at which we started counting
     * @param count     the count
     * @return the count per second
     */
    private double perSecond(final int startTime, final int count) {
        final int currentTime = getMilliTime();
        final int millisElapsed;
        if (currentTime == startTime) {
            // theoretically this is infinity, but we will say that 1 millisecond passed because some time has to have
            // passed
            millisElapsed = 1;
        } else if (currentTime > startTime) {
            millisElapsed = currentTime - startTime;
        } else {
            // if the lower 31 bits of the milli epoch has rolled over, the start time will be bigger than current time
            millisElapsed = Integer.MAX_VALUE - startTime + currentTime;
        }

        return count / (millisElapsed * Units.MILLISECONDS_TO_SECONDS);
    }

    /**
     * An implementation of a {@link com.swirlds.common.metrics.IntegerAccumulator} that does not change the value
     */
    private static int noChangeAccumulator(final int currentValue, final int toUpdate) {
        return currentValue;
    }

    /**
     * This method resets a {@code Metric}. It is for example called after startup to ensure that the startup time is
     * not taken into consideration.
     */
    public void reset() {
        accumulator.reset();
    }

    /**
     * @return the current count per second
     */
    public double get() {
        return accumulator.get();
    }

    /**
     * Configuration of a {@link LongAccumulator}
     */
    public static final class Config {

        private final String category;
        private final String name;

        private final String description;
        private final String unit;
        private final String format;

        /**
         * Constructor of {@link CountPerSecond.Config}
         *
         * @param category the kind of metric (metrics are grouped or filtered by this)
         * @param name     a short name for the metric
         */
        public Config(final String category, final String name) {
            this(category, name, name, "1/s", FORMAT_10_2);
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format) {
            this.category = throwArgBlank(category, "category");
            this.name = throwArgBlank(name, "name");
            this.description = throwArgBlank(description, "description");
            this.unit = throwArgNull(unit, "unit");
            this.format = throwArgBlank(format, "format");
        }

        /**
         * Getter of the {@link Metric#getCategory() Metric.category}
         *
         * @return the {@code category}
         */
        public String getCategory() {
            return category;
        }

        /**
         * Getter of the {@link Metric#getName() Metric.name}
         *
         * @return the {@code name}
         */
        public String getName() {
            return name;
        }

        /**
         * Getter of the {@link Metric#getDescription() Metric.description}
         *
         * @return the {@code description}
         */
        public String getDescription() {
            return description;
        }

        /**
         * Sets the {@link Metric#getDescription() Metric.description} in fluent style.
         *
         * @param description the description
         * @return a new configuration-object with updated {@code description}
         * @throws IllegalArgumentException if {@code description} is {@code null}, too long or consists only of
         *                                  whitespaces
         */
        public CountPerSecond.Config withDescription(final String description) {
            return new CountPerSecond.Config(getCategory(), getName(), description, getUnit(), getFormat());
        }

        /**
         * Getter of the {@link Metric#getUnit() Metric.unit}
         *
         * @return the {@code unit}
         */
        public String getUnit() {
            return unit;
        }

        /**
         * Sets the {@link Metric#getUnit() Metric.unit} in fluent style.
         *
         * @param unit the unit
         * @return a new configuration-object with updated {@code unit}
         * @throws IllegalArgumentException if {@code unit} is {@code null}
         */
        public CountPerSecond.Config withUnit(final String unit) {
            return new CountPerSecond.Config(getCategory(), getName(), getDescription(), unit, getFormat());
        }

        /**
         * Getter of the {@link Metric#getFormat() Metric.format}
         *
         * @return the format-{@code String}
         */
        public String getFormat() {
            return format;
        }

        /**
         * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
         *
         * @param format the format-string
         * @return a new configuration-object with updated {@code format}
         * @throws IllegalArgumentException if {@code format} is {@code null} or consists only of whitespaces
         */
        public CountPerSecond.Config withFormat(final String format) {
            return new CountPerSecond.Config(getCategory(), getName(), getDescription(), getUnit(), format);
        }
    }
}
