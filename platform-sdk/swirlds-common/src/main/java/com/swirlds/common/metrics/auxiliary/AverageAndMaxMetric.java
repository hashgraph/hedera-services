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

package com.swirlds.common.metrics.auxiliary;

import static com.swirlds.common.utility.CommonUtils.throwArgBlank;
import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.metrics.AbstractMetricConfigBuilder;
import com.swirlds.common.metrics.AbstractMetricGroup;
import com.swirlds.common.metrics.MetricConfig;
import com.swirlds.common.metrics.MetricsFactory;

/**
 * A metric that tracks the average and maximum value of an integer.
 */
public class AverageAndMaxMetric extends AbstractMetricGroup {

    private final AvgIntegerMetric avgMetric;
    private final MaxIntegerMetric maxMetric;

    public AverageAndMaxMetric(final MetricsFactory factory, final Config config) {
        super(factory, config);

        AvgIntegerMetric.Config avgConfig = new AvgIntegerMetric.ConfigBuilder(config).build();
        this.avgMetric = createMetric("avg", avgConfig);

        final MaxIntegerMetric.Config maxConfig = new MaxIntegerMetric.ConfigBuilder(config)
                .withName(config.name() + "MAX")
                .withDescription("The maximum value of " + config.name())
                .build();
        this.maxMetric = createMetric("max", maxConfig);
    }

    public void update(final int value) {
        avgMetric.update(value);
        maxMetric.update(value);
    }

    public double getAvg() {
        return avgMetric.get();
    }

    public int getMax() {
        return maxMetric.get();
    }


    /**
     * A configuration for an {@link AverageAndMaxMetric}.
     */
    public record Config(
            String category,
            String name,
            String description,
            String unit,
            String format) implements MetricConfig<AverageAndMaxMetric> {
        public Config {
            throwArgBlank(category, "category");
            throwArgBlank(name, "name");
            MetricConfig.checkDescription(description);
            throwArgNull(unit, "unit");
            throwArgBlank(format, "format");
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated This functionality will be removed soon
         */
        @SuppressWarnings("removal")
        @Deprecated(forRemoval = true)
        @Override
        public Class<AverageAndMaxMetric> getResultClass() {
            return AverageAndMaxMetric.class;
        }

        @Override
        public AverageAndMaxMetric create(final MetricsFactory factory) {
            return new AverageAndMaxMetric(factory, this);
        }
    }

    /**
     * A builder for a {@link Config}.
     */
    public static class ConfigBuilder extends AbstractMetricConfigBuilder<AverageAndMaxMetric, Config, ConfigBuilder> {

        public ConfigBuilder(final String category, final String name) {
            super(category, name);
        }

        @Override
        public ConfigBuilder withUnit(String unit) {
            return super.withUnit(unit);
        }

        @Override
        public ConfigBuilder withFormat(String format) {
            return super.withFormat(format);
        }

        @Override
        public Config build() {
            return new Config(getCategory(), getName(), getDescription(), getUnit(), getFormat());
        }

        @Override
        protected ConfigBuilder self() {
            return this;
        }
    }

}
