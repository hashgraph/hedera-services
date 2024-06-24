/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.noop.internal;

import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.metrics.api.MetricConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A no-op implementation of a speedometer metric.
 */
public class NoOpSpeedometerMetric extends AbstractNoOpMetric implements SpeedometerMetric {

    public NoOpSpeedometerMetric(final @NonNull MetricConfig<?, ?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Double get(final @NonNull ValueType valueType) {
        return 0.0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getHalfLife() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final double value) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void cycle() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public double get() {
        return 0;
    }
}
