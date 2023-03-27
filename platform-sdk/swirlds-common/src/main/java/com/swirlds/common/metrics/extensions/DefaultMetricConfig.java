/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;

import com.swirlds.common.metrics.Metric;

/**
 * A default configuration for a metrics with values that all metrics need
 */
public class DefaultMetricConfig {
    private final String category;
    private final String name;
    private final String description;

    /**
     * @param category the kind of {@code Metric} (metrics are grouped or filtered by this)
     * @param name a short name for the {@code Metric}
     * @param description a one-sentence description of the {@code Metric}
     */
    public DefaultMetricConfig(final String category, final String name, final String description) {
        this.category = throwArgBlank(category, "category");
        this.name = throwArgBlank(name, "name");
        this.description = throwArgBlank(description, "description");
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
}
