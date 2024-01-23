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

import com.swirlds.metrics.api.IntegerAccumulator;
import com.swirlds.metrics.api.MetricConfig;

/**
 * A no-op implementation of an integer accumulator.
 */
public class NoOpIntegerAccumulator extends AbstractNoOpMetric implements IntegerAccumulator {

    public NoOpIntegerAccumulator(final MetricConfig<?, ?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int get() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInitialValue() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final int other) {}
}
