/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.metrics;

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

@SuppressWarnings("unused")
public abstract class AbstractMetricConfigBuilder<T extends Metric, C extends MetricConfig<T>, B extends AbstractMetricConfigBuilder<T, C, B>> {

    private String category;
    private String name;
    private String description;
    private String unit;
    private String format;


    protected AbstractMetricConfigBuilder(final String category, final String name) {
        this(category, name, name, "", "%s");
    }

    protected AbstractMetricConfigBuilder(final String category, final String name, final String description, final String unit, final String format) {
        this.category = throwArgBlank(category, "category");
        this.name = throwArgBlank(name, "name");
        this.description = MetricConfig.checkDescription(description);
        this.unit = throwArgNull(unit, "unit");
        this.format = throwArgBlank(format, "format");
    }

    protected AbstractMetricConfigBuilder(final MetricConfig<?> config) {
        this.category = config.category();
        this.name = config.name();
        this.description = config.description();
        this.unit = config.unit();
        this.format = config.format();
    }

    /**
     * Gets the category
     *
     * @return the category
     */
    public String getCategory() {
        return category;
    }

    /**
     * Sets the category in fluent style.
     *
     * @param category the category
     * @return a new builder-object with updated category
     * @throws NullPointerException if {@code category} is {@code null}
     * @throws IllegalArgumentException if {@code category} is blank
     */
    public B withCategory(final String category) {
        this.category = throwArgBlank(category, "category");
        return self();
    }

    /**
     * Gets the name
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name in fluent style.
     *
     * @param name the name
     * @return a new builder-object with updated name
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank
     */
    public B withName(final String name) {
        this.name = throwArgBlank(name, "name");
        return self();
    }

    /**
     * Gets the description
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description in fluent style.
     *
     * @param description the description
     * @return a new builder-object with updated description
     * @throws NullPointerException if {@code description} is {@code null}
     * @throws IllegalArgumentException if {@code description} is too long or blank
     */
    public B withDescription(final String description) {
        this.description = MetricConfig.checkDescription(description);
        return self();
    }

    /**
     * Gets the unit
     *
     * @return the unit
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Sets the unit in fluent style.
     *
     * @param unit the unit
     * @return a new builder-object with updated unit
     * @throws NullPointerException if {@code unit} is {@code null}
     */
    protected B withUnit(final String unit) {
        this.unit = throwArgNull(unit, "unit");
        return self();
    }

    /**
     * Gets the format
     *
     * @return the format
     */
    public String getFormat() {
        return format;
    }

    /**
     * Sets the format in fluent style.
     *
     * @param format the format
     * @return a new builder-object with updated format
     * @throws NullPointerException if {@code format} is {@code null}
     * @throws IllegalArgumentException if {@code format} is blank
     */
    protected B withFormat(final String format) {
        this.format = throwArgBlank(format, "format");
        return self();
    }

    public abstract C build();

    protected abstract B self();
}
