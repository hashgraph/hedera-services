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

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;

/**
 * An instance of {@code MetricConfig} contains all configuration parameters needed to create a {@link Metric}.
 * <p>
 * This class is abstract and contains only common parameters. If you want to define the configuration for a specific
 * {@code Metric}, there are special purpose configuration objects (e.g. {@link Counter.Config}).
 * <p>
 * A {@code MetricConfig} should be used with {@link Metrics#getOrCreate(MetricConfig)} to create a new {@code Metric}
 * <p>
 * This class is immutable, changing a parameter creates a new instance.
 *
 * @param <T> the {@code Class} for which the configuration is
 */
public interface MetricConfig<T extends Metric> {

    int MAX_DESCRIPTION_LENGTH = 255;

    static String checkDescription(final String description) {
        throwArgBlank(description, "description");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    String.format("Description must be less than %d characters", MAX_DESCRIPTION_LENGTH));
        }
        return description;
    }

    /**
     * Gets the {@code category}
     *
     * @return the {@code category}
     */
    String category();

    /**
     * @deprecated Please use {@link #category()} instead
     */
    @Deprecated(forRemoval = true)
    default String getCategory() {
        return category();
    }

    /**
     * Gets the {@code name}
     *
     * @return the {@code name}
     */
    String name();

    /**
     * @deprecated Please use {@link #name()} instead
     */
    @Deprecated(forRemoval = true)
    default String getName() {
        return name();
    }

    /**
     * Gets the {@code description}
     *
     * @return the {@code description}
     */
    String description();

    /**
     * @deprecated Please use {@link #description()} instead
     */
    @Deprecated(forRemoval = true)
    default String getDescription() {
        return description();
    }

    /**
     * Gets the {@code unit}
     *
     * @return the {@code unit}
     */
    String unit();

    /**
     * @deprecated Please use {@link #unit()} instead
     */
    @Deprecated(forRemoval = true)
    default String getUnit() {
        return unit();
    }

    /**
     * Gets the {@code format}
     *
     * @return the format-{@code String}
     */
    String format();

    /**
     * @deprecated Please use {@link #format()} instead
     */
    @Deprecated(forRemoval = true)
    default String getFormat() {
        return format();
    }

    /**
     * Gets the class of the {@code Metric} that this configuration is meant for
     *
     * @return the {@code Class}
     * @deprecated this feature will be removed soon
     */
    @Deprecated(forRemoval = true)
    Class<T> getResultClass();

    /**
     * Create a {@code Metric} using the given {@link MetricsFactory}
     *
     * Implementation note: we use the double-dispatch pattern when creating a {@link Metric}. More details
     * can be found at {@link Metrics#getOrCreate(MetricConfig)}.
     *
     * @param factory
     * 		the {@code MetricFactory}
     * @return the new {@code Metric}-instance
     */
    T create(final MetricsFactory factory);

    static Metric.DataType mapDataType(final Class<?> type) {
        if (Double.class.equals(type) || Float.class.equals(type)) {
            return Metric.DataType.FLOAT;
        }
        if (Number.class.isAssignableFrom(type)) {
            return Metric.DataType.INT;
        }
        if (Boolean.class.equals(type)) {
            return Metric.DataType.BOOLEAN;
        }
        return Metric.DataType.STRING;
    }
}
