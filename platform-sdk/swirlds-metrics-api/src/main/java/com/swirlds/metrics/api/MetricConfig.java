/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.metrics.api;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.base.utility.ToStringBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

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
@SuppressWarnings("removal")
public abstract class MetricConfig<T extends Metric, C extends MetricConfig<T, C>> {

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final @NonNull String category;
    private final @NonNull String name;

    private final @NonNull String description;
    private final @NonNull String unit;
    private final @NonNull String format;

    private final @NonNull Set<Label> predefinedLabels;

    private final @NonNull Set<String> labelKeys;

    /**
     * Constructor of {@code MetricConfig}
     *
     * @param category the kind of metric (metrics are grouped or filtered by this)
     * @param name     a short name for the metric
     * @param description a description for the metric
     * @param unit     the unit of the metric
     * @param format   a string that can be passed to String.format() to format the metric
     * @param predefinedLabels a set of predefined labels
     * @param labelKeys a set of label keys
     * @throws NullPointerException     if one of the parameters is {@code null}
     * @throws IllegalArgumentException if one of the parameters is consists only of whitespaces
     */
    protected MetricConfig(
            final @NonNull String category,
            final @NonNull String name,
            final @NonNull String description,
            final @NonNull String unit,
            final @NonNull String format,
            final @NonNull Set<Label> predefinedLabels,
            final @NonNull Set<String> labelKeys) {

        this.category = ArgumentUtils.throwArgBlank(category, "category");
        this.name = ArgumentUtils.throwArgBlank(name, "name");
        this.description = ArgumentUtils.throwArgBlank(description, "description");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Description has " + description.length() + " characters, must not be longer than "
                            + MAX_DESCRIPTION_LENGTH + " characters: "
                            + description);
        }
        this.unit = Objects.requireNonNull(unit, "unit must not be null");
        this.format = ArgumentUtils.throwArgBlank(format, "format must not be null");
        this.labelKeys = Collections.unmodifiableSet(Objects.requireNonNull(labelKeys, "labelKeys must not be null"));
        this.predefinedLabels = Collections.unmodifiableSet(
                Objects.requireNonNull(predefinedLabels, "predefinedLabels must not be null"));
    }

    /**
     * Constructor of {@code MetricConfig}
     *
     * @param category    the kind of metric (metrics are grouped or filtered by this)
     * @param name        a short name for the metric
     * @param description a description for the metric
     * @param unit        the unit of the metric
     * @param format      a string that can be passed to String.format() to format the metric
     * @throws NullPointerException     if one of the parameters is {@code null}
     * @throws IllegalArgumentException if one of the parameters is consists only of whitespaces
     */
    protected MetricConfig(
            final @NonNull String category,
            final @NonNull String name,
            final @NonNull String description,
            final @NonNull String unit,
            final @NonNull String format) {
        this(category, name, description, unit, format, Set.of(), Set.of());
    }

    /**
     * Constructor of {@code MetricConfig}
     *
     * @param category      the kind of metric (metrics are grouped or filtered by this)
     * @param name          a short name for the metric
     * @param defaultFormat a string that can be passed to String.format() to format the metric
     * @throws IllegalArgumentException if one of the parameters is {@code null} or consists only of whitespaces
     */
    protected MetricConfig(
            final @NonNull String category, final @NonNull String name, final @NonNull String defaultFormat) {
        this(category, name, name, "", defaultFormat);
    }

    /**
     * Getter of the {@link Metric#getCategory() Metric.category}
     *
     * @return the {@code category}
     */
    @NonNull
    public String getCategory() {
        return category;
    }

    /**
     * Getter of the {@link Metric#getName() Metric.name}
     *
     * @return the {@code name}
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Getter of the {@link Metric#getDescription() Metric.description}
     *
     * @return the {@code description}
     */
    @NonNull
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
    public abstract @NonNull C withDescription(final String description);

    /**
     * Getter of the {@link Metric#getUnit() Metric.unit}
     *
     * @return the {@code unit}
     */
    public @NonNull String getUnit() {
        return unit;
    }

    /**
     * Sets the {@link Metric#getUnit() Metric.unit} in fluent style.
     *
     * @param unit the unit
     * @return a new configuration-object with updated {@code unit}
     * @throws IllegalArgumentException if {@code unit} is {@code null}
     */
    public abstract @NonNull C withUnit(final String unit);

    @NonNull
    public Set<Label> getPredefinedLabels() {
        return predefinedLabels;
    }

    @NonNull
    public Set<String> getLabelKeys() {
        return labelKeys;
    }

    /**
     * Getter of the {@link Metric#getFormat() Metric.format}
     *
     * @return the format-{@code String}
     */
    public @NonNull String getFormat() {
        return format;
    }

    /**
     * Class of the {@code Metric} that this configuration is meant for
     *
     * @return the {@code Class}
     */
    public abstract @NonNull Class<T> getResultClass();

    /**
     * Create a {@code Metric} using the given {@link MetricsFactory}
     * <p>
     * Implementation note: we use the double-dispatch pattern when creating a {@link Metric}. More details can be found
     * at {@link Metrics#getOrCreate(MetricConfig)}.
     *
     * @param factory the {@code MetricFactory}
     * @return the new {@code Metric}-instance
     */
    @NonNull
    public abstract T create(final MetricsFactory factory);

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
                .append("resultClass", getResultClass())
                .toString();
    }

    public static Metric.DataType mapDataType(final Class<?> type) {
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
