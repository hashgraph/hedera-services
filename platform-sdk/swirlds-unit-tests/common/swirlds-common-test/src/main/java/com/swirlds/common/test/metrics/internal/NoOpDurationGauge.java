/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.metrics.internal;

import com.swirlds.common.metrics.DurationGauge;
import com.swirlds.common.metrics.MetricConfig;
import java.time.Duration;

/**
 * A no-op implementation of a duration gauge.
 */
public class NoOpDurationGauge extends AbstractNoOpMetric implements DurationGauge {

    public NoOpDurationGauge(final MetricConfig<?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getNanos() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final Duration duration) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public double get() {
        return 0;
    }
}
