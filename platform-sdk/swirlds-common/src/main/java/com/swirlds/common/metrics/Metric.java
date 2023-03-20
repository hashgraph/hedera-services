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

import com.swirlds.common.statistics.StatsBuffered;
import java.util.EnumSet;
import java.util.Map;

/**
 * A single metric that is monitored here.
 */
public interface Metric {

    /**
     * A {@code Metric} can keep track of several values, which are distinguished via the {@code ValueType}
     *
     * @deprecated {@code ValueType} turned out to be too limited. We will use {@code String}s instead.
     */
    @Deprecated(forRemoval = true)
    enum ValueType {
        VALUE("mean"),
        MAX("max"),
        MIN("min"),
        STD_DEV("stddev");

        private final String label;

        ValueType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * The broad data-type of a {@code Metric}. We differentiate between integer numbers,
     * floating point numbers, strings and booleans.
     */
    enum DataType {
        INT,
        FLOAT,
        STRING,
        BOOLEAN,
        MIXED
    }

    /**
     * Returns the unique identifier of this {@code Metric}.
     * <p>
     * A {@code Metric} is identified by its category and name.
     *
     * @return the unique identifier
     */
    default String getIdentifier() {
        return getCategory() + "." + getName();
    }

    /**
     * the kind of {@code Metric} (metrics are grouped or filtered by this)
     *
     * @return the category
     */
    String getCategory();

    /**
     * a short name for the {@code Metric}
     *
     * @return the name
     */
    String getName();

    /**
     * a one-sentence description of the {@code Metric}
     *
     * @return the description
     */
    String getDescription();

    /**
     * The unit of the {@code Metric}
     *
     * @return the unit
     */
    String getUnit();

    /**
     * a string that can be passed to String.format() to format the {@code Metric}
     *
     * @return the format
     */
    String getFormat();

    /**
     * The metric type of the {@code Metric}
     *
     * @return the metric type
     * @deprecated {@link MetricType} turned out to be too limited. You can use the class-name instead.
     */
    @SuppressWarnings("removal")
    @Deprecated(forRemoval = true)
    MetricType getMetricType();

    /**
     * The data-type of the {@code BaseMetric}
     *
     * @return the data-type
     */
    DataType getDataType();

    /**
     * Returns {@code true} if the {@code Metric} can track more than one value (i.e. a metric that tracks an average
     * and maximum value).
     *
     * @return {@code true} if the {@code Metric} can collect more than one value
     */
    default boolean isMetricGroup() {
        return false;
    }

    /**
     * Returns a list of {@link ValueType}s that are provided by this {@code Metric}. The list of {@code ValueTypes}
     * will always be in the same order.
     * <p>
     * All values returned by this method have to be supported by an implementing class.
     *
     * @return the set of {@code ValueTypes}
     * @deprecated {@code ValueType} turned out to be too limited. You can get sub-metrics via
     * {@link #getBaseMetrics()}.
     */
    @Deprecated(forRemoval = true)
    EnumSet<ValueType> getValueTypes();

    /**
     * Returns the current value of the given {@link ValueType}.
     * <p>
     * The option {@link ValueType#VALUE} has a special meaning in this context. It is always supported and returns the
     * "main" value of the {@code Metric}. I.e. even if you do not know the type of {@code Metric}, it is always safe to
     * call this method with the parameter {@code ValueType.VALUE}.
     *
     * @param valueType The {@code ValueType} of the requested value
     * @return the current value
     * @throws IllegalArgumentException if the given {@code ValueType} is {@code null} or not supported by this
     * {@code Metric}
     * @deprecated {@code ValueType} turned out to be too limited. You can get sub-metrics via
     * {@link #getBaseMetrics()}.
     */
    @Deprecated(forRemoval = true)
    Object get(final ValueType valueType);

    /**
     * Returns a flattened map of all sub-metrics of this {@code Metric}.
     *
     * @return a map of all sub-metrics
     */
    Map<String, BaseMetric> getBaseMetrics();

    /**
     * This method resets a {@code Metric}. It is for example called after startup to ensure that the startup time is
     * not taken into consideration.
     */
    void reset();

    /**
     * This method returns the {@link StatsBuffered} of this metric, if there is one.
     * <p>
     * This method is only used to simplify the migration and will be removed afterwards
     *
     * @return the {@code StatsBuffered}, if there is one, {@code null} otherwise
     * @deprecated This method is only temporary and will be removed soon
     */
    @Deprecated(forRemoval = true)
    default StatsBuffered getStatsBuffered() {
        return null;
    }

    /**
     * Overwritten {@code equals}-method. Two {@code Metric}-instances are considered equal, if they have the same
     * {@code Class}, {@link #getCategory() category}, and {@link #getName() name}.
     *
     * @param other the other {@code Object}
     * @return {@code true}, if both {@code Metric}-instances are considered equal, {@code false} otherwise
     */
    @Override
    boolean equals(final Object other);

    /**
     * Overwritten {@code hashCode}-method, that matches the overwritten {@link #equals(Object)}-method.
     *
     * @return the calculated hash-code
     */
    @Override
    int hashCode();
}
